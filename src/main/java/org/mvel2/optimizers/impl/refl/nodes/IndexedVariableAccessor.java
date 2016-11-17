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

/**
 * 描述通过下标参数变量解析来处理的访问器,即相应的变量信息在相应的解析器上下文中进行了注册
 *
 *  */
public class IndexedVariableAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 相应的下标值(即在变量中注册的下标值) */
  private int register;

  public IndexedVariableAccessor(int register) {
    this.register = register;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
    //因为数据存储在相应的解析器上下文中,因此直接以下标的方式获取到解析器,再获取相应的值
    if (nextNode != null) {
      return nextNode.getValue(vrf.getIndexedVariableResolver(register).getValue(), elCtx, vrf);
    }
    else {
      return vrf.getIndexedVariableResolver(register).getValue();
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //根据是否有next来决定是自己执行set操作,还是转交相应的处理逻辑
    if (nextNode != null) {
      return nextNode.setValue(variableFactory.getIndexedVariableResolver(register).getValue(), elCtx, variableFactory, value);
    }
    else {
      variableFactory.getIndexedVariableResolver(register).setValue(value);
      return value;
    }
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  /** 返回类型未知,因此为通用Object类型 */
  public Class getKnownEgressType() {
    return Object.class;
  }
}