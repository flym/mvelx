/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.util.CompilerTools.expectType;
import static org.mvel2.util.ParseTools.subCompileExpression;

/** 表示一个处理一元操作数取反的节点信息 */
public class Invert extends ASTNode {
  /** 后面真实的执行语句 */
  private ExecutableStatement stmt;

  public Invert(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    //在编译模式下,期望其的类型为整数,因此只有整数才能进行取反操作
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      expectType(pCtx, this.stmt = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx), Integer.class, true);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //直接采用 ~X的执行方式 其中stmt为之前编译过的编译执行单元
    return ~((Integer) stmt.getValue(ctx, thisValue, factory));
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //~X,这里采用eval解释运行方式
    Object o = MVEL.eval(expr, start, offset, ctx, factory);
    if (o instanceof Integer) {
      return ~((Integer) o);
    }
    else {
      throw new CompileException("was expecting type: Integer; but found type: "
          + (o == null ? "null" : o.getClass().getName()), expr, start);
    }
  }
}