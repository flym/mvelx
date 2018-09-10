package org.mvelx.optimizers.impl.refl.nodes;

import org.mvelx.ParserContext;
import org.mvelx.ast.Function;
import org.mvelx.compiler.ExecutableStatement;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.util.InvokableUtils;


/**
 * 动态函数调用，表示当前的函数由ctx提供(即ctx即函数句柄),当前只需要直接调用相应函数即可,
 * 同时这里的函数是对原函数定义的一个句柄引用,即并不是一个实际的函数定义,因此称之为dynamic
 */
public class DynamicFunctionAccessor extends BaseAccessor {
    /** 相对应的值访问器 */
    private ExecutableStatement[] parameters;

    /** 根据相应的参数值访问器来构建相应的函数访问器 */
    public DynamicFunctionAccessor(ExecutableStatement[] params, String property, ParserContext parserContext) {
        super(InvokableUtils.fullInvokeName(property, params), parserContext);
        this.parameters = params;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //整个处理过程,即先获取相应的参数信息,将所有的参数收集之后,再直接调用相应的函数call即可
        //在调用时,当前ctx即表示函数对象本身
        Object[] params = null;

        Function function = (Function) ctx;

        if(parameters != null && parameters.length != 0) {
            params = new Object[parameters.length];
            for(int i = 0; i < params.length; i++) {
                params[i] = parameters[i].getValue(ctx, elCtx, variableFactory);
            }
        }

        Object value = function.call(ctx, elCtx, variableFactory, params);
        if(hasNextNode()) {
            return fetchNextAccessNode(value, elCtx, variableFactory).getValue(value, elCtx, variableFactory);
        }

        return value;
    }

    /** 函数只能被调用,因此没有设置函数一说 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        throw new RuntimeException("can't write to function");
    }

    /** 返回类型未知,为object类型 */
    public Class getKnownEgressType() {
        return Object.class;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}