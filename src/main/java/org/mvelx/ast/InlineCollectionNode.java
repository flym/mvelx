package org.mvelx.ast;

import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.optimizers.AccessorOptimizer;
import org.mvelx.optimizers.OptimizerFactory;
import org.mvelx.util.CollectionParser;

import java.util.List;

/**
 * 表示一个内联的集合表达式,使用[ 或 {均可以用来表示集合,即集合直接字面量 如 new int[]{1,2} 这种
 * 或者是 [1,2,3]这种
 *
 * @author Christopher Brock
 */
public class InlineCollectionNode extends ASTNode {
    /**
     * 用于描述数组内部的数据类型及值描述,可能是数组,集合,map的一种
     * 这里面的数据并没有被实际的解析,因此在实际运算时，还需要重新解析并处理
     */
    private Object collectionGraph;

    /** 初始化,但未指定数据类型 */
    public InlineCollectionNode(char[] expr, int start, int offset, int fields, ParserContext pctx) {
        this(expr, start, offset, fields, null, pctx);
    }

    /** 初始化,并使用已知的内部类型进行解析 */
    public InlineCollectionNode(char[] expr, int start, int offset, int fields, Class type, ParserContext pctx) {
        super(expr, start, offset, fields | INLINE_COLLECTION, pctx);
        this.egressType = type;

        //需要编译，则进行编译
        if((fields & COMPILE_IMMEDIATE) != 0) {
            compileAccessor(type, pctx, null, null, null);
        }
    }

    /** 采用编译的方式进行数据访问 */
    public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
        if(accessor == null)
            compileAccessor(null, null, ctx, thisValue, factory);

        return accessor.getValue(ctx, thisValue, factory);
    }

    /** 构建好相应的访问器 */
    private void compileAccessor(Class type, ParserContext pCtx, Object ctx, Object thisValue, VariableResolverFactory factory) {
        try{
            AccessorOptimizer ao = OptimizerFactory.getThreadAccessorOptimizer();
            //如果没有编译，则进行编译，然后再根据优化之后的访问器来获取数据
            if(collectionGraph == null)
                parseGraph(type, pCtx);

            accessor = ao.optimizeCollection(pCtx, collectionGraph, egressType, expr, start + offset, 0, ctx, thisValue, factory);
            egressType = ao.getEgressType();
        } finally {
            OptimizerFactory.clearThreadAccessorOptimizer();
        }
    }

    private void parseGraph(Class type, ParserContext pCtx) {
        CollectionParser parser = new CollectionParser();

        //以下因为是以内部集合的方式进行解析,即外层通过[或者{使用,即认为外层表示为集合
        //因此下层的解析返回值肯定为list,然后再单独返回内层对象
        //type仅用于表示在解析过程中的子类型的类型，因此不影响到返回的结果信息
        if(type == null) {
            collectionGraph = ((List) parser.parseCollection(expr, start, offset, true, pCtx)).get(0);
        } else {
            collectionGraph = ((List) parser.parseCollection(expr, start, offset, true, type, pCtx)).get(0);
        }

        if(this.egressType == null) this.egressType = collectionGraph.getClass();
    }
}
