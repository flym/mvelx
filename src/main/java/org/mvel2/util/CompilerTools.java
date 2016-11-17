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
import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.ast.ASTNode;
import org.mvel2.ast.And;
import org.mvel2.ast.BinaryOperation;
import org.mvel2.ast.BooleanNode;
import org.mvel2.ast.Contains;
import org.mvel2.ast.Convertable;
import org.mvel2.ast.DeclTypedVarNode;
import org.mvel2.ast.Function;
import org.mvel2.ast.Instance;
import org.mvel2.ast.IntAdd;
import org.mvel2.ast.IntDiv;
import org.mvel2.ast.IntMult;
import org.mvel2.ast.IntOptimized;
import org.mvel2.ast.IntSub;
import org.mvel2.ast.LiteralNode;
import org.mvel2.ast.Or;
import org.mvel2.ast.RegExMatchNode;
import org.mvel2.ast.Soundslike;
import org.mvel2.ast.Strsim;
import org.mvel2.compiler.Accessor;
import org.mvel2.compiler.BlankLiteral;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExecutableAccessor;
import org.mvel2.compiler.ExecutableLiteral;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ClassImportResolverFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mvel2.Operator.PTABLE;
import static org.mvel2.util.ASTBinaryTree.buildTree;
import static org.mvel2.util.ParseTools.__resolveType;
import static org.mvel2.util.ParseTools.boxPrimitive;

