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
import org.mvel2.ast.Substatement;
import org.mvel2.compiler.AbstractParser;
import org.mvel2.compiler.BlankLiteral;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.mvel2.util.ErrorUtil;
import org.mvel2.util.ExecutionStack;

import java.util.Map;

import static org.mvel2.Operator.*;


/**
 * 完成主要的以解释运行方式解析并处理运行表达式的逻辑
 * 在实现上，当前类仅借鉴了AbstractParser中的主要解析逻辑，但整个处理逻辑仍由当前类来完成.即由父类完成解析工作，当前类完成运算工作
 * The MVEL interpreted runtime, used for fast parse and execution of scripts.
 */
@SuppressWarnings({"CaughtExceptionImmediatelyRethrown"})
public class MVELInterpretedRuntime extends AbstractParser {

  /** 进行主要的解析和解释运算工作 */
  public Object parse() {
    try {
      stk = new ExecutionStack();
      dStack = new ExecutionStack();
      variableFactory.setTiltFlag(false);
      cursor = start;
      return parseAndExecuteInterpreted();
    }
    catch (ArrayIndexOutOfBoundsException e) {
      e.printStackTrace();
      throw new CompileException("unexpected end of statement", expr, length);
    }
    catch (NullPointerException e) {
      e.printStackTrace();

      if (cursor >= length) {
        throw new CompileException("unexpected end of statement", expr, length);
      }
      else {
        throw e;
      }
    }
    catch (CompileException e) {
      throw ErrorUtil.rewriteIfNeeded(e, expr, cursor);
    }
  }

  /** 临时持有相应的结果信息,即在每一个语句执行完当前语句的结果值 */
  private Object holdOverRegister;

  /**
   * 执行主要的解析和运算逻辑
   * Main interpreter loop.
   *
   * @return value
   */
  private Object parseAndExecuteInterpreted() {
    ASTNode tk = null;
    int operator;
    //重置相应的逻辑
    lastWasIdentifier = false;

    try {
      while ((tk = nextToken()) != null) {
        //重置记，表示还没有结果
        holdOverRegister = null;

        //因为当前节点需要被费掉，并且当前也是变量节点，即表示相应的数据已经被放入栈中，这里从栈中去除掉，不再进行处理
        if (lastWasIdentifier && lastNode.isDiscard()) {
          stk.discard();
        }

        /**
         * If we are at the beginning of a statement, then we immediately push the first token
         * onto the stack.
         */
        //这里表示当前执行栈是空的，因此可以马上将相应的数据放入栈中，以便继续运算
        if (stk.isEmpty()) {
          //stacklang节点,即可以直接执行,并且因为它是独立的运算单元，则可以继续进行栈减操作，以按照优先级的顺序进行处理
          if ((tk.fields & ASTNode.STACKLANG) != 0) {
            stk.push(tk.getReducedValue(stk, ctx, variableFactory));
            Object o = stk.peek();
            if (o instanceof Integer) {
              arithmeticFunctionReduction((Integer) o);
            }
          }
          else {
            stk.push(tk.getReducedValue(ctx, ctx, variableFactory));
          }

          /**
           * If this is a substatement, we need to move the result into the d-stack to preserve
           * proper execution order.
           */
          //这里表示当前处理的是一个独立的运算单元，那么即可能和后面的执行一块执行以处理相应的优先级顺序
          //并且这里tk的值已经处理掉了，因此如果下一个节点为操作符,那么就和再下一个节点一直进行处理
          if (tk instanceof Substatement && (tk = nextToken()) != null) {
            //如果接下来是运算操作符，则放入下一个值，并且开始执行栈操作数递减处理
            if (isArithmeticOperator(operator = tk.getOperator())) {
              //将下一个值和当前操作符压入栈中,主栈操作，采用后缀压入
              stk.push(nextToken().getReducedValue(ctx, ctx, variableFactory), operator);

              //如果返回值表示提前终止 OP_TERMINATE，就跳出处理
              if (procBooleanOperator(arithmeticFunctionReduction(operator)) == -1)
                return stk.peek();
              else
                continue;
            }
          }
          else {
            continue;
          }
        }

        //当前变量工厂提前结束
        if (variableFactory.tiltFlag()) {
          return stk.pop();
        }

        switch (procBooleanOperator(operator = tk.getOperator())) {
          case RETURN:
            variableFactory.setTiltFlag(true);
            return stk.pop();
          case OP_TERMINATE:
            return stk.peek();
          case OP_RESET_FRAME:
            continue;
            //这里的OP_OVERFLOW 实际上为相应的返回值为 NOOP
          case OP_OVERFLOW:
            //如果不是操作符，则仅认为当前存储的是一个类型信息，即相应的类型声明
            if (!tk.isOperator()) {
              if (!(stk.peek() instanceof Class)) {
                throw new CompileException("unexpected token or unknown identifier:" + tk.getName(), expr, st);
              }
              variableFactory.createVariable(tk.getName(), null, (Class) stk.peek());
            }
            //如果为操作符，则忽略此操作符
            continue;
        }

        //前面返回了OP_CONTINUE,因此继续执行

        stk.push(nextToken().getReducedValue(ctx, ctx, variableFactory), operator);

        switch ((operator = arithmeticFunctionReduction(operator))) {
          case OP_TERMINATE:
            return stk.peek();
          case OP_RESET_FRAME:
            continue;
        }

        if (procBooleanOperator(operator) == OP_TERMINATE) return stk.peek();
      }

      if (holdOverRegister != null) {
        return holdOverRegister;
      }
    }
    catch (CompileException e) {
      throw ErrorUtil.rewriteIfNeeded(e, expr, start);
    }
    catch (NullPointerException e) {
      if (tk != null && tk.isOperator()) {
        CompileException ce = new CompileException("incomplete statement: "
            + tk.getName() + " (possible use of reserved keyword as identifier: " + tk.getName() + ")"
            , expr, st, e);

        ce.setExpr(expr);
        ce.setLineNumber(line);
        ce.setCursor(cursor);
        throw ce;
      }
      else {
        throw e;
      }
    }
    return stk.peek();
  }

