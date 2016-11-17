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

import org.mvel2.ConversionException;
import org.mvel2.ConversionHandler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.valueOf;

/**
 * 各种类型转换字符
 * 由于字符串和字符之间容易冲突，因此支持字符串转字符，但前提是此字符串的长度必须为1
 * */
public class CharCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();

  /** 提供的字符串转字符的实现 */
  private static final Converter stringConverter =
      new Converter() {
        public Object convert(Object o) {
          //仅限制字符串长度为1
          if ((((String) o).length()) > 1)
            throw new ConversionException("cannot convert a string with a length greater than 1 to java.lang.Character");

          return (((String) o)).charAt(0);
        }
      };

  public Object convertFrom(Object in) {
    if (!CNV.containsKey(in.getClass())) throw new ConversionException("cannot convert type: "
        + in.getClass().getName() + " to: " + Integer.class.getName());
    return CNV.get(in.getClass()).convert(in);
  }


  public boolean canConvertFrom(Class cls) {
    return CNV.containsKey(cls);
  }

  static {
    //已实现的字符串转字符
    CNV.put(String.class, stringConverter);

    //对象,使用其toString形式再转
    CNV.put(Object.class,
        new Converter() {
          public Object convert(Object o) {
            return stringConverter.convert(valueOf(o));
          }
        }
    );

    //字符,自身转换
    CNV.put(Character.class,
        new Converter() {
          public Object convert(Object o) {
            //noinspection UnnecessaryBoxing
            return new Character(((Character) o));
          }
        }
    );

    //bigDecimal,窄化为int再转
    CNV.put(BigDecimal.class,
        new Converter() {
          public Object convert(Object o) {
            return (char) ((BigDecimal) o).intValue();
          }
        }
    );

    //integer,窄化转换
    CNV.put(Integer.class,
        new Converter() {

          public Object convert(Object o) {
            return (char) ((Integer) o).intValue();
          }
        }
    );
  }
}
