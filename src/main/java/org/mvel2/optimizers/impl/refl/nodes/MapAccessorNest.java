package org.mvel2.optimizers.impl.refl.nodes;

import lombok.Getter;
import lombok.val;
import org.mvel2.DataConversion;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import java.util.Map;

import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * map类型的访问器，属性为计算单元
 *
 * @author Christopher Brock
 */
public class MapAccessorNest extends BaseAccessor {
    /** 描述属性值的计算单元 */
    @Getter
    private final ExecutableStatement propertyStatement;
    /** 期望的值类型(在set时需要作类型转换) */
    private Class conversionType;

    /** 根据相应的属性表达式+相应的值类型来构建访问器 */
    public MapAccessorNest(String property, Class conversionType, ParserContext parserContext) {
        this((ExecutableStatement) subCompileExpression(property.toCharArray()), conversionType, property, parserContext);
    }

    /** 根据相应的属性计算单元+相应的值类型来构建访问器 */
    public MapAccessorNest(ExecutableStatement propertyStatement, Class conversionType, String property, ParserContext parserContext) {
        super(property, parserContext);
        this.propertyStatement = propertyStatement;
        this.conversionType = conversionType;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
        //直接先计算出属性值,再调用相应的map.get来获取相应的值
        val value = ((Map) ctx).get(propertyStatement.getValue(ctx, elCtx, vrf));
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vrf).getValue(value, elCtx, vrf);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
        //如果有next节点,则将set操作转交由next来完成
        if(hasNextNode()) {
            Object ctxValue = ((Map) ctx).get(propertyStatement.getValue(ctx, elCtx, vars));
            return fetchNextAccessNode(ctxValue, elCtx, vars).setValue(ctxValue, elCtx, vars, value);
        } else {
            //自己进行put,判断是否需要根据转换类型来决定是否需要进行数据转换
            if(conversionType != null) {
                ((Map) ctx).put(propertyStatement.getValue(ctx, elCtx, vars), value = DataConversion.convert(value, conversionType));
            } else {
                ((Map) ctx).put(propertyStatement.getValue(ctx, elCtx, vars), value);
            }
            return value;
        }
    }

    public String toString() {
        return "Map Accessor -> [" + propertyStatement + "]";
    }

    /** 类型未知,因此声明类型为Object */
    public Class getKnownEgressType() {
        return Object.class;
    }
}