  /**
   *  根据当前的操作符在栈中处理相应的boolean类型操作，即根据当前的操作符决定下一步的操作处理
   *
   * 如果返回-1，则表示已经不可以再进行处理了
   * */
  private int procBooleanOperator(int operator) {
    switch (operator) {
      //操作符为return，表示需要直接返回
      case RETURN:
        return RETURN;
      //不作任何处理，继续执行
      case NOOP:
        return -2;

      //如果是and语句，那么根据当前栈中的值决定是否执行后面的语句
      case AND:
        reduceRight();

        //如果碰到and，并且当前为false，则考虑丢弃后面的语句
        if (!stk.peekBoolean()) {
          //这里表示整个语句执行结束，因此提前返回
          if (unwindStatement(operator)) {
            return -1;
          }
          //当前碰到end 或 or，则表示,当前桢执行结束
          else {
            stk.clear();
            return OP_RESET_FRAME;
          }
        }
        //当前值为true,丢掉当前值，继续执行
        else {
          stk.discard();
          return OP_RESET_FRAME;
        }

      case OR:
        reduceRight();

        //当前值为true,那么就需要丢弃后面的语句
        if (stk.peekBoolean()) {
          //已经丢弃完毕，直接返回
          if (unwindStatement(operator)) {
            return OP_TERMINATE;
          }
          //后面还有语句，需要继续执行
          else {
            stk.clear();
            return OP_RESET_FRAME;
          }
        }
        //当前值为false，那么继续执行
        else {
          stk.discard();
          return OP_RESET_FRAME;
        }

        //数据选择，如果当前已经有值了，这里直接提前终止并返回
        //经测试，在 a= 1; a or 4; return 2 这个表达式中，整个eval会直接返回1,而并不是相要的结果2,因此可以认为这里的chor并不严谨
      case CHOR:
        if (!BlankLiteral.INSTANCE.equals(stk.peek())) {
          return OP_TERMINATE;
        }
        break;

      //碰到 ? 操作，那么，如果 根据当前的值的结果决定是否计算下一个指令还是跳过 : 后面去
      case TERNARY:
        //当前返回false，则表示要跳到 :
        if (!stk.popBoolean()) {
          stk.clear();

          ASTNode tk;

          //这里的执行节点直接跳过相应的 : 处，但是由于在上层的调用中，会直接丢掉当前节点，可以认为直接跳到 : 后面的执行语句中
          for (; ; ) {
            if ((tk = nextToken()) == null || tk.isOperator(Operator.TERNARY_ELSE))
              break;
          }
        }

        return OP_RESET_FRAME;

      //碰到 : ，这里只地表示 在执行 ? 后面表达式之后再碰到 :，因此这里直接丢弃当前节点，并且连后面的 ： 后面的表达式一并丢弃
      case TERNARY_ELSE:
        captureToEOS();
        return OP_RESET_FRAME;

      //磁到 ;表示当前语句已经执行完毕
      case END_OF_STMT:
        /**
         * Assignments are a special scenario for dealing with the stack.  Assignments are basically like
         * held-over failures that basically kickstart the parser when an assignment operator is is
         * encountered.  The originating token is captured, and the the parser is told to march on.  The
         * resultant value on the stack is then used to populate the target variable.
         *
         * The other scenario in which we don't want to wipe the stack, is when we hit the end of the
         * statement, because that top stack value is the value we want back from the parser.
         */

        //有更多的执行语句，因此将当前结果给记下来
        if (hasMore()) {
          holdOverRegister = stk.pop();
          stk.clear();
        }

        return OP_RESET_FRAME;
    }

    //表示当前表达式还需要继续处理
    return OP_CONTINUE;
  }

