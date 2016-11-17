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
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

/** 字符串下标访问，下标值为执行单元的访问器 */
public class IndexedCharSeqAccessorNest implements AccessorNode {
  private AccessorNode nextNode;
  /** 下标执行单元 */
  private ExecutableStatement index;

  public IndexedCharSeqAccessorNest() {
  }

  public IndexedCharSeqAccessorNest(ExecutableStatement index) {
    this.index = index;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //这里计算出下标值,再采用相应的charAt来获取相应的字符信息
    if (nextNode != null) {
      return nextNode.getValue(((String) ctx).charAt((Integer) index.getValue(ctx, elCtx, vars)), elCtx, vars);
    }
    else {
      return ((String) ctx).charAt((Integer) index.getValue(ctx, elCtx, vars));
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //因为字符串不可变,因此这里肯定会有相应的next节点值,以便完成整个set操作
    return nextNode.setValue(((String) ctx).charAt((Integer) index.getValue(ctx, elCtx, variableFactory)), elCtx,
        variableFactory, value);
  }

  /** 返回相应的下标计算单元 */
  public ExecutableStatement getIndex() {
    return index;
  }

  public void setIndex(ExecutableStatement index) {
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

  /** 字符串下标处理返回类型即为字符 */
  public Class getKnownEgressType() {
    return Character.class;
  }
}
