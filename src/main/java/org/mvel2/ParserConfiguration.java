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

package org.mvel2;

import org.mvel2.ast.Proto;
import org.mvel2.compiler.AbstractParser;
import org.mvel2.integration.Interceptor;
import org.mvel2.util.MethodStub;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mvel2.util.ParseTools.forNameWithInner;

/**
 * 当前解析上下文中所引用的解析配置，解析配置在一定程度上可以在多个上下文中进行共享和处理
 * 只要保证在多个context中传递即可(即通过构建函数传递)
 * 解析配置主要用于存储相应的引用类信息,因为这些可以认为是全局使用的
 * The reuseable parser configuration object.
 */
public class ParserConfiguration implements Serializable {
  /** 无用属性 */
  @Deprecated
  private static final int MAX_NEGATIVE_CACHE_SIZE;

  /** 使用到的引用的类名或方法名(不全是类名).也可能为方法句柄，或者是静态字段值等 */
  protected Map<String, Object> imports;
  /** 使用到的引用的包名 */
  protected HashSet<String> packageImports;
  /** 相应的拦截器映射组 */
  protected Map<String, Interceptor> interceptors;
  /** 当前解析配置中所引用的类加载器,用于在引用类名时进行加载 */
  protected transient ClassLoader classLoader;

  /** 用于存储一些实际上不能成功使用的import列表，即在动态引入中实际上引入失效的类,上限为1000 */
  private transient Set<String> nonValidImports;

  /** 是否允许伪方法调用 使用Mvel中的系统变量配置 */
  private boolean allowNakedMethCall = MVEL.COMPILER_OPT_ALLOW_NAKED_METH_CALL;

  /**
   * 即是否在编译的时候就允许二次优化,其值默认为true，即此开关是默认开启的
   * 此值会影响到Mvel.compileExpression中的行为，即开启二次优化
   */
  private boolean allowBootstrapBypass = true;

  static {
    String negCacheSize = System.getProperty("mvel2.compiler.max_neg_cache_size");
    if (negCacheSize != null) {
      MAX_NEGATIVE_CACHE_SIZE = Integer.parseInt(negCacheSize);
    }
    else {
      MAX_NEGATIVE_CACHE_SIZE = 1000;
    }
  }

  public ParserConfiguration() {
  }

  /** 按照指定的引入以及拦截器创建起解析配置 */
  public ParserConfiguration(Map<String, Object> imports, Map<String, Interceptor> interceptors) {
    addAllImports(imports);
    this.interceptors = interceptors;
  }

  /** 使用指定的引入+指定的包引入+拦截器创建起解析配置 */
  public ParserConfiguration(Map<String, Object> imports, HashSet<String> packageImports,
                             Map<String, Interceptor> interceptors) {
    addAllImports(imports);
    this.packageImports = packageImports;
    this.interceptors = interceptors;
  }

  public HashSet<String> getPackageImports() {
    return packageImports;
  }

  public void setPackageImports(HashSet<String> packageImports) {
    this.packageImports = packageImports;
  }

  /** 通过引用名获取之前已import进来的类名，并且期望相应的类型为class类型 */
  public Class getImport(String name) {
    if (imports != null && imports.containsKey(name) && imports.get(name) instanceof Class) {
      return (Class) imports.get(name);
    }
    return (Class) (AbstractParser.LITERALS.get(name) instanceof Class ? AbstractParser.LITERALS.get(name) : null);
  }

  /** 获取之前已经导入的静态方法句柄引用 */
  public MethodStub getStaticImport(String name) {
    return imports != null ? (MethodStub) imports.get(name) : null;
  }

  /** 获取之前导入的引用信息(可能为静态引用,也可能为类引用) */
  public Object getStaticOrClassImport(String name) {
    return (imports != null && imports.containsKey(name) ? imports.get(name) : AbstractParser.LITERALS.get(name));
  }

  /**
   * 尝试添加一个包的引用,如果此引用为一个类名或枚举信息，则尝试添加此信息中的公共静态引用
   * 如之前通过此引入一个语句，如通过parseContext.addPackageImport("T")，并且T中有一个公共字段为x
   * 那么在语句就可以直接通过x拿到相应的字段的值
   * 注：或通过在代码中 import T.*;这种也可以达到相同的效果
   */
  public void addPackageImport(String packageName) {
    if (packageImports == null) packageImports = new LinkedHashSet<String>();
    packageImports.add(packageName);
    if (!addClassMemberStaticImports(packageName)) packageImports.add(packageName);
  }

  /** 尝试添加指定枚举类的成员，或者是类的公式字段信息为import中，以方便后续拿到相应的引用 */
  private boolean addClassMemberStaticImports(String packageName) {
    try {
      Class c = Class.forName(packageName);
      initImports();
      //处理枚举信息
      if (c.isEnum()) {

        //noinspection unchecked
        //直接使用枚举的成员作为key来进行处理
        for (Enum e : (EnumSet<?>) EnumSet.allOf(c)) {
          imports.put(e.name(), e);
        }
        return true;
      }
      else {
        //引用公共字段名+相应的值信息
        for (Field f : c.getDeclaredFields()) {
          if ((f.getModifiers() & (Modifier.STATIC | Modifier.PUBLIC)) == (Modifier.STATIC | Modifier.PUBLIC)) {
            imports.put(f.getName(), f.get(null));
          }
        }

      }
    }
    catch (ClassNotFoundException e) {
      // do nothing.
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException("error adding static imports for: " + packageName, e);
    }
    return false;
  }

