package org.mvel2.ast;

import org.mvel2.ParserContext;

/** 表示一个使用两个操作符处理的运算节点(并不单指boolean操作) */
public abstract class BooleanNode extends ASTNode {
  /** 左边操作节点 */
  protected ASTNode left;
  /** 右边操作节点 */
  protected ASTNode right;

  protected BooleanNode(ParserContext pCtx) {
    super(pCtx);
  }

  public ASTNode getLeft() {
    return this.left;
  }

  public ASTNode getRight() {
    return this.right;
  }

  public void setLeft(ASTNode node) {
    this.left = node;
  }

  public void setRight(ASTNode node) {
    this.right = node;
  }

  /**
   * 从顺序上设置最右边的处理节点，即从顺序上替换右边的表达式
   * 这里的rightMost表示从逻辑上最应该被当作右边表达式访问的
   * 如
   * a + b中的b
   * a + (b-c)中的c
   * a + (b * c)中的c
   * 一般的set 和get的用处在于需要替换右边的操作数
   * 如 a + b碰到 * c之后，就需要把原来 a + b中的b替换为b*c，而实际上作的事情就是先使用getRightMost
   * 拿到b，然后转换为(b*c)，然后再调用set进行替换
   * 这里的setRightMost也可能是直接替换right元素，因此这里不需要考虑原来的right怎么处理的问题
   * 这里的set和get的用处就是从优先级以及左右的访问顺序上尽可能地保证优先级的关系处理
   * 同时又不破坏从左到右的访问顺序，如
   * a || b 再碰到 && c时，就更换为 a || (b &&c) ，在优先级上，&&的优先级更高，
   * 但在访问上，又是从左边到右边的一个访问顺序
   */
  public abstract void setRightMost(ASTNode right);

  /** 获取最右侧的处理节点 */
  public abstract ASTNode getRightMost();
}
