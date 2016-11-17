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


package org.mvel2;

import org.mvel2.conversion.*;
import org.mvel2.util.FastList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static org.mvel2.util.ReflectionUtil.isAssignableFrom;
import static org.mvel2.util.ReflectionUtil.toNonPrimitiveType;

/**
 * 内部自带的对象转换器,即把当前内置的实现全部打包进行一起处理，统一对外提供工具处理类
 * The DataConversion factory is where all of MVEL's type converters are registered with the runtime.
 *
 * @author Mike Brock
 * @see ConversionHandler
 */
public class DataConversion {
  /** 转换处理程序，key为转换至的目标类，源类为handler中自行判定 */
  private static final Map<Class, ConversionHandler> CONVERTERS
      = new HashMap<Class, ConversionHandler>(38 * 2, 0.5f);

  /** 无用接口 */
  private interface ArrayTypeMarker {
  }

  static {
    ConversionHandler ch;

    CONVERTERS.put(Integer.class, ch = new IntegerCH());
    CONVERTERS.put(int.class, ch);

    CONVERTERS.put(Short.class, ch = new ShortCH());
    CONVERTERS.put(short.class, ch);

    CONVERTERS.put(Long.class, ch = new LongCH());
    CONVERTERS.put(long.class, ch);

    CONVERTERS.put(Character.class, ch = new CharCH());
    CONVERTERS.put(char.class, ch);

    CONVERTERS.put(Byte.class, ch = new ByteCH());
    CONVERTERS.put(byte.class, ch);

    CONVERTERS.put(Float.class, ch = new FloatCH());
    CONVERTERS.put(float.class, ch);

    CONVERTERS.put(Double.class, ch = new DoubleCH());
    CONVERTERS.put(double.class, ch);

    CONVERTERS.put(Boolean.class, ch = new BooleanCH());
    CONVERTERS.put(boolean.class, ch);

    CONVERTERS.put(String.class, new StringCH());

    CONVERTERS.put(Object.class, new ObjectCH());

    CONVERTERS.put(Character[].class, ch = new CharArrayCH());
    CONVERTERS.put(char[].class, new CompositeCH(ch, new ArrayHandler(char[].class)));

    CONVERTERS.put(String[].class, new StringArrayCH());

    CONVERTERS.put(Integer[].class, new IntArrayCH());

    CONVERTERS.put(int[].class, new ArrayHandler(int[].class));
    CONVERTERS.put(long[].class, new ArrayHandler(long[].class));
    CONVERTERS.put(double[].class, new ArrayHandler(double[].class));
    CONVERTERS.put(float[].class, new ArrayHandler(float[].class));
    CONVERTERS.put(short[].class, new ArrayHandler(short[].class));
    CONVERTERS.put(boolean[].class, new ArrayHandler(boolean[].class));
    CONVERTERS.put(byte[].class, new ArrayHandler(byte[].class));

    CONVERTERS.put(BigDecimal.class, new BigDecimalCH());
    CONVERTERS.put(BigInteger.class, new BigIntegerCH());

    CONVERTERS.put(List.class, ch = new ListCH());
    CONVERTERS.put(FastList.class, ch);
    CONVERTERS.put(ArrayList.class, ch);
    CONVERTERS.put(LinkedList.class, ch);

    CONVERTERS.put(Set.class, ch = new SetCH());
    CONVERTERS.put(HashSet.class, ch);
    CONVERTERS.put(LinkedHashSet.class, ch);
    CONVERTERS.put(TreeSet.class, ch);
  }

  /** 判定两个类型之间是否能够进行转换 */
  public static boolean canConvert(Class toType, Class convertFrom) {
    //如果本身即是类型兼容，即父子类型，那么直接支持
    if (isAssignableFrom(toType, convertFrom)) return true;
    //先判定是否直接支持转换
    if (CONVERTERS.containsKey(toType)) {
      return CONVERTERS.get(toType).canConvertFrom(toNonPrimitiveType(convertFrom));
    }
    //如果转换的目标类为数组，但源类型与目标类型兼容，也可以转换
    //即可以认为两个类型之间可以通过数组再进行处理,即A[]->B[]
    else if (toType.isArray() && canConvert(toType.getComponentType(), convertFrom)) {
      return true;
    }
    return false;
  }

  /** 对象转换 */
  public static <T> T convert(Object in, Class<T> toType) {
    //空处理
    if (in == null) return null;
    //类型兼容
    if (toType == in.getClass() || toType.isAssignableFrom(in.getClass())) {
      return (T) in;
    }

    //在接下来的转换中，如果目标类型为数组，那么可以认为如果存在源类型到componentType的转换
    //则也可以进行转换，同时通过ArrayHandler来实现相应的处理逻辑即可
    ConversionHandler h = CONVERTERS.get(toType);
    if (h == null && toType.isArray()) {
      ArrayHandler ah;
      //这里动态添加新的转换处理类，即A[]->B[]的转换
      CONVERTERS.put(toType, ah = new ArrayHandler(toType));
      return (T) ah.convertFrom(in);
    }
    else {
      return (T) h.convertFrom(in);
    }
  }

  /**
   * 注册并添加新的转换器
   * Register a new {@link ConversionHandler} with the factory.
   *
   * @param type    - Target type represented by the conversion handler.
   * @param handler - An instance of the handler.
   */
  public static void addConversionHandler(Class type, ConversionHandler handler) {
    CONVERTERS.put(type, handler);
  }

  public static void main(String[] args) {
    System.out.println(char[][].class);
  }
}
