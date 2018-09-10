package org.mvelx.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvelx.ParserContext;
import org.mvelx.integration.PropertyHandler;
import org.mvelx.integration.VariableResolverFactory;


/**
 * 使用属性处理器来进行属性访问的访问器
 * 即相应的属性在外部有指定此属性的处理器
 */
public class PropertyHandlerAccessor extends BaseAccessor {
    /** 属性名 */
    private String propertyName;
    /** 对应的处理器 */
    private PropertyHandler propertyHandler;
    /** 该处理器当前所支持的类型 */
    private Class conversionType;

    public PropertyHandlerAccessor(String propertyName, Class conversionType, PropertyHandler propertyHandler, ParserContext parserContext) {
        super(propertyName, parserContext);
        this.propertyName = propertyName;
        this.conversionType = conversionType;
        this.propertyHandler = propertyHandler;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //正常的处理流程
        try{
            val value = propertyHandler.getProperty(propertyName, ctx, variableFactory);
            if(hasNextNode()) {
                return fetchNextAccessNode(value, elCtx, variableFactory).getValue(value, elCtx, variableFactory);
            }

            return value;
        } catch(Exception e) {
            throw new RuntimeException("unable to access field", e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //由next是否有值,决定是否转发请求
        if(hasNextNode()) {
            Object ctxValue = propertyHandler.getProperty(propertyName, ctx, variableFactory);
            return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, ctx, variableFactory, value);
        } else {
            return propertyHandler.setProperty(propertyName, ctx, variableFactory, value);
        }
    }

    /** 类型未知,为Object类型 */
    public Class getKnownEgressType() {
        return Object.class;
    }
}
