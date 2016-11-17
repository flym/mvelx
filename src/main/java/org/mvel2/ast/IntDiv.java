package org.mvel2.ast;

import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;


/** 描述 a / b 并且 a b 类型均为int类型的计算节点,为优化节点 */
public class IntDiv extends BinaryOperation implements IntOptimized {
  public IntDiv(ASTNode left, ASTNode right, ParserContext pCtx) {
    //这里的操作符写错了,虽然不影响具体执行
    super(Operator.MULT, pCtx);
    this.left = left;
    this.right = right;
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    // a / b ,每个节点采用编译运行的方式
    return ((Integer) left.getReducedValueAccelerated(ctx, thisValue, factory))
        / ((Integer) right.getReducedValueAccelerated(ctx, thisValue, factory));
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    // a / b 每个节点采用解释运行
    return ((Integer) left.getReducedValue(ctx, thisValue, factory))
        / ((Integer) right.getReducedValue(ctx, thisValue, factory));
  }

  @Override
  public void setRight(ASTNode node) {
    super.setRight(node);
  }

  /** 整数相除,结果肯定为整数 */
  @Override
  public Class getEgressType() {
    return Integer.class;
  }
}