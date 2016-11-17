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
import org.mvel2.util.MethodStub;

/**
 * 使用变量名来引用相应的方法来达到方法引用的目的，这里就是通过变量名解析方法引用的变量解析器
 * 相应的变量名和方法句柄在构造时确定
 * 此类仅为StaticMethodImportResolverFactory使用,但它实际上也没实际使用到,因此此类也无特别作用
 *
 * @author Christopher Brock
 */
@Deprecated
public class StaticMethodImportResolver implements VariableResolver {
  /** 相应的变量名 */
  private String name;
  /** 相应的方法句柄 */
  private MethodStub method;

  public StaticMethodImportResolver(String name, MethodStub method) {
    this.name = name;
    this.method = method;
  }

  public String getName() {
    return name;
  }

  /** 此值为方法句柄，因此无类型信息 */
  public Class getType() {
    return null;
  }

  public void setStaticType(Class type) {
  }

  /** 无特殊标识 */
  public int getFlags() {
    return 0;
  }

  public MethodStub getValue() {
    return method;
  }

  public void setValue(Object value) {
    this.method = (MethodStub) value;
  }
}
