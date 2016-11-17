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

package org.mvel2.integration.impl;

import org.mvel2.integration.VariableResolver;

/**
 * 最简单的一个变量解析实现，通过持有相应的变量的值来进行各项处理
 * 此实现不会存储变量的引用名，因此相应的name由外部进行存储使用
 */
public class SimpleValueResolver implements VariableResolver {
  /** 当前变量的值 */
  private Object value;

  public SimpleValueResolver(Object value) {
    this.value = value;
  }

  /** 无变量名，返回null */
  public String getName() {
    return null;
  }

  public Class getType() {
    return Object.class;
  }

  public void setStaticType(Class type) {
  }

  public int getFlags() {
    return 0;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }
}
