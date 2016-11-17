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

import org.mvel2.DataConversion;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

/** 动态字段访问器，表示实际的值与当前字段类型可能存在不匹配的情况(这时需要进行类型转换) */
@SuppressWarnings({"unchecked"})
public class DynamicFieldAccessor implements AccessorNode {
  private AccessorNode nextNode;
  /** 字段信息 */
  private Field field;
  /** 字段信息的声明类型,或者是期望的类型 */
  private Class targetType;

  public DynamicFieldAccessor() {
  }

  /** 使用字段进行构建相应的访问器 */
  public DynamicFieldAccessor(Field field) {
    setField(field);
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //因为是获取值,因此不需要进行类型转换
    try {
      if (nextNode != null) {
        return nextNode.getValue(field.get(ctx), elCtx, vars);
      }
      else {
        return field.get(ctx);
      }
    }
    catch (Exception e) {
      throw new RuntimeException("unable to access field", e);
    }

  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    try {
      //有下个节点,因此相应的转换工具由next来进行,这里不作任何处理
      if (nextNode != null) {
        return nextNode.setValue(field.get(ctx), elCtx, variableFactory, value);
      }
      else {
        //设置值,需要根据相应的字段类型进行类型转换,以转换成相兼容的类型
        field.set(ctx, DataConversion.convert(value, targetType));
        return value;
      }
    }
    catch (Exception e) {
      throw new RuntimeException("unable to access field", e);
    }
  }

  public Field getField() {
    return field;
  }

  /** 设置相应的字段信息,同时设置相应的目标类型 */
  public void setField(Field field) {
    this.field = field;
    this.targetType = field.getType();
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public Class getKnownEgressType() {
    return targetType;
  }
}