/** 各种编译工具，对节点链和各种操作符进行优化处理工作 */
public class CompilerTools {
  /**
   * 进行节点优化,包括运算节点转换，常量节点优化替换等
   * Finalize the payload, by reducing any stack-based-operations to dedicated nodes where possible.
   *
   * @param astLinkedList          - AST to be optimized.
   * @param secondPassOptimization - perform a second pass optimization to optimize boolean expressions.
   * @param pCtx                   - The parser context
   * @return optimized AST
   */
  public static ASTLinkedList finalizePayload(ASTLinkedList astLinkedList, boolean secondPassOptimization, ParserContext pCtx) {
    ASTLinkedList optimizedAst = new ASTLinkedList();
    //操作对象A,操作符1,操作符2
    ASTNode tk, tkOp, tkOp2;

    /**
     * Re-process the AST and optimize it.
     */
    while (astLinkedList.hasMoreNodes()) {
      //特定节点
      if ((tk = astLinkedList.nextNode()).getFields() == -1) {
        optimizedAst.addTokenNode(tk);
      }
      //非末尾节点
      else if (astLinkedList.hasMoreNodes()) {
        //继续特定节点
        if ((tkOp = astLinkedList.nextNode()).getFields() == -1) {
          optimizedAst.addTokenNode(tk, tkOp);
        }
        //小于21,表示2个对象操作
        else if (tkOp.isOperator() && tkOp.getOperator() < 21) {
          int op = tkOp.getOperator();//操作符1
          int op2;//操作符2

          if (op == -1) {
            throw new CompileException("illegal use of operator: " + tkOp.getName(), tkOp.getExpr(), tk.getStart());
          }


          //操作数2
          ASTNode tk2 = astLinkedList.nextNode();
          BinaryOperation bo;

          //如果2个都是数字，则直接进行内联优化,减少普通的运算类型判断
          if (tk.getEgressType() == Integer.class && tk2.getEgressType() == Integer.class) {
            bo = boOptimize(op, tk, tk2, pCtx);
          }
          else {
            /**
             * 以下逻辑用于解决 二进制操作和加减法优先级问题
             * Let's see if we can simply the expression more.
             */
            bo = null;

            //解决减法的问题，更换为 + -x
            boolean inv = tkOp.isOperator(Operator.SUB);
            //处理加减法优先级问题,即是否要先计算后面的,这里的reduc即表示要先计算后面的数据
            boolean reduc = isReductionOpportunity(tkOp, tk2);
            boolean p_inv = false;

            //因为这里不涉及到 乘除法，因此不需要有过度计算的问题，这里的reduc仅表示加减法
            while (reduc) {
              //下一个操作符
              ASTNode oper = astLinkedList.nextNode();
              ASTNode rightNode = astLinkedList.nextNode();

              //已经没有后面的节点，则跳出相应的处理
              if (rightNode == null) break;

              //解析出实际的右则的表达式
              Object val = new BinaryOperation(oper.getOperator(), inv ?
                  new LiteralNode(signNumber(tk2.getLiteralValue()), pCtx) : tk2, rightNode, pCtx)
                  .getReducedValueAccelerated(null, null, null);

              //这里表示后面的运算结果为0,则忽略此2个节点
              if (!astLinkedList.hasMoreNodes() && BlankLiteral.INSTANCE.equals(val)) {
                optimizedAst.addTokenNode(tk);
                continue;
              }

              //已经继续完，继续执行
              reduc = astLinkedList.hasMoreNodes()
                  && (reducacbleOperator(astLinkedList.peekNode().getOperator()))
                  && astLinkedList.peekNext().isLiteral();

              //这个地方因为如果前面是-号，那么在不继续的情况下，应该操作符应该换为 +号，在不更换操作符的情况号，只能更换操作数
              //意思就是 - -x，其中第1个是原来的操作符
              if (inv) p_inv = true;
              //因为已经处理了减法的切换，因此转换为加法
              inv = false;

              //已经不能再继续处理了，因此将相应的结果进行保存，重新定义相应 tk和tk2表示形式
              if (!reduc) {
                bo = new BinaryOperation(tkOp.getOperator(), tk, new LiteralNode(p_inv ? signNumber(val) : val, pCtx), pCtx);
              }
              //还可以继续重新，将临时的结果保存在tk2中然后继续，即 tk op tk2 op2 tk3，这里即将tk2 op2 tk3的结果重新认为为tk2
              else {
                tk2 = new LiteralNode(val, pCtx);
              }
            }

            //不能继续优化，则直接更换为二元操作
            if (bo == null)
              bo = new BinaryOperation(op, tk, tk2, pCtx);
          }

          tkOp2 = null;

          /**
           * 以下解析乘法除法问题优先级问题,以及和加减法问题,同样涉及到优先级问题(实际上这段代码已经替换掉上面代码中的else部分)
           * 以下逻辑涉及到3个操作符 bop,op,op2，其中bop表示在bo操作中的操作符,op表示在解析过程中上一次操作符，op2表示当前操作符
           * If we have a chain of math/comparitive operators then we fill them into the tree
           * right here.
           */
          while (astLinkedList.hasMoreNodes() && (tkOp2 = astLinkedList.nextNode()).isOperator()
              && tkOp2.getFields() != -1 && (op2 = tkOp2.getOperator()) != -1 && op2 < 21) {

            if (PTABLE[op2] > PTABLE[op]) {
              //优先级比上一次op更高，替换相应的操作数(先计算优先级更高的)
              //这里实际的情况就是 a + b 和 * c 替换为 a + (b * c) 其中b*c为一个整体
              //       bo.setRightMost(new BinaryOperation(op2, bo.getRightMost(), astLinkedList.nextNode(), pCtx));
              bo.setRightMost(boOptimize(op2, bo.getRightMost(), astLinkedList.nextNode(), pCtx));
            }
            //这里处理上一次优先级相同，但bo中原始的操作符不相同的情况
            else if (bo.getOperation() != op2 && PTABLE[op] == PTABLE[op2]) {
              if (PTABLE[bo.getOperation()] == PTABLE[op2]) {
                //和bo中优先级一样，直接连接起来即可，如 a * b / c 换为 (a * b) / c
                //     bo = new BinaryOperation(op2, bo, astLinkedList.nextNode(), pCtx);
                bo = boOptimize(op2, bo, astLinkedList.nextNode(), pCtx);
              }
              else {
                //这里即表示优先级为上一次优先级一样，但与bo不一样，就可以理解为这里的优先级比原来的bo中的优先级更高
                //如 a + (b*c) / d，就更换为a + ((b * c) / d)
                tk2 = astLinkedList.nextNode();

                //如果bo是一个整数优化节点，但是tk2节点不知道是什么类型，则需要进行逆化操作，即还原回来,将右侧的节点处理为普通的操作节点
                if (isIntOptimizationviolation(bo, tk2)) {
                  bo = new BinaryOperation(bo.getOperation(), bo.getLeft(), bo.getRight(), pCtx);
                }

                //重新处理右侧节点
                bo.setRight(new BinaryOperation(op2, bo.getRight(), tk2, pCtx));
              }
            }
            else if (PTABLE[bo.getOperation()] >= PTABLE[op2]) {
              //bo中优先级更高，直接连接起即可
              bo = new BinaryOperation(op2, bo, astLinkedList.nextNode(), pCtx);
            }
            else {
              //bo中优先级更小，那么连接右边逻辑
              tk2 = astLinkedList.nextNode();

              //如果bo是一个整数优化节点，但是tk2节点不知道是什么类型，则需要进行逆化操作，即还原回来,将右侧的节点处理为普通的操作节点
              if (isIntOptimizationviolation(bo, tk2)) {
                bo = new BinaryOperation(bo.getOperation(), bo.getLeft(), bo.getRight(), pCtx);
              }

              //重新处理右侧节点
              bo.setRight(new BinaryOperation(op2, bo.getRight(), tk2, pCtx));
            }

            op = op2;
            tkOp = tkOp2;
          }


          if (tkOp2 != null && tkOp2 != tkOp) {
            //这里表示操作符不是数学运算的情况,则按正常处理逻辑来执行
            optimizeOperator(tkOp2.getOperator(), bo, tkOp2, astLinkedList, optimizedAst, pCtx);
          }
          else {
            optimizedAst.addTokenNode(bo);
          }
        }
        else if (tkOp.isOperator()) {
          //普通运算操作
          optimizeOperator(tkOp.getOperator(), tk, tkOp, astLinkedList, optimizedAst, pCtx);
        }
        else if (!tkOp.isAssignment() && !tkOp.isOperator() && tk.getLiteralValue() instanceof Class) {
          //普通ast转换为类型声明
          optimizedAst.addTokenNode(new DeclTypedVarNode(tkOp.getName(), tkOp.getExpr(), tkOp.getStart(), tk.getOffset(), (Class) tk.getLiteralValue(), 0, pCtx));
        }
        else if (tkOp.isAssignment() && tk.getLiteralValue() instanceof Class) {
          //如果类似为 Object obj = xxx;这种，第1个Object没有意义，可以忽略掉
          tk.discard();
          optimizedAst.addTokenNode(tkOp);
        }
        //这里先有一个class 定义，然后后面接一个赋值，即 Object obj = xx;因此，丢掉Object声明，因为在相应的解析上下文中已经有相应的数据了
        else if (astLinkedList.hasMoreNodes() && tkOp.getLiteralValue() instanceof Class
            && astLinkedList.peekNode().isAssignment()) {
          tkOp.discard();
          optimizedAst.addTokenNode(tk, astLinkedList.nextNode());
        }
        else {
          astLinkedList.back();
          optimizedAst.addTokenNode(tk);
        }
      }
      //末尾节点
      else {
        optimizedAst.addTokenNode(tk);
      }
    }

    //开始优化 boolean 操作
    if (secondPassOptimization) {
      /**
       * boolean条件优化
       * Perform a second pass optimization for boolean conditions.
       */
      (astLinkedList = optimizedAst).reset();
      optimizedAst = new ASTLinkedList();

      while (astLinkedList.hasMoreNodes()) {
        //特殊节点忽略
        if ((tk = astLinkedList.nextNode()).getFields() == -1) {
          optimizedAst.addTokenNode(tk);
        }
        else if (astLinkedList.hasMoreNodes()) {
          //特殊节点
          if ((tkOp = astLinkedList.nextNode()).getFields() == -1) {
            optimizedAst.addTokenNode(tk, tkOp);
          }
          //处理boolean型操作
          else if (tkOp.isOperator()
              && (tkOp.getOperator() == Operator.AND || tkOp.getOperator() == Operator.OR)) {

            tkOp2 = null;
            BooleanNode bool;

            //转换为and 和 or节点
            if (tkOp.getOperator() == Operator.AND) {
              bool = new And(tk, astLinkedList.nextNode(), pCtx.isStrongTyping(), pCtx);
            }
            else {
              bool = new Or(tk, astLinkedList.nextNode(), pCtx.isStrongTyping(), pCtx);
            }

            //因为and的优先级更高，因此在整个处理过程中，需要将and优先级提高，与原先的右节点相匹配，而or节点则直接对接即可
            while (astLinkedList.hasMoreNodes() && (tkOp2 = astLinkedList.nextNode()).isOperator()
                && (tkOp2.isOperator(Operator.AND) || tkOp2.isOperator(Operator.OR))) {

              if ((tkOp = tkOp2).getOperator() == Operator.AND) {
                bool.setRightMost(new And(bool.getRightMost(), astLinkedList.nextNode(), pCtx.isStrongTyping(), pCtx));
              }
              else {
                bool = new Or(bool, astLinkedList.nextNode(), pCtx.isStrongTyping(), pCtx);
              }

            }

            optimizedAst.addTokenNode(bool);

            if (tkOp2 != null && tkOp2 != tkOp) {
              optimizedAst.addTokenNode(tkOp2);
            }
          }
          else {
            optimizedAst.addTokenNode(tk, tkOp);
          }
        }
        //末尾的节点
        else {
          optimizedAst.addTokenNode(tk);
        }
      }
    }

    return optimizedAst;
  }

