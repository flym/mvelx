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

import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.util.ParseTools.containsCheck;

/** 用于描述一个contains节点,是一个优化性的节点,由contains操作节点优化而来 */
public class Contains extends ASTNode {
  /** 左节点,即a contains b中的 a */
  private ASTNode stmt;
  /** 右节点 即 a contains b 中的 b */
  private ASTNode stmt2;

  public Contains(ASTNode stmt, ASTNode stmt2, ParserContext pCtx) {
    super(pCtx);
    this.stmt = stmt;
    this.stmt2 = stmt2;
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //分别计算两边的值,再进行contains检查
    return containsCheck(stmt.getReducedValueAccelerated(ctx, thisValue, factory), stmt2.getReducedValueAccelerated(ctx, thisValue, factory));
  }

  /** 不支持解释运行 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    throw new RuntimeException("operation not supported");
  }

  /** 返回值即为boolean */
  public Class getEgressType() {
    return Boolean.class;
  }

  /** 返回first节点,主操作节点 */
  public ASTNode getFirstStatement() {
    return stmt;
  }

  /** 返回second节点,子操作节点 */
  public ASTNode getSecondStatement() {
    return stmt2;
  }
}
