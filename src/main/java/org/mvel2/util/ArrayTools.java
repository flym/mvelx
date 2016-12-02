package org.mvel2.util;

/** 数组工具类,用于一些特定的工具处理 */
public class ArrayTools {

    /** 在指定字符数组中,指定范围的情况下,找到指定字符的第1次出现位置 */
    public static int findFirst(char c, int start, int offset, char[] array) {
        int end = start + offset;
        for(int i = start; i < end; i++) {
            if(array[i] == c) return i;
        }
        return -1;
    }

    /** 在指定字符数组中,指定范围的情况下,找到指定字符的第1次出现位置,查找过程为倒序查找 */
    public static int findLast(char c, int start, int offset, char[] array) {
        for(int i = start + offset - 1; i >= 0; i--) {
            if(array[i] == c) return i;
        }
        return -1;
    }
}
