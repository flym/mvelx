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
package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableLiteral;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.lang.String.valueOf;
import static java.util.regex.Pattern.compile;
import static org.mvel2.MVEL.eval;
import static org.mvel2.util.ParseTools.subCompileExpression;

/** 描述一个用于正则匹配的表达式节点 */
public class RegExMatch extends ASTNode {
  /** 当前变量或属性表达式 */
  private ExecutableStatement stmt;
  /** 当前正则表达式字符串的表达式 */
  private ExecutableStatement patternStmt;

  /** 正则表达式起始点 */
  private int patternStart;
  /** 正则表达式结束点 */
  private int patternOffset;
  /** 相应的正则表达式 */
  private Pattern p;

  public RegExMatch(char[] expr, int start, int offset, int fields, int patternStart, int patternOffset, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;
    this.patternStart = patternStart;
    this.patternOffset = patternOffset;

    if ((fields & COMPILE_IMMEDIATE) != 0) {
      //编译需要被正则处理的表达式
      this.stmt = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx);
      //本身的正则处理器
      if ((this.patternStmt = (ExecutableStatement)
          subCompileExpression(expr, patternStart, patternOffset, pCtx)) instanceof ExecutableLiteral) {

        //如果是常量,则尝试直接进行编译此正则式
        try {
          p = compile(valueOf(patternStmt.getValue(null, null)));
        }
        catch (PatternSyntaxException e) {
          throw new CompileException("bad regular expression", expr, patternStart, e);
        }
      }
    }
  }


  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //根据正则式是否已处理好决定如何运行
    if (p == null) {
      //这里因为正则式是一个表达式,因此不能够直接进行缓存,而是每次都重新编译
      return compile(valueOf(patternStmt.getValue(ctx, thisValue, factory))).matcher(valueOf(stmt.getValue(ctx, thisValue, factory))).matches();
    }
    else {
      return p.matcher(valueOf(stmt.getValue(ctx, thisValue, factory))).matches();
    }
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译运行相同,不过相应的计算过程为解释运行
    try {
      return compile(valueOf(eval(expr, patternStart, patternOffset, ctx, factory))).matcher(valueOf(eval(expr, start, offset, ctx, factory))).matches();
    }
    catch (PatternSyntaxException e) {
      throw new CompileException("bad regular expression", expr, patternStart, e);
    }
  }

  //正则表达式匹配,结果为boolean
  public Class getEgressType() {
    return Boolean.class;
  }

  public Pattern getPattern() {
    return p;
  }

  public ExecutableStatement getStatement() {
    return stmt;
  }

  public ExecutableStatement getPatternStatement() {
    return patternStmt;
  }
}
