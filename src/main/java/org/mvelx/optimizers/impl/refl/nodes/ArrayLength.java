package org.mvelx.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

import static java.lang.reflect.Array.getLength;

/**
 * 用于实现访问数组长度的访问器
 *
 * @author Christopher Brock
 */
public class ArrayLength extends BaseAccessor {

    public ArrayLength(String property, ParserContext parserContext) {
        super(property, parserContext);
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //采用原生Array反射调用的方式获取到相应的长度信息
        val length = getLength(ctx);
        if(hasNextNode()) {
            return fetchNextAccessNode(length, elCtx, variableFactory).getValue(length, elCtx, variableFactory);
        }

        return length;
    }

    /** 数组长度不支持相应的修改 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return null;
    }

    /** 数组长度返回类型为整数 */
    public Class getKnownEgressType() {
        return Integer.class;
    }
}
