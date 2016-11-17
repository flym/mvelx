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
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;

/**
 * 将各种数据转换为BigDecimal类型
 * 当前支持 object,BigDecimal,BigInteger,
 * String,Double,Float,Short,Long,Integer,char[]类型
 * 其基本的转换类型，即是通过原生的构建函数直接进行转换，否则就是将其string化，通过原生的valueOf进行转换
 */
public class BigDecimalCH implements ConversionHandler {
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
    //通用类型，toString转换
    CNV.put(Object.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(String.valueOf(o), MathContext.DECIMAL128);
          }
        }
    );

    //自身转换
    CNV.put(BigDecimal.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return (BigDecimal) o;
          }
        }
    );


    //bigInteger转bigDecimal
    CNV.put(BigInteger.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(((BigInteger) o).doubleValue(), MathContext.DECIMAL128);
          }
        }
    );

    //字符串转bigDecimal
    CNV.put(String.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal((String) o, MathContext.DECIMAL128);
          }
        }
    );

    //double类型转BigDecimal
    CNV.put(Double.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(((Double) o).doubleValue(), MathContext.DECIMAL128);
          }
        }
    );

    //float类型转bigDecimal
    CNV.put(Float.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(((Float) o).doubleValue(), MathContext.DECIMAL128);
          }
        }
    );


    //short类型转bigDecimal
    CNV.put(Short.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(((Short) o).doubleValue(), MathContext.DECIMAL128);
          }
        }
    );

    //long类型转bigDecimal
    CNV.put(Long.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(((Long) o).doubleValue(), MathContext.DECIMAL128);
          }
        }
    );

    //integer转bigDecimal
    CNV.put(Integer.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal(((Integer) o).doubleValue(), MathContext.DECIMAL128);
          }
        }
    );

    //前面已经作了一次,此处重复
    CNV.put(String.class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal((String) o, MathContext.DECIMAL128);
          }
        }
    );

    //字符数组也可以认为就是通过字符串来处理
    CNV.put(char[].class,
        new Converter() {
          public BigDecimal convert(Object o) {
            return new BigDecimal((char[]) o, MathContext.DECIMAL128);
          }
        }

    );
  }
}
