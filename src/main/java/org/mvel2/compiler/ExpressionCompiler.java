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

package org.mvel2.compiler;

import org.mvel2.*;
import org.mvel2.ast.*;
import org.mvel2.util.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.Operator.PTABLE;
import static org.mvel2.ast.ASTNode.COMPILE_IMMEDIATE;
import static org.mvel2.ast.ASTNode.OPT_SUBTR;
import static org.mvel2.util.CompilerTools.finalizePayload;
import static org.mvel2.util.CompilerTools.signNumber;
import static org.mvel2.util.ParseTools.subCompileExpression;
import static org.mvel2.util.ParseTools.unboxPrimitive;

/**
 * 描述相应的表达式编译器
 * This is the main MVEL compiler.
 */
public class ExpressionCompiler extends AbstractParser {
  /** 当前编译表达式返回的类型 */
  private Class returnType;

  /** 是否只是验证表达式是否合法 */
  private boolean verifyOnly = false;
  /** 表示当前是否正在验证中 */
  private boolean verifying = true;
  /** 是否需要二次优化(即去and操作) */
  private boolean secondPassOptimization = false;

  /** 主要的编译操作，返回编译表达式 */
  public CompiledExpression compile() {
    try {
      this.debugSymbols = pCtx.isDebugSymbols();
      return _compile();
    }
    finally {
      //如果有严重的编译错误，报相应的异常
      //当前很少会有严重错误，因此一般情况下都不会走到这里来，而是直接throw出相应的异常
      if (pCtx.isFatalError()) {
        StringAppender err = new StringAppender();

        Iterator<ErrorDetail> iter = pCtx.getErrorList().iterator();
        ErrorDetail e;
        while (iter.hasNext()) {
          e = iter.next();

          e = ErrorUtil.rewriteIfNeeded(e, expr, cursor);

          if (e.getExpr() != expr) {
            iter.remove();
          }
          else {
            err.append("\n - ").append("(").append(e.getLineNumber()).append(",").append(e.getColumn()).append(")")
                .append(" ").append(e.getMessage());
          }
        }

        //noinspection ThrowFromFinallyBlock
        throw new CompileException("Failed to compileShared: " + pCtx.getErrorList().size()
            + " compilation error(s): " + err.toString(), pCtx.getErrorList(), expr, cursor, pCtx);
      }
    }

  }

