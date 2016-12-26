package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;

/**
 * @author Christopher Brock
 */
public class Union extends BaseAccessor implements Accessor {
    private Accessor accessor;
    private char[] nextExpr;
    private int start;
    private int offset;
    private Accessor nextAccessor;
    private ParserContext pCtx;

    public Union(ParserContext pCtx, AccessorNode accessor, char[] nextAccessor, int start, int offset) {
        super(accessor.nodeExpr(), pCtx);

        this.accessor = accessor;
        this.start = start;
        this.offset = offset;
        this.nextExpr = nextAccessor;
        this.pCtx = pCtx;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        if(nextAccessor == null) {
            return get(ctx, elCtx, variableFactory);
        } else {
            return nextAccessor.getValue(get(ctx, elCtx, variableFactory), elCtx, variableFactory);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return nextAccessor.setValue(get(ctx, elCtx, variableFactory), elCtx, variableFactory, value);
    }

    private Object get(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        if(nextAccessor == null) {
            Object o = accessor.getValue(ctx, elCtx, variableFactory);
            AccessorOptimizer ao = OptimizerFactory.getDefaultAccessorCompiler();
            Class ingress = accessor.getKnownEgressType();

            nextAccessor = ao.optimizeAccessor(pCtx, nextExpr, start, offset, o, elCtx, variableFactory, ingress);
            return ao.getResultOptPass();
        } else {
            return accessor.getValue(ctx, elCtx, variableFactory);
        }
    }

    public Class getKnownEgressType() {
        return nextAccessor.getKnownEgressType();
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
