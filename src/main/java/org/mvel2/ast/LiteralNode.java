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
import org.mvel2.compiler.BlankLiteral;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.NullType;

/**
 * 常量节点，没有特殊的意义，仅描述当前节点是常量信息，可能是true/false这种信息，也可能是类名等信息
 * 也可能直接是数字类
 *
 * @author Christopher Brock
 */
public class LiteralNode extends ASTNode {
  /** 使用指定常量 +相应的声明类型创建起节点信息 */
  public LiteralNode(Object literal, Class type, ParserContext pCtx) {
    this(literal, pCtx);
    this.egressType = type;
  }

  /** 使用指定常量 创建出节点信息 */
  public LiteralNode(Object literal, ParserContext pCtx) {
    super(pCtx);
    if ((this.literal = literal) != null) {
      if ((this.egressType = literal.getClass()) == BlankLiteral.class) this.egressType = Object.class;
    }
    else {
      this.egressType = NullType.class;
    }
  }

  /** 直接返回此值 */
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    return literal;
  }

  /** 直接返回此值 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    return literal;
  }

  public Object getLiteralValue() {
    return literal;
  }

  public void setLiteralValue(Object literal) {
    this.literal = literal;
  }

  /** 此节点是常量节点 */
  public boolean isLiteral() {
    return true;
  }

  @Override
  public String toString() {
    return "Literal<" + literal + ">";
  }
}
