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
 * 处理while循环的节点
 * @author Christopher Brock
 */
public class WhileNode extends BlockNode {
  /** 此字段无用 */
  protected String item;
  /** while条件值 */
  protected ExecutableStatement condition;

  public WhileNode(char[] expr, int start, int offset, int blockStart, int blockEnd, int fields, ParserContext pCtx) {
    super(pCtx);
    //期望条件的执行结果为boolean
    expectType(pCtx, this.condition = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx),
        Boolean.class, ((fields & COMPILE_IMMEDIATE) != 0));


    //为执行块单独开启变量作用域,编译执行块,最后弹出相应的作用域
    if (pCtx != null) {
      pCtx.pushVariableScope();
    }
    this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockEnd, pCtx);

    if (pCtx != null) {
      pCtx.popVariableScope();

    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //为执行作用域单独创建变量解析域,在整个处理完之后,即不会再使用
    VariableResolverFactory ctxFactory = new MapVariableResolverFactory(new HashMap<String, Object>(), factory);
    //标准的while执行过程
    while ((Boolean) condition.getValue(ctx, thisValue, factory)) {
      compiledBlock.getValue(ctx, thisValue, ctxFactory);
    }

    //因为是循环语句,因此只影响过程,不影响相应的
    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //解释模式与编译模式一致
    VariableResolverFactory ctxFactory = new MapVariableResolverFactory(new HashMap<String, Object>(), factory);

    while ((Boolean) condition.getValue(ctx, thisValue, factory)) {
      compiledBlock.getValue(ctx, thisValue, ctxFactory);
    }
    return null;
  }

}