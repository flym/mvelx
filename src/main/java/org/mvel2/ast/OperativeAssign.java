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
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.math.MathProcessor;
import org.mvel2.util.ParseTools;

import static org.mvel2.MVEL.eval;
import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * 描述变量赋值(?=)的操作节点
 * 这里的变量在当前上下文中没有被定义
 * 如果是a[i] += 3 这种表达式,这里会报错
 * */
public class OperativeAssign extends ASTNode {
  /** 变量名 */
  private String varName;
  /** = 后面的表达式 */
  private ExecutableStatement statement;
  /** 操作符 */
  private final int operation;
  /** 已知当前属性的入参类型(即如果此变量被传入其它操作) 如 a+= 3，即为a = a + 3，在后面的a + 3中，需要知道a的类型值 */
  private int knownInType = -1;

  public OperativeAssign(String variableName, char[] expr, int start, int offset, int operation, int fields, ParserContext pCtx) {
    super(pCtx);
    this.varName = variableName;
    this.operation = operation;
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    if ((fields & COMPILE_IMMEDIATE) != 0) {
      //声明类型直接以后面的计算声明类型来表示
      egressType = (statement = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx)).getKnownEgressType();

      if (pCtx.isStrongTyping()) {
        knownInType = ParseTools.__resolveType(egressType);
      }

      //在上下文中注册类型，因为之前已判断没有此变量或参数
      if (!pCtx.hasVarOrInput(varName)) {
        pCtx.addInput(varName, egressType);
      }
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //因为在前面并没有对varName作特殊处理,因此这里如果varName为a[i]这里即会出错
    //这里直接通过解析器的方式获取相应的值,然后执行相应的操作,再设置回去即可
    VariableResolver resolver = factory.getVariableResolver(varName);
    resolver.setValue(ctx = MathProcessor.doOperations(resolver.getValue(), operation, knownInType, statement.getValue(ctx, thisValue, factory)));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    VariableResolver resolver = factory.getVariableResolver(varName);
    resolver.setValue(ctx = MathProcessor.doOperations(resolver.getValue(), operation, eval(expr, start, offset, ctx, factory)));
    return ctx;
  }
}