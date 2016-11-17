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

package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.debug.DebugTools.getOperatorSymbol;

/**
 * 定义一个操作节点,表明这里是一个操作节点(并没有其它含义，仅用于节点属性标明)
 * 因为后续会对此类节点进行优化
 * 为一个标记节点
 */
public class OperatorNode extends ASTNode {
  /** 操作节点名，对应 Operator中的位置 */
  private Integer operator;

  public OperatorNode(Integer operator, char[] expr, int start, ParserContext pCtx) {
    super(pCtx);
    assert operator != null;
    this.expr = expr;
    //相应的常量即为操作符本身
    this.literal = this.operator = operator;
    this.start = start;
  }

  /** 此节点为一个操作符节点 */
  public boolean isOperator() {
    return true;
  }

  public boolean isOperator(Integer operator) {
    return operator.equals(this.operator);
  }

  public Integer getOperator() {
    return operator;
  }

  /** 标记节点不可执行 */
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    throw new CompileException("illegal use of operator: " + getOperatorSymbol(operator), expr, start);
  }

  /** 标记节点不可执行 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    throw new CompileException("illegal use of operator: " + getOperatorSymbol(operator), expr, start);
  }
}
