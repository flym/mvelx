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

/**
 * 将各种数据转换为BigInteger类型
 * 当前支持 object,BigDecimal,BigInteger,
 * String,Double,Float,Short,Long,Integer,char[]类型
 * 其基本的转换类型，即是通过原生的构建函数直接进行转换，否则就是将其string化，通过原生的valueOf进行转换
 */
public class BigIntegerCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();


  public Object convertFrom(Object in) {
    if (!CNV.containsKey(in.getClass())) throw new ConversionException("cannot convert type: "
        + in.getClass().getName() + " to: " + Integer.class.getName());
    return CNV.get(in.getClass()).convert(in);
  }


  public boolean canConvertFrom(Class cls) {
    return CNV.containsKey(cls);
  }

  static {
    //对象转,即转换为string再转
    CNV.put(Object.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger(String.valueOf(o));
          }
        }
    );

    //自身转换
    CNV.put(BigInteger.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return (BigInteger) o;
          }
        }
    );


    //bigDecimal,去除小数部分转回来
    CNV.put(BigDecimal.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return ((BigDecimal) o).toBigInteger();
          }
        }
    );

    //字符串车bigInteger
    CNV.put(String.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger((String) o);
          }
        }
    );


    //short转bigInteger
    CNV.put(Short.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger(String.valueOf(o));
          }
        }
    );

    //long转bigInteger
    CNV.put(Long.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger(String.valueOf(o));
          }
        }
    );

    //integer转bigInteger
    CNV.put(Integer.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger(String.valueOf(o));
          }
        }
    );

    //字符串转,采用字符串相对应的构建函数
    CNV.put(String.class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger((String) o);
          }
        }
    );

    //字符数组,即认为与字符串一样
    CNV.put(char[].class,
        new Converter() {
          public BigInteger convert(Object o) {
            return new BigInteger(new String((char[]) o));
          }
        }

    );
  }
}
