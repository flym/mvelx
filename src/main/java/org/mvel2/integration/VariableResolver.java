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

package org.mvel2.integration;

import java.io.Serializable;

/**
 * 用于操作变量的变量解析器，用于读取，写入以及存储相应的值，可以理解为valueHolder
 * A variable resolver is responsible for physically accessing a variable, for either read or write.  VariableResolver's
 * are obtained via a {@link org.mvel2.integration.VariableResolverFactory}.
 */
public interface VariableResolver extends Serializable {
  /**
   * 获取变量名
   * Returns the name of external variable.
   *
   * @return A string representing the variable name.
   */
  public String getName();

  /**
   * 获取相应变量的类型
   * 注：此实现大部分情况下返回object，因为对于对象的处理，mvel采用自己的一些处理，不会依赖于变量所声明的类型
   *
   * This should return the type of the variable.  However, this is not completely necessary, and is particularly
   * only of benefit to systems that require use of MVEL's strict typing facilities.  In most cases, this implementation
   * can simply return: Object.class
   *
   * @return A Class instance representing the type of the target variable.
   */
  public Class getType();

  /**
   * 设置静态类型，好像没什么用处
   * If this is a declared variable of a static type, MVEL will make it known by passing the type here.
   */
  public void setStaticType(Class type);

  /**
   * 为变量设置特别的标记，以用于后续对此变量的一些定制处理
   * 此标记位并没有特别的语义层，仅是由每个特别的变量解析器自己使用。即每个valueResolver会设置不同的flag，
   * 然后在一些处理中，相应的valueResolver会按照自己的处理方式来解析此flag，可以认为是定制的一个属性
   * 一般情况下，返回0，即表示不需要特殊的处理
   * Returns the bitset of special variable flags.  Internal use only.  This should just return 0 in custom
   * implementations.
   *
   * @return Bitset of special flags.
   */
  public int getFlags();

  /**
   * 获取变量的值
   * Returns the physical target value of the variable.
   *
   * @return The actual variable value.
   */
  public Object getValue();

  /**
   * 设置相应的值
   * Sets the value of the physical target value.
   *
   * @param value The new value.
   * @return value after any conversion
   */
  public void setValue(Object value);
}
