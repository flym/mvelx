package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.DataConversion;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import java.util.List;

import static org.mvel2.util.ParseTools.subCompileExpression;

/** list集合访问器，并且下标为执行单元的情况 */
public class ListAccessorNest extends BaseAccessor {
    /** 下标执行单元 */
    private final ExecutableStatement index;
    /** 下标的执行单元语句 */
    private String property;
    /** 当前集合中存储的值的类型(用于设置值时进行参数转换),即在set需要转换为哪个类型 */
    private Class conversionType;


    /** 通过下标表达式+相应的值类型构建出相应的访问器 */
    public ListAccessorNest(String index, Class conversionType, ParserContext parserContext) {
        this((ExecutableStatement) subCompileExpression(index.toCharArray()), conversionType, index, parserContext);
    }

    /** 通过下标计算单元+相应的值类型构建出相应的访问器 */
    public ListAccessorNest(ExecutableStatement index, Class conversionType, String property, ParserContext parserContext) {
        super("[" + property + "]", parserContext);
        this.index = index;
        this.property = property;
        this.conversionType = conversionType;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //直接计算出相应的下标值,再使用list.get(int)来获取相应的值
        val value = ((List) ctx).get((Integer) index.getValue(ctx, elCtx, vars));
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
        //noinspection unchecked
        //根据是否有next来决定是否转发请求
        //有next,因此由next来处理相应值的类型,当前list只负责取值即可
        if(hasNextNode()) {
            Object ctxValue = ((List) ctx).get((Integer) index.getValue(ctx, elCtx, vars));
            return fetchNextAccessNode(ctxValue, elCtx, vars).setValue(ctxValue, elCtx, vars, value);
        } else {
            //没有next,因此为自己设置值,需要根据类型决定是否需要进行类型转换
            if(conversionType != null) {
                ((List) ctx).set((Integer) index.getValue(ctx, elCtx, vars), value = DataConversion.convert(value, conversionType));
            } else {
                ((List) ctx).set((Integer) index.getValue(ctx, elCtx, vars), value);
            }
            return value;
        }

    }

    public String toString() {
        return "ListAccessorNest[" + property + "]";
    }

    /** 类型未知,为Object类型 */
    public Class getKnownEgressType() {
        return Object.class;
    }
}
