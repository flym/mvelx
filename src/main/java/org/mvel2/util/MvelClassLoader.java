package org.mvel2.util;

/** 自实现加载器，用于加载特定的类 */
public interface MvelClassLoader {
    Class defineClassX(String className, byte[] b, int start, int end);

}
