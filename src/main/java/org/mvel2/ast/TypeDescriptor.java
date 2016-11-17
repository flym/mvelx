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

package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.ParserContext;
import org.mvel2.compiler.AbstractParser;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.ParseTools;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;

import static java.lang.Character.isDigit;
import static org.mvel2.ast.ASTNode.COMPILE_IMMEDIATE;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.ReflectionUtil.toPrimitiveArrayType;

/**
 * 类型描述，即描述一个有效的类型定义信息,如Date Date[2]这样的
 * 对于非数组，这里仅描述类名，对于数组，则要求描述相应的数组长度信息
 */
public class TypeDescriptor implements Serializable {
  /** 存储的类名,这里的类名可能是一个类名的引用(即通过var进行变量引用),或者是函数名 */
  private String className;
  /** 当前处理字符串引用 */
  private char[] expr;

  /** 起始点 */
  private int start;
  /** 跨度(即start+size中的size值) */
  private int offset;

  /** 用于描述数组长度的信息值 */
  private ArraySize[] arraySize;
  /** 用于描述长度信息中可能存在的计算表达式 */
  private ExecutableStatement[] compiledArraySize;
  /** 当前类型信息中类型的结束位置(有效位置，即构建函数的字符串，或者是数组的]位置 */
  int endRange;

  public TypeDescriptor(char[] name, int start, int offset, int fields) {
    updateClassName(this.expr = name, this.start = start, this.offset = offset, fields);
  }

  /** 重新处理相应的类型信息 */
  public void updateClassName(char[] name, int start, int offset, int fields) {
    this.expr = name;

    //不是一个有效的类型定义
    if (offset == 0 || !ParseTools.isIdentifierPart(name[start]) || isDigit(name[start])) return;

    //在new后面只有两种情况，一种为（,即声明声明，另一种为[声明数组，这里进行了判定
    if ((endRange = findFirst('(', start, offset, name)) == -1) {

      //存在[，即表示是数组信息
      if ((endRange = findFirst('[', start, offset, name)) != -1) {
        //[前面的类型信息
        className = new String(name, start, endRange - start).trim();
        int to;

        LinkedList<char[]> sizes = new LinkedList<char[]>();

        int end = start + offset;
        while (endRange < end) {
          //在后续的处理当中,如果出现[xx]  [yy]这种,跳过数组之间的空格
          //在第一次循环时,这里的name[endRange]肯定是[,因此不会处理此处理
          while (endRange < end && isWhitespace(name[endRange])) endRange++;

          //碰到结束,或者是{声明(即时声明数组),则结束
          if (endRange == end || name[endRange] == '{') break;

          //这里即要求数组声明中最后一位肯定是[符号
          if (name[endRange] != '[') {
            throw new CompileException("unexpected token in constructor", name, endRange);
          }
          //找到[]中间的信息,并将其添加到size里面,即表示长度信息
          to = balancedCapture(name, endRange, start + offset, '[');
          sizes.add(subset(name, ++endRange, to - endRange));
          endRange = to + 1;
        }

        Iterator<char[]> iter = sizes.iterator();
        arraySize = new ArraySize[sizes.size()];

        for (int i = 0; i < arraySize.length; i++)
          arraySize[i] = new ArraySize(iter.next());

        //这里表示在编译期的话，需要再次处理[]里面表达式的值,将里面的信息定义为需要继续处理的表达式
        if ((fields & COMPILE_IMMEDIATE) != 0) {
          compiledArraySize = new ExecutableStatement[arraySize.length];
          for (int i = 0; i < compiledArraySize.length; i++)
            compiledArraySize[i] = (ExecutableStatement) subCompileExpression(arraySize[i].value);
        }

        return;
      }

      //这里，即不存在(，也不存在[，实际上在后续的处理中会出现相应的异常，这里仅作声明处理
      className = new String(name, start, offset).trim();
    }
    else {
      //类型声明，从(处往前为整个类型定义
      className = new String(name, start, endRange - start).trim();
    }
  }

  /** 此类型是否是数组 */
  public boolean isArray() {
    //即查看是否有arraySize的属性
    return arraySize != null;
  }

  /** 如果是数组,则返回相应的维数,即一维还是多维 */
  public int getArrayLength() {
    return arraySize.length;
  }

  public ArraySize[] getArraySize() {
    return arraySize;
  }

