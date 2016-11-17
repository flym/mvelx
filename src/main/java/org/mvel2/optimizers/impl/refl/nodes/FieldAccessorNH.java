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
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Field;

import static org.mvel2.DataConversion.convert;

/** 字段访问器，附带空处理的情况 */
public class FieldAccessorNH implements AccessorNode {
  private AccessorNode nextNode;
  /** 当前字段 */
  private Field field;
  /** 是否需要转型 */
  private boolean coercionRequired = false;
  /** 相应的空值处理器 */
  private PropertyHandler nullHandler;

  /** 使用字段以及相应的空值处理器构建相应的字段访问器 */
  public FieldAccessorNH(Field field, PropertyHandler handler) {
    this.field = field;
    this.nullHandler = handler;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    try {
      Object v = field.get(ctx);
      //如果返回值为null,则调用相应的空值处理器
      if (v == null) v = nullHandler.getProperty(field.getName(), elCtx, vars);


      if (nextNode != null) {
        return nextNode.getValue(v, elCtx, vars);
      }
      else {
        return v;
      }
    }
    catch (Exception e) {
      throw new RuntimeException("unable to access field", e);
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    try {
      if (nextNode != null) {
        //此处有bug,应该先调用field.get(ctx)获取相应的值
        return nextNode.setValue(ctx, elCtx, variableFactory, value);
      }
      //先尝试参数不作类型转换,出错了再转换
      else if (coercionRequired) {
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
      //类型转换之后,还会失败,则直接报错
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