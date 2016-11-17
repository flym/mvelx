package org.mvel2.ast;

import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

/** 用于描述 a + b并且 a b 类型均为int类型的优化计算节点 */
public class IntAdd extends BinaryOperation implements IntOptimized {

  public IntAdd(ASTNode left, ASTNode right, ParserContext pCtx) {
    super(Operator.ADD, pCtx);
    this.left = left;
    this.right = right;
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //整数相加,分别计算出数值,直接进行+即可,而不必通过math.doOperate进行多项判断
    //各个节点采用编译执行完成
    return ((Integer) left.getReducedValueAccelerated(ctx, thisValue, factory))
        + ((Integer) right.getReducedValueAccelerated(ctx, thisValue, factory));
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //a + b,各个节点采用解释运行
    return ((Integer) left.getReducedValue(ctx, thisValue, factory))
        + ((Integer) right.getReducedValue(ctx, thisValue, factory));
  }

  /** 整数相加,结果肯定为int类型 */
  @Override
  public Class getEgressType() {
    return Integer.class;
  }
}
