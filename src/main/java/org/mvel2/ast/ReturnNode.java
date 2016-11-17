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

import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.StackDemarcResolverFactory;

import static org.mvel2.MVEL.eval;
import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * 描述一个简单的return x 节点 此节点的作用除对x求值外,还将在当前作用域中设定标记,以提前中止计算并返回
 * @author Christopher Brock
 */
public class ReturnNode extends ASTNode {

  public ReturnNode(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    //在翻译期对相应的返回数据进行编译,以确保其执行
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      setAccessor((Accessor) subCompileExpression(expr, start, offset, pCtx));
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    if (accessor == null) {
      setAccessor((Accessor) subCompileExpression(expr, start, offset, pCtx));
    }

    //因为已经最终需要返回,因此相应的解析器工厂设置终止标记
    factory.setTiltFlag(true);

    //直接使用访问器来处理相应的数据
    //使用StackDemarcResolverFactory来隔离相应的终止标记
    return accessor.getValue(ctx, thisValue, new StackDemarcResolverFactory(factory));
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //使用解释运行方式来处理
    factory.setTiltFlag(true);
    return eval(expr, start, offset, ctx, new StackDemarcResolverFactory(factory));
  }

  /** return 也算作操作符的一部分，但优先级最低 */
  @Override
  public boolean isOperator() {
    return true;
  }

  /** 其操作符为return 在某些执行中,也会使用此操作符进行相应的流程处理 */
  @Override
  public Integer getOperator() {
    return Operator.RETURN;
  }

  @Override
  public boolean isOperator(Integer operator) {
    return Operator.RETURN == operator;
  }
}
