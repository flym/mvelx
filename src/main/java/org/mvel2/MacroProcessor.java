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

import org.mvel2.compiler.AbstractParser;
import org.mvel2.util.StringAppender;

import java.util.Map;

import static org.mvel2.util.ParseTools.*;

/**
 * 宏处理器,用于将已注册的宏对相应的字符串进行处理,并进行替换
 * A simple, fast, macro processor.  This processor works by simply replacing a matched identifier with a set of code.
 */
public class MacroProcessor extends AbstractParser implements PreProcessor {
  /** 已注册的宏信息,其中key为定义时的占位符 */
  private Map<String, Macro> macros;

  public MacroProcessor() {
  }

  /** 基于已有的宏进行相应的处理器构造 */
  public MacroProcessor(Map<String, Macro> macros) {
    this.macros = macros;
  }

  /** 使用宏处理相应的字符串,并返回处理后的值 */
  public char[] parse(char[] input) {
    setExpression(input);

    StringAppender appender = new StringAppender();

    int start;
    //当前状态下是否可能有宏被命中 因为在某些情况下是不可能有宏替换的,比如字符串中.或者是字段中这种情况
    boolean macroArmed = true;
    String token;

    for (; cursor < length; cursor++) {
      start = cursor;
      while (cursor < length && isIdentifierPart(expr[cursor])) cursor++;
      if (cursor > start) {
        //因为当前会拿到一个token,即全属性,那么就可能是一个宏占位符
        //如果确定是宏占位符,则进行相应的处理动作
        //这里的判断前提就是宏是被激活的状态下,即只有在当前为可被命中的情况下才进行.如果是字段等,则不会被激活的
        if (macros.containsKey(token = new String(expr, start, cursor - start)) && macroArmed) {
          appender.append(macros.get(token).doMacro());
        }
        else {
          appender.append(token);
        }
      }

      if (cursor < length) {
        switch (expr[cursor]) {
          case '\\':
            cursor++;
            break;
          //处理注释
          case '/':
            start = cursor;

            if (cursor + 1 != length) {
              switch (expr[cursor + 1]) {
                case '/':
                  while (cursor != length && expr[cursor] != '\n') cursor++;
                  break;
                case '*':
                  int len = length - 1;
                  while (cursor != len && !(expr[cursor] == '*' && expr[cursor + 1] == '/')) cursor++;
                  cursor += 2;
                  break;
              }
            }

            if (cursor < length) cursor++;

            appender.append(new String(expr, start, cursor - start));

            if (cursor < length) cursor--;
            break;

          //引号,表示碰到了中,则整个过程中不可能有宏,则整个一块解析跳过
          case '"':
          case '\'':
            appender.append(new String(expr, (start = cursor),
                (cursor = captureStringLiteral(expr[cursor], expr, cursor, length)) - start));

            if (cursor >= length) break;
            else if (isIdentifierPart(expr[cursor])) cursor--;

            //剩下的,在指定的标识符号之后,处理可能的宏命中状态
          default:
            switch (expr[cursor]) {
            //碰到了.表示后面的为属性访问(字段或方法).那么就不能激活宏处理
              case '.':
                macroArmed = false;
                break;
              //一个语句结束了,那么后面的起始阶段,就可能会激活宏处理
              case ';':
              case '{':
              case '(':
                macroArmed = true;
                break;
            }

            appender.append(expr[cursor]);
        }
      }
    }

    return appender.toChars();
  }

  public String parse(String input) {
    return new String(parse(input.toCharArray()));
  }

  public Map<String, Macro> getMacros() {
    return macros;
  }

  public void setMacros(Map<String, Macro> macros) {
    this.macros = macros;
  }

  public void captureToWhitespace() {
    while (cursor < length && !isWhitespace(expr[cursor])) cursor++;
  }
}
