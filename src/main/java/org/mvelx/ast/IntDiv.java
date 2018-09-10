package org.mvelx.ast;

import org.mvelx.Operator;
import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;


/** 描述 a / b 并且 a b 类型均为int类型的计算节点,为优化节点 */
public class IntDiv extends BinaryOperation implements IntOptimized {
    public IntDiv(ASTNode left, ASTNode right, ParserContext pCtx) {
        super(Operator.DIV, pCtx);
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
    public void setRight(ASTNode node) {
        super.setRight(node);
    }

    /** 整数相除,结果肯定为整数 */
    @Override
    public Class getEgressType() {
        return Integer.class;
    }
}