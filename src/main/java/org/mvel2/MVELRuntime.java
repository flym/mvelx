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

import org.mvel2.ast.ASTNode;
import org.mvel2.ast.LineLabel;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.debug.Debugger;
import org.mvel2.debug.DebuggerContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.ExecutionStack;

import static org.mvel2.Operator.*;
import static org.mvel2.util.PropertyTools.isEmpty;

/**
 * 用于主要运行已经编译过的表达式的处理，完成主要的运算逻辑处理
 * This class contains the runtime for running compiled MVEL expressions.
 */
@SuppressWarnings({"CaughtExceptionImmediatelyRethrown"})
public class MVELRuntime {
  // public static final ImmutableDefaultFactory IMMUTABLE_DEFAULT_FACTORY = new ImmutableDefaultFactory();
  /** 描述相应的调试断点上下文，以支持在处理过程中设置相应的调试断点，并进行相应的断点处理 */
  private static ThreadLocal<DebuggerContext> debuggerContext;

  /**
   * 执行相应的编译运行表达式
   * Main interpreter.
   *
   * @param debugger        Run in debug mode
   * @param expression      The compiled expression object
   * @param ctx             The root context object
   * @param variableFactory The variable factory to be injected
   * @return The resultant value
   * @see org.mvel2.MVEL
   */
  public static Object execute(boolean debugger, final CompiledExpression expression, final Object ctx,
                               VariableResolverFactory variableFactory) {

    Object v1, v2;
    //保存当前临时的执行栈
    ExecutionStack stk = new ExecutionStack();

    ASTNode tk = expression.getFirstNode();
    Integer operator;

    //本身就没有可执行节点，则直接返回null
    if (tk == null) return null;

    try {
      do {
        //这里表示当前节点为调试节点，因此尝试设置相应的调试上下文，fields 为 1 为调试节点
        if (tk.fields == -1) {
          /**
           * This may seem silly and redundant, however, when an MVEL script recurses into a block
           * or substatement, a new runtime loop is entered.   Since the debugger state is not
           * passed through the AST, it is not possible to forward the state directly.  So when we
           * encounter a debugging symbol, we check the thread local to see if there is are registered
           * breakpoints.  If we find them, we assume that we are debugging.
           *
           * The consequence of this of course, is that it's not ideal to compileShared expressions with
           * debugging symbols which you plan to use in a production enviroment.
           */
          if (debugger || (debugger = hasDebuggerContext())) {
            try {
              debuggerContext.get().checkBreak((LineLabel) tk, variableFactory, expression);
            }
            catch (NullPointerException e) {
              // do nothing for now.  this isn't as calus as it seems.
            }
          }
          continue;
        }
        //当前操作栈为空的，则压入当前值，以进行处理
        else if (stk.isEmpty()) {
          stk.push(tk.getReducedValueAccelerated(ctx, ctx, variableFactory));
        }

        //如果标识已结束了则直接返回数据信息
        if (variableFactory.tiltFlag()) {
          return stk.pop();
        }

        switch (operator = tk.getOperator()) {
          //操作符return 在前一步即可以处理了
          case RETURN:
            variableFactory.setTiltFlag(true);
            return stk.pop();

          //默认情况下，大部分的节点均返回NOOP操作符
          case NOOP:
            continue;

            //? 操作符，如果当前结果为false，则表示要获取 ? :冒号之后的值，则当前执行流程直接跳转到:后面去.并且清空操作数栈
          case TERNARY:
            if (!stk.popBoolean()) {
              //noinspection StatementWithEmptyBody
              while (tk.nextASTNode != null && !(tk = tk.nextASTNode).isOperator(TERNARY_ELSE)) ;
            }
            stk.clear();
            continue;

            //因为这里到达 :，表示对于?已经执行完:之前的表达式，因此直接返回即可
          case TERNARY_ELSE:
            return stk.pop();

          case END_OF_STMT:
            /**
             * 这里如果还有下一个节点，表示还有进一步的操作，那么就把当前执行栈给清掉，表示不再需要之前的信息
             * If the program doesn't end here then we wipe anything off the stack that remains.
             * Althought it may seem like intuitive stack optimizations could be leveraged by
             * leaving hanging values on the stack,  trust me it's not a good idea.
             */
            if (tk.nextASTNode != null) {
              stk.clear();
            }

            continue;
        }

        //这里继续圧入下一个节点值
        //到这里这里的tk.nextASTNode肯定不为null，因为如果为null,则在上一个switch中已经处理掉，这里只要是支持一些当前还未支持到的处理
        stk.push(tk.nextASTNode.getReducedValueAccelerated(ctx, ctx, variableFactory), operator);

        try {
          //这里保证当前栈中只有一个操作数，因为之前的操作数均没有用处
          while (stk.isReduceable()) {
            if ((Integer) stk.peek() == CHOR) {
              stk.pop();
              v1 = stk.pop();
              v2 = stk.pop();
              if (!isEmpty(v2) || !isEmpty(v1)) {
                stk.clear();
                stk.push(!isEmpty(v2) ? v2 : v1);
              }
              else stk.push(null);
            }
            else {
              stk.op();
            }
          }
        }
        catch (ClassCastException e) {
          throw new CompileException("syntax error or incomptable types", new char[0], 0, e);
        }
        catch (CompileException e) {
          throw e;
        }
        catch (Exception e) {
          throw new CompileException("failed to compileShared sub expression", new char[0], 0, e);
        }
      }
      //这里继续循环的前提在于下一个节点必须不为null
      while ((tk = tk.nextASTNode) != null);

      //最终到达结尾，直接返回最后一个表达式的值
      return stk.peek();
    }
    catch (NullPointerException e) {
      if (tk != null && tk.isOperator() && tk.nextASTNode != null) {
        throw new CompileException("incomplete statement: "
            + tk.getName() + " (possible use of reserved keyword as identifier: " + tk.getName() + ")", tk.getExpr(), tk.getStart());
      }
      else {
        throw e;
      }
    }
    finally {
      OptimizerFactory.clearThreadAccessorOptimizer();
    }
  }

