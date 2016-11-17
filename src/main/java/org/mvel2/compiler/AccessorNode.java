/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
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
 */

package org.mvel2.compiler;

import java.io.Serializable;

/**
 * 带有引用信息的节点器,通过节点连接达到多次调用的目的，如a.b.c，就可以通过这种方式来进行处理
 * 因为next的存在,因此会导致相应的set语义存在一些理解上的问题.
 * 比如a.b的访问,可以理解为a节点.next = b节点, 当a.b = 4这种处理时,应该是先get(a).b.set(4)
 * 但是并没有真正的b节点,可以理解为对外只有a节点,最终的表象就是a.set(4),但在内部,因此a.next = b.因此相应的实现就是外部直接
 * 调用a.set(4),内部先是获取a的值,再将a的值,传递给b,再b.set这样调用.具体就是b.set(a.get(),4)这样的方式
 *
 * */
public interface AccessorNode extends Accessor, Serializable {
  /** 获取下一个节点 */
  public AccessorNode getNextNode();

  /** 设置下一个节点 */
  public AccessorNode setNextNode(AccessorNode accessorNode);
}
