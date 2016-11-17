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

import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.HashMap;

import static org.mvel2.util.CompilerTools.expectType;
import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * 实现与while一致,但相应的条件需要取反
 * @author Christopher Brock
 */
public class UntilNode extends BlockNode {
  /** 无用变量 */
  protected String item;
  /** 条件节点 */
  protected ExecutableStatement condition;

  public UntilNode(char[] expr, int start, int offset, int blockStart, int blockOffset, int fields, ParserContext pCtx) {
    super(pCtx);
    //期望条件执行结果为boolean
    expectType(pCtx, this.condition = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx),
        Boolean.class, ((fields & COMPILE_IMMEDIATE) != 0));


    //开启新编译作用域进行内部语法块编译
    if (pCtx != null) {
      pCtx.pushVariableScope();
    }

    this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockOffset, pCtx);

    if (pCtx != null) {
      pCtx.popVariableScope();
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //开启单独的循环解析作用域,按照与while相反的判断条件进行循环执行
    VariableResolverFactory ctxFactory = new MapVariableResolverFactory(new HashMap(0), factory);
    while (!(Boolean) condition.getValue(ctx, thisValue, factory)) {
      compiledBlock.getValue(ctx, thisValue, ctxFactory);
    }

    //循环执行,无返回值
    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译执行相同
    VariableResolverFactory ctxFactory = new MapVariableResolverFactory(new HashMap(0), factory);

    while (!(Boolean) condition.getValue(ctx, thisValue, factory)) {
      compiledBlock.getValue(ctx, thisValue, ctxFactory);
    }
    return null;
  }

}