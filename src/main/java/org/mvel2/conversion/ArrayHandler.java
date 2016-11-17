/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.conversion;

import org.mvel2.ConversionHandler;

import java.lang.reflect.Array;
import java.util.Collection;

import static java.lang.reflect.Array.*;
import static org.mvel2.DataConversion.convert;

/**
 * 提供一种从一种数组，集合转换为另一种数组的能力
 * 在具体的元素转换当中，仍采用原始的转换能力来进行，即采用DataConversion的转换规则
 */
public class ArrayHandler implements ConversionHandler {
  /** 此数组对象的类型，要求必须为X[]的类型，如传递一个int[].class这样的class类型 */
  private final Class type;

  public ArrayHandler(Class type) {
    this.type = type;
  }

  public Object convertFrom(Object in) {
    return handleLooseTypeConversion(in.getClass(), in, type);
  }

  public boolean canConvertFrom(Class cls) {
    //只要原类型为数组或集合即可进行转换
    return cls.isArray() || Collection.class.isAssignableFrom(cls);
  }


  /**
   * 支持基本类型数组的信息转换
   * Messy method to handle primitive boxing for conversion. If someone can re-write this more
   * elegantly, be my guest.
   *
   * @param sourceType  源类型
   * @param input 输入值
   * @param targetType  要转换的目标类型
   * @return  转换后的结果数组信息
   */
  private static Object handleLooseTypeConversion(Class sourceType, Object input, Class targetType) {
    Class targType = targetType.getComponentType();
    //原类型为集合，那么直接遍列转换即可
    if (Collection.class.isAssignableFrom(sourceType)) {
      Object newArray = newInstance(targType, ((Collection) input).size());

      int i = 0;
      for (Object o : ((Collection) input)) {
        Array.set(newArray, i++, convert(o, targType));
      }

      return newArray;
    }

    //这里判定原类型不是数组，但从之前的canHandle来看，这里不是集合那肯定就是数组
    // 因此可以认为这里的if判定永不为true
    //这里的原意为如果源类型不是数组，则将其数组化，即转换为[x]，但实际上没什么作用
    if (!input.getClass().isArray()) {
      // if the input isn't an array converts it in an array with lenght = 1 having has its single item the input itself
      Object target = newInstance(targType, 1);
      set(target, 0, input);
      return target;
    }

    //原为数组,则直接按照数组相互转换的逻辑进行
    int len = getLength(input);
    Object target = newInstance(targType, len);

    for (int i = 0; i < len; i++) {
      set(target, i, convert(get(input, i), targType));
    }

    return target;
  }
}
