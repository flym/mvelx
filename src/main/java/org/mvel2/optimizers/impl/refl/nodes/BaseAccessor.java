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

/** 用于实现基本的下一个节点的接口访问,即包装一个能够承上启下的级联调用的访问器概念 */
public abstract class BaseAccessor implements AccessorNode {
  /** 引用的下一个节点 */
  protected AccessorNode nextNode;

  public AccessorNode setNextNode(AccessorNode accessorNode) {
    return this.nextNode = accessorNode;
  }

  public AccessorNode getNextNode() {
    return this.nextNode;
  }
}
