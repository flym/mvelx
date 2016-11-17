package org.mvel2.util;

import java.lang.reflect.*;

/** 可变参数转换工具类 */
public class Varargs {

  /** 将可变的参数信息转换为正常的参数 */
    public static Object[] normalizeArgsForVarArgs(Class<?>[] parameterTypes, Object[] args, boolean isVarArgs) {
      //本身不是可变的,直接返回
        if (!isVarArgs) return args;
      //这里判断相应的参数定义长度和实现的参数长度是否一致,如果一致,则表示相应的参数是相匹配的
        Object lastArgument = args.length > 0 ? args[args.length - 1] : Array.newInstance(parameterTypes[parameterTypes.length-1].getComponentType(), 0);
      //定义和相应的长度是匹配的,并且最末一个要么是null(表示可变参数值为null),要么为数组,则表示可变参数本身即是已经为数组,即是正常的调用
        if (parameterTypes.length == args.length && (lastArgument == null || lastArgument.getClass().isArray())) return args;

      //尝试构建出最后一个参数对象
        int varargLength = args.length - parameterTypes.length + 1;
        Object vararg = Array.newInstance(parameterTypes[parameterTypes.length-1].getComponentType(), varargLength);
        for (int i = 0; i < varargLength; i++) Array.set(vararg, i, args[parameterTypes.length - 1 + i]);

      //将可变参数和正常的参数一起,组合成与定义参数相同数组长度的实际参数
        Object[] normalizedArgs = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length - 1; i++) normalizedArgs[i] = args[i];
        normalizedArgs[parameterTypes.length - 1] = vararg;
        return normalizedArgs;
    }

  /** 获取指定参数的类型信息,并且保证是变参安全的,如果是变参，则获取相应的组件类型 */
    public static Class<?> paramTypeVarArgsSafe(Class<?>[] parameterTypes, int i, boolean isVarArgs) {
      if (!isVarArgs) return parameterTypes[i];
      if (i < parameterTypes.length-1) return parameterTypes[i];
      return parameterTypes[parameterTypes.length-1].getComponentType();
    }
}
