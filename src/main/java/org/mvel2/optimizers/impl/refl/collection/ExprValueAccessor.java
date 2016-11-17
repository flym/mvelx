/**
 * MVEL (The MVFLEX Expression Language)
 *
 * Copyright (C) 2007 Christopher Brock, MVFLEX/Valhalla Project and the Codehaus
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
 *
 */
package org.mvel2.optimizers.impl.refl.collection;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.compiler.ExecutableLiteral;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.ParseTools;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.util.ParseTools.getSubComponentType;
import static org.mvel2.util.ReflectionUtil.isAssignableFrom;

/**
 * 一个将表达式封装为可执行的访问器的表达式访问器
 * @author Christopher Brock
 */
public class ExprValueAccessor implements Accessor {
  /** 封装好的可执行语句 */
  public ExecutableStatement stmt;

  /** 使用表达式+期望的声明类型+变量工厂+解析上下文创建出相应的访问器 */
  public ExprValueAccessor(String ex, Class expectedType, Object ctx, VariableResolverFactory factory, ParserContext pCtx) {
    //直接硬编译为可执行表达式
    stmt = (ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx);

    //if (expectedType.isArray()) {
    Class tt = getSubComponentType(expectedType);
    Class et = stmt.getKnownEgressType();
    //验证一下相应的类型之间是否是能够进行匹配的,如果不能匹配,则尝试转换为能够匹配的类型
    if (stmt.getKnownEgressType() != null && !isAssignableFrom(tt, et)) {
      //尝试按照常量的方式进行转换
      if ((stmt instanceof ExecutableLiteral) && canConvert(et, tt)) {
        try {
          stmt = new ExecutableLiteral(convert(stmt.getValue(ctx, factory), tt));
          return;
        }
        catch (IllegalArgumentException e) {
          // fall through;
        }
      }
      //不能匹配,则直接throw异常
      if (pCtx != null && pCtx.isStrongTyping())
        throw new RuntimeException("was expecting type: " + tt + "; but found type: "
            + (et == null ? "null" : et.getName()));
    }
  }


  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    //直接使用表达式返回相应的值
    return stmt.getValue(elCtx, variableFactory);
  }

  /** 此节点不执行直接设置相应的值,即只能进行获取相应的值 */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    // not implemented
    return null;
  }

  /** 返回编译好的相应的表达式 */
  public ExecutableStatement getStmt() {
    return stmt;
  }

  public void setStmt(ExecutableStatement stmt) {
    this.stmt = stmt;
  }

  public Class getKnownEgressType() {
    return stmt.getKnownEgressType();
  }
}