  /** 整数计算优化 */
  private static BinaryOperation boOptimize(int op, ASTNode tk, ASTNode tk2, ParserContext pCtx) {
    if (tk.getEgressType() == Integer.class && tk2.getEgressType() == Integer.class) {
      switch (op) {
        case Operator.ADD:
          return new IntAdd(tk, tk2, pCtx);

        case Operator.SUB:
          return new IntSub(tk, tk2, pCtx);

        case Operator.MULT:
          return new IntMult(tk, tk2, pCtx);

        case Operator.DIV:
          return new IntDiv(tk, tk2, pCtx);

        default:
          return new BinaryOperation(op, tk, tk2, pCtx);
      }
    }
    else {
      return new BinaryOperation(op, tk, tk2, pCtx);
    }
  }

  /** 判断当前操作符是否后面有 加减法操作，并且当前操作优先级比后面操作优先级低(或相同) */
  private static boolean isReductionOpportunity(ASTNode oper, ASTNode node) {
    ASTNode n = node;
    return (n != null && n.isLiteral()
        && (n = n.nextASTNode) != null && reducacbleOperator(n.getOperator())
        && PTABLE[oper.getOperator()] <= PTABLE[n.getOperator()]
        && (n = n.nextASTNode) != null && n.isLiteral() && n.getLiteralValue() instanceof Number);
  }

