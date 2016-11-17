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
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Method;

import static java.lang.Thread.currentThread;
import static java.lang.reflect.Modifier.isStatic;
import static org.mvel2.util.ArrayTools.findLast;

/**
 * 描述静态引用节点,即引用方法
 * @author Christopher Brock
 */
public class StaticImportNode extends ASTNode {
  /** 此静态引用所指向的类 */
  private Class declaringClass;
  /** 静态引用方法名 */
  private String methodName;
  /** 引用的方法 */
  private transient Method method;

  public StaticImportNode(char[] expr, int start, int offset, ParserContext pCtx) {
    super(pCtx);
    try {
      this.expr = expr;
      this.start = start;
      this.offset = offset;

      int mark;
      
      ClassLoader classLoader = getClassLoader();

      //最后一个.之前的被认为是类名
      declaringClass = Class.forName(new String(expr, start, (mark = findLast('.', start, offset, this.expr = expr)) - start),
          true, classLoader );

      //后面的被认为是方法
      methodName = new String(expr, ++mark, offset - (mark - start));

      if (resolveMethod() == null) {
        throw new CompileException("can not find method for static import: "
            + declaringClass.getName() + "." + methodName, expr, start);
      }
    }
    catch (Exception e) {
      throw new CompileException("unable to import class", expr, start, e);
    }
  }

  /** 尝试根据类+相应的方法名找到相应的静态方法 */
  private Method resolveMethod() {
    for (Method meth : declaringClass.getMethods()) {
      if (isStatic(meth.getModifiers()) && methodName.equals(meth.getName())) {
        return method = meth;
      }
    }
    return null;
  }

  /** 返回相应的静态方法 */
  public Method getMethod() {
    return method;
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //其执行,即在上下作用域中创建一个相应的变量处理工厂
    factory.createVariable(methodName, method == null ? method = resolveMethod() : method);
    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }
}
