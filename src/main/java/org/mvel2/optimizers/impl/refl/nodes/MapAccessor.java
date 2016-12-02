package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import java.util.Map;

/** 表示map类型的访问器 */
public class MapAccessor extends BaseAccessor {
    /** 属性值,为一个常量值，可能为字符串，也可能为整数 */
    private Object property;

    public MapAccessor(Object property, ParserContext parserContext) {
        super(String.valueOf(property), parserContext);
        this.property = property;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
        //直接通过调用map.get(key)来完成处理
        val value = ((Map) ctx).get(property);
        return hasNextNode() ? fetchNextAccessNode(value, elCtx, vrf).getValue(value, elCtx, vrf) : value;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
        //根据是否有next决定是否转发请求
        if(hasNextNode()) {
            val v = ((Map) ctx).get(property);
            return fetchNextAccessNode(v, elCtx, vars).setValue(v, elCtx, vars, value);
        }

        //noinspection unchecked
        ((Map) ctx).put(property, value);
        return value;
    }

    public String toString() {
        return "Map Accessor -> [" + property + "]";
    }

    /** 类型未知,声明为Object类型 */
    public Class getKnownEgressType() {
        return Object.class;
    }
}
