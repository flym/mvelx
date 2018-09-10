package org.mvelx.optimizers.impl.refl.nodes;

import org.mvelx.ParserContext;
import org.mvelx.compiler.Accessor;
import org.mvelx.integration.VariableResolverFactory;

/**
 * created at 2018-02-27
 *
 * @author flym
 */
public class DelegatedAccessorNode extends BaseAccessor {
    private Accessor accessor;

    public DelegatedAccessorNode(String nodeExpr, ParserContext parserContext, Accessor accessor) {
        super(nodeExpr, parserContext);
        this.accessor = accessor;
    }

    @Override
    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        return accessor.getValue(ctx, elCtx, variableFactory);
    }

    @Override
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return accessor.setValue(ctx, elCtx, variableFactory, value);
    }

    @Override
    public Class getKnownEgressType() {
        return accessor.getKnownEgressType();
    }
}
