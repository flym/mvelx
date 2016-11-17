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
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Array;

/** 针对数组的访问器 */
public class ArrayAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 数组下标 */
  private int index;

  public ArrayAccessor() {
  }

  public ArrayAccessor(int index) {
    this.index = index;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //直接使用数组的反射调用方式来进行处理
    if (nextNode != null) {
      return nextNode.getValue(Array.get(ctx, index), elCtx, vars);
    }
    else {
      try {
        return Array.get(ctx, index);
      }
      catch(IllegalArgumentException e) {
        // This isn't great, but the mechanism for deoptimizing a stale accessor is currently based on 
        //  Accessor's  throwing a ClassCastException.  Catching  IllegalArgumentException in 
        // org.mvel2.ast.ASTNode.getReducedValueAccelerated(Object, Object, VariableResolverFactory)
        // is a bad idea and currently there is nowhere to easily introduce pre-emptive accessor validity.
        throw new ClassCastException("Argument of type '"+ctx.getClass()+"' is not an Array");  
      }
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //采用数据反射的调用方式,如果级联调用,则将相应的处理转交给下级节点,即下级节点才是set调用
    if (nextNode != null) {
      return nextNode.setValue(Array.get(ctx, index), elCtx, variableFactory, value);
    }
    else {
      Array.set(ctx, index, value);
      return value;
    }
  }

  /** 返回相应的下标 */
  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  /** 因为是数组访问器,当前认为声明类型为数组 */
  public Class getKnownEgressType() {
    return Object[].class;
  }

  public String toString() {
    return "Array Accessor -> [" + index + "]";
  }
}
