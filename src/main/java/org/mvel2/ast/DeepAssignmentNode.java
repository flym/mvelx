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
import org.mvel2.compiler.CompiledAccExpression;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.MVEL.compileSetExpression;
import static org.mvel2.MVEL.eval;
import static org.mvel2.PropertyAccessor.set;
import static org.mvel2.util.ParseTools.*;

/**
 * 指针对一个属性进行 ?= 操作的节点 ? 表示 +-* / 等
 * 此表达式可以理解为 a += b 即 a = a + b，因此实际层会作相应的转化处理
 * <p/>
 * 这里的深度即是指对一个属性进行操作，而不是简单地对变量进行操作
 * 这里的 操作符可为 -1，即没有?，可以为a.b=3这种表达式
 *
 * @author Christopher Brock
 */
public class DeepAssignmentNode extends ASTNode implements Assignment {
  /** 当前正在处理的属性名(整个属性表达式) */
  private String property;
  // private char[] stmt;

  /** 当前的属性的访问器表达式 */
  private CompiledAccExpression acc;
  /** 将 a ?= b 修改为 a ? b的表达式 */
  private ExecutableStatement statement;

  public DeepAssignmentNode(char[] expr, int start, int offset, int fields, int operation, String name, ParserContext pCtx) {
    super(pCtx);
    this.fields |= DEEP_PROPERTY | fields;

    this.expr = expr;
    this.start = start;
    this.offset = offset;
    int mark;

    if (operation != -1) {//有操作符
      //类型即后面 a + b 之后的结果类型
      this.egressType = ((statement =
          (ExecutableStatement) subCompileExpression(
              createShortFormOperativeAssignment(this.property = name, expr, start, offset, operation), pCtx))).getKnownEgressType();
    }
    else if ((mark = find(expr, start, offset, '=')) != -1) {
      //没有操作符，就是简单的赋值操作
      property = createStringTrimmed(expr, start, mark - start);

      // this.start = mark + 1;
      this.start = skipWhitespace(expr, mark + 1);

      if (this.start >= start + offset) {
        throw new CompileException("unexpected end of statement", expr, mark + 1);
      }

      this.offset = offset - (this.start - start);

      if ((fields & COMPILE_IMMEDIATE) != 0) {
        statement = (ExecutableStatement) subCompileExpression(expr, this.start, this.offset, pCtx);
      }
    }
    else {
      property = new String(expr);
    }

    //对当前属性进行解析并处理
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      acc = (CompiledAccExpression) compileSetExpression(property.toCharArray(), start, offset, pCtx);
    }
  }

  public DeepAssignmentNode(char[] expr, int start, int offset, int fields, ParserContext pCtx) {
    this(expr, start, offset, fields, -1, null, pCtx);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //重新编译单元
    if (statement == null) {
      statement = (ExecutableStatement) subCompileExpression(expr, this.start, this.offset, pCtx);
      acc = (CompiledAccExpression) compileSetExpression(property.toCharArray(), statement.getKnownEgressType(), pCtx);
    }
    //在之前已经将statement,转换为a+b,因此这里整个表达式即为a = a +b,即对后面进行求值,再重新设置回去
    //如果本身没有+= 这种操作符,则直接即为a = b这种
    acc.setValue(ctx, thisValue, factory, ctx = statement.getValue(ctx, thisValue, factory));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    set(ctx, factory, property, ctx = eval(expr, this.start, this.offset, ctx, factory), pCtx);
    return ctx;
  }

  @Override
  public String getAbsoluteName() {
    //最靠前的属性即为第1个.之前,这里不需要作判断,因为编译时已经处理过了
    return property.substring(0, property.indexOf('.'));
  }

  public String getAssignmentVar() {
    return property;
  }

  public char[] getExpression() {
    return subArray(expr, start, offset);
  }

  public boolean isNewDeclaration() {
    return false;
  }

  /** 是赋值节点 */
  public boolean isAssignment() {
    return true;
  }

  public void setValueStatement(ExecutableStatement stmt) {
    this.statement = stmt;
  }
}
