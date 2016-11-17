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

package org.mvel2.util;

import java.io.Serializable;

/**
 * 描述当前栈中的节点信息,通过当前值和上一个值来描述相应的链式结构信息.
 * 即存储的都是当前值(value)，next引向上一次存储的值信息
 */
public class StackElement implements Serializable {
  public StackElement(StackElement next, Object value) {
    this.next = next;
    this.value = value;
  }

  public StackElement next;
  public Object value;
}
