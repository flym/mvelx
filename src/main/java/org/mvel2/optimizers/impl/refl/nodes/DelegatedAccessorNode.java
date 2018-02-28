package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;

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