  /**
   * This method peforms the equivilent of an XSWAP operation to flip the operator
   * over to the top of the stack, and loads the stored values on the d-stack onto
   * the main program stack.
   * 这里将辅助栈里面的暂存数据重新放到入主栈中，并进行相应的计算处理
   */
  private void reduceRight() {
    if (dStack.isEmpty()) return;

    Object o = stk.pop();
    stk.push(dStack.pop(), o, dStack.pop());

    reduce();
  }

  /** 是否存在更多操作数 */
  private boolean hasMore() {
    return cursor <= end;
  }

  /**
   * This method is called to unwind the current statement without any reduction or further parsing.
   * 这里表示因为之前的操作返回false(true) true(or) 那么应该直接丢弃接下来的语句。
   * 针对and，会丢弃接下来与and同一优先级的语句
   *
   * 如果碰到end(and的话，碰到or也可以提前返回)，就表示可以提前返回了
   *
   * @param operator -
   * @return -
   */
  private boolean unwindStatement(int operator) {
    ASTNode tk;

    switch (operator) {
      case AND:
        while ((tk = nextToken()) != null && !tk.isOperator(Operator.END_OF_STMT) && !tk.isOperator(Operator.OR)) {
          //nothing
        }
        break;
      default:
        while ((tk = nextToken()) != null && !tk.isOperator(Operator.END_OF_STMT)) {
          //nothing
        }
    }
    return tk == null;
  }

  MVELInterpretedRuntime(char[] expression, Object ctx, Map<String, Object> variables) {
    this.expr = expression;
    this.length = expr.length;
    this.ctx = ctx;
    this.variableFactory = new MapVariableResolverFactory(variables);
  }

  MVELInterpretedRuntime(char[] expression, Object ctx) {
    this.expr = expression;
    this.length = expr.length;
    this.ctx = ctx;
    this.variableFactory = new ImmutableDefaultFactory();
  }


  MVELInterpretedRuntime(String expression) {
    setExpression(expression);
    this.variableFactory = new ImmutableDefaultFactory();
  }

  MVELInterpretedRuntime(char[] expression) {
    this.length = end = (this.expr = expression).length;
  }

  public MVELInterpretedRuntime(char[] expr, Object ctx, VariableResolverFactory resolverFactory) {
    this.length = end = (this.expr = expr).length;
    this.ctx = ctx;
    this.variableFactory = resolverFactory;
  }

  public MVELInterpretedRuntime(char[] expr, int start, int offset, Object ctx, VariableResolverFactory resolverFactory) {
    this.expr = expr;
    this.start = start;
    this.end = start + offset;
    this.length = end - start;
    this.ctx = ctx;
    this.variableFactory = resolverFactory;
  }

  public MVELInterpretedRuntime(char[] expr, int start, int offset, Object ctx, VariableResolverFactory resolverFactory, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.end = start + offset;
    this.length = end - start;
    this.ctx = ctx;
    this.variableFactory = resolverFactory;
  }

  public MVELInterpretedRuntime(String expression, Object ctx, VariableResolverFactory resolverFactory) {
    setExpression(expression);
    this.ctx = ctx;
    this.variableFactory = resolverFactory;
  }

  public MVELInterpretedRuntime(String expression, Object ctx, VariableResolverFactory resolverFactory, ParserContext pCtx) {
    super(pCtx);
    setExpression(expression);
    this.ctx = ctx;
    this.variableFactory = resolverFactory;
  }

  MVELInterpretedRuntime(String expression, VariableResolverFactory resolverFactory) {
    setExpression(expression);
    this.variableFactory = resolverFactory;
  }

  MVELInterpretedRuntime(String expression, Object ctx) {
    setExpression(expression);
    this.ctx = ctx;
    this.variableFactory = new ImmutableDefaultFactory();
  }
}

