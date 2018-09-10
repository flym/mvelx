package org.mvelx.optimizers.impl.refl.nodes;

import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

/**
 * 对特定的属性this的一个表示
 * <p/>
 * this属性只能在一个表达式的起始点才能使用，在属性名中，如abc.this使用无效
 * this值即在最开始调用executeExpression时传递的上下文
 */
public class ThisValueAccessor extends BaseAccessor {

    public ThisValueAccessor(ParserContext parserContext) {
        super("this", parserContext);
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //根据是否有子节点决定相应的值
        if(hasNextNode()) {
            return fetchNextAccessNode(elCtx, elCtx, vars).getValue(elCtx, elCtx, vars);
        } else {
            //因为是this值,则直接返回相应的this值即可.在这里ctx和elCtx实际上相同,均从最开始传递下来
            return elCtx;
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //不需要直接修改this值,因此只能将this值往后传
        if(!hasNextNode())
            throw new RuntimeException("assignment to reserved variable 'this' not permitted");
        return fetchNextAccessNode(elCtx, elCtx, variableFactory).setValue(elCtx, elCtx, variableFactory, value);

    }

    public Class getKnownEgressType() {
        return Object.class;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
