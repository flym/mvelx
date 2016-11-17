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
import org.mvel2.util.PropertyTools;

import java.lang.reflect.Field;

import static org.mvel2.DataConversion.convert;

/** 表示一个字段的访问器 */
public class FieldAccessor implements AccessorNode {
  /** 下一个节点 */
  private AccessorNode nextNode;
  /** 当前所对应的字段信息 */
  private Field field;
  /** 是否需要对参数进行类型转换,是一个逻辑处理变量 */
  private boolean coercionRequired = false;
  /** 当前字段是否是基本类型 */
  private boolean primitive;


  public FieldAccessor() {
  }

  /** 通过字段构建起相应的访问器 */
  public FieldAccessor(Field field) {
    primitive = (this.field = field).getType().isPrimitive();
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    try {
      //直接通过是否有next节点决定相应的处理流程
      if (nextNode != null) {
        return nextNode.getValue(field.get(ctx), elCtx, vars);
      }
      else {
        return field.get(ctx);
      }
    }
    catch (Exception e) {
      throw new RuntimeException("unable to access field: " + field.getName(), e);
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //有下一个节点,则表示当前节点只需要get调用即可
    if (nextNode != null) {
      try {
        //由当前字段是否是基本类型决定是否需要进行类型转换,即创建为基本的0数据
        return nextNode.setValue(field.get(ctx), elCtx, variableFactory, value == null && primitive ? PropertyTools.getPrimitiveInitialValue(field.getType()) : value);
      }
      catch (Exception e) {
        throw new RuntimeException("unable to access field", e);
      }
    }

    try {

      //先尝试不会进行类型转换,如果访问出错了,再调回来,重新运行
      if (coercionRequired) {
        field.set(ctx, value = convert(ctx, field.getClass()));
        return value;
      }
      else {
        field.set(ctx, value);
        return value;
      }
    }
    catch (IllegalArgumentException e) {
      if (!coercionRequired) {
        coercionRequired = true;
        return setValue(ctx, elCtx, variableFactory, value);
      }
      //后续如果类型转换出错了还会报错,则直接报相应的错误
      throw new RuntimeException("unable to bind property", e);
    }
    catch (Exception e) {
      throw new RuntimeException("unable to access field", e);
    }
  }

  public Field getField() {
    return field;
  }

  public void setField(Field field) {
    this.field = field;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public Class getKnownEgressType() {
    //这里有问题,应该为字段声明类型
    return field.getClass();
  }
}
