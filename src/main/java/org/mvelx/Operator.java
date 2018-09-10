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
package org.mvelx;

/**
 * 描述各种操作符及相应的优先级
 * Contains a list of constants representing internal operators.
 */
public interface Operator {

    /** 表示不作任何操作 */
    int NOOP = -1;

    /**
     * 存储各个操作的优先级，值越大，表示这个操作优先级更高,以用于在计算时判断相应的优先级操作，实现优先处理
     * 小于21表示2项操作
     * The index positions of the operator precedence values
     * correspond to the actual operator itself. So ADD is PTABLE[0],
     * SUB is PTABLE[1] and so on.
     */
    int[] PTABLE = {
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
    int ADD = 0;
    /** - */
    int SUB = 1;
    /** * */
    int MULT = 2;
    /** / */
    int DIV = 3;
    /** % 取模运算 */
    int MOD = 4;
    /** 乘方 */
    int POWER = 5;

    /** & 二进制操作 */
    int BW_AND = 6;
    /** | 二进制操作 */
    int BW_OR = 7;
    /** ^ 二进制操作 */
    int BW_XOR = 8;
    /** >> */
    int BW_SHIFT_RIGHT = 9;
    /** << */
    int BW_SHIFT_LEFT = 10;
    /** >>> */
    int BW_USHIFT_RIGHT = 11;
    /** <<< */
    int BW_USHIFT_LEFT = 12;
    /** ! 二进制操作 */
    int BW_NOT = 13;

    /** < */
    int LTHAN = 14;
    /** > */
    int GTHAN = 15;
    /** <= */
    int LETHAN = 16;
    /** >= */
    int GETHAN = 17;

    /** == */
    int EQUAL = 18;
    /** != */
    int NEQUAL = 19;

    /** # 字符串拼接操作 */
    int STR_APPEND = 20;
    /** && */
    int AND = 21;
    /** || */
    int OR = 22;
    /** 交换操作数操作符 or */
    int CHOR = 23;
    /** ~= */
    int REGEX = 24;
    /** instanceof */
    int INSTANCEOF = 25;
    /** contains 包含 */
    int CONTAINS = 26;

    /** ? */
    int TERNARY = 29;
    /** ?号后面的 :操作符 */
    int TERNARY_ELSE = 30;
    /** = */
    int ASSIGN = 31;
    /** a++ */
    int INC_ASSIGN = 32;
    /** a-- */
    int DEC_ASSIGN = 33;
    /** new对象 */
    int NEW = 34;
    /** in 操作,即映射 */
    int PROJECTION = 35;
    /** convertable_to 表示是否能转换到目标类型 */
    int CONVERTABLE_TO = 36;
    /** 表示一个类似于 ;的操作，表示当前节点执行结束 */
    int END_OF_STMT = 37;

    /** foreach */
    int FOREACH = 38;
    /** if */
    int IF = 39;
    /** else */
    int ELSE = 40;
    /** while */
    int WHILE = 41;
    /** until */
    int UNTIL = 42;
    /** for */
    int FOR = 43;
    /** switch */
    int SWITCH = 44;
    /** do */
    int DO = 45;
    int WITH = 46;
    /** isdef 是否有定义此变量 */
    int ISDEF = 47;

    /** ++ a */
    int INC = 50;
    /** -- a */
    int DEC = 51;
    /** += */
    int ASSIGN_ADD = 52;
    /** -= */
    int ASSIGN_SUB = 53;
    /** += 字符串拼接 */
    int ASSIGN_STR_APPEND = 54;
    /** /= */
    int ASSIGN_DIV = 55;
    /** %= */
    int ASSIGN_MOD = 56;

    int ASSIGN_OR = 57;
    int ASSIGN_AND = 58;
    int ASSIGN_XOR = 59;
    int ASSIGN_LSHIFT = 60;
    int ASSIGN_RSHIFT = 61;
    int ASSIGN_RUSHIFT = 62;

    /** import_static 静态引入 */
    int IMPORT_STATIC = 95;
    /** import 直接引入 */
    int IMPORT = 96;
    /** assert */
    int ASSERT = 97;
    /** var 声明 */
    int UNTYPED_VAR = 98;
    /** return 语句 */
    int RETURN = 99;

    /** function 声明 */
    int FUNCTION = 100;

}
