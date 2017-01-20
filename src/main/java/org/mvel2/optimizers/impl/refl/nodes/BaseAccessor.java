package org.mvel2.optimizers.impl.refl.nodes;

import com.google.common.collect.MapMaker;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mvel2.ParserContext;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.CloneUtils;

import java.util.Map;

/** 用于实现基本的下一个节点的接口访问,即包装一个能够承上启下的级联调用的访问器概念 */
@Slf4j
public abstract class BaseAccessor implements AccessorNode {
    /** 引用的下一个节点 */
    @Getter
    private AccessorNode nextNode;

    private transient Class lastCtxClass;
    private transient AccessorNode lastNextNode;
    private transient Map<Class, AccessorNode> accessorNodeMap = new MapMaker().weakValues().makeMap();

    /** 当前节点最开始使用时相应的节点表达式 */
    private String nodeExpr;

    /** 此访问时最开始解析时所使用的解析上下文 */
    private ParserContext parserContext;

    protected BaseAccessor(String nodeExpr, ParserContext parserContext) {
        this.nodeExpr = nodeExpr;
        this.parserContext = parserContext;
    }

    protected boolean hasNextNode() {
        return lastNextNode != null;
    }

    public AccessorNode setNextNode(AccessorNode accessorNode, Class<?> currentCtxType) {
        this.lastCtxClass = currentCtxType;
        if(lastCtxClass != null && accessorNode != null)
            accessorNodeMap.put(lastCtxClass, accessorNode);

        return this.nextNode = lastNextNode = accessorNode;
    }

    @Override
    public Class<?> getLastCtxType() {
        return lastCtxClass;
    }

    protected AccessorNode fetchNextAccessNode(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        if(ctx == null)
            return lastNextNode;

        if(!lastNextNode.ctxSensitive())
            return lastNextNode;

        Class<?> clazz = ctx.getClass();
        if(clazz == lastCtxClass)
            return lastNextNode;

        //此map之前是empty,因此可以理解为第一次访问
        if(accessorNodeMap.isEmpty()) {
            accessorNodeMap.putIfAbsent(clazz, lastNextNode);
            return lastNextNode;
        }

        lastCtxClass = clazz;

        return accessorNodeMap.computeIfAbsent(clazz, t -> {
            try{
                AccessorOptimizer accessorOptimizer = OptimizerFactory.getThreadAccessorOptimizer();
                String nodeExpr = lastNextNode.nodeExpr();
                AccessorNode accessorNode = (AccessorNode) accessorOptimizer.optimizeAccessor(parserContext, nodeExpr.toCharArray(), 0, nodeExpr.length(), ctx, elCtx, variableFactory, null);
                accessorNode.setNextNode(clone(lastNextNode.getNextNode()), lastNextNode.getLastCtxType());

                log.debug("ctx type changed，change the accessor.source:{},current:{}", lastNextNode, accessorNode);

                lastNextNode = accessorNode;
                return accessorNode;
            } finally {
                OptimizerFactory.clearThreadAccessorOptimizer();
            }
        });
    }

    protected AccessorNode clone(AccessorNode accessorNode) {
        AccessorNode node = CloneUtils.clone(accessorNode);

        if(node instanceof BaseAccessor) {
            Map<Class, AccessorNode> newNodeMap = new MapMaker().weakValues().makeMap();
            newNodeMap.putAll(accessorNodeMap);
            ((BaseAccessor) node).accessorNodeMap = newNodeMap;
        }

        return node;
    }

    @Override
    public String nodeExpr() {
        return nodeExpr;
    }
}
