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
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.math.MathProcessor;

import static org.mvel2.MVEL.eval;
import static org.mvel2.util.ParseTools.subCompileExpression;

/** 描述一个已经在解析上下文中声明过的变量的运算并进行x=赋值操作 */
public class IndexedOperativeAssign extends ASTNode {
  /** 相应变量在之前注册过的下标值(即在变量工厂中的变量位置值) */
  private final int register;
  /** 用于描述右边相应的表达式 即 a += xx 中的右边部分 */
  private ExecutableStatement statement;
  /** 相应的运算符 */
  private final int operation;

  public IndexedOperativeAssign(char[] expr, int start, int offset, int operation, int register, int fields, ParserContext pCtx) {
    super(pCtx);
    this.operation = operation;
    this.expr = expr;
    this.start = start;
    this.offset = offset;
    this.register = register;

    //编译模式下即编译相应的表达式
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      statement = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx);
      egressType = statement.getKnownEgressType();
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //直接从相应的下标值获取相应的变量处理器然后执行类似 a = a + b的操作
    VariableResolver resolver = factory.getIndexedVariableResolver(register);
    resolver.setValue(ctx = MathProcessor.doOperations(resolver.getValue(), operation, statement.getValue(ctx, thisValue, factory)));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    VariableResolver resolver = factory.getIndexedVariableResolver(register);
    resolver.setValue(ctx = MathProcessor.doOperations(resolver.getValue(), operation, eval(expr, start, offset, ctx, factory)));
    return ctx;
  }
}