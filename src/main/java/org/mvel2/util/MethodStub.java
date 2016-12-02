package org.mvel2.util;

import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** 表示一个方法句柄，可以通过import引用起来 */
public class MethodStub implements StaticStub {
    /** 当前方法所对应的方法 */
    private Class classReference;
    /** 方法名 */
    private String name;

    /** 当前方法 */
    private transient Method method;

    /** 通过方法对象来创建出句柄引用 */
    public MethodStub(Method method) {
        this.classReference = method.getDeclaringClass();
        this.name = method.getName();
    }

    /** 通过类名以及方法的名字创建出句柄引用 */
    public MethodStub(Class classReference, String methodName) {
        this.classReference = classReference;
        this.name = methodName;
    }

    public Class getClassReference() {
        return classReference;
    }

    public void setClassReference(Class classReference) {
        this.classReference = classReference;
    }

    public String getMethodName() {
        return name;
    }

    public void setMethodName(String methodName) {
        this.name = methodName;
    }

    /** 获取实际的方法引用 */
    public Method getMethod() {
        if(method == null) {
            for(Method method : classReference.getMethods()) {
                if(name.equals(method.getName())) return this.method = method;
            }
        }
        return method;
    }

    public Object call(Object ctx, Object thisCtx, VariableResolverFactory factory, Object[] parameters)
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke(ctx, parameters);
    }
}
