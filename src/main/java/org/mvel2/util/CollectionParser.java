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

import org.mvel2.CompileException;
import org.mvel2.DataConversion;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.ReflectionUtil.isAssignableFrom;

/**
 * 集合解析器,用于解析一个如{1,2,3},或者是[1,2,3},或者是{a:b}这种直接表达式
 * 这种表达式,将在运行期直接即产生一个相应的数组或集合,或map
 * This is the inline collection sub-parser.  It produces a skeleton model of the collection which is in turn translated
 * into a sequenced AST to produce the collection efficiently at runtime, and passed off to one of the JIT's if
 * configured.
 *
 * @author Christopher Brock
 */
public class CollectionParser {
  /** 要处理的字符数组 */
  private char[] property;

  /** 处理过程中的下标位 */
  private int cursor;
  /** 当前表达式的起始位置 */
  private int start;
  /** 表达式结束位置 */
  private int end;

  /** 当前处理的数据类型,即实际处理的数组，集合的类型信息 */
  private int type;

  /** 相应的处理类型,认为是ArrayList */
  public static final int LIST = 0;
  /** 相应的处理类型,认为是数组 */
  public static final int ARRAY = 1;
  /** 认为是hashmap */
  public static final int MAP = 2;

  /** 具体的集合中每一个数据的类型(非数组类型) */
  private Class colType;
  /** 认为的编译上下文 */
  private ParserContext pCtx;

  /** 常量,空数组描述 */
  private static final Object[] EMPTY_ARRAY = new Object[0];

  public CollectionParser() {
  }

  public CollectionParser(int type) {
    this.type = type;
  }

  /** 解析一个集合的属性表达式,并根据子编译选项决定是否每一项进行编译 */
  public Object parseCollection(char[] property, int start, int offset, boolean subcompile, ParserContext pCtx) {
    this.property = property;
    this.pCtx = pCtx;
    this.end = start + offset;

    while (start < end && isWhitespace(property[start])) {
      start++;
    }
    this.start = this.cursor = start;

    return parseCollection(subcompile);
  }

  /** 解析一个集合的属性表达式，并根据其传入的声明子类型进行定义和解析 */
  public Object parseCollection(char[] property, int start, int offset, boolean subcompile, Class colType, ParserContext pCtx) {
    if (colType != null) this.colType = getBaseComponentType(colType);
    this.property = property;

    this.end = start + offset;

    while (start < end && isWhitespace(property[start])) {
      start++;
    }

    this.start = this.cursor = start;

    this.pCtx = pCtx;

    return parseCollection(subcompile);
  }

