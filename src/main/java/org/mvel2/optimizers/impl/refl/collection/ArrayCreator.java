/**
 * MVEL (The MVFLEX Expression Language)
 * <p>
 * Copyright (C) 2007 Christopher Brock, MVFLEX/Valhalla Project and the Codehaus
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvel2.optimizers.impl.refl.collection;

import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Array;

import static java.lang.reflect.Array.newInstance;

/**
 * 根据一个已有的类型以及相应的数组中的每一项值构建出相应的数组对象
 * 用于内联方式的数组创建处理
 * @author Christopher Brock
 */
public class ArrayCreator implements Accessor {
  /** 数组中的每一项的值 */
  public Accessor[] template;
  /** 构建出来的数组类型 */
  private Class arrayType;

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    //根据所定义的数组类型进行不同的处理,使用object或者是使用array.newInstance来处理
    if (Object.class.equals(arrayType)) {
      Object[] newArray = new Object[template.length];

      for (int i = 0; i < newArray.length; i++) {
        newArray[i] = template[i].getValue(ctx, elCtx, variableFactory);
      }

      return newArray;
    }
    else {
      Object newArray = newInstance(arrayType, template.length);
      for (int i = 0; i < template.length; i++) {
        Array.set(newArray, i, template[i].getValue(ctx, elCtx, variableFactory));
      }

      return newArray;
    }
  }

  /** 返回相应的数组对象中的访问器 */
  public Accessor[] getTemplate() {
    return template;
  }

  /** 使用数组对象值+相应的定义类型进行构建 */
  public ArrayCreator(Accessor[] template, Class arrayType) {
    this.template = template;
    this.arrayType = arrayType;
  }

  /** 因为是创建对象,因此不能设置值信息 */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    return null;
  }

  /** 相应的声明类型为所定义的类型 */
  public Class getKnownEgressType() {
    return arrayType;
  }
}
