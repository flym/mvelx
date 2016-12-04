package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.DataConversion;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

/** 动态字段访问器，表示实际的值与当前字段类型可能存在不匹配的情况(这时需要进行类型转换) */
@SuppressWarnings({"unchecked"})
public class DynamicFieldAccessor extends BaseAccessor {
    /** 字段信息 */
    private final Field field;
    /** 字段信息的声明类型,或者是期望的类型 */
    private final Class targetType;

    /** 使用字段进行构建相应的访问器 */
    public DynamicFieldAccessor(Field field, ParserContext parserContext) {
        super(field.getName(), parserContext);

        this.field = field;
        this.targetType = field.getType();
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //因为是获取值,因此不需要进行类型转换
        try{
            val value = field.get(ctx);
            if(hasNextNode()) {
                return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
            }
            return value;
        } catch(Exception e) {
            throw new RuntimeException("unable to access field", e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        try{
            //有下个节点,因此相应的转换工具由next来进行,这里不作任何处理
            if(hasNextNode()) {
                Object ctxValue = field.get(ctx);
                return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
            } else {
                //设置值,需要根据相应的字段类型进行类型转换,以转换成相兼容的类型
                field.set(ctx, DataConversion.convert(value, targetType));
                return value;
            }
        } catch(Exception e) {
            throw new RuntimeException("unable to access field", e);
        }
    }

    public Class getKnownEgressType() {
        return targetType;
    }
}