  /**
   * 编译并返回已编译好的结果(即最终的处理数据),最终以list的方式或map方式,数组方式返回
   * 返回类型取决于相应数据的类型
   * 针对为{a:b}的这种类型，返回的数据为List<Map> 类型
   * 因为默认的type即为0，因此默认返回的类型均是List类型
   *
   * @param subcompile 是否要对每一项进行编译
   */
  private Object parseCollection(boolean subcompile) {
    //首尾相同,即[]或{}这种,默认为空数组
    if (end - start == 0) {
      if (type == LIST) return new ArrayList();
      else return EMPTY_ARRAY;
    }

    Map<Object, Object> map = null;
    List<Object> list = null;

    int st = start;

    //在解析前已经了数据的类型，因此这里先根据类型创建好相应的结果信息
    if (type != -1) {
      switch (type) {
        case ARRAY:
        case LIST:
          list = new ArrayList<Object>();
          break;
        case MAP:
          map = new HashMap<Object, Object>();
          break;
      }
    }

    //临时对象,用于表示在过程中的不同中间对象
    Object curr = null;
    //根据数据格式,重新解析相应的类型,即前面已经确定是集合或map，但还可能是数组
    int newType = -1;


    for (; cursor < end; cursor++) {
      switch (property[cursor]) {
        //在{,即认为是{1,2}这种,因此认为是数组
        case '{':
          if (newType == -1) {
            newType = ARRAY;
          }

          //集合
        case '[':
          //处理在[存在,如[new int[]{}的这种情况
          if (cursor > start && isIdentifierPart(property[cursor - 1])) continue;

          if (newType == -1) {
            newType = LIST;
          }

          /**
           * 处理在数组中存在[new int[]{1,2}]的这种情况
           * 这里会定位于后面的{1,2},这样进行判定,同时相应的类型由外层的单个数据类型来决定
           * 对于map，则认为这里已经到达 {a:[1,2]}的这种情况，那么相应的 key值肯定已经确定，只需要将后面的[1,2]解析为list,然后再以
           * key为a,value为[1,2]放到map中即可
           * 针对{a:1}的这种类型，在这里进入内部解析时，已经把相应的{ [ 去掉，因此内部只解析a:1，那么这样就内部解析为map，但外部仍然为List类型
           * Sub-parse nested collections.
           */
          //这里拿到内层的数据值,通过传入一个参考的过程对象的类型进行解析
          Object o = new CollectionParser(newType).parseCollection(property, (st = cursor) + 1,
              (cursor = balancedCapture(property, st, end, property[st])) - st - 1, subcompile, colType, pCtx);

          //如果外层为map,放到map中即可
          if (type == MAP) {
            map.put(curr, o);
          }
          //外层为list，直接加到其中即可
          else {
            list.add(curr = o);
          }

          cursor = skipWhitespace(property, ++cursor);

          //到达一个,号,即表示已经解析了一个表达式
          if ((st = cursor) < end && property[cursor] == ',') {
            st = cursor + 1;
          }
          else if (cursor < end) {
            //这里原意是支持如{new int[]{1,23}.length,4}这种处理,但实际上这里并不支持
            //因此,实际上语法并不能支持这种处理,这里仅作相应的考虑,即元素内部可以进一步符号处理
            if (ParseTools.opLookup(property[cursor]) == -1) {
              throw new CompileException("unterminated collection element", property, cursor);
            }
          }

          continue;

          //处理碰到(，双引号这种需要特殊处理的表达式,即对整个表达式认为一个统一的数据信息
        case '(':
          cursor = balancedCapture(property, cursor, end, '(');

          break;

        case '\"':
        case '\'':
          cursor = balancedCapture(property, cursor, end, property[cursor]);

          break;

        case ',':
          //到达分隔符,这里即认为已经解析完一条数据了,这些数据均没有继续解析，可以认为是一个未经解析的表达式
          //这里的表达式直接使用字符串将其预填充起来
          if (type != MAP) {
            list.add(new String(property, st, cursor - st).trim());
          }
          else {
            //是map,则将之前解析好的临时对象认为是key
            map.put(curr, createStringTrimmed(property, st, cursor - st));
          }

          //语法及类型效验，在子编译的过程中进行
          if (subcompile) {
            subCompile(st, cursor - st);
          }

          st = cursor + 1;

          break;

        //碰到:号,因为之前碰到{，认为为数组，那么这里重新认定为map,即使传入的参数为array，也会更正为map，主要原因为传入的类型可能也并不确定，即
        //是一个参考的类型值
        // 则重新认为要解析的数据为map类型,因此重新设置值进行处理
        case ':':
          if (type != MAP) {
            map = new HashMap<Object, Object>();
            type = MAP;
          }
          //认为之前的解析值为key,认为直接是一个字符串
          curr = createStringTrimmed(property, st, cursor - st);

          //key也需要进行编译和校验
          if (subcompile) {
            subCompile(st, cursor - st);
          }

          st = cursor + 1;
          break;

        //.号,认为是with语句
        case '.':
          cursor++;
          cursor = skipWhitespace(property, cursor);
          if (cursor != end && property[cursor] == '{') {
            cursor = balancedCapture(property, cursor, '{');
          }
          break;
      }
    }

    if (st < end && isWhitespace(property[st])) {
      st = skipWhitespace(property, st);
    }

    //已经解析完,将最后一部分记入数据中
    if (st < end) {
      if (cursor < (end - 1)) cursor++;

      if (type == MAP) {
        map.put(curr, createStringTrimmed(property, st, cursor - st));
      }
      else {
        if (cursor < end) cursor++;
        list.add(createStringTrimmed(property, st, cursor - st));
      }

      if (subcompile) subCompile(st, cursor - st);
    }

    //根据实际的类型再进行处理，同时返回其正确的类型信息
    switch (type) {
      case MAP:
        return map;
      case ARRAY:
        return list.toArray();
      default:
        return list;
    }
  }

  /**
   * 尝试对每一个子表达式，如list中的解析顶，map中的解析value值进行编译，并且判断其是否能和声明的集合内的对象类型进行兼容
   * 同时判断其语法是否正确
   */
  private void subCompile(int start, int offset) {
    if (colType == null) {
      subCompileExpression(property, start, offset, pCtx);
    }
    else {
      Class r = ((ExecutableStatement) subCompileExpression(property, start, offset, pCtx)).getKnownEgressType();
      if (r != null && !isAssignableFrom(colType, r) && (isStrongType() || !DataConversion.canConvert(r, colType))) {
        throw new CompileException("expected type: " + colType.getName() + "; but found: " + r.getName(), property, cursor);
      }
    }
  }

  /** 当前解析是否是强类型解析 */
  private boolean isStrongType() {
    return pCtx != null && pCtx.isStrongTyping();
  }

  public int getCursor() {
    return cursor;
  }
}
