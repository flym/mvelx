package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.InvokableUtils;

import java.lang.reflect.Method;

import static org.mvel2.util.ParseTools.getBestCandidate;
import static org.mvel2.util.ParseTools.getWidenedTarget;

/** 表示方法访问器，通过方法调用来进行处理 */
public class MethodAccessor extends InvokableAccessor {

    /** 所引用的方法信息 */
    private final Method method;

    /** 通过方法以及相应的参数执行单元来进行方法访问器构建 */
    public MethodAccessor(Method method, ExecutableStatement[] params, ParserContext parserContext) {
        super(InvokableUtils.fullInvokeName(method.getName(), params), parserContext);
        this.method = method;
        //需要重新设置相应的方法参数类型信息以及参数个数
        this.length = (this.parameterTypes = this.method.getParameterTypes()).length;
        this.parms = params;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
        //通过可变参数和不可变分别处理,同时还要考虑方法重载的问题
        //如方法名为 getAbc(long abc)，但实际参数为getAbc(int)，则可能还存在方法getAbc(int)

        //先按照不需要进行参数转换的逻辑来执行
        if(!coercionNeeded) {
            try{
                Object value = method.invoke(ctx, executeAll(elCtx, vars, method));
                if(hasNextNode()) {
                    return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
                }
                return value;
            } catch(IllegalArgumentException e) {
                //调用失败了,则重新尝试方法重写的可能
                if(ctx != null && method.getDeclaringClass() != ctx.getClass()) {
                    Method o = getBestCandidate(parameterTypes, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
                    if(o != null) {
                        return executeOverrideTarget(getWidenedTarget(o), ctx, elCtx, vars);
                    }
                }

                //不是方法重写,则再尝试参数可能需要转换和变参的问题
                coercionNeeded = true;
                return getValue(ctx, elCtx, vars);
            } catch(Exception e) {
                throw new RuntimeException("cannot invoke method: " + method.getName(), e);
            }

        } else {
            try{
                //尝试进行参数转换并处理
                Object value = method.invoke(ctx, executeAndCoerce(parameterTypes, elCtx, vars, method.isVarArgs()));
                if(hasNextNode()) {
                    return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
                }

                return value;
            } catch(IllegalArgumentException e) {
                //参数转换之后仍调用失败,则尝试从父类找到定义更宽泛的方法来进行调用
                //比如,当前方法定义为get(X) 而父类定义为get(Object),则往往父类的调用很可能成功
                Object[] vs = executeAndCoerce(parameterTypes, elCtx, vars, false);
                Method newMeth;
                if((newMeth = getWidenedTarget(getBestCandidate(vs, method.getName(), ctx.getClass(),
                        ctx.getClass().getMethods(), false))) != null) {
                    return executeOverrideTarget(newMeth, ctx, elCtx, vars);
                } else {
                    throw e;
                }
            } catch(Exception e) {
                throw new RuntimeException("cannot invoke method: " + method.getName(), e);
            }
        }
    }

    /** 重新使用重写过的方法来执行相应的方法调用 */
    private Object executeOverrideTarget(Method o, Object ctx, Object elCtx, VariableResolverFactory vars) {
        //仍然先假定不需要进行参数转换和变参处理
        if(!coercionNeeded) {
            try{
                try{
                    Object value = o.invoke(ctx, executeAll(elCtx, vars, o));
                    if(hasNextNode()) {
                        return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
                    } else {
                        return value;
                    }
                } catch(IllegalArgumentException e) {
                    if(coercionNeeded) throw e;

                    coercionNeeded = true;
                    return executeOverrideTarget(o, ctx, elCtx, vars);
                }
            } catch(Exception e2) {
                throw new RuntimeException("unable to invoke method", e2);
            }
        } else {
            //按照参数转换和变长参数处理之后再执行
            try{
                Object value = o.invoke(ctx, executeAndCoerce(o.getParameterTypes(), elCtx, vars, o.isVarArgs()));
                if(hasNextNode()) {
                    return fetchNextAccessNode(value, elCtx, vars).getValue(value, elCtx, vars);
                } else {
                    return value;
                }
            } catch(Exception e2) {
                throw new RuntimeException("unable to invoke method (expected target: " + method.getDeclaringClass().getName() + "::" + method.getName() + "; " +
                        "actual target: " + ctx.getClass().getName() + "::" + method.getName() + "; coercionNeeded=" + (coercionNeeded ? "yes" : "no") + ")");
            }
        }
    }

    /** 处理参数,并处理相应的变长参数 */
    private Object[] executeAll(Object ctx, VariableResolverFactory vars, Method m) {
        //无参数
        if(length == 0) return GetterAccessor.EMPTY;

        //剩下的,为避免最后一个参数为变长参数,则进行小心判断
        //如果最后一个参数为变长,则不需要之,由后面统一处理
        Object[] vals = new Object[length];
        for(int i = 0; i < length - (m.isVarArgs() ? 1 : 0); i++) {
            vals[i] = parms[i].getValue(ctx, vars);
        }

        //处理变长参数
        if(m.isVarArgs()) {
            //当前参数值为null,则没有传递任何参数,则可以理解为仅有一个参数声明,就是变长参数,则直接传递空数组参数即可
            if(parms == null) {
                vals[length - 1] = new Object[0];
            }
            //当前实际参数个数与定义参数个数一样,则认为最没有传递相应的变长参数过来,则根据最后个实参是否已经为数组来判断是否需要将其转换为数组
            else if(parms.length == length) {
                Object lastParam = parms[length - 1].getValue(ctx, vars);
                //这里实际上有问题,可能期望的调用值为(Object)a[],但这里仍然将其认为是变参,这是一个潜在的问题.
                //如调用 setX((Object)(new java.lang.Integer[]{1,2,3}));
                vals[length - 1] = lastParam == null || lastParam.getClass().isArray() ? lastParam : new Object[]{lastParam};
            } else {
                //实参与定义参数不一样,则声明相应的实际变长参数个位置的数组,将相应的数据填充到数组中,最后将整个数组作为相应的实际参数来处理
                Object[] vararg = new Object[parms.length - length + 1];
                for(int i = 0; i < vararg.length; i++) vararg[i] = parms[length - 1 + i].getValue(ctx, vars);
                vals[length - 1] = vararg;
            }
        }

        return vals;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //这里仅支持按实际参数直接调用,不需要参数转换.是一个潜在处理的问题
        try{
            Object ctxValue = method.invoke(ctx, executeAll(elCtx, variableFactory, method));
            return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
        } catch(IllegalArgumentException e) {
            if(ctx != null && method.getDeclaringClass() != ctx.getClass()) {
                Method o = getBestCandidate(parameterTypes, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
                if(o != null) {
                    Object ctxValue = executeOverrideTarget(o, ctx, elCtx, variableFactory);
                    return fetchNextAccessNode(ctxValue, elCtx, variableFactory).setValue(ctxValue, elCtx, variableFactory, value);
                }
            }

            //这里的参数转换并没有被调用, 需要调整进行处理
            coercionNeeded = true;
            return setValue(ctx, elCtx, variableFactory, value);
        } catch(Exception e) {
            throw new RuntimeException("cannot invoke method", e);
        }
    }

    /** 返回类型为方法的声明返回类型 */
    public Class getKnownEgressType() {
        return method.getReturnType();
    }
}