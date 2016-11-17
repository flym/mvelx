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

package org.mvel2.optimizers.dynamic;

import org.mvel2.util.MVELClassLoader;

import java.util.LinkedList;


/**
 * 用于控制在整个mvel中生成优化访问器的数量，以及承担类加载器的职能，
 * 避免将所有类都绑定在基础类加载器上
 */
public class DynamicClassLoader extends ClassLoader implements MVELClassLoader {
  /** 当前经过优化的类上限 */
  private int totalClasses;
  /** 优化限制上限值 */
  private int tenureLimit;
  /** 当前所有管理的动态访问器,通过引用达到简单管理的目的 */
  private final LinkedList<DynamicAccessor> allAccessors = new LinkedList<DynamicAccessor>();

  public DynamicClassLoader(ClassLoader classLoader, int tenureLimit) {
    super(classLoader);
    this.tenureLimit = tenureLimit;
  }

  public Class defineClassX(String className, byte[] b, int start, int end) {
    totalClasses++;
    return super.defineClass(className, b, start, end);
  }

  public int getTotalClasses() {
    return totalClasses;
  }

  /** 注册一个，如果已达上限，则尝试反优化一个访问器 */
  public DynamicAccessor registerDynamicAccessor(DynamicAccessor accessor) {
    synchronized (allAccessors) {
      allAccessors.add(accessor);
      while (allAccessors.size() > tenureLimit) {
        DynamicAccessor da = allAccessors.removeFirst();
        if (da != null) {
          da.deoptimize();
        }
      }
      assert accessor != null;
      return accessor;
    }
  }

  /** 反优化所有动态访问器 */
  public void deoptimizeAll() {
    synchronized (allAccessors) {
      for (DynamicAccessor a : allAccessors) {
        if (a != null) a.deoptimize();
      }
      allAccessors.clear();
    }
  }

  /** 当前加载器是否过载,即优化生成类太多 */
  public boolean isOverloaded() {
    return tenureLimit < totalClasses;
  }
}