  /** 判断操作符是否可降权处理(即可继续处理),就是是否是加减法 */
  private static boolean reducacbleOperator(int oper) {
    switch (oper) {
      case Operator.ADD:
      case Operator.SUB:
        return true;

    }
    return false;
  }

  /**
   * 优化操作符，处理非数学运算的情况
   *
   * @param astLinkedList 当前正在处理的节点链表
   * @param optimizedAst  要放入的优化节点链表
   */
  private static void optimizeOperator(int operator, ASTNode tk, ASTNode tkOp,
                                       ASTLinkedList astLinkedList,
                                       ASTLinkedList optimizedAst,
                                       ParserContext pCtx) {
    switch (operator) {
      //正则式处理
      case Operator.REGEX:
        optimizedAst.addTokenNode(new RegExMatchNode(tk, astLinkedList.nextNode(), pCtx));
        break;
      //contails优化节点
      case Operator.CONTAINS:
        optimizedAst.addTokenNode(new Contains(tk, astLinkedList.nextNode(), pCtx));
        break;
      //支持instanceOf
      case Operator.INSTANCEOF:
        optimizedAst.addTokenNode(new Instance(tk, astLinkedList.nextNode(), pCtx));
        break;
      //转换操作
      case Operator.CONVERTABLE_TO:
        optimizedAst.addTokenNode((new Convertable(tk, astLinkedList.nextNode(), pCtx)));
        break;
      case Operator.SIMILARITY:
        optimizedAst.addTokenNode(new Strsim(tk, astLinkedList.nextNode(), pCtx));
        break;
      case Operator.SOUNDEX:
        optimizedAst.addTokenNode(new Soundslike(tk, astLinkedList.nextNode(), pCtx));
        break;

      //默认情况，不知道如何优化，先直接添加到链表中
      default:
        optimizedAst.addTokenNode(tk, tkOp);
    }
  }

