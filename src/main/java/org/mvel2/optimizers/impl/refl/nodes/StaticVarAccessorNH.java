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
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

/** 描述静态字段访问器，附带空值处理 */
public class StaticVarAccessorNH implements AccessorNode {
  private AccessorNode nextNode;
  /** 相应的字段 */
  Field field;
  /** 空值处理器 */
  private PropertyHandler nullHandler;

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //先获取值,再根据返回的结果决定是否采用空值处理器
    try {
      Object v = field.get(ctx);
      //值为null,则跳转到nullHandler中
      if (v == null) v = nullHandler.getProperty(field.getName(), elCtx, vars);

      if (nextNode != null) {
        return nextNode.getValue(v, elCtx, vars);
      }
      else {
        return v;
      }
    }
    catch (Exception e) {
      throw new OptimizationFailure("unable to access static field", e);
    }
  }

  /** 根据相应的静态字段和空值处理器构建出处理器 */
  public StaticVarAccessorNH(Field field, PropertyHandler handler) {
    this.field = field;
    this.nullHandler = handler;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //设置值,不需要空值处理器参与,因此只需要通过next来决定是否转发请求
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

  /** 声明类型为字段的声明类型 */
  public Class getKnownEgressType() {
    //这里应该为field.getType()
    return field.getClass();
  }
}