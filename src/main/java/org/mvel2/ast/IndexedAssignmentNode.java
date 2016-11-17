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
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledAccExpression;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.MVEL.compileSetExpression;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.ParseTools.*;

/**
 * 按操作节点 这里的下标是可选的,实际上也可能没有下标
 * 这里的indexed表示相应的变量已经在相应的解析上下文中进行过声明了,即表示已经存在则变量声明了
 * @author Christopher Brock
 */
public class IndexedAssignmentNode extends ASTNode implements Assignment {
  /** 当前声明的变量名(带[i],如a[i]) */
  private String assignmentVar;
  /** 当前处理赋值的变量名(真实变量名,不带[i],如a[i]中的a) */
  private String name;
  /** 当前变量在上下文中的下标值 */
  private int register;
  /** 表示当前变量的属性表达式,主要是如果有下标,则a[i]的表达式 */
  private transient CompiledAccExpression accExpr;

  /** 当前集合属性名的字符串 */
  private char[] indexTarget;
  /** 当前集合属性的下标字符串 */
  private char[] index;

  /** 表示 a + b的操作表达式 */
  private char[] stmt;
  /** 表示 a + b的编译表达式 */
  private ExecutableStatement statement;

  /** 当前变量中是否存在集合表达式,如a[1] */
  private boolean col = false;

  /**
   * 构建起赋值节点
   * 这里带算术符的调用中,由外部保证相应的name已经是一个可以被解析的变量了
   * @param operation 当前的下标赋值是否存在算术操作,如 a[1] += 3这种
   */
  public IndexedAssignmentNode(char[] expr, int start, int offset, int fields, int operation,
                               String name, int register, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    this.register = register;

    int assignStart;

    //如果有算术操作,则将 a[i] += b,替换为a[i] + b,最后形成如a[i] = a[i] + b的效果
    //有算术操作的情况下,相应的name和表达式是分开的
    if (operation != -1) {
      checkNameSafety(this.name = name);

      this.egressType = (statement = (ExecutableStatement)
          subCompileExpression(stmt = createShortFormOperativeAssignment(name, expr, start, offset, operation), pCtx)).getKnownEgressType();
    }
    //没有算术符,则整个表达式均算作a = x这种操作
    else if ((assignStart = find(expr, start, offset, '=')) != -1) {

      this.name = createStringTrimmed(expr, start, assignStart - start);
      this.assignmentVar = name;

      this.start = skipWhitespace(expr, assignStart + 1);

      if (this.start >= start + offset) {
        throw new CompileException("unexpected end of statement", expr, assignStart + 1);
      }

      this.offset = offset - (this.start - start);
      stmt = subset(expr, this.start, this.offset);

      //= 后面部分的表达式
      this.egressType = (statement
          = (ExecutableStatement) subCompileExpression(expr, this.start, this.offset, pCtx))
          .getKnownEgressType();

      //判定是否存在下标访问,则处理相应的下标表达式以及相应的下标位
      if (col = ((endOfName = (short) findFirst('[', 0, this.name.length(), indexTarget = this.name.toCharArray())) > 0)) {
        if (((this.fields |= COLLECTION) & COMPILE_IMMEDIATE) != 0) {
          accExpr = (CompiledAccExpression) compileSetExpression(indexTarget, pCtx);
        }

        this.name = this.name.substring(0, endOfName);
        index = subset(indexTarget, endOfName, indexTarget.length - endOfName);
      }

      checkNameSafety(this.name);
    }
    else {
      checkNameSafety(this.name = new String(expr));
      this.assignmentVar = name;
    }

    //name加入变量,以方便后面拿到相应的类型
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      pCtx.addVariable(name, egressType);
    }
  }

  /** 表示普通的赋值操作 没有 += 这种处理 */
  public IndexedAssignmentNode(char[] expr, int start, int offset, int fields, int register, ParserContext pCtx) {
    this(expr, start, offset, fields, -1, null, register, pCtx);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //普通赋值,并且前面是基于下标处理的,这里重建相应的表达式
    if (accExpr == null && indexTarget != null) {
      accExpr = (CompiledAccExpression) compileSetExpression(indexTarget);
    }

    //集合访问
    if (col) {
      accExpr.setValue(ctx, thisValue, factory, ctx = statement.getValue(ctx, thisValue, factory));
    }
    //普通赋值处理
    else if (statement != null) {
      if (factory.isIndexedFactory()) {
        factory.createIndexedVariable(register, name, ctx = statement.getValue(ctx, thisValue, factory));
      }
      else {
        factory.createVariable(name, ctx = statement.getValue(ctx, thisValue, factory));
      }
    }
    //只是创建起变量
    else {
      if (factory.isIndexedFactory()) {
        factory.createIndexedVariable(register, name, null);
      }
      else {
        factory.createVariable(name, statement.getValue(ctx, thisValue, factory));
      }
      return Void.class;
    }

    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    checkNameSafety(name);

    if (col) {
      MVEL.setProperty(factory.getIndexedVariableResolver(register).getValue(), new String(index), ctx = MVEL.eval(stmt, ctx, factory));
    }
    else {
      factory.createIndexedVariable(register, name, ctx = MVEL.eval(stmt, ctx, factory));
    }

    return ctx;
  }

  public String getAssignmentVar() {
    return assignmentVar;
  }

  public String getVarName() {
    return name;
  }

  public char[] getExpression() {
    return stmt;
  }

  public int getRegister() {
    return register;
  }

  public void setRegister(int register) {
    this.register = register;
  }

  /** 当前节点为赋值节点 */
  public boolean isAssignment() {
    return true;
  }

  @Override
  public String getAbsoluteName() {
    return name;
  }

  /** 因为当前节点为a += xx 类似这样的操作,因此不是新声明节点 */
  public boolean isNewDeclaration() {
    return false;
  }

  public void setValueStatement(ExecutableStatement stmt) {
    this.statement = stmt;
  }
}