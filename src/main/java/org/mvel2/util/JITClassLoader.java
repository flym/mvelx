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

package org.mvel2.util;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * 内部统一使用的类加载器
 * 提供统一的加载访问,避免随便引用
 *  */
public class JITClassLoader extends ClassLoader implements MVELClassLoader {
  public JITClassLoader(ClassLoader classLoader) {
    super(classLoader);
  }

  public Class<?> defineClassX(String className, byte[] b, int off, int len) {
    return super.defineClass(className, b, off, len);
  }
}