  /** 返回相应的数组的长度的编译单元 */
  public ExecutableStatement[] getCompiledArraySize() {
    return compiledArraySize;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  /** 当前类型是否为普通类声明 */
  public boolean isClass() {
    return className != null && className.length() != 0;
  }

  public int getEndRange() {
    return endRange;
  }

  public void setEndRange(int endRange) {
    this.endRange = endRange;
  }

  public Class<?> getClassReference() throws ClassNotFoundException {
    return getClassReference(null, this);
  }

  /** 获取实际上的相应的类引用 */
  public Class<?> getClassReference(ParserContext ctx) throws ClassNotFoundException {
    return getClassReference(ctx, this);
  }

  /**
   * 基于基类根据相应的描述符创建出相应的类型信息(数组或基类)
   *
   * @param baseType 在数组中的基类
   */
  public static Class getClassReference(Class baseType,
                                        TypeDescriptor tDescr,
                                        VariableResolverFactory factory, ParserContext ctx) throws ClassNotFoundException {
    return findClass(factory, repeatChar('[', tDescr.arraySize.length) + "L" + baseType.getName() + ";", ctx);
  }

  /** 根据描述符以及当前要创建的类创建出类型信息(即创建当前类或者是数组类的简化判定) */
  public static Class getClassReference(ParserContext ctx, Class cls, TypeDescriptor tDescr) throws ClassNotFoundException {
    //针对数组类型作特殊判定,如果是数组,则转换相应的数组类
    if (tDescr.isArray()) {
      cls = cls.isPrimitive() ?
          toPrimitiveArrayType(cls) :
          findClass(null, repeatChar('[', tDescr.arraySize.length) + "L" + cls.getName() + ";", ctx);
    }
    return cls;
  }


  /** 从上下文中+相应的类型描述中获取相应的类型信息,此方法是在编译期调用的 */
  public static Class getClassReference(ParserContext ctx, TypeDescriptor tDescr) throws ClassNotFoundException {
    Class cls;
    //在上下文中存在,并且已经引用过
    if (ctx != null && ctx.hasImport(tDescr.className)) {
      //默认类型信息
      cls = ctx.getImport(tDescr.className);
      //针对数组,则创建出相应的数组类型信息
      if (tDescr.isArray()) {
        cls = cls.isPrimitive() ?
            toPrimitiveArrayType(cls) :
            findClass(null, repeatChar('[', tDescr.arraySize.length) + "L" + cls.getName() + ";", ctx);
      }
    }
    else if (ctx == null && hasContextFreeImport(tDescr.className)) {
      //默认引用的类信息
      cls = getContextFreeImport(tDescr.className);
      if (tDescr.isArray()) {
        cls = cls.isPrimitive() ?
            toPrimitiveArrayType(cls) :
            findClass(null, repeatChar('[', tDescr.arraySize.length) + "L" + cls.getName() + ";", ctx);
      }
    }
    else {
      //默认处理,尝试创建起相应的类型信息
      cls = createClass(tDescr.getClassName(), ctx);
      if (tDescr.isArray()) {
        cls = cls.isPrimitive() ?
            toPrimitiveArrayType(cls) :
            findClass(null, repeatChar('[', tDescr.arraySize.length) + "L" + cls.getName() + ";", ctx);
      }
    }

    return cls;
  }

  /** 判定数组中是否是无维度声明的,即数组即为[]这种声明信息,只有无维度声明才能够带后面的{}声明,如new int[]{1,2} */
  public boolean isUndimensionedArray() {
    if (arraySize != null) {
      for (ArraySize anArraySize : arraySize) {
        if (anArraySize.value.length == 0) return true;
      }
    }

    return false;
  }

  /** 查找是否是常量里面存在相应的类型信息(即各种从class_literals中继承过来的) */
  public static boolean hasContextFreeImport(String name) {
    return AbstractParser.LITERALS.containsKey(name) && AbstractParser.LITERALS.get(name) instanceof Class;
  }

  /** 通过别名获取默认引用的类信息 */
  public static Class getContextFreeImport(String name) {
    return (Class) AbstractParser.LITERALS.get(name);
  }

  public char[] getExpr() {
    return expr;
  }

  public int getStart() {
    return start;
  }

  public int getOffset() {
    return offset;
  }
}
