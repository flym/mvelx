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
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.MVEL.eval;
import static org.mvel2.util.ParseTools.subCompileExpression;
import static org.mvel2.util.ReflectionUtil.isAssignableFrom;

/** 表示一个类型转换的转换节点 */
public class TypeCast extends ASTNode {
  /** 待转换的类型的表达式信息 */
  private ExecutableStatement statement;
  /** 是否是宽转换，表示从子类型转换为父类型 */
  private boolean widen;

  public TypeCast(char[] expr, int start, int offset, Class cast, int fields, ParserContext pCtx) {
    super(pCtx);
    this.egressType = cast;
    this.expr = expr;
    this.start = start;
    this.offset = offset;

    if ((fields & COMPILE_IMMEDIATE) != 0) {

      //对后面的执行节点进行处理.如果后面节点返回不是object,则表示是一个具体的类型.则进行转换
      //这里首先通过转换来处理,如果不能转换,则判断是否是宽转换,即(Collection)list这种转换处理,如果仍不是,则throw 相应的异常
      if ((statement = (ExecutableStatement) subCompileExpression(expr, start, offset, pCtx))
          .getKnownEgressType() != Object.class
          && !canConvert(cast, statement.getKnownEgressType())) {

        if (canCast(statement.getKnownEgressType(), cast)) {
          widen = true;
        }
        else {
          throw new CompileException("unable to cast type: "
              + statement.getKnownEgressType() + "; to: " + cast, expr, start);
        }
      }
    }
  }

  /** 两个类型之间是否能进行相应的类型转换 */
  private boolean canCast(Class from, Class to) {
    //a为b的父类型或者是 a的某个声明接口是b的父类
    return isAssignableFrom(from, to) || (from.isInterface() && interfaceAssignable(from, to));
  }

  /** 两个类是接口兼容的,用于宽化转换检查 */
  private boolean interfaceAssignable(Class from, Class to) {
    for (Class c : from.getInterfaces()) {
      if (c.isAssignableFrom(to)) return true;
    }
    return false;
  }


  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //根据宽化逻辑采用简单的cast 或者是类型转换处理
    //noinspection unchecked
    return widen ? typeCheck(statement.getValue(ctx, thisValue, factory), egressType) : convert(statement.getValue(ctx, thisValue, factory), egressType);
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //noinspection unchecked
    return widen ? typeCheck(eval(expr, start, offset, ctx, factory), egressType) :
        convert(eval(expr, start, offset, ctx, factory), egressType);
  }

  /** 检查相应的实例类型是否是指定类型的实例 */
  private static Object typeCheck(Object inst, Class type) {
    if (inst == null) return null;
    if (type.isInstance(inst)) {
      return inst;
    }
    else {
      throw new ClassCastException(inst.getClass().getName() + " cannot be cast to: " + type.getClass().getName());
    }
  }

  public ExecutableStatement getStatement() {
    return statement;
  }
}
