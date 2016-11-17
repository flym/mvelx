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
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.util.ParseTools.checkNameSafety;

/**
 * 用于描述一个之前未声明的变量信息的节点
 * 即var x;这种语句
 * @author Christopher Brock
 */
public class DeclTypedVarNode extends ASTNode implements Assignment {
  /** 当前变量的名称 */
  private String name;

  public DeclTypedVarNode(String name, char[] expr, int start, int offset, Class type, int fields, ParserContext pCtx) {
    super(pCtx);
    this.egressType = type;
    checkNameSafety(this.name = name);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    //因为是已经声明了,因此加入解析变量域中
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      pCtx.addVariable(name, egressType, true);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //直接定义此变量
    if (!factory.isResolveable(name)) factory.createVariable(name, null, egressType);
    else throw new CompileException("variable defined within scope: " + name, expr, start);
    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    if (!factory.isResolveable(name)) factory.createVariable(name, null, egressType);
    else throw new CompileException("variable defined within scope: " + name, expr, start);

    return null;
  }

  public String getName() {
    return name;
  }

  public String getAssignmentVar() {
    return name;
  }

  public char[] getExpression() {
    return new char[0];
  }

  public boolean isAssignment() {
    return true;
  }

  /** 是新声明变量节点 */
  public boolean isNewDeclaration() {
    return true;
  }

  public void setValueStatement(ExecutableStatement stmt) {
    throw new RuntimeException("illegal operation");
  }

  public String toString() {
    return "var:" + name;
  }
}