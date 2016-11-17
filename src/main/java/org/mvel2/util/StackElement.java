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
