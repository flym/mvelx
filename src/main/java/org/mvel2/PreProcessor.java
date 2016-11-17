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

package org.mvel2;

/**
 * 用于在编译脚本时预先对脚本进行处理，如处理宏(内容替换)等
 * A preprocessor used for pre-processing any expressions before being parsed/compiled.
 */
public interface PreProcessor {
  /** 处理字符数组，进行翻译 */
  public char[] parse(char[] input);

  /** 翻译字符串 */
  public String parse(String input);
}