  /** 将相应的引入添加到import列表当中，并区分方法和类信息 */
  public void addAllImports(Map<String, Object> imports) {
    if (imports == null) return;

    initImports();

    Object o;

    for (Map.Entry<String, Object> entry : imports.entrySet()) {
      //方法引用
      if ((o = entry.getValue()) instanceof Method) {
        this.imports.put(entry.getKey(), new MethodStub((Method) o));
      }
      else {
        //其它引用
        this.imports.put(entry.getKey(), o);
      }
    }
  }

  /** 检查此引用是否有有效的,不是有效的，则记入失效名单，避免多次解析 */
  private boolean checkForDynamicImport(String className) {
    if (packageImports == null) return false;
    //本身不是有效的类名开始
    if (!Character.isJavaIdentifierStart(className.charAt(0))) return false;
    //如果之前就判定为无效,则快速判断
    if (nonValidImports != null && nonValidImports.contains(className)) return false;

    //尝试在之前包引用的情况下,查看此类是否在之前的哪个包下面
    int found = 0;
    Class cls = null;
    for (String pkg : packageImports) {
      try {
        //package.class ,即一个完整的类
        cls = forNameWithInner(pkg + "." + className, getClassLoader());
        found++;
      }
      catch (Throwable cnfe) {
        // do nothing.
      }
    }

    //避免多包下名字冲突
    if (found > 1) throw new RuntimeException("ambiguous class name: " + className);
    if (found == 1) {
      addImport(className, cls);
      return true;
    }

    cacheNegativeHitForDynamicImport(className);
    return false;
  }

  /**
   * 检查是否有引入过，或者当前能够将其引入(如果此name为全类名)
   * 即相当于在表达式中使用全类名，就认为其本身就是可以被引入的
   */
  public boolean hasImport(String name) {
    return (imports != null && imports.containsKey(name)) ||
        AbstractParser.CLASS_LITERALS.containsKey(name) ||
        checkForDynamicImport(name);
  }

  private void initImports() {
    if (this.imports == null) {
      this.imports = new ConcurrentHashMap<String, Object>();
    }
  }

  /** 对指定类进行引入 */
  public void addImport(Class cls) {
    initImports();
    addImport(cls.getSimpleName(), cls);
  }

  /**
   * 使用指定别名对类进行引入，即后续可以直接别名来使用类
   * 如 addImport("abc",T)
   * 在表达式中new abc() 就相当于new T()的使用
   */
  public void addImport(String name, Class cls) {
    initImports();
    this.imports.put(name, cls);
  }

  /** 使用别名对原型进行引用 */
  public void addImport(String name, Proto proto) {
    initImports();
    this.imports.put(name, proto);
  }

  /** 使用别名对方法进行引用，这里采用方法句柄来进行描述 */
  public void addImport(String name, Method method) {
    addImport(name, new MethodStub(method));
  }

  /** 使用别名+方法句柄添加相应的引用 */
  public void addImport(String name, MethodStub method) {
    initImports();
    this.imports.put(name, method);
  }

  /** 获取相应的拦截器列表 */
  public Map<String, Interceptor> getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(Map<String, Interceptor> interceptors) {
    this.interceptors = interceptors;
  }

  public Map<String, Object> getImports() {
    return imports;
  }

  /** 将新的引用完全添加到现有引用当中来 */
  public void setImports(Map<String, Object> imports) {
    if (imports == null) return;

    Object val;

    for (Map.Entry<String, Object> entry : imports.entrySet()) {
      if ((val = entry.getValue()) instanceof Class) {
        addImport(entry.getKey(), (Class) val);
      }
      else if (val instanceof Method) {
        addImport(entry.getKey(), (Method) val);
      }
      else if (val instanceof MethodStub) {
        addImport(entry.getKey(), (MethodStub) val);
      }
      else if (val instanceof Proto) {
        addImport(entry.getKey(), (Proto) entry.getValue());
      }
      else {
        throw new RuntimeException("invalid element in imports map: " + entry.getKey() + " (" + val + ")");
      }
    }
  }

  /** 当前配置中是否有引用信息 */
  public boolean hasImports() {
    return !(imports != null && imports.isEmpty()) || (packageImports != null && packageImports.size() != 0);
  }

  /** 获取当前使用的加载器 */
  public ClassLoader getClassLoader() {
    return classLoader == null ? classLoader = Thread.currentThread().getContextClassLoader() : classLoader;
  }

  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /** 重置相应的引用信息 */
  public void setAllImports(Map<String, Object> imports) {
    initImports();
    this.imports.clear();
    if (imports != null) this.imports.putAll(imports);
  }

  /** 未使用到,后面会被删除掉 */
  @Deprecated
  public void setImports(HashMap<String, Object> imports) {
    // TODO: this method is here for backward compatibility. Could it be removed/deprecated?
    setAllImports(imports);
  }

  /** 将不能引用的信息添加到nonValid集合中,因为有限制，因此去掉超过限制的 */
  private void cacheNegativeHitForDynamicImport(String negativeHit) {
    if (nonValidImports == null) {
      nonValidImports = new LinkedHashSet<String>();
    }
    else if (nonValidImports.size() > 1000) {
      Iterator<String> i = nonValidImports.iterator();
      i.next();
      i.remove();
    }

    nonValidImports.add(negativeHit);
  }

  public void flushCaches() {
    if (nonValidImports != null)
      nonValidImports.clear();
  }

  public boolean isAllowNakedMethCall() {
    return allowNakedMethCall;
  }

  public void setAllowNakedMethCall(boolean allowNakedMethCall) {
    this.allowNakedMethCall = allowNakedMethCall;
  }

  public boolean isAllowBootstrapBypass() {
    return allowBootstrapBypass;
  }

  /** 设置当前是否允许二次优化标记 */
  public void setAllowBootstrapBypass(boolean allowBootstrapBypass) {
    this.allowBootstrapBypass = allowBootstrapBypass;
  }
}