  /**
   * 进行实际的编译操作
   * 在整个过程中stk栈作为常量处理栈，辅助进行节点链的创建
   * Initiate an in-context compileShared.  This method should really only be called by the internal API.
   *
   * @return compiled expression object
   */
  public CompiledExpression _compile() {
    ASTNode tk;
    ASTNode tkOp;
    ASTNode tkOp2;
    ASTNode tkLA;
    ASTNode tkLA2;

    int op, lastOp = -1;
    cursor = start;

    ASTLinkedList astBuild = new ASTLinkedList();
    stk = new ExecutionStack();
    dStack = new ExecutionStack();
    //开启编译状态
    compileMode = true;

    boolean firstLA;

    try {
      if (verifying) {
        pCtx.initializeTables();
      }

      //首先表示当前为编译阶段，以便各个阶段进行信息编译，即状态值调整
      fields |= COMPILE_IMMEDIATE;

      while ((tk = nextToken()) != null) {
        /**
         * 调试节点？继续处理,同时加入到处理链中
         * If this is a debug symbol, just add it and continue.
         */
        if (tk.fields == -1) {
          astBuild.addTokenNode(tk);
          continue;
        }

        /**
         * Record the type of the current node..
         */
        returnType = tk.getEgressType();

        //单独处理子运行程序
        if (tk instanceof Substatement) {
          String key = new String(expr, tk.getStart(), tk.getOffset());
          Map<String, CompiledExpression> cec = pCtx.getCompiledExpressionCache();
          Map<String, Class> rtc = pCtx.getReturnTypeCache();
          CompiledExpression compiled = cec.get(key);
          Class rt = rtc.get(key);
          //子程序单独进行编译,以保证相应的程序都已经成功的进行了解析
          //但实际上没有什么特别的用处,因此subStatement的执行不是通过accessor来保证的,而是由statement处理
          if (compiled == null) {
            ExpressionCompiler subCompiler = new ExpressionCompiler(expr, tk.getStart(), tk.getOffset(), pCtx);
            compiled = subCompiler._compile();
            rt = subCompiler.getReturnType();
            cec.put(key, compiled);
            rtc.put(key, rt);
          }
          tk.setAccessor(compiled);
          returnType = rt;
        }

        /**
         * 这下面的动作在于进行编译期优化，以减少运行期的信息处理
         * 即当前节点是一个常量节点(可能是数字),如果下一个常量类似于 1 + 2 这种,则可以优化
         * This kludge of code is to handle compileShared-time literal reduction.  We need to avoid
         * reducing for certain literals like, 'this', ternary and ternary else.
         */
        if (!verifyOnly && tk.isLiteral()) {
          if (literalOnly == -1) literalOnly = 1;

          //下一个符号为操作符节点
          if ((tkOp = nextTokenSkipSymbols()) != null && tkOp.isOperator()
              && !tkOp.isOperator(Operator.TERNARY) && !tkOp.isOperator(Operator.TERNARY_ELSE)) {

            /**
             * 如果操作符之后的节点是常量节点 则表示可以编译期优化
             * If the next token is ALSO a literal, then we have a candidate for a compileShared-time literal
             * reduction.
             */
            //同时这里的当前操作符的优先级比上次的更高，因此可以先执行
            if ((tkLA = nextTokenSkipSymbols()) != null && tkLA.isLiteral()
                && tkOp.getOperator() < 34 && ((lastOp == -1
                || (lastOp < PTABLE.length && PTABLE[lastOp] < PTABLE[tkOp.getOperator()])))) {
              //先将其推入栈中,以方便计算处理,采用后缀表示法,以方便进行栈式判断和处理
              stk.push(tk.getLiteralValue(), tkLA.getLiteralValue(), op = tkOp.getOperator());

              /**
               * 如果是算术运算,则表示是基于的数学处理,则需要根据当前栈以及节点的进一步信息进行处理
               * Reduce the token now.
               */
              if (isArithmeticOperator(op)) {
                //如果不能继续优化，则继续进行处理
                if (!compileReduce(op, astBuild)) continue;
              }
              //这里表示不是基本运算,则进行其它处理,比如 1 < 2这种或者是 true || false这种操作
              //因为是常量，因此可以直接进行递减处理
              else {
                reduce();
              }

              //todo 这里的firstLA不明白是什么意思...
              firstLA = true;

              /**
               * Now we need to check to see if this is a continuing reduction.
               */
              //这里接下来的数据仍是操作符，因此如果不是操作符，已经在上面进行了相应的continue操作
              while ((tkOp2 = nextTokenSkipSymbols()) != null) {
                //如果是bool操作节点，因此优先级很低，因此添加到表达式链中
                if (isBooleanOperator(tkOp2.getOperator())) {
                  astBuild.addTokenNode(new LiteralNode(stk.pop(), pCtx), verify(pCtx, tkOp2));
                  break;
                }
                else if ((tkLA2 = nextTokenSkipSymbols()) != null) {

                  //如果接下来的数据仍是常量，则仍继续进行常量递增处理
                  if (tkLA2.isLiteral()) {
                    stk.push(tkLA2.getLiteralValue(), op = tkOp2.getOperator());

                    if (isArithmeticOperator(op)) {
                      compileReduce(op, astBuild);
                    }
                    else {
                      reduce();
                    }
                  }
                  //不是常量，因此需要将上次的数据重新加入到执行链当中,这里的stk 作为一个数据处理链来使用
                  else {
                    /**
                     * A reducable line of literals has ended.  We must now terminate here and
                     * leave the rest to be determined at runtime.
                     */
                    if (!stk.isEmpty()) {
                      astBuild.addTokenNode(new LiteralNode(getStackValueResult(), pCtx));
                    }

                    astBuild.addTokenNode(new OperatorNode(tkOp2.getOperator(), expr, st, pCtx), verify(pCtx, tkLA2));
                    break;
                  }

                  firstLA = false;
                  //因为不再是常量，因此设置相应的标记信息
                  literalOnly = 0;
                }
                else {
                  if (firstLA) {
                    /**
                     * There are more tokens, but we can't reduce anymore.  So
                     * we create a reduced token for what we've got.
                     */
                    astBuild.addTokenNode(new LiteralNode(getStackValueResult(), pCtx));
                  }
                  else {
                    /**
                     * We have reduced additional tokens, but we can't reduce
                     * anymore.
                     */
                    astBuild.addTokenNode(new LiteralNode(getStackValueResult(), pCtx), tkOp2);

                    if (tkLA2 != null) astBuild.addTokenNode(verify(pCtx, tkLA2));
                  }

                  break;
                }
              }

              /**
               * If there are no more tokens left to parse, we check to see if
               * we've been doing any reducing, and if so we create the token
               * now.
               */
              //这里表示已经没有更多的节点要处理，因此把相应的栈中的数据拿出来，声明为常量节点
              if (!stk.isEmpty())
                astBuild.addTokenNode(new LiteralNode(getStackValueResult(), pCtx));

              continue;
            }
            else {
              astBuild.addTokenNode(verify(pCtx, tk), verify(pCtx, tkOp));
              if (tkLA != null) astBuild.addTokenNode(verify(pCtx, tkLA));
              continue;
            }
          }
          else if (tkOp != null && !tkOp.isOperator() && !(tk.getLiteralValue() instanceof Class)) {
            throw new CompileException("unexpected token: " + tkOp.getName(), expr, tkOp.getStart());
          }
          else {
            literalOnly = 0;
            astBuild.addTokenNode(verify(pCtx, tk));
            if (tkOp != null) astBuild.addTokenNode(verify(pCtx, tkOp));
            continue;
          }
        }
        else {
          if (tk.isOperator()) {
            lastOp = tk.getOperator();
          }
          else {
            literalOnly = 0;
          }
        }

        astBuild.addTokenNode(verify(pCtx, tk));
      }

      astBuild.finish();

      //这里表示已经验证完毕了，因此将相应的变量信息去除
      if (verifying && !verifyOnly) {
        pCtx.processTables();
      }

      if (!stk.isEmpty()) {
        throw new CompileException("COMPILE ERROR: non-empty stack after compileShared.", expr, cursor);
      }

      //如果并不仅仅是验证,还需要进一步优化，因此进行相应的优化操作
      if (!verifyOnly) {
        return new CompiledExpression(finalizePayload(astBuild, secondPassOptimization, pCtx), pCtx.getSourceFile(), returnType, pCtx.getParserConfiguration(), literalOnly == 1);
      }
      //仅验证，因此这里分析出相应的返回类型，直接返回null
      else {
        try {
          returnType = CompilerTools.getReturnType(astBuild, pCtx.isStrongTyping());
        } catch (RuntimeException e) {
          throw new CompileException(e.getMessage(), expr, st, e);
        }
        return null;
      }
    }
    catch (NullPointerException e) {
      throw new CompileException("not a statement, or badly formed structure", expr, st, e);
    }
    catch (CompileException e) {
      throw ErrorUtil.rewriteIfNeeded(e, expr, st);
    }
    catch (Throwable e) {
      if (e instanceof RuntimeException) throw (RuntimeException) e;
      else {
        throw new CompileException(e.getMessage(), expr, st, e);
      }
    }
  }

