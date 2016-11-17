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

import static org.mvel2.MVEL.eval;
import static org.mvel2.util.ParseTools.*;

/**
 * 用于描述对指定类型变量的声明,如 var x = 3这种声明节点
 * @author Christopher Brock
 */
public class TypedVarNode extends ASTNode implements Assignment {
  /** 相应的变量信息 */
  private String name;

  /** 声明附带的表达式 */
  private ExecutableStatement statement;

  public TypedVarNode(char[] expr, int start, int offset, int fields, Class type, ParserContext pCtx) {
    super(pCtx);
    this.egressType = type;
    this.fields = fields;

    this.expr = expr;
    this.start = start;
    this.offset = offset;

    int assignStart;
    //找到相应的=操作符,后面的即为相应的赋值表达式
    if ((assignStart = find(this.expr = expr, start, offset, '=')) != -1) {
      checkNameSafety(name = createStringTrimmed(expr, start, assignStart - start));
      this.offset -= (assignStart - start);
      this.start = assignStart + 1;

      if (((fields |= ASSIGN) & COMPILE_IMMEDIATE) != 0) {
        statement = (ExecutableStatement) subCompileExpression(expr, this.start, this.offset, pCtx);
      }
    }
    else {
      //因为在前面调用时已经进行了判定,因此不会走到这里的逻辑
      checkNameSafety(name = new String(expr, start, offset));
    }

    //尝试判断一个相应的类型兼容,如果之前有声明相应的类型,这里则尝试进行类型检查
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      Class x = pCtx.getVarOrInputType(name);
      if (x != null && x != Object.class && !x.isAssignableFrom(egressType)) {
        throw new RuntimeException("statically-typed variable already defined in scope: " + name);
      }
      pCtx.addVariable(name, egressType, false);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    if (statement == null) statement = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx);
    //因为是新声明,因此直接带相应的声明类型进行处理,以保证是新的变量处理
    factory.createVariable(name, ctx = statement.getValue(ctx, thisValue, factory), egressType);
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    factory.createVariable(name, ctx = eval(expr, start, offset, thisValue, factory), egressType);
    return ctx;
  }


  public String getName() {
    return name;
  }


  public String getAssignmentVar() {
    return name;
  }

  public char[] getExpression() {
    return expr;
  }

  /** 是新值声明节点 */
  public boolean isNewDeclaration() {
    return true;
  }

  public void setValueStatement(ExecutableStatement stmt) {
    this.statement = stmt;
  }
}
