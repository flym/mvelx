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
package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;

/** 描述静态引用的访问器,通过持有此引用，在处理时直接返回此引用即可 */
public class StaticReferenceAccessor implements AccessorNode {
  private AccessorNode nextNode;

  /** 相应的引用信息 */
  Object literal;

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //直接返回该值信息
    if (nextNode != null) {
      return nextNode.getValue(literal, elCtx, vars);
    }
    else {
      return literal;
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //因为是静态值引用,因此要调用set,只有当存在next时才会有调用的情况.本身不会直接进行修改
    return nextNode.setValue(literal, elCtx, variableFactory, value);
  }

  /** 返回相应的静态值信息 */
  public Object getLiteral() {
    return literal;
  }

  public void setLiteral(Object literal) {
    this.literal = literal;
  }

  public StaticReferenceAccessor() {
  }

  /** 根据相应的静态引用值来构建出相应的访问器 */
  public StaticReferenceAccessor(Object literal) {
    this.literal = literal;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  /** 相应的声明类型即静态数据本身的类型 */
  public Class getKnownEgressType() {
    return literal.getClass();
  }
}
