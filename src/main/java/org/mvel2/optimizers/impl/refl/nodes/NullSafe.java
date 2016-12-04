package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.ClassUtils;

/**
 * 对象安全的访问器,即在具体的处理时，如果当前对象为null，则不再处理剩下的处理
 * <p/>
 * 如?a.b,如果a为null，则整体返回为null，而不是报NPE.这里表示a属性的值可能为null
 */
public class NullSafe extends BaseAccessor {
    private char[] expr;
    private int start;
    private int offset;
    /** 解析上下文 */
    private ParserContext pCtx;

    public NullSafe(char[] expr, int start, int offset, ParserContext pCtx) {
        super(new String(expr, start, offset), pCtx);

        this.expr = expr;
        this.start = start;
        this.offset = offset;
        this.pCtx = pCtx;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //当前对象为null，跳过
        if(ctx == null)
            return null;

        //真实调用还未进行编译,则尝试编译,并将相应的调用委托给相应的编译好的访问器
        if(!hasNextNode()) {
            final Accessor a = OptimizerFactory.getAccessorCompiler(OptimizerFactory.SAFE_REFLECTIVE)
                    .optimizeAccessor(pCtx, expr, start, offset, ctx, elCtx, variableFactory, ctx.getClass());

            setNextNode(new BaseAccessor(new String(expr, start, offset), pCtx) {
                public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
                    return a.getValue(ctx, elCtx, variableFactory);
                }

                public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
                    return a.setValue(ctx, elCtx, variableFactory, value);
                }

                public Class getKnownEgressType() {
                    return a.getKnownEgressType();
                }
            }, ClassUtils.getType(ctx));
        }
        return fetchNextAccessNode(ctx, elCtx, variableFactory).getValue(ctx, elCtx, variableFactory);
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //当前对象为null，跳过
        if(ctx == null)
            return null;

        return fetchNextAccessNode(ctx, elCtx, variableFactory).setValue(ctx, elCtx, variableFactory, value);
    }

    /** 类型未知,为Object类型 */
    public Class getKnownEgressType() {
        return Object.class;
    }

    @Override
    public String nodeExpr() {
        return "?";
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
