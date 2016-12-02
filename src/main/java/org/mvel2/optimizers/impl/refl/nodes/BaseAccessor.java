package org.mvel2.optimizers.impl.refl.nodes;

import lombok.Getter;
import org.mvel2.ParserContext;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.CloneUtils;

import java.util.HashMap;
import java.util.Map;

/** 用于实现基本的下一个节点的接口访问,即包装一个能够承上启下的级联调用的访问器概念 */
public abstract class BaseAccessor implements AccessorNode {
    /** 引用的下一个节点 */
    @Getter
    protected AccessorNode nextNode;

    private transient Class lastCtxClass;
    private transient AccessorNode lastNextNode;
    private transient Map<Class, AccessorNode> accessorNodeMap = new HashMap<>();

    /** 当前节点最开始使用时相应的节点表达式 */
    private String nodeExpr;

    /** 此访问时最开始解析时所使用的解析上下文 */
    private ParserContext parserContext;

    protected BaseAccessor() {
    }

    protected BaseAccessor(String nodeExpr, ParserContext parserContext) {
        this.nodeExpr = nodeExpr;
        this.parserContext = parserContext;
    }

    protected boolean hasNextNode() {
        return lastNextNode != null;
    }

    public AccessorNode setNextNode(AccessorNode accessorNode) {
        return this.nextNode = lastNextNode = accessorNode;
    }

    protected AccessorNode fetchNextAccessNode(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        if(ctx == null)
            return lastNextNode;

        Class<?> clazz = ctx.getClass();
        if(clazz == lastCtxClass)
            return lastNextNode;

        return accessorNodeMap.computeIfAbsent(clazz, t -> {
            try{
                AccessorOptimizer accessorOptimizer = OptimizerFactory.getThreadAccessorOptimizer();
                String nodeExpr = lastNextNode.nodeExpr();
                AccessorNode accessorNode = (AccessorNode) accessorOptimizer.optimizeAccessor(parserContext, nodeExpr.toCharArray(), 0, nodeExpr.length(), ctx, elCtx, variableFactory, null);
                accessorNode.setNextNode(clone(lastNextNode.getNextNode()));
                return accessorNode;
            } finally {
                OptimizerFactory.clearThreadAccessorOptimizer();
            }
        });
    }

    protected AccessorNode clone(AccessorNode accessorNode) {
        return CloneUtils.clone(accessorNode);
    }

    @Override
    public String nodeExpr() {
        return nodeExpr;
    }
}
