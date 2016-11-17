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

import org.mvel2.OptimizationFailure;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

/** 描述静态字段访问器 */
public class StaticVarAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 相应的字段 */
  Field field;

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //直接通过field.get来获取静态字段的值,因为是静态字段,因此无需传参
    try {
      if (nextNode != null) {
        return nextNode.getValue(field.get(null), elCtx, vars);
      }
      else {
        return field.get(null);
      }
    }
    catch (Exception e) {
      throw new OptimizationFailure("unable to access static field", e);
    }
  }

  public StaticVarAccessor(Field field) {
    this.field = field;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //根据是否有next决定是否转发请求
    try {
      if (nextNode == null) {
        field.set(null, value);
      }
      else {
        return nextNode.setValue(field.get(null), elCtx, variableFactory, value);
      }
    }
    catch (Exception e) {
      throw new RuntimeException("error accessing static variable", e);
    }
    return value;
  }

  /** 返回相应的静态字段 */
  public Field getField() {
    return field;
  }

  /** 声明类型即为相应字段所声明的类型 */
  public Class getKnownEgressType() {
    //这里有问题,应该返回声明类型,而不是Field类型
    //这个方法不会被调用,因此相应的类型确定在astNode阶段即已经确认
    return field.getClass();
  }
}