  /** 获取当前栈中的值，如果有取反，则进行取反 */
  private Object getStackValueResult() {
    return (fields & OPT_SUBTR) == 0 ? stk.pop() : signNumber(stk.pop());
  }

  /** 根据当前操作码以及节点信息进行进一步处理 */
  private boolean compileReduce(int opCode, ASTLinkedList astBuild) {
    //以下的switch里面，证明是有bug的，即实际上并不知道应该如何继续往下执行
    //相应的实现与注释并不相同
    switch (arithmeticFunctionReduction(opCode)) {
      //这里的值为-1,实际上注解所要求的返回值应该是 OP_OVERFLOW 即-2
      case -1:
        //以下按照-2的执行执行逻辑，即表示,当前碰到非常量值，并且相应的临时数据被放入了split栈中，因此这里将其提取出来
        //并且按照中缀的方式，即
        /**
         * The reduction failed because we encountered a non-literal,
         * so we must now back out and cleanup.
         */

        stk.xswap_op();

        astBuild.addTokenNode(new LiteralNode(stk.pop(), pCtx));
        //这里从相应的分栈中重新拿出相应的节点信息并放到节点链中来，这里保证了了相应的 a + b的顺序
        astBuild.addTokenNode(
            (OperatorNode) splitAccumulator.pop(),
            verify(pCtx, (ASTNode) splitAccumulator.pop())
        );
        return false;
      //这里的-1,不知道本意是多少，实际上即使是 -2,也是不正确的
      //如编译 1 + 2 * 3 > 10 and false，在实际中stack的2次pop也会报NPE，因此以下的处理并不了解是什么意思
      case -2:
        /**
         * Back out completely, pull everything back off the stack and add the instructions
         * to the output payload as they are.
         */

        LiteralNode rightValue = new LiteralNode(stk.pop(), pCtx);
        OperatorNode operator = new OperatorNode((Integer) stk.pop(), expr, st, pCtx);

        astBuild.addTokenNode(new LiteralNode(stk.pop(), pCtx), operator);
        astBuild.addTokenNode(rightValue, (OperatorNode) splitAccumulator.pop());
        astBuild.addTokenNode(verify(pCtx, (ASTNode) splitAccumulator.pop()));
    }
    return true;
  }

