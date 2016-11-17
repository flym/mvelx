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

/** 表示对变量信息的访问,即变量之前以变量名的方式存储在上下文中,这里从相应的上下文中进行获取 */
public class VariableAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 变量名 */
  private String property;

  public VariableAccessor(String property) {
    this.property = property;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
    //要求必须有相应的解析器上下文
    if (vrf == null)
      throw new RuntimeException("cannot access property in optimized accessor: " + property);

    //直接从相应的解析器上下文通过变量名的方式获取到解析器,再通过解析器获取到相应的值
    if (nextNode != null) {
      return nextNode.getValue(vrf.getVariableResolver(property).getValue(), elCtx, vrf);
    }
    else {
      return vrf.getVariableResolver(property).getValue();
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //通过next来判定是否由自己进行值设置,还是转交由next节点处理
    if (nextNode != null) {
      return nextNode.setValue(variableFactory.getVariableResolver(property).getValue(), elCtx, variableFactory, value);
    }
    else {
      variableFactory.getVariableResolver(property).setValue(value);
    }

    return value;
  }

  /** 返回相应的变量名 */
  public Object getProperty() {
    return property;
  }

  public void setProperty(String property) {
    this.property = property;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  /** 无法探测类型,因此声明类型未知 */
  public Class getKnownEgressType() {
    return Object.class;
  }
}