  /** 两个节点之间是否不支持进一步优化级联，即左侧一个优化 的 a + b ? c这种操作，这里的?优先级更高， 因此需要把a 和b拆开，形成 a + (b ? c)的这种形式 */
  private static boolean isIntOptimizationviolation(BooleanNode bn, ASTNode bn2) {
    return (bn instanceof IntOptimized && bn2.getEgressType() != Integer.class);
  }

  /** 返回一个执行链最终的执行类型 */
  public static Class getReturnType(ASTIterator input, boolean strongTyping) {
    ASTNode begin = input.firstNode();
    if (begin == null) return Object.class;
    if (input.size() == 1) return begin.getEgressType();
    return buildTree(input).getReturnType(strongTyping);
  }

  /**
   * 从一个表达式执行单元中提取所有的函数信息，按照 函数名和函数定义放入map中并返回
   * Returns an ordered Map of all functions declared within an compiled script.
   *
   * @param compile
   * @return - ordered Map
   */
  public static Map<String, Function> extractAllDeclaredFunctions(CompiledExpression compile) {
    Map<String, Function> allFunctions = new LinkedHashMap<String, Function>();
    ASTIterator instructions = new ASTLinkedList(compile.getFirstNode());

    ASTNode n;
    while (instructions.hasMoreNodes()) {
      if ((n = instructions.nextNode()) instanceof Function) {
        allFunctions.put(n.getName(), (Function) n);
      }
    }

    return allFunctions;
  }

  /** 期望指定访问器的已知返回格式与预期的类型相兼容 */
  public static void expectType(ParserContext pCtx, Accessor expression, Class type, boolean compileMode) {
    Class retType = expression.getKnownEgressType();
    //编译模式下,如果 (返回类型为null 或者 类型不兼容)的情况下,同时解析上下文要求严格类型调用或者不是object类型
    //即允许的情况包括 当前返回类型不能为null,可以为object但要求上下文解析非严格,或者是类型兼容
    if (compileMode) {
      if ((retType == null || !boxPrimitive(type).isAssignableFrom(boxPrimitive(retType))) && (!Object.class.equals(retType)
          || pCtx.isStrictTypeEnforcement())) {
        throw new CompileException("was expecting type: " + type.getName() + "; but found type: "
            + (retType != null ? retType.getName() : "<Unknown>"), new char[0], 0);
      }
    }
    //如果当前不是编译模式,则当返回值为null 或 在不是object的情况下与预期的类型不兼容,则认为是异常
    //因为返回object表示当前类型不确定
    //允许的情况为返回类型不为null,可以为object,或者是类型相兼容
    else if (retType == null || !Object.class.equals(retType) && !boxPrimitive(type).isAssignableFrom(boxPrimitive(retType))) {
      throw new CompileException("was expecting type: " + type.getName() + "; but found type: "
          + (retType != null ? retType.getName() : "<Unknown>"), new char[0], 0);
    }
  }

