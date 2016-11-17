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
import org.mvel2.compiler.BlankLiteral;

import static java.lang.String.valueOf;

/** 各种转字符串 */
public class StringCH implements ConversionHandler {
  public Object convertFrom(Object in) {
    return valueOf(in);
  }


  /** 只要不是内部空常量,都可以进行转换 */
  public boolean canConvertFrom(Class cls) {
    return cls != BlankLiteral.class;
  }
}
