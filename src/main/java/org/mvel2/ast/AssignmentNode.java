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
import org.mvel2.MVELInterpretedRuntime;
import org.mvel2.ParserContext;
import org.mvel2.PropertyAccessor;
import org.mvel2.compiler.CompiledAccExpression;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.MVEL.compileSetExpression;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.ParseTools.*;

/**
 * 描述一个正常的变量声明节点,即如var a, a = new X()这种节点
 * @author Christopher Brock
 */
public class AssignmentNode extends ASTNode implements Assignment {
  /** 整个赋值变量名(包括数组下标) 如a[3]整个部分 */
  private String assignmentVar;
  /** 变量名(如果是集合访问,则集合访问的前面部分,如a[3]中的a */
  private String varName;
  /** 如果是基于集合访问,那么这个集合的变量值,即a[3]的引用对象 */
  private transient CompiledAccExpression accExpr;

  /** 如果是基于集合访问,相应的数组目标字符串(相当于varName) */
  private char[] indexTarget;
  /** 基于集合访问的下标描述字符串 如a[3]中的3 */
  private String index;

  // private char[] stmt;
  /** 右侧值的执行表达式 */
  private ExecutableStatement statement;
  /** 是否变量中存在集合标记,即a[1]这种 */
  private boolean col = false;


  public AssignmentNode(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    int assignStart;

    //这里表示有相应的赋值过程
    if ((assignStart = find(expr, start, offset, '=')) != -1) {
      //从=号之前的被认为是变量名
      this.varName = createStringTrimmed(expr, start, assignStart - start);
      this.assignmentVar = varName;

      //=号后面的表达式start标记
      this.start = skipWhitespace(expr, assignStart + 1);
      if (this.start >= start + offset) {
        throw new CompileException("unexpected end of statement", expr, assignStart + 1);
      }

      this.offset = offset - (this.start - start);

      //编译后面的表达式,同时认为相应的声明类型就是表达式的返回类型
      if ((fields & COMPILE_IMMEDIATE) != 0) {
        this.egressType = (statement = (ExecutableStatement)
            subCompileExpression(expr, this.start, this.offset, pCtx)).getKnownEgressType();
      }

      //这里通过对之前的变量判断是否有[]这种符号,即前面的是否为数组或集合访问
      if (col = ((endOfName = findFirst('[', 0, this.varName.length(), indexTarget = this.varName.toCharArray())) > 0)) {
        //是集合访问,设置相应的标记,同时设置相应的集合访问表达式,即最终需要达到一个accExpr.setValue的目的
        if (((this.fields |= COLLECTION) & COMPILE_IMMEDIATE) != 0) {
          accExpr = (CompiledAccExpression) compileSetExpression(indexTarget, pCtx);
        }

        //重新设置相应的变量名以及相应的下标数字
        this.varName = new String(expr, start, endOfName);
        index = new String(indexTarget, endOfName, indexTarget.length - endOfName);
      }


      try {
        checkNameSafety(this.varName);
      }
      catch (RuntimeException e) {
        throw new CompileException(e.getMessage(), expr, start);
      }
    }
    //以下表示可能为纯粹的变量声明,但从程序的引用来看以下场景不存在
    else {
      try {
        checkNameSafety(this.varName = new String(expr, start, offset));
        this.assignmentVar = varName;
      }
      catch (RuntimeException e) {
        throw new CompileException(e.getMessage(), expr, start);
      }
    }

    //此变量加入到上下文中,表示已被占用
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      pCtx.addVariable(this.varName, egressType);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //如果没有提前编译,则这里要重新编译一下
    if (accExpr == null && indexTarget != null) {
      accExpr = (CompiledAccExpression) compileSetExpression(indexTarget);
    }

    //集合访问,则读取相应的变量,设置值即可
    if (col) {
      return accExpr.setValue(ctx, thisValue, factory, statement.getValue(ctx, thisValue, factory));
    }
    //普通的赋值访问,则在当前变量工厂中创建出相应的变量以及相对应的值即可
    else if (statement != null) {
      if (factory == null)
        throw new CompileException("cannot assign variables; no variable resolver factory available", expr, start);
      return factory.createVariable(varName, statement.getValue(ctx, thisValue, factory)).getValue();
    }
    //单独声明变量
    else {
      if (factory == null)
        throw new CompileException("cannot assign variables; no variable resolver factory available", expr, start);
      factory.createVariable(varName, null);
      return null;
    }
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    checkNameSafety(varName);

    //采用解释的方式来进行运行,则尝试通过属性访问的方式来进行处理
    MVELInterpretedRuntime runtime = new MVELInterpretedRuntime(expr, start, offset, ctx, factory, pCtx);

    if (col) {
      PropertyAccessor.set(factory.getVariableResolver(varName).getValue(), factory, index, ctx = runtime.parse(), pCtx);
    }
    else {
      return factory.createVariable(varName, runtime.parse()).getValue();
    }

    return ctx;
  }


  public String getAssignmentVar() {
    return assignmentVar;
  }

  public char[] getExpression() {
    return subset(expr, start, offset);
  }

  public boolean isNewDeclaration() {
    return false;
  }

  public void setValueStatement(ExecutableStatement stmt) {
    this.statement = stmt;
  }

  @Override
  public String toString() {
    return assignmentVar + " = " + new String(expr, start, offset);
  }
}
