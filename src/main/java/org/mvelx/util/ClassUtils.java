/* Created by flym at 12/4/16 */
package org.mvelx.util;

/** @author flym */
public class ClassUtils {
    /** 获取相应对象的类型信息 */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getType(T obj) {
        return obj == null ? null : (Class<T>) obj.getClass();
    }
}
