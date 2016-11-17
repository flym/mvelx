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
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * 断言类节点，用于进行程序断言
 *
 * @author Christopher Brock
 */
public class AssertNode extends ASTNode {
  /** 断言的表达式 */
  public ExecutableStatement assertion;
  //无用语句
  public ExecutableStatement fail;

  public AssertNode(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    //编译模式下编译相应的表达式
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      assertion = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    try {
      //这里进行强转,并且在catch中进行判定,即要求assert后面的结果就是一个boolean值
      if (!((Boolean) assertion.getValue(ctx, thisValue, factory))) {
        throw new AssertionError("assertion failed in expression: " + new String(this.expr, start, offset));
      }
      else {
        return true;
      }
    }
    //这里出现了cast异常,表示上面的类型转换出现了问题,因此这里进行判定
    //当前也可以在上面提前进行类型判定,这里采用的是运行期强制判断
    catch (ClassCastException e) {
      throw new CompileException("assertion does not contain a boolean statement", expr, start);
    }
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    try {
      //采用解释模式来评估相应的值
      if (!((Boolean) MVEL.eval(this.expr, ctx, factory))) {
        throw new AssertionError("assertion failed in expression: " + new String(this.expr, start, offset));
      }
      else {
        return true;
      }
    }
    //出现cast异常,表示类型转换失败,则丢异常
    catch (ClassCastException e) {
      throw new CompileException("assertion does not contain a boolean statement", expr, start);
    }
  }
}
