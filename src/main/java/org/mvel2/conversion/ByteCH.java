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

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.valueOf;

/** 将各种数据转换为byte类型 */
public class ByteCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();

  /** 字符串转byte的实现,即采用parse来实现 */
  private static Converter stringConverter = new Converter() {
    public Object convert(Object o) {
      return Byte.parseByte(((String) o));
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
    //已实现的字符串转
    CNV.put(String.class,
        stringConverter
    );

    //任意对象,通过其toString形式处理
    CNV.put(Object.class,
        new Converter() {
          public Object convert(Object o) {
            return stringConverter.convert(valueOf(o));
          }
        }
    );

    //byte类型,宽化处理
    CNV.put(Byte.class,
        new Converter() {
          public Object convert(Object o) {
            //noinspection UnnecessaryBoxing
            return new Byte(((Byte) o));
          }
        }
    );

    //integer, 窄化处理
    CNV.put(Integer.class,
        new Converter() {
          public Object convert(Object o) {
            return ((Integer) o).byteValue();
          }
        }
    );

    //long, 窄化处理
    CNV.put(Long.class,
        new Converter() {
          public Object convert(Object o) {
            return ((Long) o).byteValue();
          }
        });

    //double, 窄化处理
    CNV.put(Double.class,
        new Converter() {
          public Object convert(Object o) {
            return ((Double) o).byteValue();
          }
        });

    //float, 窄化处理
    CNV.put(Float.class,
        new Converter() {
          public Object convert(Object o) {
            return ((Float) o).byteValue();
          }
        });

    //short, 窄化处理
    CNV.put(Short.class,
        new Converter() {
          public Object convert(Object o) {
            return ((Short) o).byteValue();
          }
        });
  }
}
