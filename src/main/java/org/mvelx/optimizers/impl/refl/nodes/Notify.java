package org.mvelx.optimizers.impl.refl.nodes;

import org.mvelx.ParserContext;
import org.mvelx.integration.GlobalListenerFactory;
import org.mvelx.integration.VariableResolverFactory;


/**
 * 描述一下可以在调用时进行监听通知的访问器
 * 在具体使用时,即在实现时,先通过使用此访问器进行占位,然后将真实地访问器作为next来处理,因此可以在真实的访问器执行前进行相应的拦截处理
 */
public class Notify extends BaseAccessor {
    /** 当前处理的属性名 */
    private String name;

    public Notify(String name, ParserContext parserContext) {
        super(name, parserContext);
        this.name = name;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
        //调用前进行get调用通知
        GlobalListenerFactory.notifyGetListeners(ctx, name, vrf);
        return fetchNextAccessNode(ctx, elCtx, vrf).getValue(ctx, elCtx, vrf);
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //调用前进行set调用通知
        GlobalListenerFactory.notifySetListeners(ctx, name, variableFactory, value);
        return fetchNextAccessNode(ctx, elCtx, variableFactory).setValue(ctx, elCtx, variableFactory, value);
    }

    /** 声明类型未知,为Object */
    public Class getKnownEgressType() {
        return Object.class;
    }

    @Override
    public String nodeExpr() {
        return name;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
