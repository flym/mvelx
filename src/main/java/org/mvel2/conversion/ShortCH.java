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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Short.parseShort;
import static java.lang.String.valueOf;

/** 各种对象转short */
public class ShortCH implements ConversionHandler {
  /**
   * This is purely because Eclipse sucks, and has a serious bug with
   * it's java parser.
   */
  private static final Short TRUE = (short) 1;
  private static final Short FALSE = (short) 0;

  /** 自实现的字符串转short,采用parseShort处理 */
  private static Converter stringConverter = new Converter() {
    public Short convert(Object o) {
      return parseShort(((String) o));
    }
  };

  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();


  public Object convertFrom(Object in) {
    if (!CNV.containsKey(in.getClass())) throw new ConversionException("cannot convert type: "
        + in.getClass().getName() + " to: " + Short.class.getName());
    return CNV.get(in.getClass()).convert(in);
  }


  public boolean canConvertFrom(Class cls) {
    return CNV.containsKey(cls);
  }


  static {
    //字符串转,已实现
    CNV.put(String.class,
        stringConverter
    );

    //对象转,先toString,再处理
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
          public Short convert(Object o) {
            return ((BigDecimal) o).shortValue();
          }
        }
    );


    //bigInteger,窄化处理
    CNV.put(BigInteger.class,
        new Converter() {
          public Short convert(Object o) {
            return ((BigInteger) o).shortValue();
          }
        }
    );


    //short,原样输出
    CNV.put(Short.class,
        new Converter() {
          public Object convert(Object o) {
            return o;
          }
        }
    );

    //integer,窄化处理
    CNV.put(Integer.class,
        new Converter() {
          public Short convert(Object o) {
            //限制表示值的范围,必须在short范围之内
            if (((Integer) o) > Short.MAX_VALUE) {
              throw new ConversionException("cannot coerce Integer to Short since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Integer) o).shortValue();
            }
          }
        }
    );

    //float,窄化处理
    CNV.put(Float.class,
        new Converter() {
          public Short convert(Object o) {
            //限制表示值的范围,必须在short范围之内
            if (((Float) o) > Short.MAX_VALUE) {
              throw new ConversionException("cannot coerce Float to Short since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Float) o).shortValue();
            }
          }
        }
    );

    //double转,窄化处理
    CNV.put(Double.class,
        new Converter() {
          public Short convert(Object o) {
            //限制表示值的范围,必须在short范围之内
            if (((Double) o) > Short.MAX_VALUE) {
              throw new ConversionException("cannot coerce Double to Short since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Double) o).shortValue();
            }
          }
        }
    );

    //long转换,窄化处理
    CNV.put(Long.class,
        new Converter() {
          public Short convert(Object o) {
            //限制表示值的范围,必须在short范围之内
            if (((Long) o) > Short.MAX_VALUE) {
              throw new ConversionException("cannot coerce Integer to Short since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Long) o).shortValue();
            }
          }
        }
    );

    //boolean处理,true为1,false为0
    CNV.put(Boolean.class,
        new Converter() {
          public Short convert(Object o) {

            if ((Boolean) o)
              return TRUE;
            else {
              return FALSE;
            }

          }
        }
    );

  }
}
