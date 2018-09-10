package org.mvelx.optimizers.impl.refl.nodes;

import lombok.Getter;
import lombok.val;
import org.mvelx.CompileException;
import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;

import java.lang.reflect.Method;

import static org.mvelx.util.ParseTools.getBestCandidate;

/** 表示访问一个getter方法 访问器 */
public class GetterAccessor extends BaseAccessor {
    /** 所对应的方法 */
    @Getter
    private final Method method;

    public static final Object[] EMPTY = new Object[0];

    /** 通过相应的getter方法进行访问器构建 */
    public GetterAccessor(Method method, String property, ParserContext parserContext) {
        super(property, parserContext);
        this.method = method;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            val value = method.invoke(ctx, EMPTY);
            //根据是否有下级节点决定相应的逻辑
            if(hasNextNode()) {
                return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
            }
            return value;
        } catch(IllegalArgumentException e) {
            //这里处理类型不匹配 的问题，即method的调用者不正确，因此这里重新获取相应的方法信息进行处理
            if(ctx != null && method.getDeclaringClass() != ctx.getClass()) {
                Method o = getBestCandidate(EMPTY, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
                if(o != null) {
                    return executeOverrideTarget(o, ctx, elCtx, vars);
                }
            }
            throw e;
        } catch(NullPointerException e) {
            if(ctx == null) {
                throw new RuntimeException("unable to invoke method: " + method.getDeclaringClass().getName() + "." + method.getName() + ": " +
                        "target of method is null", e);
            } else {
                throw new RuntimeException("cannot invoke getter: " + method.getName() + " (see trace)", e);
            }
        } catch(Exception e) {
            throw new RuntimeException("cannot invoke getter: " + method.getName()
                    + " [declr.class: " + method.getDeclaringClass().getName() + "; act.class: "
                    + (ctx != null ? ctx.getClass().getName() : "null") + "] (see trace)", e);
        }
    }

    public String toString() {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /** 不直接进行设置值,但是如果有下一级节点,则将相应的值传递给下一级节点 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
        try{
            if(hasNextNode()) {
                Object ctxValue = method.invoke(ctx, EMPTY);
                return fetchNextAccessNode(ctxValue, elCtx, vars).setValue(ctxValue, elCtx, vars, value);
            } else {
                //不需要单独设置值
                throw new RuntimeException("bad payload");
            }
        } catch(CompileException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException("error " + method.getName() + ": " + e.getClass().getName() + ":" + e.getMessage(), e);
        }
    }

    public Class getKnownEgressType() {
        return method.getReturnType();
    }

    /** 重新调用重写的其它方法 */
    private Object executeOverrideTarget(Method o, Object ctx, Object elCtx, VariableResolverFactory vars) {
        try{
            val v = o.invoke(ctx, EMPTY);
            if(hasNextNode()) {
                return fetchNextAccessNode(v, elCtx, vars).getValue(v, elCtx, vars);
            }

            return v;
        } catch(Exception e2) {
            throw new RuntimeException("unable to invoke method:" + e2.getMessage(), e2);
        }
    }
}
