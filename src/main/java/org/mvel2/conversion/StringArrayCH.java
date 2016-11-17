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

import java.util.HashMap;
import java.util.Map;

/** 对象数组转字符串数组，即将每个对象string化 */
public class StringArrayCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();


  public Object convertFrom(Object in) {

    if (in.getClass().isArray()) {

      Object[] old = (Object[]) in;
      String[] n = new String[old.length];
      for (int i = 0; i < old.length; i++) {
        n[i] = String.valueOf(old[i]);
      }

      return n;
    }
    else {
      return new String[]{String.valueOf(in)};
    }

    //    return CNV.get(in.getClass()).convert(in);
  }


  public boolean canConvertFrom(Class cls) {
    return CNV.containsKey(cls);
  }

  static {
    //支持非基本类型数组转换为字符串
    CNV.put(Object[].class,
        new Converter() {
          public Object convert(Object o) {
            Object[] old = (Object[]) o;
            String[] n = new String[old.length];
            for (int i = 0; i < old.length; i++) {
              n[i] = String.valueOf(old[i]);
            }

            return n;
          }
        }
    );

  }
}
