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
package org.mvel2;

/**
 * 描述各种操作符及相应的优先级
 * Contains a list of constants representing internal operators.
 */
public interface Operator {

  /** 表示不作任何操作 */
  public static final int NOOP = -1;

  /**
   * 存储各个操作的优先级，值越大，表示这个操作优先级更高,以用于在计算时判断相应的优先级操作，实现优先处理
   * 小于21表示2项操作
   * The index positions of the operator precedence values
   * correspond to the actual operator itself. So ADD is PTABLE[0],
   * SUB is PTABLE[1] and so on.
   */
  public static final int[] PTABLE = {
      10,   // ADD
      10,   // SUB
      11,   // MULT
      11,   // DIV
      11,   // MOD
      12,   // POWER

      6,   // BW_AND
      4,   // BW_OR
      5,   // BW_XOR
      9,   // BW_SHIFT_RIGHT
      9,   // BW_SHIFT_LEFT
      9,   // BW_USHIFT_RIGHT
      9,   // BW_USHIFT_LEFT
      5,   // BW_NOT

      8,   // LTHAN
      8,   // GTHAN
      8,   // LETHAN
      8,   // GETHAN

      7,   // EQUAL
      7,   // NEQUAL

      13,    // STR_APPEND
      3,   // AND
      2,   // OR
      2,   // CHOR
      13,   // REGEX
      8,   // INSTANCEOF
      13,   // CONTAINS
      13,   // SOUNDEX
      13,   // SIMILARITY

      0,  // TERNARY
      0,  // TERNARY ELSE
      13,   // ASSIGN
      13,   // INC_ASSIGN
      13   // DEC ASSIGN

  };

  //---------------------------- 小于20的操作符表示是数学操作 ------------------------------//

  /** + */
  public static final int ADD = 0;
  /** - */
  public static final int SUB = 1;
  /** * */
  public static final int MULT = 2;
  /** / */
  public static final int DIV = 3;
  /** % 取模运算 */
  public static final int MOD = 4;
  /** 乘方 */
  public static final int POWER = 5;

  /** & 二进制操作 */
  public static final int BW_AND = 6;
  /** | 二进制操作 */
  public static final int BW_OR = 7;
  /** ^ 二进制操作 */
  public static final int BW_XOR = 8;
  /** >> */
  public static final int BW_SHIFT_RIGHT = 9;
  /** << */
  public static final int BW_SHIFT_LEFT = 10;
  /** >>> */
  public static final int BW_USHIFT_RIGHT = 11;
  /** <<< */
  public static final int BW_USHIFT_LEFT = 12;
  /** ! 二进制操作 */
  public static final int BW_NOT = 13;

  /** < */
  public static final int LTHAN = 14;
  /** > */
  public static final int GTHAN = 15;
  /** <= */
  public static final int LETHAN = 16;
  /** >= */
  public static final int GETHAN = 17;

  /** == */
  public static final int EQUAL = 18;
  /** != */
  public static final int NEQUAL = 19;

  /** # 字符串拼接操作 */
  public static final int STR_APPEND = 20;
  /** && */
  public static final int AND = 21;
  /** || */
  public static final int OR = 22;
  /** 交换操作数操作符 or */
  public static final int CHOR = 23;
  /** ~= */
  public static final int REGEX = 24;
  /** instanceof */
  public static final int INSTANCEOF = 25;
  /** contains 包含 */
  public static final int CONTAINS = 26;
  /** soundslike */
  public static final int SOUNDEX = 27;
  /** strsim */
  public static final int SIMILARITY = 28;

  /** ? */
  public static final int TERNARY = 29;
  /** ?号后面的 :操作符 */
  public static final int TERNARY_ELSE = 30;
  /** = */
  public static final int ASSIGN = 31;
  /** a++ */
  public static final int INC_ASSIGN = 32;
  /** a-- */
  public static final int DEC_ASSIGN = 33;
  /** new对象 */
  public static final int NEW = 34;
  /** in 操作,即映射 */
  public static final int PROJECTION = 35;
  /** convertable_to 表示是否能转换到目标类型 */
  public static final int CONVERTABLE_TO = 36;
  /** 表示一个类似于 ;的操作，表示当前节点执行结束 */
  public static final int END_OF_STMT = 37;

  /** foreach */
  public static final int FOREACH = 38;
  /** if */
  public static final int IF = 39;
  /** else */
  public static final int ELSE = 40;
  /** while */
  public static final int WHILE = 41;
  /** until */
  public static final int UNTIL = 42;
  /** for */
  public static final int FOR = 43;
  /** switch */
  public static final int SWITCH = 44;
  /** do */
  public static final int DO = 45;
  public static final int WITH = 46;
  /** isdef 是否有定义此变量 */
  public static final int ISDEF = 47;

  /** 原型指令声明 */
  public static final int PROTO = 48;

  /** ++ a */
  public static final int INC = 50;
  /** -- a */
  public static final int DEC = 51;
  /** += */
  public static final int ASSIGN_ADD = 52;
  /** -= */
  public static final int ASSIGN_SUB = 53;
  /** += 字符串拼接 */
  public static final int ASSIGN_STR_APPEND = 54;
  /** /= */
  public static final int ASSIGN_DIV = 55;
  /** %= */
  public static final int ASSIGN_MOD = 56;

  public static final int ASSIGN_OR = 57;
  public static final int ASSIGN_AND = 58;
  public static final int ASSIGN_XOR = 59;
  public static final int ASSIGN_LSHIFT = 60;
  public static final int ASSIGN_RSHIFT = 61;
  public static final int ASSIGN_RUSHIFT = 62;

  /** import_static 静态引入 */
  public static final int IMPORT_STATIC = 95;
  /** import 直接引入 */
  public static final int IMPORT = 96;
  /** assert */
  public static final int ASSERT = 97;
  /** var 声明 */
  public static final int UNTYPED_VAR = 98;
  /** return 语句 */
  public static final int RETURN = 99;

  /** function 声明 */
  public static final int FUNCTION = 100;
  /** stacklang指令声明 */
  public static final int STACKLANG = 101;

  //---------------------------- stacklang指令集 start ------------------------------//

  public static final int PUSH = 102;
  public static final int POP = 103;
  public static final int LOAD = 104;
  public static final int LDTYPE = 105;
  public static final int INVOKE = 106;
  public static final int GETFIELD = 107;
  public static final int STOREFIELD = 108;
  public static final int STORE = 109;
  public static final int DUP = 110;
  public static final int LABEL = 111;
  public static final int JUMP = 112;
  public static final int JUMPIF = 113;
  public static final int REDUCE = 114;
  public static final int SWAP = 115;
  public static final int XSWAP = 116;

  //---------------------------- stacklang指令集 end ------------------------------//

}
