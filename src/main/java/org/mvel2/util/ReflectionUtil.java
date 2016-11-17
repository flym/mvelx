/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.util;

import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.lang.System.arraycopy;

/**
 * 基于类型处理的一些工具类
 * Utilities for working with reflection.
 */
public class ReflectionUtil {

  /**
   * 获取指定字段串的setAbc形式
   * This new method 'slightly' outperforms the old method, it was
   * essentially a perfect example of me wasting my time and a
   * premature optimization.  But what the hell...
   *
   * @param s -
   * @return String
   */
  public static String getSetter(String s) {
    char[] chars = new char[s.length() + 3];

    chars[0] = 's';
    chars[1] = 'e';
    chars[2] = 't';

    chars[3] = toUpperCase(s.charAt(0));

    for (int i = s.length() - 1; i != 0; i--) {
      chars[i + 3] = s.charAt(i);
    }

    return new String(chars);
  }


  /** 获取指定字符串的 getAbc形式 参数为abc */
  public static String getGetter(String s) {
    char[] c = s.toCharArray();
    char[] chars = new char[c.length + 3];

    chars[0] = 'g';
    chars[1] = 'e';
    chars[2] = 't';

    chars[3] = toUpperCase(c[0]);

    arraycopy(c, 1, chars, 4, c.length - 1);

    return new String(chars);
  }


  /** 获取指定字符串的 isAbc形式 参数为abc */
  public static String getIsGetter(String s) {
    char[] c = s.toCharArray();
    char[] chars = new char[c.length + 2];

    chars[0] = 'i';
    chars[1] = 's';

    chars[2] = toUpperCase(c[0]);

    arraycopy(c, 1, chars, 3, c.length - 1);

    return new String(chars);
  }

  /** 尝试从方法器的方法名(字段名)中获取相应的属性信息 */
  public static String getPropertyFromAccessor(String s) {
    char[] c = s.toCharArray();
    char[] chars;

    if (c.length > 3 && c[1] == 'e' && c[2] == 't') {
      chars = new char[c.length - 3];

      if (c[0] == 'g' || c[0] == 's') {
        chars[0] = toLowerCase(c[3]);

        for (int i = 1; i < chars.length; i++) {
          chars[i] = c[i + 3];
        }

        return new String(chars);
      }
      else {
        return s;
      }
    }
    else if (c.length > 2 && c[0] == 'i' && c[1] == 's') {
      chars = new char[c.length - 2];

      chars[0] = toLowerCase(c[2]);

      for (int i = 1; i < chars.length; i++) {
        chars[i] = c[i + 2];
      }

      return new String(chars);
    }
    return s;
  }

  /** 转换为非基本类型 */
  public static Class<?> toNonPrimitiveType(Class<?> c) {
    if (!c.isPrimitive()) return c;
    if (c == int.class) return Integer.class;
    if (c == long.class) return Long.class;
    if (c == double.class) return Double.class;
    if (c == float.class) return Float.class;
    if (c == short.class) return Short.class;
    if (c == byte.class) return Byte.class;
    if (c == char.class) return Character.class;
    return Boolean.class;
  }

  /** 将基本类型数组转换为非基本类型数组 */
  public static Class<?> toNonPrimitiveArray(Class<?> c) {
    if (!c.isArray() || !c.getComponentType().isPrimitive()) return c;
    if (c == int[].class) return Integer[].class;
    if (c == long[].class) return Long[].class;
    if (c == double[].class) return Double[].class;
    if (c == float[].class) return Float[].class;
    if (c == short[].class) return Short[].class;
    if (c == byte[].class) return Byte[].class;
    if (c == char[].class) return Character[].class;
    return Boolean[].class;
  }

  /** 返回对指定基本类型的数组形式 */
  public static Class<?> toPrimitiveArrayType(Class<?> c) {
    if (!c.isPrimitive()) throw new RuntimeException(c + " is not a primitive type");
    if (c == int.class) return int[].class;
    if (c == long.class) return long[].class;
    if (c == double.class) return double[].class;
    if (c == float.class) return float[].class;
    if (c == short.class) return short[].class;
    if (c == byte.class) return byte[].class;
    if (c == char.class) return char[].class;
    return boolean[].class;
  }

  /** 判定2个类型是否可赋值兼容的 */
  public static boolean isAssignableFrom(Class<?> from, Class<?> to) {
    return from.isAssignableFrom(to) || areBoxingCompatible(from, to);
  }

  /** 判断2个类型是否在包装类型上兼容,即1个为基本,另一个为包装类型 */
  private static boolean areBoxingCompatible(Class<?> c1, Class<?> c2) {
    return c1.isPrimitive() ? isPrimitiveOf(c2, c1) : (c2.isPrimitive() && isPrimitiveOf(c1, c2));
  }

  /** 判断A类型是否是B类型的包装形式 */
  private static boolean isPrimitiveOf(Class<?> boxed, Class<?> primitive) {
    if (primitive == int.class) return boxed == Integer.class;
    if (primitive == long.class) return boxed == Long.class;
    if (primitive == double.class) return boxed == Double.class;
    if (primitive == float.class) return boxed == Float.class;
    if (primitive == short.class) return boxed == Short.class;
    if (primitive == byte.class) return boxed == Byte.class;
    if (primitive == char.class) return boxed == Character.class;
    if (primitive == boolean.class) return boxed == Boolean.class;
    return false;
  }
}
