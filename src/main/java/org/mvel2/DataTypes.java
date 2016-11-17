/**
 * MVEL 2.0
 * Copyright (C) 2007  MVFLEX/Valhalla Project and the Codehaus
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


package org.mvel2;


/**
 * 数据类型内部表示法，用于表示相应的类型，在进行类型 操作时会根据类型的大小，进行宽化或窄化，如 int + long 就会返回long
 * 100以上表示数字类型
 * Contains constants for standard internal types.
 */
public interface DataTypes {
  public static final int NULL = -1;

  public static final int OBJECT = 0;
  public static final int STRING = 1;
  public static final int SHORT = 100;
  public static final int INTEGER = 101;
  public static final int LONG = 102;
  public static final int DOUBLE = 103;
  public static final int FLOAT = 104;
  public static final int BOOLEAN = 7;
  public static final int CHAR = 8;
  public static final int BYTE = 9;

  /** boolean的包装类型 */
  public static final int W_BOOLEAN = 15;

  public static final int COLLECTION = 50;

  /** Short */
  public static final int W_SHORT = 105;
  /** Integer包装 */
  public static final int W_INTEGER = 106;

  /** long 包装 */
  public static final int W_LONG = 107;
  /** float 包装 */
  public static final int W_FLOAT = 108;
  /** double 包装 */
  public static final int W_DOUBLE = 109;

  /** char 包装 */
  public static final int W_CHAR = 112;
  /** byte 包装 */
  public static final int W_BYTE = 113;

  public static final int BIG_DECIMAL = 110;
  public static final int BIG_INTEGER = 111;

  /** 表示是一个空常量信息 */
  public static final int EMPTY = 200;

  /** 表示一个处理单元 */
  public static final int UNIT = 300;
}
