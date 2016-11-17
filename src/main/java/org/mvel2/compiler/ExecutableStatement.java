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

package org.mvel2.compiler;

import org.mvel2.integration.VariableResolverFactory;

import java.io.Serializable;

/** 可执行节点，表示当前是一个可执行的计算单元(可以由node封装) */
public interface ExecutableStatement extends Accessor, Serializable, Cloneable {
  /** 取值操作，不带elContext的版本 */
  public Object getValue(Object staticContext, VariableResolverFactory factory);

  /** 设置相应的入参类型(这里的入参也不一定是真实的入参，也可能是当前的结果的处理结果类型 */
  public void setKnownIngressType(Class type);

  /** 设置相应的出参类型，表示当前单元应该返回的类型信息 */
  public void setKnownEgressType(Class type);

  /** 获取相应的已知入参类型 */
  public Class getKnownIngressType();

  /** 执行节点, 能够拿到确定的声明返回类型 */
  public Class getKnownEgressType();

  /** 当前单元是否是classCast计算单元) */
  public boolean isExplicitCast();

  /** 入参和出参是否兼容 */
  public boolean isConvertableIngressEgress();

  /** 判断相应的入参和出参是否一致 */
  public void computeTypeConversionRule();

  /** 是否具备整数优化(即当前表达式是已经被编译优化的 */
  public boolean intOptimized();

  /** 当前执行节点是否是纯字面量 */
  public boolean isLiteralOnly();

  /** 当前执行节点是否是空节点 */
  public boolean isEmptyStatement();
}
