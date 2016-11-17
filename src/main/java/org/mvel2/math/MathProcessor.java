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
package org.mvel2.math;

import org.mvel2.DataTypes;
import org.mvel2.Unit;
import org.mvel2.compiler.BlankLiteral;
import org.mvel2.debug.DebugTools;
import org.mvel2.util.InternalNumber;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.valueOf;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.DataTypes.BIG_DECIMAL;
import static org.mvel2.DataTypes.EMPTY;
import static org.mvel2.Operator.*;
import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.Soundex.soundex;

/**
 * 数学处理，进行各项数学运算
 *
 * @author Christopher Brock
 */
public strictfp class MathProcessor {
  private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

  /** 2个对象进行计算,并获取相应的值 */
  public static Object doOperations(Object val1, int operation, Object val2) {
    return doOperations(val1 == null ? DataTypes.OBJECT : __resolveType(val1.getClass()),
        val1, operation,
        val2 == null ? DataTypes.NULL : __resolveType(val2.getClass()), val2);
  }

  /** 2个对象进行计算,并获取相应的值,同时对参数1进行相应的类型推断 */
  public static Object doOperations(Object val1, int operation, int type2, Object val2) {
    return doOperations(val1 == null ? DataTypes.OBJECT : __resolveType(val1.getClass()), val1, operation, type2, val2);
  }

  /**
   * 根据2个值,2个值的类型,操作符计算出相应的值
   *
   * @param type1     左边的值类型,可能未计算
   * @param type2     右边的值类型,可能未计算
   * @param operation 操作符
   */
  public static Object doOperations(int type1, Object val1, int operation, int type2, Object val2) {
    //-1 表示要重新计算处理
    if (type1 == -1)
      type1 = val1 == null ? DataTypes.OBJECT : __resolveType(val1.getClass());

    if (type2 == -1)
      type2 = val2 == null ? DataTypes.OBJECT : __resolveType(val2.getClass());

    switch (type1) {
      //单独处理bigDecimal系列,因为右边的类型可能要进行转型处理
      case BIG_DECIMAL:

        switch (type2) {
          case BIG_DECIMAL:
            return doBigDecimalArithmetic((BigDecimal) val1, operation, (BigDecimal) val2, false, -1);
          default:
            //类型2是数字,则进行相应的数字运算
            if (type2 > 99) {
              return doBigDecimalArithmetic((BigDecimal) val1, operation, getInternalNumberFromType(val2, type2), false, -1);
            }
            else {
              return _doOperations(type1, val1, operation, type2, val2);
            }
        }
      default:
        return _doOperations(type1, val1, operation, type2, val2);

    }
  }

  /** 进行窄化的数学运算，先使用double进行处理，最后转换为相应的类型 */
  private static Object doPrimWrapperArithmetic(final Number val1, final int operation, final Number val2, boolean iNumber, int returnTarget) {
    switch (operation) {
      case ADD:
        return toType(val1.doubleValue() + val2.doubleValue(), returnTarget);
      case DIV:
        return toType(val1.doubleValue() / val2.doubleValue(), returnTarget);
      case SUB:
        return toType(val1.doubleValue() - val2.doubleValue(), returnTarget);
      case MULT:
        return toType(val1.doubleValue() * val2.doubleValue(), returnTarget);
      case POWER:
        return toType(Math.pow(val1.doubleValue(), val2.doubleValue()), returnTarget);
      case MOD:
        return toType(val1.doubleValue() % val2.doubleValue(), returnTarget);
      case GTHAN:
        return val1.doubleValue() > val2.doubleValue() ? Boolean.TRUE : Boolean.FALSE;
      case GETHAN:
        return val1.doubleValue() >= val2.doubleValue() ? Boolean.TRUE : Boolean.FALSE;
      case LTHAN:
        return val1.doubleValue() < val2.doubleValue() ? Boolean.TRUE : Boolean.FALSE;
      case LETHAN:
        return val1.doubleValue() <= val2.doubleValue() ? Boolean.TRUE : Boolean.FALSE;
      case EQUAL:
        return val1.doubleValue() == val2.doubleValue() ? Boolean.TRUE : Boolean.FALSE;
      case NEQUAL:
        return val1.doubleValue() != val2.doubleValue() ? Boolean.TRUE : Boolean.FALSE;
    }
    return null;

  }

  /** 将相应的数字转换为相应的类型 */
  private static Object toType(Number val, int returnType) {
    switch (returnType) {
      //转double
      case DataTypes.W_DOUBLE:
      case DataTypes.DOUBLE:
        return val.doubleValue();
      //转float
      case DataTypes.W_FLOAT:
      case DataTypes.FLOAT:
        return val.floatValue();
      //转int
      case DataTypes.INTEGER:
      case DataTypes.W_INTEGER:
        return val.intValue();
      //转long
      case DataTypes.W_LONG:
      case DataTypes.LONG:
        return val.longValue();
      //转short
      case DataTypes.W_SHORT:
      case DataTypes.SHORT:
        return val.shortValue();
      //转bigDecimal
      case DataTypes.BIG_DECIMAL:
        return new BigDecimal(val.doubleValue());
      //转bigInteger
      case DataTypes.BIG_INTEGER:
        return BigInteger.valueOf(val.longValue());

      //不明所以,这里声明转换为字符串,但实际转换为float?
      case DataTypes.STRING:
        return val.doubleValue();
    }
    throw new RuntimeException("internal error: " + returnType);
  }

  /**
   * 进行bigDecimal运算
   *
   * @param iNumber      最后是否返回正常数字
   * @param returnTarget 目的返回类型
   */
  private static Object doBigDecimalArithmetic(final BigDecimal val1, final int operation, final BigDecimal val2, boolean iNumber, int returnTarget) {
    switch (operation) {
      //加法
      case ADD:
        //最终返回数字,则尝试
        if (iNumber) {
          return narrowType(val1.add(val2, MATH_CONTEXT), returnTarget);
        }
        else {
          return val1.add(val2, MATH_CONTEXT);
        }
        //减法
      case DIV:
        if (iNumber) {
          return narrowType(val1.divide(val2, MATH_CONTEXT), returnTarget);
        }
        else {
          return val1.divide(val2, MATH_CONTEXT);
        }

        //除法
      case SUB:
        if (iNumber) {
          return narrowType(val1.subtract(val2, MATH_CONTEXT), returnTarget);
        }
        else {
          return val1.subtract(val2, MATH_CONTEXT);
        }
        //乘法
      case MULT:
        if (iNumber) {
          return narrowType(val1.multiply(val2, MATH_CONTEXT), returnTarget);
        }
        else {
          return val1.multiply(val2, MATH_CONTEXT);
        }

        //乘方
      case POWER:
        if (iNumber) {
          return narrowType(val1.pow(val2.intValue(), MATH_CONTEXT), returnTarget);
        }
        else {
          return val1.pow(val2.intValue(), MATH_CONTEXT);
        }

        //取模
      case MOD:
        if (iNumber) {
          return narrowType(val1.remainder(val2), returnTarget);
        }
        else {
          return val1.remainder(val2);
        }

        //大于
      case GTHAN:
        return val1.compareTo(val2) == 1 ? Boolean.TRUE : Boolean.FALSE;
      //大于等于
      case GETHAN:
        return val1.compareTo(val2) >= 0 ? Boolean.TRUE : Boolean.FALSE;
      //小于
      case LTHAN:
        return val1.compareTo(val2) == -1 ? Boolean.TRUE : Boolean.FALSE;
      //小于等于
      case LETHAN:
        return val1.compareTo(val2) <= 0 ? Boolean.TRUE : Boolean.FALSE;
      //等于
      case EQUAL:
        return val1.compareTo(val2) == 0 ? Boolean.TRUE : Boolean.FALSE;
      //不等于
      case NEQUAL:
        return val1.compareTo(val2) != 0 ? Boolean.TRUE : Boolean.FALSE;
    }

    //其它情况未考虑的,暂返回null值
    return null;
  }

  /** 进行普通的运算 */
  private static Object _doOperations(int type1, Object val1, int operation, int type2, Object val2) {
    if (operation < 20) {//操作符小于20,表示是数学操作
      //第一种情况，表示是同类型操作，包括==和 != 以及整数操作,但并不一定都是数字
      if (((type1 > 49 || operation == EQUAL || operation == NEQUAL) && type1 == type2) ||
          (isIntegerType(type1) && isIntegerType(type2) && operation >= BW_AND && operation <= BW_NOT)) {
        return doOperationsSameType(type1, val1, operation, val2);
      }
      //确实是数字操作
      else if (isNumericOperation(type1, val1, operation, type2, val2)) {
        return doPrimWrapperArithmetic(getNumber(val1, type1),
            operation,
            getNumber(val2, type2), true, box(type2) > box(type1) ? box(type2) : box(type1));
      }
      //非数学操作,并且2者有1个为boolean类型，则表示进行boolean的各项操作
      else if (operation != ADD &&
          (type1 == DataTypes.W_BOOLEAN || type2 == DataTypes.W_BOOLEAN) &&
          type1 != type2 && type1 != EMPTY && type2 != EMPTY) {

        return doOperationNonNumeric(type1, convert(val1, Boolean.class), operation, convert(val2, Boolean.class));
      }
      // Fix for: MVEL-56
      //字符串与字符操作,就进行联接处理
      else if ((type1 == 1 || type2 == 1) && (type1 == 8 || type1 == 112 || type2 == 8 || type2 == 112)) {
        //参数1为字符串,则把第2个转换为字符串
        if (type1 == 1) {
          return doOperationNonNumeric(type1, val1, operation, valueOf(val2));
        }
        else {
          return doOperationNonNumeric(type1, valueOf(val1), operation, val2);
        }
      }
    }
    return doOperationNonNumeric(type1, val1, operation, val2);
  }

  /** 判断两个对象是否是直接的数字运算 */
  private static boolean isNumericOperation(int type1, Object val1, int operation, int type2, Object val2) {
    //要么两个的类型都是数字
    //或者是作任意一个为数字,并且另外的可以转换为数字并且相应的操作不能为+,因为可能是字符串+数字,这时候为字符串拼接
    return (type1 > 99 && type2 > 99)
        || (operation != ADD && (type1 > 99 || type2 > 99 || operation < LTHAN || operation > GETHAN) && isNumber(val1) && isNumber(val2));
  }

  /** 是否是整数类型 */
  private static boolean isIntegerType(int type) {
    return type == DataTypes.INTEGER || type == DataTypes.W_INTEGER || type == DataTypes.LONG || type == DataTypes.W_LONG;
  }

  /** 非数字运算 */
  private static Object doOperationNonNumeric(int type1, final Object val1, final int operation, final Object val2) {
    switch (operation) {
      //集合
      case ADD:
        if (type1 == DataTypes.COLLECTION) {
          List list = new ArrayList((Collection) val1);
          list.add(val2);
          return list;
        }
        //因为之前已经作了数字运算,因此这里的+就只剩下字符串拼接了
        else {
          return valueOf(val1) + valueOf(val2);
        }

        //相等性判定
      case EQUAL:
        return safeEquals(val2, val1) ? Boolean.TRUE : Boolean.FALSE;

      //不相等判定
      case NEQUAL:
        return safeNotEquals(val2, val1) ? Boolean.TRUE : Boolean.FALSE;

      //数学运算如为比较 类型，则进行比较 操作，否则直接返回false
      case SUB:
      case DIV:
      case MULT:
      case MOD:
      case GTHAN:
        if (val1 instanceof Comparable) {
          try {
            return val2 != null && (((Comparable) val1).compareTo(val2) >= 1 ? Boolean.TRUE : Boolean.FALSE);
          }
          catch (ClassCastException e) {
            throw new RuntimeException("uncomparable values <<" + val1 + ">> and <<" + val2 + ">>", e);
          }
        }
        else {
          return Boolean.FALSE;
        }
        //     break;

        //>= 判定,使用comparable来判定
      case GETHAN:
        if (val1 instanceof Comparable) {
          //noinspection unchecked
          try {
            return val2 != null && ((Comparable) val1).compareTo(val2) >= 0 ? Boolean.TRUE : Boolean.FALSE;
          }
          catch (ClassCastException e) {
            throw new RuntimeException("uncomparable values <<" + val1 + ">> and <<" + val2 + ">>", e);
          }

        }
        else {
          return Boolean.FALSE;
        }


        // < 判定,使用comparable来判定
      case LTHAN:
        if (val1 instanceof Comparable) {
          //noinspection unchecked
          try {
            return val2 != null && ((Comparable) val1).compareTo(val2) <= -1 ? Boolean.TRUE : Boolean.FALSE;
          }
          catch (ClassCastException e) {
            throw new RuntimeException("uncomparable values <<" + val1 + ">> and <<" + val2 + ">>", e);
          }

        }
        else {
          return Boolean.FALSE;
        }


        //<= 判定
      case LETHAN:
        if (val1 instanceof Comparable) {
          //noinspection unchecked
          try {
            return val2 != null && ((Comparable) val1).compareTo(val2) <= 0 ? Boolean.TRUE : Boolean.FALSE;
          }
          catch (ClassCastException e) {
            throw new RuntimeException("uncomparable values <<" + val1 + ">> and <<" + val2 + ">>", e);
          }

        }
        else {
          return Boolean.FALSE;
        }


      case SOUNDEX:
        return soundex(String.valueOf(val1)).equals(soundex(String.valueOf(val2)));

      // #操作,直接字符串拼接
      case STR_APPEND:
        return valueOf(val1) + valueOf(val2);
    }

    throw new RuntimeException("could not perform numeric operation on non-numeric types: left-type="
        + (val1 != null ? val1.getClass().getName() : "null") + "; right-type="
        + (val2 != null ? val2.getClass().getName() : "null")
        + " [vals (" + valueOf(val1) + ", " + valueOf(val2) + ") operation=" + DebugTools.getOperatorName(operation) + " (opcode:" + operation + ") ]");
  }

  /** 安全地eq判定,即处理null值,避免null.equals 的操作 */
  private static Boolean safeEquals(final Object val1, final Object val2) {
    if (val1 != null) {
      return val1.equals(val2) ? Boolean.TRUE : Boolean.FALSE;
    }
    else return val2 == null || (val2.equals(val1) ? Boolean.TRUE : Boolean.FALSE);
  }

  /** 安全地notEq判定,即处理null值 */
  private static Boolean safeNotEquals(final Object val1, final Object val2) {
    if (val1 != null) {
      return !val1.equals(val2) ? Boolean.TRUE : Boolean.FALSE;
    }
    else return (val2 != null && !val2.equals(val1)) ? Boolean.TRUE : Boolean.FALSE;
  }

  /** 同类型操作 */
  private static Object doOperationsSameType(int type1, Object val1, int operation, Object val2) {
    switch (type1) {
      //集合操作，支持[] + []
      case DataTypes.COLLECTION:
        switch (operation) {
          case ADD:
            List list = new ArrayList((Collection) val1);
            list.addAll((Collection) val2);
            return list;

          //同类型判断,因此不需要进行类型推断
          case EQUAL:
            return val1.equals(val2);

          case NEQUAL:
            return !val1.equals(val2);

          default:
            throw new UnsupportedOperationException("illegal operation on Collection type");
        }

        //整数
      case DataTypes.INTEGER:
      case DataTypes.W_INTEGER:
        switch (operation) {
          case ADD:
            return ((Integer) val1) + ((Integer) val2);
          case SUB:
            return ((Integer) val1) - ((Integer) val2);
          case DIV:
            return ((Integer) val1).doubleValue() / ((Integer) val2).doubleValue();
          case MULT:
            return ((Integer) val1) * ((Integer) val2);
          case POWER:
            double d = Math.pow((Integer) val1, (Integer) val2);
            if (d > Integer.MAX_VALUE) return d;
            else return (int) d;
          case MOD:
            return ((Integer) val1) % ((Integer) val2);
          case GTHAN:
            return ((Integer) val1) > ((Integer) val2) ? Boolean.TRUE : Boolean.FALSE;
          case GETHAN:
            return ((Integer) val1) >= ((Integer) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LTHAN:
            return ((Integer) val1) < ((Integer) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LETHAN:
            return ((Integer) val1) <= ((Integer) val2) ? Boolean.TRUE : Boolean.FALSE;
          case EQUAL:
            return ((Integer) val1).intValue() == ((Integer) val2).intValue() ? Boolean.TRUE : Boolean.FALSE;
          case NEQUAL:
            return ((Integer) val1).intValue() != ((Integer) val2).intValue() ? Boolean.TRUE : Boolean.FALSE;
          case BW_AND:
            if (val2 instanceof Long) return (Integer) val1 & (Long) val2;
            return (Integer) val1 & (Integer) val2;
          case BW_OR:
            if (val2 instanceof Long) return (Integer) val1 | (Long) val2;
            return (Integer) val1 | (Integer) val2;
          case BW_SHIFT_LEFT:
            if (val2 instanceof Long) return (Integer) val1 << (Long) val2;
            return (Integer) val1 << (Integer) val2;
          case BW_SHIFT_RIGHT:
            if (val2 instanceof Long) return (Integer) val1 >> (Long) val2;
            return (Integer) val1 >> (Integer) val2;
          case BW_USHIFT_RIGHT:
            if (val2 instanceof Long) return (Integer) val1 >>> (Long) val2;
            return (Integer) val1 >>> (Integer) val2;
          case BW_XOR:
            if (val2 instanceof Long) return (Integer) val1 ^ (Long) val2;
            return (Integer) val1 ^ (Integer) val2;
        }

        //short类型
      case DataTypes.SHORT:
      case DataTypes.W_SHORT:
        switch (operation) {
          case ADD:
            return ((Short) val1) + ((Short) val2);
          case SUB:
            return ((Short) val1) - ((Short) val2);
          case DIV:
            return ((Short) val1).doubleValue() / ((Short) val2).doubleValue();
          case MULT:
            return ((Short) val1) * ((Short) val2);
          case POWER:
            double d = Math.pow((Short) val1, (Short) val2);
            if (d > Short.MAX_VALUE) return d;
            else return (short) d;
          case MOD:
            return ((Short) val1) % ((Short) val2);
          case GTHAN:
            return ((Short) val1) > ((Short) val2) ? Boolean.TRUE : Boolean.FALSE;
          case GETHAN:
            return ((Short) val1) >= ((Short) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LTHAN:
            return ((Short) val1) < ((Short) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LETHAN:
            return ((Short) val1) <= ((Short) val2) ? Boolean.TRUE : Boolean.FALSE;
          case EQUAL:
            return ((Short) val1).shortValue() == ((Short) val2).shortValue() ? Boolean.TRUE : Boolean.FALSE;
          case NEQUAL:
            return ((Short) val1).shortValue() != ((Short) val2).shortValue() ? Boolean.TRUE : Boolean.FALSE;
          case BW_AND:
            return (Short) val1 & (Short) val2;
          case BW_OR:
            return (Short) val1 | (Short) val2;
          case BW_SHIFT_LEFT:
            return (Short) val1 << (Short) val2;
          case BW_SHIFT_RIGHT:
            return (Short) val1 >> (Short) val2;
          case BW_USHIFT_RIGHT:
            return (Short) val1 >>> (Short) val2;
          case BW_XOR:
            return (Short) val1 ^ (Short) val2;
        }

        //long类型
      case DataTypes.LONG:
      case DataTypes.W_LONG:
        switch (operation) {
          case ADD:
            return ((Long) val1) + ((Long) val2);
          case SUB:
            return ((Long) val1) - ((Long) val2);
          case DIV:
            return ((Long) val1).doubleValue() / ((Long) val2).doubleValue();
          case MULT:
            return ((Long) val1) * ((Long) val2);
          case POWER:
            double d = Math.pow((Long) val1, (Long) val2);
            if (d > Long.MAX_VALUE) return d;
            else return (long) d;
          case MOD:
            return ((Long) val1) % ((Long) val2);
          case GTHAN:
            return ((Long) val1) > ((Long) val2) ? Boolean.TRUE : Boolean.FALSE;
          case GETHAN:
            return ((Long) val1) >= ((Long) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LTHAN:
            return ((Long) val1) < ((Long) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LETHAN:
            return ((Long) val1) <= ((Long) val2) ? Boolean.TRUE : Boolean.FALSE;
          case EQUAL:
            return ((Long) val1).longValue() == ((Long) val2).longValue() ? Boolean.TRUE : Boolean.FALSE;
          case NEQUAL:
            return ((Long) val1).longValue() != ((Long) val2).longValue() ? Boolean.TRUE : Boolean.FALSE;
          case BW_AND:
            if (val2 instanceof Integer) return (Long) val1 & (Integer) val2;
            return (Long) val1 & (Long) val2;
          case BW_OR:
            if (val2 instanceof Integer) return (Long) val1 | (Integer) val2;
            return (Long) val1 | (Long) val2;
          case BW_SHIFT_LEFT:
            if (val2 instanceof Integer) return (Long) val1 << (Integer) val2;
            return (Long) val1 << (Long) val2;
          case BW_USHIFT_LEFT:
            throw new UnsupportedOperationException("unsigned left-shift not supported");
          case BW_SHIFT_RIGHT:
            if (val2 instanceof Integer) return (Long) val1 >> (Integer) val2;
            return (Long) val1 >> (Long) val2;
          case BW_USHIFT_RIGHT:
            if (val2 instanceof Integer) return (Long) val1 >>> (Integer) val2;
            return (Long) val1 >>> (Long) val2;
          case BW_XOR:
            if (val2 instanceof Integer) return (Long) val1 ^ (Integer) val2;
            return (Long) val1 ^ (Long) val2;
        }

      case DataTypes.UNIT:
        val2 = ((Unit) val1).convertFrom(val2);
        val1 = ((Unit) val1).getValue();

        //double类型
      case DataTypes.DOUBLE:
      case DataTypes.W_DOUBLE:
        switch (operation) {
          case ADD:
            return ((Double) val1) + ((Double) val2);
          case SUB:
            return ((Double) val1) - ((Double) val2);
          case DIV:
            return ((Double) val1) / ((Double) val2);
          case MULT:
            return ((Double) val1) * ((Double) val2);
          case POWER:
            return Math.pow((Double) val1, (Double) val2);
          case MOD:
            return ((Double) val1) % ((Double) val2);
          case GTHAN:
            return ((Double) val1) > ((Double) val2) ? Boolean.TRUE : Boolean.FALSE;
          case GETHAN:
            return ((Double) val1) >= ((Double) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LTHAN:
            return ((Double) val1) < ((Double) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LETHAN:
            return ((Double) val1) <= ((Double) val2) ? Boolean.TRUE : Boolean.FALSE;
          case EQUAL:
            return ((Double) val1).doubleValue() == ((Double) val2).doubleValue() ? Boolean.TRUE : Boolean.FALSE;
          case NEQUAL:
            return ((Double) val1).doubleValue() != ((Double) val2).doubleValue() ? Boolean.TRUE : Boolean.FALSE;
          case BW_AND:
          case BW_OR:
          case BW_SHIFT_LEFT:
          case BW_SHIFT_RIGHT:
          case BW_USHIFT_RIGHT:
          case BW_XOR:
            throw new RuntimeException("bitwise operation on a non-fixed-point number.");
        }

        //float类型
      case DataTypes.FLOAT:
      case DataTypes.W_FLOAT:
        switch (operation) {
          case ADD:
            return ((Float) val1) + ((Float) val2);
          case SUB:
            return ((Float) val1) - ((Float) val2);
          case DIV:
            return ((Float) val1).doubleValue() / ((Float) val2).doubleValue();
          case MULT:
            return ((Float) val1) * ((Float) val2);
          case POWER:
            return narrowType(new InternalNumber((Float) val1, MATH_CONTEXT).pow(new InternalNumber((Float) val2).intValue(), MATH_CONTEXT), -1);
          case MOD:
            return ((Float) val1) % ((Float) val2);
          case GTHAN:
            return ((Float) val1) > ((Float) val2) ? Boolean.TRUE : Boolean.FALSE;
          case GETHAN:
            return ((Float) val1) >= ((Float) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LTHAN:
            return ((Float) val1) < ((Float) val2) ? Boolean.TRUE : Boolean.FALSE;
          case LETHAN:
            return ((Float) val1) <= ((Float) val2) ? Boolean.TRUE : Boolean.FALSE;
          case EQUAL:
            return ((Float) val1).floatValue() == ((Float) val2).floatValue() ? Boolean.TRUE : Boolean.FALSE;
          case NEQUAL:
            return ((Float) val1).floatValue() != ((Float) val2).floatValue() ? Boolean.TRUE : Boolean.FALSE;
          case BW_AND:
          case BW_OR:
          case BW_SHIFT_LEFT:
          case BW_SHIFT_RIGHT:
          case BW_USHIFT_RIGHT:
          case BW_XOR:
            throw new RuntimeException("bitwise operation on a non-fixed-point number.");
        }

        //biginteger类型
      case DataTypes.BIG_INTEGER:
        switch (operation) {
          case ADD:
            return ((BigInteger) val1).add(((BigInteger) val2));
          case SUB:
            return ((BigInteger) val1).subtract(((BigInteger) val2));
          case DIV:
            return ((BigInteger) val1).divide(((BigInteger) val2));
          case MULT:
            return ((BigInteger) val1).multiply(((BigInteger) val2));
          case POWER:
            return ((BigInteger) val1).pow(((BigInteger) val2).intValue());
          case MOD:
            return ((BigInteger) val1).remainder(((BigInteger) val2));
          case GTHAN:
            return ((BigInteger) val1).compareTo(((BigInteger) val2)) == 1 ? Boolean.TRUE : Boolean.FALSE;
          case GETHAN:
            return ((BigInteger) val1).compareTo(((BigInteger) val2)) >= 0 ? Boolean.TRUE : Boolean.FALSE;
          case LTHAN:
            return ((BigInteger) val1).compareTo(((BigInteger) val2)) == -1 ? Boolean.TRUE : Boolean.FALSE;
          case LETHAN:
            return ((BigInteger) val1).compareTo(((BigInteger) val2)) <= 0 ? Boolean.TRUE : Boolean.FALSE;
          case EQUAL:
            return ((BigInteger) val1).compareTo(((BigInteger) val2)) == 0 ? Boolean.TRUE : Boolean.FALSE;
          case NEQUAL:
            return ((BigInteger) val1).compareTo(((BigInteger) val2)) != 0 ? Boolean.TRUE : Boolean.FALSE;
          case BW_AND:
          case BW_OR:
          case BW_SHIFT_LEFT:
          case BW_SHIFT_RIGHT:
          case BW_USHIFT_RIGHT:
          case BW_XOR:
            throw new RuntimeException("bitwise operation on a number greater than 32-bits not possible");
        }


        //其它情况下，实现==和!=以及add操作,+号默认实现为字符串处理
      default:
        switch (operation) {
          case EQUAL:
            return safeEquals(val2, val1);
          case NEQUAL:
            return safeNotEquals(val2, val1);
          case ADD:
            return valueOf(val1) + valueOf(val2);
        }
    }
    return null;
  }

  /** 装箱,以将装箱后的类型进行宽化比较 */
  private static int box(int type) {
    switch (type) {
      case DataTypes.INTEGER:
        return DataTypes.W_INTEGER;
      case DataTypes.DOUBLE:
        return DataTypes.W_DOUBLE;
      case DataTypes.LONG:
        return DataTypes.W_LONG;
      case DataTypes.SHORT:
        return DataTypes.W_SHORT;
      case DataTypes.BYTE:
        return DataTypes.W_BYTE;
      case DataTypes.FLOAT:
        return DataTypes.W_FLOAT;
      case DataTypes.CHAR:
        return DataTypes.W_CHAR;
      case DataTypes.BOOLEAN:
        return DataTypes.W_BOOLEAN;
    }
    return type;
  }

  /**
   * 获取相应的数字形式,用于数学运算,转换为double来处理
   *
   * @param type 相应参数的实际类型
   */
  private static Double getNumber(Object in, int type) {
    if (in == null || in == BlankLiteral.INSTANCE)
      return 0d;
    switch (type) {
      case BIG_DECIMAL:
        return ((Number) in).doubleValue();
      case DataTypes.BIG_INTEGER:
        return ((Number) in).doubleValue();
      case DataTypes.INTEGER:
      case DataTypes.W_INTEGER:
        return ((Number) in).doubleValue();
      case DataTypes.LONG:
      case DataTypes.W_LONG:
        return ((Number) in).doubleValue();
      case DataTypes.STRING:
        return Double.parseDouble((String) in);
      case DataTypes.FLOAT:
      case DataTypes.W_FLOAT:
        return ((Number) in).doubleValue();
      case DataTypes.DOUBLE:
      case DataTypes.W_DOUBLE:
        return (Double) in;
      case DataTypes.SHORT:
      case DataTypes.W_SHORT:
        return ((Number) in).doubleValue();
      case DataTypes.CHAR:
      case DataTypes.W_CHAR:
        return Double.parseDouble(String.valueOf((Character) in));
      case DataTypes.BOOLEAN:
      case DataTypes.W_BOOLEAN:
        return ((Boolean) in) ? 1d : 0d;
      case DataTypes.W_BYTE:
      case DataTypes.BYTE:
        return ((Byte) in).doubleValue();
    }

    throw new RuntimeException("cannot convert <" + in + "> to a numeric type: " + in.getClass() + " [" + type + "]");


  }


  /** 将正常数字转换为一个内部表示的bigDecimal数字 */
  private static InternalNumber getInternalNumberFromType(Object in, int type) {
    if (in == null || in == BlankLiteral.INSTANCE)
      return new InternalNumber(0, MATH_CONTEXT);
    switch (type) {
      case BIG_DECIMAL:
        return new InternalNumber(((BigDecimal) in).doubleValue());
      case DataTypes.BIG_INTEGER:
        return new InternalNumber((BigInteger) in, MathContext.DECIMAL128);
      case DataTypes.INTEGER:
      case DataTypes.W_INTEGER:
        return new InternalNumber((Integer) in, MathContext.DECIMAL32);
      case DataTypes.LONG:
      case DataTypes.W_LONG:
        return new InternalNumber((Long) in, MathContext.DECIMAL64);
      case DataTypes.STRING:
        return new InternalNumber((String) in, MathContext.DECIMAL64);
      case DataTypes.FLOAT:
      case DataTypes.W_FLOAT:
        return new InternalNumber((Float) in, MathContext.DECIMAL64);
      case DataTypes.DOUBLE:
      case DataTypes.W_DOUBLE:
        return new InternalNumber((Double) in, MathContext.DECIMAL64);
      case DataTypes.SHORT:
      case DataTypes.W_SHORT:
        return new InternalNumber((Short) in, MathContext.DECIMAL32);
      case DataTypes.CHAR:
      case DataTypes.W_CHAR:
        return new InternalNumber((Character) in, MathContext.DECIMAL32);
      case DataTypes.BOOLEAN:
      case DataTypes.W_BOOLEAN:
        return new InternalNumber(((Boolean) in) ? 1 : 0);
      case DataTypes.UNIT:
        return new InternalNumber(((Unit) in).getValue(), MathContext.DECIMAL64);
      case DataTypes.W_BYTE:
      case DataTypes.BYTE:
        return new InternalNumber(((Byte) in).intValue());


    }

    throw new RuntimeException("cannot convert <" + in + "> to a numeric type: " + in.getClass() + " [" + type + "]");
  }
}