  /** 判定相应的操作符是否是bool操作符 */
  private static boolean isBooleanOperator(int operator) {
    return operator == Operator.AND || operator == Operator.OR || operator == Operator.TERNARY || operator == Operator.TERNARY_ELSE;
  }

  /** 验证节点并设置相应的信息，同时返回该节点 */
  protected ASTNode verify(ParserContext pCtx, ASTNode tk) {
    if (tk.isOperator() && (tk.getOperator().equals(Operator.AND) || tk.getOperator().equals(Operator.OR))) {
      secondPassOptimization = true;
    }
    if (tk.isDiscard() || tk.isOperator()) {
      return tk;
    }
    //将常量节点进行调整，直接转换为常量节点，这样更简化一些判定处理
    else if (tk.isLiteral()) {
      /**
       * 如果当前节点是常量节点(可能为ASTNode，则强制转换为LiteralNode节点，以避免再操作astNode)
       * Convert literal values from the default ASTNode to the more-efficient LiteralNode.
       */
      if ((fields & COMPILE_IMMEDIATE) != 0 && tk.getClass() == ASTNode.class) {
        return new LiteralNode(tk.getLiteralValue(), pCtx);
      }
      else {
        return tk;
      }
    }

    //如果需要校验相应的节点信息，因此尝试进行校验
    if (verifying) {
      if (tk.isIdentifier()) {
        //使用相应的校验器进行属性校验
        PropertyVerifier propVerifier = new PropertyVerifier(expr, tk.getStart(), tk.getOffset(), pCtx);

        //如果是联合，则使用相应的联合的主节点类型作为上下文进行分析
        if (tk instanceof Union) {
          propVerifier.setCtx(((Union) tk).getLeftEgressType());
          tk.setEgressType(returnType = propVerifier.analyze());
        }
        else {
          //重新设置分析的返回类型
          tk.setEgressType(returnType = propVerifier.analyze());

          //设置相应的静态调用标识
          if (propVerifier.isFqcn()) {
            tk.setAsFQCNReference();
          }

          //如果分析出当前节点是一个常量类型节点，则替换节点定义
          if (propVerifier.isClassLiteral()) {
            return new LiteralNode(returnType, pCtx);
          }
          //如果分析出当前节点是输入属性，则将相应的属性加入到解析上下文中
          if (propVerifier.isInput()) {
            pCtx.addInput(tk.getAbsoluteName(), propVerifier.isDeepProperty() ? Object.class : returnType);
          }

          //分析结果不是方法调用，即表示不能分析出此结果，因此直接报错
          if (!propVerifier.isMethodCall() && !returnType.isEnum() && !pCtx.isOptimizerNotified() &&
                  pCtx.isStrongTyping() && !pCtx.isVariableVisible(tk.getAbsoluteName()) && !tk.isFQCN()) {
            throw new CompileException("no such identifier: " + tk.getAbsoluteName(), expr, tk.getStart());
          }
        }
      }
      //节点类型为赋值节点
      else if (tk.isAssignment()) {
        Assignment a = (Assignment) tk;

        if (a.getAssignmentVar() != null) {
          //    pCtx.makeVisible(a.getAssignmentVar());

          PropertyVerifier propVerifier = new PropertyVerifier(a.getAssignmentVar(), pCtx);
          tk.setEgressType(returnType = propVerifier.analyze( ));

          //此节点不是新建节点，则表示是之前已经建的节点，并且是由外部解析的，这里加入到解析上下文中，表示有此变量信息
          if (!a.isNewDeclaration() && propVerifier.isResolvedExternally()) {
            pCtx.addInput(tk.getAbsoluteName(), returnType);
          }

          ExecutableStatement c = (ExecutableStatement) subCompileExpression(expr, tk.getStart(),
              tk.getOffset(), pCtx);

          //如果是严格类型调用，这里尝试对类型进行判定
          if (pCtx.isStrictTypeEnforcement()) {
            /**
             * If we're using strict type enforcement, we need to see if this coercion can be done now,
             * or fail epicly.
             */
            //这里如果相应的分析类型和相应的声明类型之间不能兼容，并且相应的节点为常量(即常量的类型是确定的)
            if (!returnType.isAssignableFrom(c.getKnownEgressType()) && c.isLiteralOnly()) {
              //如果能进行转换，则直接更换为一个正确类型的常量节点
              if (canConvert(c.getKnownEgressType(), returnType)) {
                /**
                 * We convert the literal to the proper type.
                 */
                try {
                  a.setValueStatement(new ExecutableLiteral(convert(c.getValue(null, null), returnType)));
                  return tk;
                }
                catch (Exception e) {
                  // fall through.
                }
              }
              //处理基本类型之间的转换
              else if (returnType.isPrimitive()
                  && unboxPrimitive(c.getKnownEgressType()).equals(returnType)) {
                /**
                 * We ignore boxed primitive cases, since MVEL does not recognize primitives.
                 */
                return tk;
              }

              //这里表示不能进行转换，则直接报错
              throw new CompileException(
                  "cannot assign type " + c.getKnownEgressType().getName()
                      + " to " + returnType.getName(), expr, st);
            }
          }
        }
      }
      //这里是新建对象节点，则对每一个新建节点的内部参数进行验证
      else if (tk instanceof NewObjectNode) {
        // this is a bit of a hack for now.
        NewObjectNode n = (NewObjectNode) tk;
        List<char[]> parms = ParseTools.parseMethodOrConstructor(tk.getNameAsArray());
        if (parms != null) {
          for (char[] p : parms) {
            MVEL.analyze(p, pCtx);
          }
        }
      }
      returnType = tk.getEgressType();
    }

    //当前节点并不是常量节点，因此相应的常量值可以运用起来，因此这里将相应的解析上下文存储在常量值当中，以便于后面进行优化执行时，直接采用此上下文
    //而避免重新进行创建
    if (!tk.isLiteral() && tk.getClass() == ASTNode.class && (tk.getFields() & ASTNode.ARRAY_TYPE_LITERAL) == 0) {
      if (pCtx.isStrongTyping()) tk.strongTyping();
      tk.storePctx();
      tk.storeInLiteralRegister(pCtx);
    }

    return tk;
  }