  /** 期望相应的节点的声明类型为指定的类型 */
  public static void expectType(ParserContext pCtx, ASTNode node, Class type, boolean compileMode) {
    Class retType = boxPrimitive(node.getEgressType());
    //编译期不能确定相应的类型,因此编译期当声明类型不能确定时,还需要对当前上下文作处理,即只有在严格模式下才进行异常处理
    if (compileMode) {
      if ((retType == null || !boxPrimitive(type).isAssignableFrom(retType)) && (!Object.class.equals(retType) && pCtx.isStrictTypeEnforcement())) {
        throw new CompileException("was expecting type: " + type.getName() + "; but found type: "
            + (retType != null ? retType.getName() : "<Unknown>"), new char[0], 0);
      }
    }
    else if (retType == null || !Object.class.equals(retType) && !boxPrimitive(type).isAssignableFrom(retType)) {
      throw new CompileException("was expecting type: " + type.getName() + "; but found type: "
          + (retType != null ? retType.getName() : "<Unknown>"), new char[0], 0);
    }
  }

  /** 根据操作符以及左边类型和右边类型，推断整个结果的返回类型 */
  public static Class getReturnTypeFromOp(int operation, Class left, Class right) {
    switch (operation) {
      //boolean 型操作
      case Operator.LETHAN:
      case Operator.LTHAN:
      case Operator.GETHAN:
      case Operator.GTHAN:
      case Operator.EQUAL:
      case Operator.NEQUAL:
      case Operator.AND:
      case Operator.OR:
      case Operator.CONTAINS:
      case Operator.CONVERTABLE_TO:
        return Boolean.class;

      //4符混合运算，则返回宽化类型
      case Operator.ADD:
        if (left == String.class) return String.class;//特殊处理字符串问题
      case Operator.SUB:
      case Operator.MULT:
      case Operator.POWER:
      case Operator.MOD:
      case Operator.DIV:
        if (left == Object.class || right == Object.class)
          return Object.class;
        else
          return __resolveType(boxPrimitive(left)) < __resolveType(boxPrimitive(right)) ? right : left;

        //二进制操作，均返回整数
      case Operator.BW_AND:
      case Operator.BW_OR:
      case Operator.BW_XOR:
      case Operator.BW_SHIFT_RIGHT:
      case Operator.BW_SHIFT_LEFT:
      case Operator.BW_USHIFT_LEFT:
      case Operator.BW_USHIFT_RIGHT:
      case Operator.BW_NOT:
        return Integer.class;

      //字符串拼接，返回字符串
      case Operator.STR_APPEND:
        return String.class;
    }
    return null;
  }

  /** 将相应的节点转换为访问器 */
  @Deprecated
  public static Accessor extractAccessor(ASTNode n) {
    if (n instanceof LiteralNode) return new ExecutableLiteral(n.getLiteralValue());
    else return new ExecutableAccessor(n, n.getEgressType());
  }


  /** 获取在变量工厂中作为类引用已经引入的各项信息 */
  public static Map<String, Object> getInjectedImports(VariableResolverFactory factory) {
    if (factory == null) return null;
    do {
      if (factory instanceof ClassImportResolverFactory) {
        return ((ClassImportResolverFactory) factory).getImportedClasses();
      }
    }
    while ((factory = factory.getNextFactory()) != null);

    return null;
  }

  /** 将相应的数字进行转负处理,即返回相应的负数形式 */
  public static Number signNumber(Object number) {
    if (number instanceof Integer) {
      return -((Integer) number);
    }
    else if (number instanceof Double) {
      return -((Double) number);
    }
    else if (number instanceof Float) {
      return -((Float) number);
    }
    else if (number instanceof Short) {
      return -((Short) number);
    }
    else {
      throw new CompileException("expected a numeric type but found: " + number.getClass().getName(), new char[0], 0);
    }
  }

}
