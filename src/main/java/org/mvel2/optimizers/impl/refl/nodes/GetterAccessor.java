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
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Method;

import static org.mvel2.MVEL.getProperty;
import static org.mvel2.util.ParseTools.getBestCandidate;
import static org.mvel2.util.ReflectionUtil.getPropertyFromAccessor;

/** 表示访问一个getter方法 访问器 */
public class GetterAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 所对应的方法 */
  private final Method method;

  public static final Object[] EMPTY = new Object[0];

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    try {
      //根据是否有下级节点决定相应的逻辑
      if (nextNode != null) {
        return nextNode.getValue(method.invoke(ctx, EMPTY), elCtx, vars);
      }
      else {
        return method.invoke(ctx, EMPTY);
      }
    }
    catch (IllegalArgumentException e) {
      //这里处理类型不匹配 的问题，即method的调用者不正确，因此这里重新获取相应的方法信息进行处理
      if (ctx != null && method.getDeclaringClass() != ctx.getClass()) {
        Method o = getBestCandidate(EMPTY, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
        if (o != null) {
          return executeOverrideTarget(o, ctx, elCtx, vars);
        }
      }

      /**
       * 仍然不行，则退化到使用属性访问的方式来处理
       * HACK: Try to access this another way.
       */
      if (nextNode != null) {
        return nextNode.getValue(getProperty(getPropertyFromAccessor(method.getName()), ctx), elCtx, vars);
      }
      else {
        return getProperty(getPropertyFromAccessor(method.getName()), ctx);
      }
    }
    catch (NullPointerException e) {
      if (ctx == null) {
        throw new RuntimeException("unable to invoke method: " + method.getDeclaringClass().getName() + "." + method.getName() + ": " +
            "target of method is null", e);
      }
      else {
        throw new RuntimeException("cannot invoke getter: " + method.getName() + " (see trace)", e);
      }
    }
    catch (Exception e) {
      throw new RuntimeException("cannot invoke getter: " + method.getName()
          + " [declr.class: " + method.getDeclaringClass().getName() + "; act.class: "
          + (ctx != null ? ctx.getClass().getName() : "null") + "] (see trace)", e);
    }
  }

  /** 通过相应的getter方法进行访问器构建 */
  public GetterAccessor(Method method) {
    this.method = method;
  }

  /** 获取相应的getter方法 */
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

  /** 不直接进行设置值,但是如果有下一级节点,则将相应的值传递给下一级节点 */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
    try {
      if (nextNode != null) {
        return nextNode.setValue(method.invoke(ctx, EMPTY), elCtx, vars, value);
      }
      else {
        //不需要单独设置值
        throw new RuntimeException("bad payload");
      }
    }
    catch (IllegalArgumentException e) {
      /**
       * HACK: Try to access this another way.
       */

      //如果调用失败,仍然退化到属性访问的方式
      if (nextNode != null) {
        return nextNode.setValue(getProperty(getPropertyFromAccessor(method.getName()), ctx), elCtx, vars, value);
      }
      else {
        return getProperty(getPropertyFromAccessor(method.getName()), ctx);
      }
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

  /** 重新调用重写的其它方法 */
  private Object executeOverrideTarget(Method o, Object ctx, Object elCtx, VariableResolverFactory vars) {
    try {
      if (nextNode != null) {
        return nextNode.getValue(o.invoke(ctx, EMPTY), elCtx, vars);
      }
      else {
        return o.invoke(ctx, EMPTY);
      }
    }
    catch (Exception e2) {
      throw new RuntimeException("unable to invoke method", e2);
    }
  }
}
