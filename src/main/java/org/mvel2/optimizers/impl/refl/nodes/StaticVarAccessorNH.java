package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.OptimizationFailure;
import org.mvel2.ParserContext;
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

/** 描述静态字段访问器，附带空值处理 */
public class StaticVarAccessorNH extends BaseAccessor {
    /** 相应的字段 */
    private Field field;
    /** 空值处理器 */
    private PropertyHandler nullHandler;

    /** 根据相应的静态字段和空值处理器构建出处理器 */
    public StaticVarAccessorNH(Field field, PropertyHandler handler, String property, ParserContext parserContext) {
        super(property, parserContext);
        this.field = field;
        this.nullHandler = handler;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //先获取值,再根据返回的结果决定是否采用空值处理器
        try{
            Object v = field.get(null);
            //值为null,则跳转到nullHandler中
            if(v == null)
                v = nullHandler.getProperty(field.getName(), elCtx, vars);

            if(hasNextNode()) {
                return fetchNextAccessNode(v, elCtx, vars).getValue(v, elCtx, vars);
            } else {
                return v;
            }
        } catch(Exception e) {
            throw new OptimizationFailure("unable to access static field", e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //设置值,不需要空值处理器参与,因此只需要通过next来决定是否转发请求
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

    /** 声明类型为字段的声明类型 */
    public Class getKnownEgressType() {
        //这里应该为field.getType()
        return field.getType();
    }
}