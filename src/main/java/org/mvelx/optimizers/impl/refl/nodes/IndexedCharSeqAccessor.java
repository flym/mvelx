package org.mvelx.optimizers.impl.refl.nodes;

import lombok.Getter;
import lombok.val;
import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

/** 处理对字符串的下标处理调用，如'abcd'[1] 将返回 b */
public class IndexedCharSeqAccessor extends BaseAccessor {
    /** 下标值 */
    @Getter
    private final int index;

    public IndexedCharSeqAccessor(int index, ParserContext parserContext) {
        super("[" + index + "]", parserContext);
        this.index = index;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //直接使用字符串的charAt实现相应的取下标字符方法
        val value = ((String) ctx).charAt(index);
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
        }

        return value;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //因为字符串不可变，因此这里不再判定是否有nextNode了，因为必须存在,否则即是错误的调用
        char ctxValue = ((String) ctx).charAt(index);
        return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
    }

    public String toString() {
        return "IndexedCharSeqAccessor->[" + index + "]";
    }

    /** 单个下标的结果为字符,因此返回类型为字符 */
    public Class getKnownEgressType() {
        return Character.class;
    }
}
