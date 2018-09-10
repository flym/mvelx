package org.mvelx.optimizers.impl.refl.nodes;

import lombok.Getter;
import lombok.val;
import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

import java.lang.reflect.Array;

/** 针对数组的访问器 */
public class ArrayAccessor extends BaseAccessor {
    /** 数组下标 */
    @Getter
    private final int index;

    public ArrayAccessor(int index, ParserContext parserContext) {
        super("[" + index + "]", parserContext);
        this.index = index;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //直接使用数组的反射调用方式来进行处理
        val value = Array.get(ctx, index);
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
        }

        return value;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //采用数据反射的调用方式,如果级联调用,则将相应的处理转交给下级节点,即下级节点才是set调用
        if(hasNextNode()) {
            val paramValue = Array.get(ctx, index);
            return fetchNextAccessNode(paramValue, elCtx, variableFactory).setValue(paramValue, elCtx, variableFactory, value);
        } else {
            Array.set(ctx, index, value);
            return value;
        }
    }

    @Override
    public String nodeExpr() {
        return "[" + index + "]";
    }

    /** 因为是数组访问器,当前认为声明类型为数组 */
    public Class getKnownEgressType() {
        return Object[].class;
    }

    public String toString() {
        return "ArrayAccessor[" + index + "]";
    }
}
