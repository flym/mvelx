package org.mvel2.util;

/**
 * 内部统一使用的类加载器
 * 提供统一的加载访问,避免随便引用
 */
public class JitClassLoader extends ClassLoader implements MvelClassLoader {
    public JitClassLoader(ClassLoader classLoader) {
        super(classLoader);
    }

    public Class<?> defineClassX(String className, byte[] b, int off, int len) {
        return super.defineClass(className, b, off, len);
    }
}
