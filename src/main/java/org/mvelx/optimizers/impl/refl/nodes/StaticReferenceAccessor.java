package org.mvelx.optimizers.impl.refl.nodes;

import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

/** 描述静态引用的访问器,通过持有此引用，在处理时直接返回此引用即可 */
public class StaticReferenceAccessor extends BaseAccessor {
    /** 相应的引用信息 */
    Object literal;

    /** 根据相应的静态引用值来构建出相应的访问器 */
    public StaticReferenceAccessor(Object literal, String property, ParserContext parserContext) {
        super(property, parserContext);
        this.literal = literal;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //直接返回该值信息
        if(hasNextNode()) {
            return fetchNextAccessNode(literal, elCtx, vars).getValue(literal, elCtx, vars);
        } else {
            return literal;
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //因为是静态值引用,因此要调用set,只有当存在next时才会有调用的情况.本身不会直接进行修改
        return fetchNextAccessNode(literal, elCtx, variableFactory).setValue(literal, elCtx, variableFactory, value);
    }

    /** 相应的声明类型即静态数据本身的类型 */
    public Class getKnownEgressType() {
        return literal.getClass();
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
