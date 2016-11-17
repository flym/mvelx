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

/** 处理对字符串的下标处理调用，如'abcd'[1] 将返回 b */
public class IndexedCharSeqAccessor implements AccessorNode {
  private AccessorNode nextNode;

  /** 下标值 */
  private int index;

  public IndexedCharSeqAccessor() {
  }

  public IndexedCharSeqAccessor(int index) {
    this.index = index;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //直接使用字符串的charAt实现相应的取下标字符方法
    if (nextNode != null) {
      return nextNode.getValue(((String) ctx).charAt(index), elCtx, vars);
    }
    else {
      return ((String) ctx).charAt(index);
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //因为字符串不可变，因此这里不再判定是否有nextNode了，因为必须存在,否则即是错误的调用
    return nextNode.setValue(((String) ctx).charAt(index), elCtx, variableFactory, value);
  }

  /** 返回相应的下标值 */
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

  /** 单个下标的结果为字符,因此返回类型为字符 */
  public Class getKnownEgressType() {
    return Character.class;
  }
}
