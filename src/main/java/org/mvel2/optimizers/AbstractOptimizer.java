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
package org.mvel2.optimizers;

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.AbstractParser;

import java.lang.reflect.Method;

import static java.lang.Thread.currentThread;
import static org.mvel2.util.ParseTools.*;

/**
 * 抽象的优化解析器，用于定义一些公用的信息，以及定义一些公用的方法，通用的逻辑处理等
 *
 * @author Christopher Brock
 */
public class AbstractOptimizer extends AbstractParser {
  /** 表示属性访问 */
  protected static final int BEAN = 0;
  /** 表示方法访问 */
  protected static final int METH = 1;
  /** 表示集合访问 */
  protected static final int COL = 2;
  /** 表示特殊的with访问 */
  protected static final int WITH = 3;

  /** 当前处理是否是集合 */
  protected boolean collection = false;
  /** 当前处理是否是null安全的 */
  protected boolean nullSafe = false;
  /** 当前处理属性的类型 */
  protected Class currType = null;
  /** 当前是否是静态方法，即静态访问字段，类等 */
  protected boolean staticAccess = false;

  /** 当前处理的表达式在整个语句中的起始下标,为一个在处理过程中会变化的下标位,其可认为在start和end当中作于其它作用的临时变量 */
  protected int tkStart;

  protected AbstractOptimizer() {
  }

  protected AbstractOptimizer(ParserContext pCtx) {
    super(pCtx);
  }

  /**
   * 尝试静态访问此属性，此属性可能是字段，类或者对象本身
   * Try static access of the property, and return an instance of the Field, Method of Class if successful.
   *
   * @return - Field, Method or Class instance.
   */
  protected Object tryStaticAccess() {
    int begin = cursor;
    try {
      /**
       * Try to resolve this *smartly* as a static class reference.
       *
       * This starts at the end of the token and starts to step backwards to figure out whether
       * or not this may be a static class reference.  We search for method calls simply by
       * inspecting for ()'s.  The first union area we come to where no brackets are present is our
       * test-point for a class reference.  If we find a class, we pass the reference to the
       * property accessor along  with trailing methods (if any).
       * 这里查找方式为从后往前进行查找
       *
       */
      boolean meth = false;
      // int end = start + length;
      int last = end;
      for (int i = end - 1; i > start; i--) {
        switch (expr[i]) {
          case '.':
            //找到一个.符号，表示可能直接是字段访问或者是方法读取 ,如 a.b 这种方式，那么下面的处理直接处理a,而忽略b，因此后面再处理b属性
            if (!meth) {
              //这里的last和i可能并不相同，如last可能直接为 end属性，即表示直接就是 a.b 处理
              //而另一种情况就是 a.b()属性，这里last和i就是相同的
              ClassLoader classLoader = pCtx != null ? pCtx.getClassLoader() : currentThread().getContextClassLoader();
              String test = new String(expr, start, (cursor = last) - start);
              try {
                //先处理类后面直接带.class的类
                if (MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS && test.endsWith(".class"))
                  test = test.substring(0, test.length() - 6);

                //尝试直接加载此类
                return Class.forName(test, true, classLoader);
              }
              catch (ClassNotFoundException cnfe) {
                try {
                  //因为上面加载失败了，那么可能是内部类，这里尝试加载内部类
                  return findInnerClass(test, classLoader, cnfe);
                }
                catch (ClassNotFoundException e) { /* ignore */ }
                //这里可能处理的数据了 a.b 而a为类，b为字段或方法，因此尝试使用i 处理a 类
                Class cls = forNameWithInner(new String(expr, start, i - start), classLoader);
                //认为剩下的数据为字段或方法，尝试进行加载处理
                String name = new String(expr, i + 1, end - i - 1);
                try {
                  return cls.getField(name);
                }
                catch (NoSuchFieldException nfe) {
                  for (Method m : cls.getMethods()) {
                    if (name.equals(m.getName())) return m;
                  }
                  return null;
                }
              }
            }

            //这里是因为找到()，则相应的标记置true,这里将last重新进行处理，表示a.b()先由last到(处，这里再到a的后面的下标处
            //然后在这里将last和i置为一样
            meth = false;
            last = i;
            break;

          //这里表示碰到代码块，无其它作用，先直接跳过，可能为内部类初始化块
          case '}':
            i--;
            for (int d = 1; i > start && d != 0; i--) {
              switch (expr[i]) {
                case '}':
                  d++;
                  break;
                case '{':
                  d--;
                  break;
                case '"':
                case '\'':
                  char s = expr[i];
                  while (i > start && (expr[i] != s && expr[i - 1] != '\\')) i--;
              }
            }
            break;

          //碰到)，表示肯定是方法调用,但这里仅用于查找相应的方法实例，而并不是方法调用本身，因此将跳过相应的方法参数信息
          case ')':
            i--;

            //采用相应的d作为深度，一直找到相匹配的 (为止
            for (int d = 1; i > start && d != 0; i--) {
              switch (expr[i]) {
                case ')':
                  d++;
                  break;
                case '(':
                  d--;
                  break;
                //跳过字符串
                case '"':
                case '\'':
                  char s = expr[i];
                  while (i > start && (expr[i] != s && expr[i - 1] != '\\')) i--;
              }
            }

            //这里作相应的标记，表示为字符串,并且相应的last标记往前推进
            meth = true;
            last = i++;
            break;


          //碰到字符串，跳过
          case '\'':
            while (--i > start) {
              if (expr[i] == '\'' && expr[i - 1] != '\\') {
                break;
              }
            }
            break;

          //碰到字符串，跳过
          case '"':
            while (--i > start) {
              if (expr[i] == '"' && expr[i - 1] != '\\') {
                break;
              }
            }
            break;
        }
      }
    }
    catch (Exception cnfe) {
      cursor = begin;
    }

    return null;
  }

