package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.ast.FunctionInstance;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.InvokableUtils;


/** 描述一个函数调用访问器 */
public class FunctionAccessor extends BaseAccessor {
    /** 函数定义实体对象 */
    private FunctionInstance function;
    /** 相应的参数信息 */
    private ExecutableStatement[] parameters;

    /** 通过相应的函数实例以及相应的参数构建出相应的访问器 */
    public FunctionAccessor(FunctionInstance function, ExecutableStatement[] params, ParserContext parserContext) {
        super(InvokableUtils.fullInvokeName(function.getFunction().getName(), params), parserContext);
        this.function = function;
        this.parameters = params;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        Object[] parms = null;

        //先处理参数
        if(parameters != null && parameters.length != 0) {
            parms = new Object[parameters.length];
            for(int i = 0; i < parms.length; i++) {
                parms[i] = parameters[i].getValue(ctx, elCtx, variableFactory);
            }
        }

        val value = function.call(ctx, elCtx, variableFactory, parms);

        //根据是否是下级节点决定相应的逻辑处理
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, variableFactory).getValue(value, elCtx, variableFactory);
        }

        return value;
    }

    /** 函数调用,只能调用,而不能修改 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        throw new RuntimeException("can't write to function");
    }

    /** 返回类型未知,声明为Object类型 */
    public Class getKnownEgressType() {
        return Object.class;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