  public ExpressionCompiler(String expression) {
    setExpression(expression);
  }

  public ExpressionCompiler(String expression, boolean verifying) {
    setExpression(expression);
    this.verifying = verifying;
  }

  public ExpressionCompiler(char[] expression) {
    setExpression(expression);
  }

  public ExpressionCompiler(String expression, ParserContext ctx) {
    setExpression(expression);
    this.pCtx = ctx;
  }

  public ExpressionCompiler(char[] expression, int start, int offset) {
    this.expr = expression;
    this.start = start;
    this.end = start + offset;
    this.end = trimLeft(this.end);
    this.length = this.end - start;
  }

  public ExpressionCompiler(String expression, int start, int offset, ParserContext ctx) {
    this.expr = expression.toCharArray();
    this.start = start;
    this.end = start + offset;
    this.end = trimLeft(this.end);
    this.length = this.end - start;
    this.pCtx = ctx;
  }

  public ExpressionCompiler(char[] expression, int start, int offset, ParserContext ctx) {
    this.expr = expression;
    this.start = start;
    this.end = start + offset;
    this.end = trimLeft(this.end);
    this.length = this.end - start;
    this.pCtx = ctx;
  }

  public ExpressionCompiler(char[] expression, ParserContext ctx) {
    setExpression(expression);
    this.pCtx = ctx;
  }

  public boolean isVerifying() {
    return verifying;
  }

  public void setVerifying(boolean verifying) {
    this.verifying = verifying;
  }

  public boolean isVerifyOnly() {
    return verifyOnly;
  }

  public void setVerifyOnly(boolean verifyOnly) {
    this.verifyOnly = verifyOnly;
  }

  public Class getReturnType() {
    return returnType;
  }

  public void setReturnType(Class returnType) {
    this.returnType = returnType;
  }

  public ParserContext getParserContextState() {
    return pCtx;
  }

  /** 当前表达式是否仅是常量 */
  public boolean isLiteralOnly() {
    return literalOnly == 1;
  }
}
