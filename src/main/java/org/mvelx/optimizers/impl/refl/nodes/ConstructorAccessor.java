package org.mvelx.optimizers.impl.refl.nodes;

import lombok.Getter;
import org.mvelx.ParserContext;
import org.mvelx.compiler.ExecutableStatement;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.util.InvokableUtils;

import java.lang.reflect.Constructor;

/**
 * 表示对象访问器(创建对象)(由NewNode对应)
 * 如new T(a,b)这种的对象创建
 */
public class ConstructorAccessor extends InvokableAccessor {
    /** 当前所引用的构建函数 */
    @Getter
    private final Constructor constructor;

    /** 通过相应的构造函数,相应的参数访问器来进行构建 */
    public ConstructorAccessor(Constructor constructor, ExecutableStatement[] params, ParserContext parserContext) {
        super(InvokableUtils.fullInvokeName("new " + constructor.getDeclaringClass().getName(), params), parserContext);

        this.constructor = constructor;
        //相应的参数个数不能由参数访问器来决定,因为可能存在可变参数访问,因此由构造函数的方法声明来决定
        this.length = (this.parameterTypes = constructor.getParameterTypes()).length;
        this.parms = params;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //对象构建,直接使用相应的构造器的newInstance处理
        try{
            //默认情况下不作可变参数处理
            if(!coercionNeeded) {
                //根据是否存在nextNode决定是否转发请求
                try{
                    Object value = constructor.newInstance(executeAll(elCtx, variableFactory));
                    if(hasNextNode()) {
                        return fetchNextAccessNode(value, elCtx, variableFactory).getValue(value, elCtx, variableFactory);
                    }

                    return value;
                } catch(IllegalArgumentException e) {
                    //默认处理报错，重新设置标记位处理
                    coercionNeeded = true;
                    return getValue(ctx, elCtx, variableFactory);
                }

            }
            //参数是可变的,即... 可变参数的情况
            else {
                //变参处理,先根据相应的参数个数重新解析参数,再进行对象创建
                Object value = constructor.newInstance(executeAndCoerce(parameterTypes, elCtx, variableFactory, constructor.isVarArgs()));
                if(hasNextNode()) {
                    return fetchNextAccessNode(value, elCtx, variableFactory).getValue(value, elCtx, variableFactory);
                }
                return value;
            }
        } catch(Exception e) {
            throw new RuntimeException("cannot construct object", e);
        }
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return null;
    }

    /** 通过额外的参数上下文执行所有的参数节点信息,并返回相应的值信息 */
    private Object[] executeAll(Object ctx, VariableResolverFactory vars) {
        //参数为0,即无参构造函数
        if(length == 0) return GetterAccessor.EMPTY;

        Object[] vals = new Object[length];
        for(int i = 0; i < length; i++) {
            vals[i] = parms[i].getValue(ctx, vars);
        }
        return vals;
    }

    /** 相应的声明类型为构造函数的声明类来决定 */
    public Class getKnownEgressType() {
        //这里有问题(bug),应该返回相应的声明类型
        return constructor.getClass();
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }

    /** 返回相应的参数执行表达式 */
    public ExecutableStatement[] getParameters() {
        return parms;
    }
}
