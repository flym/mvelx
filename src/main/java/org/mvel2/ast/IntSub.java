package org.mvel2.ast;

import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;


/** 描述 a - b 并且 a b 类型均为整数的优化计算节点 */
public class IntSub extends BinaryOperation implements IntOptimized {
  public IntSub(ASTNode left, ASTNode right, ParserContext pCtx) {
    super(Operator.SUB, pCtx);
    this.left = left;
    this.right = right;
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    // a - b,每个节点采用编译运行
    return ((Integer) left.getReducedValueAccelerated(ctx, thisValue, factory))
        - ((Integer) right.getReducedValueAccelerated(ctx, thisValue, factory));
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    // a - b,每个节点采用解释运行
    return ((Integer) left.getReducedValue(ctx, thisValue, factory))
        - ((Integer) right.getReducedValue(ctx, thisValue, factory));
  }


  /** 整数相减,结果为整数 */
  @Override
  public Class getEgressType() {
    return Integer.class;
  }
}
