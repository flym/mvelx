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
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.mvel2.util.ParseTools;

import java.util.HashMap;

import static org.mvel2.util.CompilerTools.expectType;
import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * 描述for循环的节点信息
 * @author Christopher Brock
 */
public class ForNode extends BlockNode {
  /** 无用字段 */
  protected String item;

  /** 初始化语句(第1段) */
  protected ExecutableStatement initializer;
  /** 条件语句(第2段) */
  protected ExecutableStatement condition;

  /** 第3段语句 */
  protected ExecutableStatement after;

  /** 当前上下文是否支持新变量创建 */
  protected boolean indexAlloc = false;

  public ForNode(char[] expr, int start, int offset, int blockStart, int blockEnd, int fields, ParserContext pCtx) {
    super(pCtx);

    boolean varsEscape = buildForEach(this.expr = expr, this.start = start, this.offset = offset,
        this.blockStart = blockStart, this.blockOffset = blockEnd, fields, pCtx);

    //新解析作用域判定,以方便在执行时考虑新解析工厂作用域
    this.indexAlloc = pCtx != null && pCtx.isIndexAllocation();

    //避免无限循环的问题,即条件不变(没有更新变量)，语句为空的情况
    if ((fields & COMPILE_IMMEDIATE) != 0 && compiledBlock.isEmptyStatement() && !varsEscape) {
      throw new RedundantCodeException();
    }

    //在build过程中有入栈,因此这里进行出栈,以丢弃已使用完的解析上下文作用域
    if (pCtx != null) {
      pCtx.popVariableScope();
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //根据解析时上下文情况考虑新建作用域
    VariableResolverFactory ctxFactory = indexAlloc ? factory : new MapVariableResolverFactory(new HashMap<String, Object>(1), factory);
    Object v;
    //标准的for循环3段式处理,初始化,条件,递增处理
    for (initializer.getValue(ctx, thisValue, ctxFactory); (Boolean) condition.getValue(ctx, thisValue, ctxFactory); after.getValue(ctx, thisValue, ctxFactory)) {
      //内部语法块执行
      v = compiledBlock.getValue(ctx, thisValue, ctxFactory);
      //因为过程中可能有相应的return 语句,因此这里进行判定,以支持在for循环中提前返回
      if (ctxFactory.tiltFlag()) return v;
    }
    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译执行相同,只不过这里采用解释运行
    Object v;
    for (initializer.getValue(ctx, thisValue, factory = new MapVariableResolverFactory(new HashMap<String, Object>(1), factory)); (Boolean) condition.getValue(ctx, thisValue, factory); after.getValue(ctx, thisValue, factory)) {
      v = compiledBlock.getValue(ctx, thisValue, factory);
      if (factory.tiltFlag()) return v;
    }

    return null;
  }

  /** 编译整个for循环语句,并通过变量逸出简单判断是否是死循环 */
  private boolean buildForEach(char[] condition, int start, int offset, int blockStart, int blockEnd, int fields, ParserContext pCtx) {
    int end = start + offset;
    //第1个分号
    int cursor = nextCondPart(condition, start, end, false);

    boolean varsEscape = false;

    try {
      ParserContext spCtx;
      //判断一个子clone解析上下文来支持相应的逸出判断
      if (pCtx != null) {
        spCtx = pCtx.createSubcontext().createColoringSubcontext();
      }
      else {
        spCtx = new ParserContext();
      }

      //起始节点
      this.initializer = (ExecutableStatement) subCompileExpression(condition, start, cursor - start - 1, spCtx);

      //进入语句块，因此添加新的作用域t
      if (pCtx != null) {
        pCtx.pushVariableScope();
      }

      //条件节点
      try {
        //期望相应的条件结果为boolean
        //第2个分号
        expectType(pCtx, this.condition = (ExecutableStatement) subCompileExpression(condition, start = cursor,
            (cursor = nextCondPart(condition, start, end, false)) - start - 1, spCtx), Boolean.class, ((fields & COMPILE_IMMEDIATE) != 0));
      }
      catch (CompileException e) {
        if (e.getExpr().length == 0) {
          e.setExpr(expr);

          //重新定位到出错的起始点位置
          while (start < expr.length && ParseTools.isWhitespace(expr[start])) {
            start++;
          }

          e.setCursor(start);
        }
        throw e;
      }

      //第三块节点
      //第3个分号或结尾
      this.after = (ExecutableStatement)
          subCompileExpression(condition, start = cursor, (nextCondPart(condition, start, end, true)) - start, spCtx);

      //通过子上下文判断出是有变量逸出的,因此判断出不是死循环
      //在for循环中3段中的变量认为都是属于当前作用域的一部分
      if (spCtx != null && (fields & COMPILE_IMMEDIATE) != 0 && spCtx.isVariablesEscape()) {
        if (pCtx != spCtx) pCtx.addVariables(spCtx.getVariables());
        varsEscape = true;
      }
      else if (spCtx != null && pCtx != null) {
        pCtx.addVariables(spCtx.getVariables());
      }

      //执行节点
      this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockEnd, spCtx);
      if (pCtx != null) {
        pCtx.setInputs(spCtx.getInputs());
      }
    }
    catch (NegativeArraySizeException e) {
      throw new CompileException("wrong syntax; did you mean to use 'foreach'?", expr, start);
    }
    return varsEscape;
  }

  /**
   * for循环中的条件域,根据分号来确定相应的分隔信息
   * @param allowEnd 在过程中没有找到,是否认为是到末尾了.只有第3段才允许,因此第3段认为是到了结尾
   *
   * */
  private static int nextCondPart(char[] condition, int cursor, int end, boolean allowEnd) {
    for (; cursor < end; cursor++) {
      if (condition[cursor] == ';') return ++cursor;
    }
    //不允许到末尾,则认为语句本身有问题
    if (!allowEnd) throw new CompileException("expected ;", condition, cursor);
    return cursor;
  }
}