/**
 * MVEL (The MVFLEX Expression Language)
 *
 * Copyright (C) 2007 Christopher Brock, MVFLEX/Valhalla Project and the Codehaus
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
 *
 */
package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.CompileException;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Method;

import static org.mvel2.MVEL.getProperty;
import static org.mvel2.util.ParseTools.getBestCandidate;

/** 带空值处理器的getter方法访问 */
public class GetterAccessorNH implements AccessorNode {
  private AccessorNode nextNode;
  /** 对应的方法 */
  private final Method method;
  /** 空值处理 */
  private PropertyHandler nullHandler;

  public static final Object[] EMPTY = new Object[0];

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    try {
      return nullHandle(method.getName(), method.invoke(ctx, EMPTY), ctx, elCtx, vars);
    }
    catch (IllegalArgumentException e) {
      if (ctx != null && method.getDeclaringClass() != ctx.getClass()) {
        Method o = getBestCandidate(EMPTY, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
        if (o != null) {
          return executeOverrideTarget(o, ctx, elCtx, vars);
        }
      }
      /**
       * HACK: Try to access this another way.
       */

      //退化到属性方法的方式
      return nullHandle(method.getName(), getProperty(method.getName() + "()", ctx), ctx, elCtx, vars);
    }
    catch (Exception e) {
      throw new RuntimeException("cannot invoke getter: " + method.getName()
          + " [declr.class: " + method.getDeclaringClass().getName() + "; act.class: "
          + (ctx != null ? ctx.getClass().getName() : "null") + "]", e);
    }
  }

  /** 使用getter方法和相应的null值处理器进行访问器构建 */
  public GetterAccessorNH(Method method, PropertyHandler nullHandler) {
    this.method = method;
    this.nullHandler = nullHandler;
  }

  /** 返回相应的getter方法 */
  public Method getMethod() {
    return method;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public String toString() {
    return method.getDeclaringClass().getName() + "." + method.getName();
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
    //这里并未判断是否有子级节点,实际上应该进行判断

    try {
      //获取相应的值,再转由子节点处理
      Object v = method.invoke(ctx, EMPTY);
      if (v == null) v = nullHandler.getProperty(method.getName(), ctx, vars);
      return nextNode.setValue(v, elCtx, vars, value);

    }
    catch (IllegalArgumentException e) {
      /**
       * HACK: Try to access this another way.
       */

      //参数处理失败,退化到属性访问的方式
      Object v = getProperty(method.getName() + "()", ctx);
      if (v == null) v = nullHandler.getProperty(method.getName(), ctx, vars);
      return nextNode.setValue(v, elCtx, vars, value);
    }
    catch (CompileException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException("error " + method.getName() + ": " + e.getClass().getName() + ":" + e.getMessage(), e);
    }
  }

  public Class getKnownEgressType() {
    return method.getReturnType();
  }

  /** 执行重写过后的方法 */
  private Object executeOverrideTarget(Method o, Object ctx, Object elCtx, VariableResolverFactory vars) {
    try {
      return nullHandle(o.getName(), o.invoke(ctx, EMPTY), ctx, elCtx, vars);
    }
    catch (Exception e2) {
      throw new RuntimeException("unable to invoke method", e2);
    }
  }

  /** 根据当前方法的执行值,先进行null值处理,再根据子级节点进行相应的逻辑调用 */
  private Object nullHandle(String name, Object v, Object ctx, Object elCtx, VariableResolverFactory vars) {
    //值非空,转由next或直接返回
    if (v != null) {
      if (nextNode != null) {
        return nextNode.getValue(v, elCtx, vars);
      }
      else {
        return v;
      }
    }
    //值为空,则交由null值处理或继续转向next
    else {
      if (nextNode != null) {
        return nextNode.getValue(nullHandler.getProperty(name, ctx, vars), elCtx, vars);
      }
      else {
        return nullHandler.getProperty(name, ctx, vars);
      }
    }
  }
}