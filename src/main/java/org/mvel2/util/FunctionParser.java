package org.mvel2.util;

import org.mvel2.CompileException;
import org.mvel2.ParserContext;
import org.mvel2.ast.EndOfStatement;
import org.mvel2.ast.Function;

import static org.mvel2.util.ParseTools.balancedCaptureWithLineAccounting;

/** 函数解析器,用于解析相应的函数,以及相应的调用参数信息 */
public class FunctionParser {
  /** 当前函数名 */
  private String name;

  /** 当前解析位置 */
  private int cursor;
  /** 最大解析长度 */
  private int length;

  /** 当前解析标识 */
  private int fields;
  /** 解析串 */
  private char[] expr;
  /** 上下文 */
  private ParserContext pCtx;

  /** 整个函数体的执行栈 */
  private ExecutionStack splitAccumulator;

  public FunctionParser(String functionName,
                        int cursor,
                        int endOffset,
                        char[] expr,
                        int fields,
                        ParserContext pCtx,
                        ExecutionStack splitAccumulator) {

    this.name = functionName;
    this.cursor = cursor;
    this.length = endOffset;

    this.expr = expr;
    this.fields = fields;
    this.pCtx = pCtx;
    this.splitAccumulator = splitAccumulator;
  }

  /** 进行函数的解析，并最终返回一个函数对象 */
  public Function parse() {
    int start = cursor;

    //括号内的数据分割
    int startCond = 0;
    int endCond = 0;

    //语句部分分割
    int blockStart;
    int blockEnd;

    int end = cursor + length;

    //定位到相应的函数名后面的位置,因为函数名已经提前被解析了，因此这里只是位置往后进行定位
    cursor = ParseTools.captureToNextTokenJunction(expr, cursor, end, pCtx);

    //该函数有相应的参数定义信息
    if (expr[cursor = ParseTools.nextNonBlank(expr, cursor)] == '(') {
      /**
       * 这里因为是(，表示有参数定义了，这里找到()之内的定义信息
       * 同时认为是相应的参数信息定义的结束位置
       * If we discover an opening bracket after the function name, we check to see
       * if this function accepts parameters.
       */
      endCond = cursor = balancedCaptureWithLineAccounting(expr, startCond = cursor, end, '(', pCtx);
      startCond++;
      cursor++;

      cursor = ParseTools.skipWhitespace(expr, cursor);

      if (cursor >= end) {
        throw new CompileException("incomplete statement", expr, cursor);
      }
      else if (expr[cursor] == '{') {
        //具备{，表示是多条指令的定义主体
        blockEnd = cursor = balancedCaptureWithLineAccounting(expr, blockStart = cursor, end, '{', pCtx);
      }
      else {
        //这里没有 {，即表示只有单条定义指令
        blockStart = cursor - 1;
        cursor = ParseTools.captureToEOS(expr, cursor, end, pCtx);
        blockEnd = cursor;
      }
    }
    else {
      /**
       * 没有参数定义，按照有 { 和没有{的两种处理逻辑
       * This function has not parameters.
       */
      if (expr[cursor] == '{') {
        /**
         * This function is bracketed.  We capture the entire range in the brackets.
         */
        blockEnd = cursor = balancedCaptureWithLineAccounting(expr, blockStart = cursor, end, '{', pCtx);
      }
      else {
        /**
         * 单指令函数
         * This is a single statement function declaration.  We only capture the statement.
         */
        blockStart = cursor - 1;
        cursor = ParseTools.captureToEOS(expr, cursor, end, pCtx);
        blockEnd = cursor;
      }
    }

    /**
     * Trim any whitespace from the captured block range.
     */
    blockStart = ParseTools.trimRight(expr, blockStart + 1);
    blockEnd = ParseTools.trimLeft(expr, start, blockEnd);

    cursor++;

    /**
     * 没有结束点，这里手动地添加一个截止标记
     * Check if the function is manually terminated.
     */
    if (splitAccumulator != null && ParseTools.isStatementNotManuallyTerminated(expr, cursor)) {
      /**
       * Add an EndOfStatement to the split accumulator in the parser.
       */
      splitAccumulator.add(new EndOfStatement(pCtx));
    }

    /**
     * 根据最终解析到的各种数据构建出相应的函数对象
     * Produce the funciton node.
     */
    return new Function(name, expr, startCond, endCond - startCond, blockStart, blockEnd - blockStart, fields, pCtx);
  }

  public String getName() {
    return name;
  }

  public int getCursor() {
    return cursor;
  }
}
