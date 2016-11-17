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
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvel2.ast;

import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.util.CompilerTools.expectType;

/** 描述一个 a || b 的节点信息 为一个优化性节点 */
public class Or extends BooleanNode {

  public Or(ASTNode left, ASTNode right, boolean strongTyping, ParserContext pCtx) {
    super(pCtx);
    //期望返回类型为boolean
    expectType(pCtx, this.left = left, Boolean.class, strongTyping);
    expectType(pCtx, this.right = right, Boolean.class, strongTyping);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //采用与java相一致的 || 运算,同时满足java的懒计算原则
    return (((Boolean) left.getReducedValueAccelerated(ctx, thisValue, factory))
        || ((Boolean) right.getReducedValueAccelerated(ctx, thisValue, factory)));
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //不支持解释运行
    throw new RuntimeException("improper use of AST element");
  }

  /** 设置最右侧节点,即同一运行级别最右侧的 */
  public void setRightMost(ASTNode right) {
    Or n = this;
    while (n.right != null && n.right instanceof Or) {
      n = (Or) n.right;
    }
    n.right = right;
  }

  /** 获取最右侧节点,即同一运行级别最右侧的 */
  public ASTNode getRightMost() {
    Or n = this;
    while (n.right != null && n.right instanceof Or) {
      n = (Or) n.right;
    }
    return n.right;
  }

  public String toString() {
    return "(" + left.toString() + " || " + right.toString() + ")";
  }

  /** 返回类型为boolean */
  public Class getEgressType() {
    return Boolean.class;
  }
}
