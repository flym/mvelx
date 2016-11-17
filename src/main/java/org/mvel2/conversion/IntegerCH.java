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

import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;

/** 各种类型转数字 */
public class IntegerCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>(10);


  public Object convertFrom(Object in) {
    if (!CNV.containsKey(in.getClass())) throw new ConversionException("cannot convert type: "
        + in.getClass().getName() + " to: " + Integer.class.getName());

    return CNV.get(in.getClass()).convert(in);
  }


  public boolean canConvertFrom(Class cls) {
    return CNV.containsKey(cls);
  }

  static {
    //对象转,此处有bug
    CNV.put(Object.class,
        new Converter() {
          public Object convert(Object o) {
            //这里尝试强制转换,即会出现classCast异常,因此实际上只有字符串才会成功
            //而字符串在下面本身即提供
            if (((String) o).length() == 0) return 0;

            return parseInt(valueOf(o));
          }
        }
    );

    //bigDecimal转int,窄化处理
    CNV.put(BigDecimal.class,
        new Converter() {
          public Integer convert(Object o) {
            return ((BigDecimal) o).intValue();
          }
        }
    );


    //bigInteger转int,窄化处理
    CNV.put(BigInteger.class,
        new Converter() {
          public Integer convert(Object o) {
            return ((BigInteger) o).intValue();
          }
        }
    );

    //字符串转,使用parseInt方式
    CNV.put(String.class,
        new Converter() {
          public Object convert(Object o) {
            return parseInt(((String) o));
          }
        }
    );

    //short转换,宽化处理
    CNV.put(Short.class,
        new Converter() {
          public Object convert(Object o) {
            return ((Short) o).intValue();
          }
        }
    );

    //long转换,窄化处理
    CNV.put(Long.class,
        new Converter() {
          public Object convert(Object o) {
            //这里不支持>integer最大值的处理
            //noinspection UnnecessaryBoxing
            if (((Long) o) > Integer.MAX_VALUE) {
              throw new ConversionException("cannot coerce Long to Integer since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Long) o).intValue();
            }
          }
        }
    );


    //float转,窄化处理
    CNV.put(Float.class,
        new Converter() {
          public Object convert(Object o) {
            //noinspection UnnecessaryBoxing
            if (((Float) o) > Integer.MAX_VALUE) {
              throw new ConversionException("cannot coerce Float to Integer since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Float) o).intValue();
            }
          }
        }
    );

    //double处理,窄化处理
    CNV.put(Double.class,
        new Converter() {
          public Object convert(Object o) {
            //noinspection UnnecessaryBoxing
            if (((Double) o) > Integer.MAX_VALUE) {
              throw new ConversionException("cannot coerce Long to Integer since the value ("
                  + valueOf(o) + ") exceeds that maximum precision of Integer.");
            }
            else {
              return ((Double) o).intValue();
            }
          }
        }
    );


    //integer处理,原样返回
    CNV.put(Integer.class,
        new Converter() {
          public Object convert(Object o) {
            return o;
          }
        }
    );

    //boolean, true为1,false为0
    CNV.put(Boolean.class,
        new Converter() {
          public Integer convert(Object o) {
            if ((Boolean) o) return 1;
            else return 0;
          }
        }
    );

    //char转,原样返回
    CNV.put(Character.class,
        new Converter() {
          public Integer convert(Object o) {
            return (int) ((Character) o).charValue();
          }
        }
    );
  }
}
