package org.mvelx.optimizers.impl.refl.nodes;

import org.mvelx.ParserContext;
import org.mvelx.compiler.ExecutableStatement;
import org.mvelx.integration.PropertyHandler;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.util.InvokableUtils;

import java.lang.reflect.Method;

import static org.mvelx.DataConversion.convert;
import static org.mvelx.util.ParseTools.getBestCandidate;


/**
 * 描述调用方法的访问器，并且带有空值处理
 * 处理逻辑与MethodAccessor一致
 */
public class MethodAccessorNH extends BaseAccessor {
    /** 方法 */
    private Method method;
    /** 参数类型信息 */
    private Class[] parameterTypes;
    /** 表示参数的计算单元信息 */
    private ExecutableStatement[] params;
    /** 参数有效长度 */
    private int length;
    /** 是否需要作参数转换处理 */
    private boolean coercionNeeded = false;

    /** 相应的空值处理器 */
    private PropertyHandler nullHandler;

    /** 使用定义方法,相应的参数处理单元,以及相应的空值处理器来进行方法访问器构建 */
    public MethodAccessorNH(Method method, ExecutableStatement[] params, PropertyHandler handler, ParserContext parserContext) {
        super(InvokableUtils.fullInvokeName(method.getName(), params), parserContext);

        this.method = method;
        this.length = (this.parameterTypes = this.method.getParameterTypes()).length;

        this.params = params;
        this.nullHandler = handler;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //先尝试不作参数转换的调用
        if(!coercionNeeded) {
            try{
                //正常的调用,并根据是否返回null来决定是否加上空值处理器
                Object v = method.invoke(ctx, executeAll(elCtx, vars));
                if(v == null)
                    v = nullHandler.getProperty(method.getName(), ctx, vars);

                if(hasNextNode()) {
                    return fetchNextAccessNode(v, elCtx, vars).getValue(v, elCtx, vars);
                }
                return v;
            } catch(IllegalArgumentException e) {
                //先尝试可能的方法重写调用.
                if(ctx != null && method.getDeclaringClass() != ctx.getClass()) {
                    Method o = getBestCandidate(parameterTypes, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
                    if(o != null) {
                        return executeOverrideTarget(o, ctx, elCtx, vars);
                    }
                }

                //仍然不行,再尝试参数转换调用
                coercionNeeded = true;
                return getValue(ctx, elCtx, vars);
            } catch(Exception e) {
                throw new RuntimeException("cannot invoke method", e);
            }

        } else {
            //参数转换调用,则先对参数进行转换,再进行调用
            try{
                Object value = method.invoke(ctx, executeAndCoerce(parameterTypes, elCtx, vars));
                if(hasNextNode()) {
                    return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
                } else {
                    return value;
                }
            } catch(Exception e) {
                throw new RuntimeException("cannot invoke method", e);
            }
        }
    }

    /** 执行重写之后的相应目标方法 */
    private Object executeOverrideTarget(Method o, Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            Object v = o.invoke(ctx, executeAll(elCtx, vars));
            if(v == null)
                v = nullHandler.getProperty(o.getName(), ctx, vars);

            if(hasNextNode()) {
                return fetchNextAccessNode(v, elCtx, vars).getValue(v, elCtx, vars);
            } else {
                return v;
            }
        } catch(Exception e2) {
            throw new RuntimeException("unable to invoke method", e2);
        }
    }

    /** 处理相应的参数,对参数进行求值,转换为实际的传递参数 */
    private Object[] executeAll(Object ctx, VariableResolverFactory vars) {
        if(length == 0) return GetterAccessor.EMPTY;

        Object[] vals = new Object[length];
        for(int i = 0; i < length; i++) {
            vals[i] = params[i].getValue(ctx, vars);
        }
        return vals;
    }

    /** 对相应的参数进行求值,并根据相应的参数类型进行转换 */
    private Object[] executeAndCoerce(Class[] target, Object elCtx, VariableResolverFactory vars) {
        Object[] values = new Object[length];
        for(int i = 0; i < length; i++) {
            //noinspection unchecked
            values[i] = convert(params[i].getValue(elCtx, vars), target[i]);
        }
        return values;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //这里有问题,应该先调用相应的方法,再调用子节点
        Object ctxValue = getValue(ctx, elCtx, variableFactory);
        return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctx, elCtx, variableFactory, value);
    }

    /** 声明的类型即方法的返回类型 */
    public Class getKnownEgressType() {
        return method.getReturnType();
    }

}