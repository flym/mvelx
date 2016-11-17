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

import java.util.List;

/** list集合访问器 */
public class ListAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 下标 */
  private int index;


  public ListAccessor() {
  }

  public ListAccessor(int index) {
    this.index = index;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //直接采用相应的list.get访问相应的值信息
    if (nextNode != null) {
      return nextNode.getValue(((List) ctx).get(index), elCtx, vars);
    }
    else {
      return ((List) ctx).get(index);
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
    //根据是否有next来决定是否转发set请求
    if (nextNode != null) {
      return nextNode.setValue(((List) ctx).get(index), elCtx, vars, value);
    }
    else {
      //noinspection unchecked
      ((List) ctx).set(index, value);
      return value;
    }
  }

  /** 获取相应的下标值 */
  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public String toString() {
    return "Array Accessor -> [" + index + "]";
  }

  /** list取值,相应的类型未知,因此声明类型为Object */
  public Class getKnownEgressType() {
    return Object.class;
  }
}
