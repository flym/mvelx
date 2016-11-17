package org.mvel2.util;

/** 兼容性策略,用于判断在一些操作场景中,两个对象类型是否是想到兼容的,比如相等性处理,比较性处理 */
public class CompatibilityStrategy {

    private CompatibilityStrategy() { }

    /** 相应的兼容接口定义 */
    public interface CompatibilityEvaluator {
      /** 两个类型是否可用于 equals 判定 */
        boolean areEqualityCompatible(Class<?> c1, Class<?> c2);
      /** 两个类型是否可用于比较(包括数字比较或运算) */
        boolean areComparisonCompatible(Class<?> c1, Class<?> c2);
    }

    /** 默认的兼容实现 */
    public static CompatibilityEvaluator compatibilityEvaluator = new DefaultCompatibilityEvaluator();

  /** 工具方法,使用默认的兼容器判断两个类型是否可用于 equals 比较判定 */
    public static boolean areEqualityCompatible(Class<?> c1, Class<?> c2) {
        return compatibilityEvaluator.areEqualityCompatible(c1, c2);
    }

    /** 工具方法,使用默认的兼容器判断两个类型是否可用于 比较 或数字运算 */
    public static boolean areComparisonCompatible(Class<?> c1, Class<?> c2) {
        return compatibilityEvaluator.areComparisonCompatible(c1, c2);
    }

    public static void setCompatibilityEvaluator(CompatibilityEvaluator compatibilityEvaluator) {
        CompatibilityStrategy.compatibilityEvaluator = compatibilityEvaluator;
    }

    public static class DefaultCompatibilityEvaluator implements CompatibilityEvaluator {

        public boolean areEqualityCompatible(Class<?> c1, Class<?> c2) {
          //两个都为空类型
            if (c1 == NullType.class || c2 == NullType.class) return true;
          //两个为父子类型
            if (c1.isAssignableFrom(c2) || c2.isAssignableFrom(c1)) return true;
          //如果第1个为数字包装类型且第2个也同样为数字包装类型或者字符串,也认为是可以相等判断的
            if (isBoxedNumber(c1, false) && isBoxedNumber(c2, true)) return true;
          //第1个为基本类型,第2个为基本类型或 基本类型兼容(或字符串) 也认为可以作相等判断
            if (c1.isPrimitive()) return c2.isPrimitive() || arePrimitiveCompatible(c1, c2, true);
          //第2个为基本类型,则第1个必须为相应基本类型的包装类型才行
            if (c2.isPrimitive()) return arePrimitiveCompatible(c2, c1, false);
            return false;
        }

        public boolean areComparisonCompatible(Class<?> c1, Class<?> c2) {
          //与比较判断相同
            return areEqualityCompatible(c1, c2);
        }

        /**
         * 两个类型是否基本类型上是兼容的
         * @param leftFirst 是否可以一方为字符串
         *  */
        private boolean arePrimitiveCompatible(Class<?> primitive, Class<?> boxed, boolean leftFirst) {
            if (primitive == Boolean.TYPE) return boxed == Boolean.class;
            if (primitive == Integer.TYPE) return isBoxedNumber(boxed, leftFirst);
            if (primitive == Long.TYPE) return isBoxedNumber(boxed, leftFirst);
            if (primitive == Double.TYPE) return isBoxedNumber(boxed, leftFirst);
            if (primitive == Float.TYPE) return isBoxedNumber(boxed, leftFirst);
            if (primitive == Character.TYPE) return boxed == Character.class;
            if (primitive == Byte.TYPE) return boxed == Byte.class;
            if (primitive == Short.TYPE) return boxed == Short.class;
            return false;
        }

        /**
         * 判断指定类型是否是数字类型 或者是字符串类型
         * @param allowString 如果类型不是数字,那么根据此标记判断其是否允许为字符串.如果此标记被设置,且为字符串,也返回true
         *
         * */
        private boolean isBoxedNumber(Class<?> c, boolean allowString) {
            return Number.class.isAssignableFrom(c) || (allowString && c == String.class);
        }
    }
}