  /**
   * 读取可能的操作属性,通过查找当前字符串中可能存在的特殊符号来进行定位.
   * <p/>
   * 操作属性的读取是通过读取最接近的操作来完成的,而并不是一步一步来完成的.如a.b[2]则会定义为集合访问，即最终为(a.b)[2]这种操作，然后先处理a.b，再处理[2]操作
   */
  protected int nextSubToken() {
    skipWhitespace();
    nullSafe = false;

    //先通过首字符来判定，可能是集合，属性或者其它调用
    switch (expr[tkStart = cursor]) {
      //集合调用
      case '[':
        return COL;
      //with调用
      case '{':
        if (expr[cursor - 1] == '.') {
          return WITH;
        }
        break;
      //属性调用,如果.后接一个?号，表示当前属性的值结果可能是null的
      case '.':
        if ((start + 1) != end) {
          switch (expr[cursor = ++tkStart]) {
            case '?':
              skipWhitespace();
              if ((cursor = ++tkStart) == end) {
                throw new CompileException("unexpected end of statement", expr, start);
              }
              nullSafe = true;

              fields = -1;
              break;
            //.后面接{,表示with调用
            case '{':
              return WITH;
            default:
              if (isWhitespace(expr[tkStart])) {
                skipWhitespace();
                tkStart = cursor;
              }
          }
        }
        else {
          throw new CompileException("unexpected end of statement", expr, start);
        }
        break;
      //这里直接在最前台加一个?，即表示访问这个属性，并且这个属性值可能为null
      case '?':
        if (start == cursor) {
          tkStart++;
          cursor++;
          nullSafe = true;
        }
    }

    //表示没有特殊字段,则是正常的字符,则继续找到下一个非字符处理
    //noinspection StatementWithEmptyBody
    while (++cursor < end && isIdentifierPart(expr[cursor])) ;

    //在跳过一堆字段之后，还没有到达末尾，表示中间有类似操作符存在，则通过第一个非字段点来进行判断
    skipWhitespace();
    if (cursor < end) {
      switch (expr[cursor]) {
        case '[':
          return COL;
        case '(':
          return METH;
        default:
          return BEAN;
      }
    }

    //默认为bean操作，即读取属性
    return 0;
  }

  /** 当前捕获的属性名(字符串),即在刚才的处理过程中处理的字符串 */
  protected String capture() {
    /**
     * Trim off any whitespace.
     */
    return new String(expr, tkStart = trimRight(tkStart), trimLeft(cursor) - tkStart);
  }

  /**
   * 跳过相应的空白块
   * Skip to the next non-whitespace position.
   */
  protected void whiteSpaceSkip() {
    if (cursor < length)
      //noinspection StatementWithEmptyBody
      while (isWhitespace(expr[cursor]) && ++cursor != length) ;
  }

  /**
   * 查找指定的字符，直到找到为止,同时相应的相应的下标会往前递进
   *
   * @param c - character to scan to.
   * @return - returns true is end of statement is hit, false if the scan scar is countered.
   */
  protected boolean scanTo(char c) {
    for (; cursor < end; cursor++) {
      switch (expr[cursor]) {
        case '\'':
        case '"':
          cursor = captureStringLiteral(expr[cursor], expr, cursor, end);
        default:
          if (expr[cursor] == c) {
            return false;
          }
      }
    }
    return true;
  }

  /** 从后往前找到最后一个用于处理联合操作的下标值，如 a.b.c, 将找到c前面的位置。而a.b{{1+2}},将找到b后面的大括号的位置 */
  protected int findLastUnion() {
    int split = -1;
    int depth = 0;

    int end = start + length;
    for (int i = end - 1; i != start; i--) {
      switch (expr[i]) {
        //因为是从后往前，因此对后右括号需要深度加1,表示需要从前找到相匹配的符号
        case '}':
        case ']':
          depth++;
          break;

        case '{':
        case '[':
          //归0表示找到相匹配的信息，否则继续
          if (--depth == 0) {
            split = i;
            //因为碰到{ [,表示是集合访问
            collection = true;
          }
          break;
        // . 符号，正常的调用访问
        case '.':
          if (depth == 0) {
            split = i;
          }
          break;
      }
      //找到数据，直接退出
      if (split != -1) break;
    }

    return split;
  }
}
