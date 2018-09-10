package org.mvelx.optimizers.impl.refl.collection;

import org.mvelx.compiler.Accessor;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.optimizers.impl.refl.nodes.BaseAccessor;

import java.lang.reflect.Array;

import static java.lang.reflect.Array.newInstance;

/**
 * 根据一个已有的类型以及相应的数组中的每一项值构建出相应的数组对象
 * 用于内联方式的数组创建处理
 *
 * @author Christopher Brock
 */
public class ArrayCreator extends BaseAccessor implements Accessor {
    /** 数组中的每一项的值 */
    public Accessor[] template;
    /** 构建出来的数组类型 */
    private Class arrayType;

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //根据所定义的数组类型进行不同的处理,使用object或者是使用array.newInstance来处理
        if(Object.class.equals(arrayType)) {
            Object[] newArray = new Object[template.length];

            for(int i = 0; i < newArray.length; i++) {
                newArray[i] = template[i].getValue(ctx, elCtx, variableFactory);
            }

            return newArray;
        } else {
            Object newArray = newInstance(arrayType, template.length);
            for(int i = 0; i < template.length; i++) {
                Array.set(newArray, i, template[i].getValue(ctx, elCtx, variableFactory));
            }

            return newArray;
        }
    }

    /** 使用数组对象值+相应的定义类型进行构建 */
    public ArrayCreator(Accessor[] template, Class arrayType) {
        super(null, null);
        this.template = template;
        this.arrayType = arrayType;
    }

    /** 因为是创建对象,因此不能设置值信息 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return null;
    }

    /** 相应的声明类型为所定义的类型 */
    public Class getKnownEgressType() {
        return arrayType;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
