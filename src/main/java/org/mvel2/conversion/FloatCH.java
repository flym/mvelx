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

package org.mvel2.conversion;

import org.mvel2.ConversionException;
import org.mvel2.ConversionHandler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.valueOf;

/** 各种类型转float */
public class FloatCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();

  /** 已实现的字符串转float */
  private static Converter stringConverter = new Converter() {
    public Object convert(Object o) {
      if (((String) o).length() == 0) return (float) 0;

      return Float.parseFloat(((String) o));
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
    // 使用已实现的转换器处理
    CNV.put(String.class,
        stringConverter
    );


    //对象转, 采用其toString形式再转换
    CNV.put(Object.class,
        new Converter() {
          public Object convert(Object o) {
            return stringConverter.convert(valueOf(o));
          }
        }
    );

    //bigDecimal,窄化处理
    CNV.put(BigDecimal.class,
        new Converter() {
          public Float convert(Object o) {
            return ((BigDecimal) o).floatValue();
          }
        }
    );


    //bigInteger,窄化处理
    CNV.put(BigInteger.class,
        new Converter() {
          public Float convert(Object o) {
            return ((BigInteger) o).floatValue();
          }
        }
    );


    //float类型,原样返回
    CNV.put(Float.class,
        new Converter() {
          public Object convert(Object o) {
            return o;
          }
        }
    );

    //integer,宽化处理
    CNV.put(Integer.class,
        new Converter() {
          public Float convert(Object o) {
            //noinspection UnnecessaryBoxing
            return ((Integer) o).floatValue();
          }
        }
    );


    //double,窄化处理
    CNV.put(Double.class,
        new Converter() {
          public Float convert(Object o) {
            return ((Double) o).floatValue();
          }
        }
    );

    //long, 使用number相应方法返回
    CNV.put(Long.class,
        new Converter() {
          public Float convert(Object o) {
            return ((Long) o).floatValue();
          }
        }
    );

    //short,宽化处理
    CNV.put(Short.class,
        new Converter() {
          public Float convert(Object o) {
            return ((Short) o).floatValue();
          }
        }
    );

    //boolean,true为1,false为0
    CNV.put(Boolean.class,
        new Converter() {
          public Float convert(Object o) {
            if ((Boolean) o) return 1f;
            else return 0f;
          }
        }
    );

  }
}
