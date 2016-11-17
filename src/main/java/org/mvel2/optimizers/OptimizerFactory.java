/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.optimizers;

import org.mvel2.optimizers.dynamic.DynamicOptimizer;
import org.mvel2.optimizers.impl.asm.ASMAccessorOptimizer;
import org.mvel2.optimizers.impl.refl.ReflectiveAccessorOptimizer;

import java.util.HashMap;
import java.util.Map;

/**
 * 优化器工厂，用于创建或者管理不同的优化器实例
 */
public class OptimizerFactory {
  /** 通过动态转换(从asm和reflect之间)的访问模式 */
  public static String DYNAMIC = "dynamic";
  /** 通过反映进行属性，方法访问的处理模式 */
  public static String SAFE_REFLECTIVE = "reflective";

  /** 默认的优化器 */
  private static String defaultOptimizer;

  /** 用于存储多个优化器 */
  private static final Map<String, AccessorOptimizer> accessorCompilers = new HashMap<String, AccessorOptimizer>();

  /** 对当前线程优化器的持有引用，以方便获取和处理 */
  private static ThreadLocal<Class<? extends AccessorOptimizer>> threadOptimizer
      = new ThreadLocal<Class<? extends AccessorOptimizer>>();

  static {
    accessorCompilers.put(SAFE_REFLECTIVE, new ReflectiveAccessorOptimizer());
    accessorCompilers.put(DYNAMIC, new DynamicOptimizer());
    /**
     * 因为asm已经内置到mvel中，因此这里的启动一定会成功。这里即启用asm优化器
     * 并且dynamic优化器也依赖于ASM，从整个代码层面来看，以下的代码一定会成功
     * By default, activate the JIT if ASM is present in the classpath
     */
    try {
      if (OptimizerFactory.class.getClassLoader() != null) {
        OptimizerFactory.class.getClassLoader().loadClass("org.mvel2.asm.ClassWriter");
      }
      else {
        ClassLoader.getSystemClassLoader().loadClass("org.mvel2.asm.ClassWriter");
      }
      accessorCompilers.put("ASM", new ASMAccessorOptimizer());
    }
    catch (ClassNotFoundException e) {
      defaultOptimizer = SAFE_REFLECTIVE;
    }
    catch (Throwable e) {
      e.printStackTrace();
      System.err.println("[MVEL] Notice: Possible incorrect version of ASM present (3.0 required).  " +
          "Disabling JIT compiler.  Reflective Optimizer will be used.");
      defaultOptimizer = SAFE_REFLECTIVE;
    }

    //因为已经内置了asm处理，因此除非显示的禁用jit，一定会采用dynamic优化器处理
    if (Boolean.getBoolean("mvel2.disable.jit"))
      setDefaultOptimizer(SAFE_REFLECTIVE);
    else
      setDefaultOptimizer(DYNAMIC);
  }

  /** 获取默认优化器的一个实例 */
  public static AccessorOptimizer getDefaultAccessorCompiler() {
    try {
      return accessorCompilers.get(defaultOptimizer).getClass().newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("unable to instantiate accessor compiler", e);
    }
  }

  /** 获取指定优化器的一个实例 */
  public static AccessorOptimizer getAccessorCompiler(String name) {
    try {
      return accessorCompilers.get(name).getClass().newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("unable to instantiate accessor compiler", e);
    }
  }

  /** 创建出线程优化器的一个实例 */
  public static AccessorOptimizer getThreadAccessorOptimizer() {
    if (threadOptimizer.get() == null) {
      threadOptimizer.set(getDefaultAccessorCompiler().getClass());
    }
    try {
      return threadOptimizer.get().newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("unable to instantiate accessor compiler", e);
    }
  }

  /** 设置当前线程的优化器(实际上没有特别的作用 */
  public static void setThreadAccessorOptimizer(Class<? extends AccessorOptimizer> optimizer) {
    if (optimizer == null) throw new RuntimeException("null optimizer");
    threadOptimizer.set(optimizer);
  }

  /** 设置默认的优化器 */
  public static void setDefaultOptimizer(String name) {
    try {
      //noinspection unchecked
      AccessorOptimizer ao = accessorCompilers.get(defaultOptimizer = name);
      //保证被静态初始化了
      ao.init();
      //clear optimizer so next call to getThreadAccessorOptimizer uses the default again, don't set thread optimizer
      //or else static initializers setting the default will unintentionally set up ThreadLocals
      //这里清除当前线程内的优化器，以保证下次能够从最新的默认优化器中获取
      //这里不是重新设置的原因在于，可能处理中还不需要使用到,如在程序启动时设置，那么就不需要显式地进行设置
      threadOptimizer.set(null);
    }
    catch (Exception e) {
      throw new RuntimeException("unable to instantiate accessor compiler", e);
    }
  }

  /** 清除相应的优化器(已使用完毕) */
  public static void clearThreadAccessorOptimizer() {
    threadOptimizer.set(null);
    threadOptimizer.remove();
  }

  public static boolean isThreadAccessorOptimizerInitialized() {
    return threadOptimizer.get() != null;
  }
}
