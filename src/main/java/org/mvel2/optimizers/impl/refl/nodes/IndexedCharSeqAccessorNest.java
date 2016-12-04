package org.mvel2.optimizers.impl.refl.nodes;

import lombok.Getter;
import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

/** 字符串下标访问，下标值为执行单元的访问器 */
public class IndexedCharSeqAccessorNest extends BaseAccessor {
    /** 下标执行单元 */
    @Getter
    private final ExecutableStatement index;

    public IndexedCharSeqAccessorNest(ExecutableStatement index, String property, ParserContext parserContext) {
        super("[" + property + "]", parserContext);
        this.index = index;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //这里计算出下标值,再采用相应的charAt来获取相应的字符信息
        val value = ((String) ctx).charAt((Integer) index.getValue(ctx, elCtx, vars));
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
        }

        return value;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //因为字符串不可变,因此这里肯定会有相应的next节点值,以便完成整个set操作
        char ctxValue = ((String) ctx).charAt((Integer) index.getValue(ctx, elCtx, variableFactory));
        return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
    }

    public String toString() {
        return "IndexedCharSeqAccessorNest[" + index + "]";
    }

    /** 字符串下标处理返回类型即为字符 */
    public Class getKnownEgressType() {
        return Character.class;
    }
}
