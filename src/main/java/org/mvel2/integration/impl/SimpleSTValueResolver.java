/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
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

package org.mvel2.integration.impl;

import org.mvel2.integration.VariableResolver;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;

/**
 * 通过声明值类型来保证值的类型与预期相一致的解析器。即相应的类型是由创建时决定的，后续的值都必须要转换为
 * 相应的类型。
 * 同时，为了更方便地记录值的变化情况，通过特别的标记来判定值是否被修改过,以方便后续根据此标记位进行额外的处理.如将相应的值重新设置到其它容器中
 */
public class SimpleSTValueResolver implements VariableResolver {
  /** 相应的值信息 */
  private Object value;
  /** 声明的类型(值的类型) */
  private Class type;
  /** 值是否有变化 */
  private boolean updated = false;

  /** 根据值以及声明的类型进行创建解析器 */
  public SimpleSTValueResolver(Object value, Class type) {
    this.value = handleTypeCoercion(type, value);
    this.type = type;
  }

  /** 根据值以及声明的类型进行创建解析器,同时设定相应的修改标记 */
  public SimpleSTValueResolver(Object value, Class type, boolean updated) {
    this.value = handleTypeCoercion(type, value);
    this.type = type;
    this.updated = updated;
  }

  /** 无变量名 */
  public String getName() {
    return null;
  }

  public Class getType() {
    return type;
  }

  public void setStaticType(Class type) {
    this.type = type;
  }

  /** 根据修改情况返回相应的标记位 */
  public int getFlags() {
    return updated ? -1 : 0;
  }

  public Object getValue() {
    return value;
  }

  /** 设置并转换值，同时记录修改标记位 */
  public void setValue(Object value) {
    updated = true;
    //可能存在数据转换
    this.value = handleTypeCoercion(type, value);
  }

  /** 对即将处理的值进行转换,转换为相对应的类型 */
  private static Object handleTypeCoercion(Class type, Object value) {
    if (type != null && value != null && value.getClass() != type) {
      if (!canConvert(type, value.getClass())) {
        throw new RuntimeException("cannot assign " + value.getClass().getName() + " to type: "
            + type.getName());
      }
      try {
        return convert(value, type);
      }
      catch (Exception e) {
        throw new RuntimeException("cannot convert value of " + value.getClass().getName()
            + " to: " + type.getName());
      }
    }
    return value;
  }

}