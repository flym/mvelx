package org.mvelx.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 支持clone操作
 * Created by flym on 5/13/2016.
 */
public class CloneUtils {
    private static final Method cloneMethod;

    static {
        try{
            cloneMethod = Object.class.getDeclaredMethod("clone");
            cloneMethod.setAccessible(true);
        } catch(NoSuchMethodException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /** 对指定的对象进行clone处理，如果不能clone，则throw相应的异常 */
    @SuppressWarnings("unchecked")
    public static <T> T clone(T t) {
        if(t == null)
            return null;

        if(t instanceof Cloneable) {
            try{
                return (T) cloneMethod.invoke(t);
            } catch(IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return null;
    }
}
