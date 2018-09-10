package org.mvelx.optimizers.impl.refl.nodes;

import lombok.Getter;
import org.mvelx.CompileException;
import org.mvelx.ParserContext;
import org.mvelx.integration.PropertyHandler;
import org.mvelx.integration.VariableResolverFactory;

import java.lang.reflect.Method;

import static org.mvelx.util.ParseTools.getBestCandidate;

/** 带空值处理器的getter方法访问 */
public class GetterAccessorNH extends BaseAccessor {
    /** 对应的方法 */
    @Getter
    private final Method method;
    /** 空值处理 */
    private PropertyHandler nullHandler;

    public static final Object[] EMPTY = new Object[0];

    /** 使用getter方法和相应的null值处理器进行访问器构建 */
    public GetterAccessorNH(Method method, PropertyHandler nullHandler, String property, ParserContext parserContext) {
        super(property, parserContext);
        this.method = method;
        this.nullHandler = nullHandler;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            return nullHandle(method.getName(), method.invoke(ctx, EMPTY), ctx, elCtx, vars);
        } catch(IllegalArgumentException e) {
            if(ctx != null && method.getDeclaringClass() != ctx.getClass()) {
                Method o = getBestCandidate(EMPTY, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
                if(o != null) {
                    return executeOverrideTarget(o, ctx, elCtx, vars);
                }
            }
            throw e;
        } catch(Exception e) {
            throw new RuntimeException("cannot invoke getter: " + method.getName()
                    + " [declr.class: " + method.getDeclaringClass().getName() + "; act.class: "
                    + (ctx != null ? ctx.getClass().getName() : "null") + "]", e);
        }
    }

    public String toString() {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
        //这里并未判断是否有子级节点,实际上应该进行判断

        try{
            //获取相应的值,再转由子节点处理
            Object v = method.invoke(ctx, EMPTY);
            if(v == null)
                v = nullHandler.getProperty(method.getName(), ctx, vars);
            return fetchNextAccessNode(v, elCtx, vars).setValue(v, elCtx, vars, value);

        } catch(CompileException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException("error " + method.getName() + ": " + e.getClass().getName() + ":" + e.getMessage(), e);
        }
    }

    public Class getKnownEgressType() {
        return method.getReturnType();
    }

    /** 执行重写过后的方法 */
    private Object executeOverrideTarget(Method o, Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            return nullHandle(o.getName(), o.invoke(ctx, EMPTY), ctx, elCtx, vars);
        } catch(Exception e2) {
            throw new RuntimeException("unable to invoke method", e2);
        }
    }

    /** 根据当前方法的执行值,先进行null值处理,再根据子级节点进行相应的逻辑调用 */
    private Object nullHandle(String name, Object v, Object ctx, Object elCtx, VariableResolverFactory vars) {
        //值非空,转由next或直接返回
        if(v != null) {
            if(hasNextNode()) {
                return fetchNextAccessNode(v, elCtx, vars).getValue(v, elCtx, vars);
            } else {
                return v;
            }
        }
        //值为空,则交由null值处理或继续转向next
        else {
            if(hasNextNode()) {
                return fetchNextAccessNode(null, elCtx, vars).getValue(nullHandler.getProperty(name, ctx, vars), elCtx, vars);
            } else {
                return nullHandler.getProperty(name, ctx, vars);
            }
        }
    }
}