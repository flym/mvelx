package org.mvel2.optimizers.impl.refl.nodes;

import lombok.Getter;
import org.mvel2.ParserContext;
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

import static org.mvel2.DataConversion.convert;

/** 字段访问器，附带空处理的情况 */
public class FieldAccessorNH extends BaseAccessor {
    /** 当前字段 */
    @Getter
    private final Field field;
    /** 是否需要转型 */
    private boolean coercionRequired = false;
    /** 相应的空值处理器 */
    private PropertyHandler nullHandler;

    /** 使用字段以及相应的空值处理器构建相应的字段访问器 */
    public FieldAccessorNH(Field field, PropertyHandler handler, ParserContext parserContext) {
        super(field.getName(), parserContext);
        this.field = field;
        this.nullHandler = handler;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            Object v = field.get(ctx);
            //如果返回值为null,则调用相应的空值处理器
            if(v == null)
                v = nullHandler.getProperty(field.getName(), elCtx, vars);

            if(hasNextNode()) {
                return fetchNextAccessNode(v, elCtx, vars).getValue(v, elCtx, vars);
            }

            return v;
        } catch(Exception e) {
            throw new RuntimeException("unable to access field", e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        try{
            if(hasNextNode()) {
                Object ctxValue = field.get(ctx);
                return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctx, elCtx, variableFactory, value);
            }
            //先尝试参数不作类型转换,出错了再转换
            else if(coercionRequired) {
                field.set(ctx, value = convert(ctx, field.getClass()));
                return value;
            } else {
                field.set(ctx, value);
                return value;
            }
        } catch(IllegalArgumentException e) {
            if(!coercionRequired) {
                coercionRequired = true;
                return setValue(ctx, elCtx, variableFactory, value);
            }
            //类型转换之后,还会失败,则直接报错
            throw new RuntimeException("unable to bind property", e);
        } catch(Exception e) {
            throw new RuntimeException("unable to access field", e);
        }
    }

    public Class getKnownEgressType() {
        return field.getType();
    }
}