  /**
   * 注册相应的调试代码行,以支持在特定的运行时增加相应的调试信息
   * Register a debugger breakpoint.
   *
   * @param source - the source file the breakpoint is registered in
   * @param line   - the line number of the breakpoint
   */
  public static void registerBreakpoint(String source, int line) {
    ensureDebuggerContext();
    debuggerContext.get().registerBreakpoint(source, line);
  }

  /**
   * 移除相应的调试代码行
   * Remove a specific breakpoint.
   *
   * @param source - the source file the breakpoint is registered in
   * @param line   - the line number of the breakpoint to be removed
   */
  public static void removeBreakpoint(String source, int line) {
    if (hasDebuggerContext()) {
      debuggerContext.get().removeBreakpoint(source, line);
    }
  }

  /**
   * 读取当前是否存在调试信息
   * Tests whether or not a debugger context exist.
   *
   * @return boolean
   */
  public static boolean hasDebuggerContext() {
    return debuggerContext != null && debuggerContext.get() != null;
  }

  /**
   * 保证建立起相应的调试上下文
   * Ensures that debugger context exists.
   */
  private static void ensureDebuggerContext() {
    if (debuggerContext == null) debuggerContext = new ThreadLocal<DebuggerContext>();
    if (debuggerContext.get() == null) debuggerContext.set(new DebuggerContext());
  }

  /**
   * 清除所有的调试信息
   * Reset all the currently registered breakpoints.
   */
  public static void clearAllBreakpoints() {
    if (hasDebuggerContext()) {
      debuggerContext.get().clearAllBreakpoints();
    }
  }

  /**
   * 读取当前是否存在调试信息
   * Tests whether or not breakpoints have been declared.
   *
   * @return boolean
   */
  public static boolean hasBreakpoints() {
    return hasDebuggerContext() && debuggerContext.get().hasBreakpoints();
  }

  /**
   * 设置具体的调试器
   * Sets the Debugger instance to handle breakpoints.   A debugger may only be registered once per thread.
   * Calling this method more than once will result in the second and subsequent calls to simply fail silently.
   * To re-register the Debugger, you must call {@link #resetDebugger}
   *
   * @param debugger - debugger instance
   */
  public static void setThreadDebugger(Debugger debugger) {
    ensureDebuggerContext();
    debuggerContext.get().setDebugger(debugger);
  }

  /**
   * 清除相应的调试上下文信息
   * Reset all information registered in the debugger, including the actual attached Debugger and registered
   * breakpoints.
   */
  public static void resetDebugger() {
    debuggerContext = null;
  }
}
