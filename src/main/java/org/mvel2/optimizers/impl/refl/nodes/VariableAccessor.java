package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

/** 表示对变量信息的访问,即变量之前以变量名的方式存储在上下文中,这里从相应的上下文中进行获取 */
public class VariableAccessor extends BaseAccessor {
    /** 变量名 */
    private String property;

    public VariableAccessor(String property, ParserContext parserContext) {
        super(property, parserContext);
        this.property = property;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
        //要求必须有相应的解析器上下文
        if(vrf == null)
            throw new RuntimeException("cannot access property in optimized accessor: " + property);

        //直接从相应的解析器上下文通过变量名的方式获取到解析器,再通过解析器获取到相应的值
        val value = vrf.getVariableResolver(property).getValue();
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vrf).getValue(value, elCtx, vrf);
        }

        return value;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //通过next来判定是否由自己进行值设置,还是转交由next节点处理
        if(hasNextNode()) {
            Object ctxValue = variableFactory.getVariableResolver(property).getValue();
            return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
        } else {
            variableFactory.getVariableResolver(property).setValue(value);
        }

        return value;
    }

    /** 无法探测类型,因此声明类型未知 */
    public Class getKnownEgressType() {
        return Object.class;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
