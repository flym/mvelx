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

import org.mvel2.compiler.AccessorNode;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.ParseTools;

import java.lang.reflect.Array;

import static org.mvel2.DataConversion.convert;

/** 数组访问器，但下标值是一个执行单元的情况(在编译层不能优化) */
public class ArrayAccessorNest implements AccessorNode {
  private AccessorNode nextNode;
  /** 下标执行单元 */
  private ExecutableStatement index;

  /** 当前处理的数组类型 */
  private Class baseComponentType;
  /** 设置值时是否需要类型转换 */
  private boolean requireConversion;

  public ArrayAccessorNest() {
  }

  /** 使用下标表达式来构建出相应的数组访问器 */
  public ArrayAccessorNest(String index) {
    this.index = (ExecutableStatement) ParseTools.subCompileExpression(index.toCharArray());
  }

  /** 使用已编译好的下标表达式进行构建 */
  public ArrayAccessorNest(ExecutableStatement stmt) {
    this.index = stmt;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //这里采用强转处理,实际上这里有bug,如果ctx为基本类型,则会报classCast,进而转为反优化处理.
    if (nextNode != null) {
      return nextNode.getValue(((Object[]) ctx)[(Integer) index.getValue(ctx, elCtx, vars)], elCtx, vars);
    }
    else {
      return ((Object[]) ctx)[(Integer) index.getValue(ctx, elCtx, vars)];
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
    if (nextNode != null) {
      //这里的类型强转有问题
      return nextNode.setValue(((Object[]) ctx)[(Integer) index.getValue(ctx, elCtx, vars)], elCtx, vars, value);
    }
    else {
      //还没有找到数组中的元素类型,因此先检测类型,再判断是否需要进行类型转换,以便能够set到数组中
      if (baseComponentType == null) {
        baseComponentType = ParseTools.getBaseComponentType(ctx.getClass());
        requireConversion = baseComponentType != value.getClass() && !baseComponentType.isAssignableFrom(value.getClass());
      }

      //根据是否需要转换为进行相应的不同逻辑分支处理
      if (requireConversion) {
        Object o = convert(value, baseComponentType);
        Array.set(ctx, (Integer) index.getValue(ctx, elCtx, vars), o);
        return o;
      }
      else {
        Array.set(ctx, (Integer) index.getValue(ctx, elCtx, vars), value);
        return value;
      }
    }
  }

  /** 获取相应的下标执行单元 */
  public ExecutableStatement getIndex() {
    return index;
  }

  public void setIndex(ExecutableStatement index) {
    this.index = index;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public Class getKnownEgressType() {
    return baseComponentType;
  }

  public String toString() {
    return "Array Accessor -> [" + index + "]";
  }
}
