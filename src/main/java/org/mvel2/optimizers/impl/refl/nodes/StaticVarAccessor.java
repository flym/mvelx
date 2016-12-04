package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.OptimizationFailure;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

/** 描述静态字段访问器 */
public class StaticVarAccessor extends BaseAccessor {
    /** 相应的字段 */
    private Field field;

    public StaticVarAccessor(Field field, String property, ParserContext parserContext) {
        super(property, parserContext);
        this.field = field;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //直接通过field.get来获取静态字段的值,因为是静态字段,因此无需传参
        try{
            val value = field.get(null);
            if(hasNextNode()) {
                return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
            }
            return value;
        } catch(Exception e) {
            throw new OptimizationFailure("unable to access static field", e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //根据是否有next决定是否转发请求
        try{
            if(!hasNextNode()) {
                field.set(null, value);
            } else {
                Object ctxValue = field.get(null);
                return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
            }
        } catch(Exception e) {
            throw new RuntimeException("error accessing static variable", e);
        }
        return value;
    }

    /** 声明类型即为相应字段所声明的类型 */
    public Class getKnownEgressType() {
        //这里有问题,应该返回声明类型,而不是Field类型
        //这个方法不会被调用,因此相应的类型确定在astNode阶段即已经确认
        return field.getClass();
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}