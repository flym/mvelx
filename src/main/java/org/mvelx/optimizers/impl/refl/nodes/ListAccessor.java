package org.mvelx.optimizers.impl.refl.nodes;

import lombok.Getter;
import lombok.val;
import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

import java.util.List;

/** list集合访问器 */
public class ListAccessor extends BaseAccessor {
    /** 下标 */
    @Getter
    private final int index;

    public ListAccessor(int index, ParserContext parserContext) {
        super("[" + index + "]", parserContext);
        this.index = index;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //直接采用相应的list.get访问相应的值信息
        val value = ((List) ctx).get(index);
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
        }

        return value;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
        //根据是否有next来决定是否转发set请求
        if(hasNextNode()) {
            val v = ((List) ctx).get(index);
            return fetchNextAccessNode(v, elCtx, vars).setValue(v, elCtx, vars, value);
        }

        //noinspection unchecked
        ((List) ctx).set(index, value);
        return value;
    }

    public String toString() {
        return "ListAccessor[" + index + "]";
    }

    /** list取值,相应的类型未知,因此声明类型为Object */
    public Class getKnownEgressType() {
        return Object.class;
    }
}
