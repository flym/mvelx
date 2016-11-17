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
import org.mvel2.util.ParseTools;

import static org.mvel2.util.ParseTools.subCompileExpression;

/** 表示一个对表达式boolean取反的操作节点 */
public class Negation extends ASTNode {
  /** 后面待取反的执行块 */
  private ExecutableStatement stmt;

  public Negation(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    //编译模式下,进行编译,同时期望其类型为boolean
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      if (((this.stmt = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx)).getKnownEgressType() != null)
          && (!ParseTools.boxPrimitive(stmt.getKnownEgressType()).isAssignableFrom(Boolean.class))) {
        throw new CompileException("negation operator cannot be applied to non-boolean type", expr, start);
      }
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //直接采用!boolean 的处理方式即可.编译执行下采用计算单元的方式
    return !((Boolean) stmt.getValue(ctx, thisValue, factory));
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    try {
      //采用 !boolean, 数据采用解释模式运行
      return !((Boolean) MVEL.eval(expr, start, offset, ctx, factory));
    }
    catch (NullPointerException e) {
      throw new CompileException("negation operator applied to a null value", expr, start, e);
    }
    catch (ClassCastException e) {
      throw new CompileException("negation operator applied to non-boolean expression", expr, start, e);
    }
  }

  /** 原表达式为boolean,取反也为boolean类型 */
  public Class getEgressType() {
    return Boolean.class;
  }

  /** 返回待操作的处理单元 */
  public ExecutableStatement getStatement() {
    return stmt;
  }
}
