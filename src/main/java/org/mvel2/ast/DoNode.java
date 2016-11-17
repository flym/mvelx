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
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.HashMap;

import static org.mvel2.util.CompilerTools.expectType;
import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * 描述do while循环的节点
 *
 * @author Christopher Brock
 */
public class DoNode extends BlockNode {
  /** 未使用字段，此字段实际上并未被使用 */
  protected String item;
  /** 条件表达式 */
  protected ExecutableStatement condition;

  public DoNode(char[] expr, int start, int offset, int blockStart, int blockOffset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;
    this.blockStart = blockStart;
    this.blockOffset = blockOffset;

    this.condition = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx);

    //期望条件类型为boolean
    expectType(pCtx, this.condition, Boolean.class, ((fields & COMPILE_IMMEDIATE) != 0));

    //因为要编译子块,因此这里如果有上下文,则需要在新的上下文中进行编译和处理

    if (pCtx != null) {
      pCtx.pushVariableScope();
    }

    this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockOffset, pCtx);

    if (pCtx != null) {
      pCtx.popVariableScope();
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //接下来的while循环,如果有相应的变量,则表示在整个循环中变量都是重用的,因此这里采用mapVarFactory以重用相应的处理
    VariableResolverFactory ctxFactory = new MapVariableResolverFactory(new HashMap<String, Object>(0), factory);

    //整个过程即采用标准的do while循环处理
    do {
      compiledBlock.getValue(ctx, thisValue, ctxFactory);
    }
    //这里的条件判断还是作用的外部作用域,以与执行体相区分
    while ((Boolean) condition.getValue(ctx, thisValue, factory));

    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //循环体内需要新的作用域,因此创建新执行作用域来执行
    VariableResolverFactory ctxFactory = new MapVariableResolverFactory(new HashMap<String, Object>(0), factory);

    do {
      compiledBlock.getValue(ctx, thisValue, ctxFactory);
    }
    //这里的条件判断还是作用的外部作用域,以与执行体相区分
    while ((Boolean) condition.getValue(ctx, thisValue, factory));

    return null;
  }

}