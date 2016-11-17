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
package org.mvel2.util;

/** 数组工具类,用于一些特定的工具处理 */
public class ArrayTools {

  /** 在指定字符数组中,指定范围的情况下,找到指定字符的第1次出现位置 */
  public static int findFirst(char c, int start, int offset, char[] array) {
    int end = start + offset;
    for (int i = start; i < end; i++) {
      if (array[i] == c) return i;
    }
    return -1;
  }

  /** 在指定字符数组中,指定范围的情况下,找到指定字符的第1次出现位置,查找过程为倒序查找 */
  public static int findLast(char c, int start, int offset, char[] array) {
    for (int i = start + offset - 1; i >= 0; i--) {
      if (array[i] == c) return i;
    }
    return -1;
  }
}
