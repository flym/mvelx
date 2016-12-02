package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.PropertyTools;

import java.lang.reflect.Field;

import static org.mvel2.DataConversion.convert;

/** 表示一个字段的访问器 */
public class FieldAccessor extends BaseAccessor {
    /** 当前所对应的字段信息 */
    private final Field field;
    /** 是否需要对参数进行类型转换,是一个逻辑处理变量 */
    private boolean coercionRequired = false;
    /** 当前字段是否是基本类型 */
    private boolean primitive;

    /** 通过字段构建起相应的访问器 */
    public FieldAccessor(Field field, ParserContext parserContext) {
        super(field.getName(), parserContext);
        primitive = (this.field = field).getType().isPrimitive();
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            val value = field.get(ctx);
            //直接通过是否有next节点决定相应的处理流程
            if(hasNextNode()) {
                return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
            }

            return value;
        } catch(Exception e) {
            throw new RuntimeException("unable to access field: " + field.getName(), e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //有下一个节点,则表示当前节点只需要get调用即可
        if(hasNextNode()) {
            try{
                //由当前字段是否是基本类型决定是否需要进行类型转换,即创建为基本的0数据
                Object v = field.get(ctx);
                Object realValue = value == null && primitive ? PropertyTools.getPrimitiveInitialValue(field.getType()) : value;
                return fetchNextAccessNode(v, elCtx, variableFactory).setValue(v, elCtx, variableFactory, realValue);
            } catch(Exception e) {
                throw new RuntimeException("unable to access field", e);
            }
        }

        try{
            //先尝试不会进行类型转换,如果访问出错了,再调回来,重新运行
            if(coercionRequired) {
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
            //后续如果类型转换出错了还会报错,则直接报相应的错误
            throw new RuntimeException("unable to bind property", e);
        } catch(Exception e) {
            throw new RuntimeException("unable to access field", e);
        }
    }

    public Class getKnownEgressType() {
        return field.getType();
    }
}
