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

/** 字符串转换为字符数组 */
public class CharArrayCH implements ConversionHandler {
  private static final Map<Class, Converter> CNV =
      new HashMap<Class, Converter>();


  public Object convertFrom(Object in) {
    if (!CNV.containsKey(in.getClass())) throw new ConversionException("cannot convert type: "
        + in.getClass().getName() + " to: " + Boolean.class.getName());
    return CNV.get(in.getClass()).convert(in);
  }


  public boolean canConvertFrom(Class cls) {
    return CNV.containsKey(cls);
  }

  /* 仅提供了字符串和字符数组之间的转换 */
  static {
    //字符串转字符数组
    CNV.put(String.class,
        new Converter() {
          public Object convert(Object o) {
            return ((String) o).toCharArray();
          }
        }
    );

  }
}
