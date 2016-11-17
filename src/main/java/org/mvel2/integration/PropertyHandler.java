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

/**
 * 用于描述在某些场景下，使用特定的属性处理器来处理set和get调用，而不是使用原生的处理方式
 * 比如在正常的调用中返回null或出错的情况下，就再通过相应的配置进行委托调用
 * This interface allows an external property handler to resolve a property against the provided context.
 *
 * @see org.mvel2.optimizers.impl.asm.ProducesBytecode
 */
public interface PropertyHandler {
  /**
   * 在指定的属性名，上下文和变量工厂中获取相应的属性值
   * Retrieves the value of the property.
   *
   * @param name            - the name of the property to be resolved.
   * @param contextObj      - the current context object.
   * @param variableFactory - the root variable factory provided by the runtime.
   * @return - the value of the property.
   */
  public Object getProperty(String name, Object contextObj, VariableResolverFactory variableFactory);


  /**
   * 对指定的属性名，上下文，变量工厂和值进行set操作
   * Sets the value of the property.
   *
   * @param name            - the name of the property to be resolved.
   * @param contextObj      - the current context object.
   * @param variableFactory - the root variable factory provided by the runtime.
   * @param value           - the value to be set to the resolved property
   * @return - the resultant value of the property (should normally be the same as the value passed)
   */
  public Object setProperty(String name, Object contextObj, VariableResolverFactory variableFactory, Object value);
}
