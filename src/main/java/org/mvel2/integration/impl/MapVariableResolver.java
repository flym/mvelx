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

import java.util.Map;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;

/**
 * 通过使用外部的一个map来存储相应值，并进行值处理的变量解析器
 * 在实际使用中，多个varResolver会使用同一个map，每个varResolver使用对应的name来描述自己所要处理的变量信息
 * 同时使用声明的类型来标明变量的类型
 * <p/>
 * 即变量的值被存储在一个统一的变量map中
 */
public class MapVariableResolver implements VariableResolver {
  /** 相应的变量名 */
  private String name;
  /** 已知的变量类型(可能为null) */
  private Class<?> knownType;
  /** 存储变量信息的map */
  private Map<String, Object> variableMap;

  /** 通过相应的map+key名来进行构建 */
  public MapVariableResolver(Map<String, Object> variableMap, String name) {
    this.variableMap = variableMap;
    this.name = name;
  }

  /** 通过相应的map+key名+值类型来进行构建 */
  public MapVariableResolver(Map<String, Object> variableMap, String name, Class knownType) {
    this.name = name;
    this.knownType = knownType;
    this.variableMap = variableMap;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setStaticType(Class knownType) {
    this.knownType = knownType;
  }

  /** 改变相应的存储map(可能会有用,当前没有被使用) */
  public void setVariableMap(Map<String, Object> variableMap) {
    this.variableMap = variableMap;
  }

  public String getName() {
    return name;
  }

  public Class getType() {
    return knownType;
  }

  /** 设置并转换值 */
  public void setValue(Object value) {
    //如果有声明类型,则尝试进行类型转换
    if (knownType != null && value != null && value.getClass() != knownType) {
      if (!canConvert(knownType, value.getClass())) {
        throw new RuntimeException("cannot assign " + value.getClass().getName() + " to type: "
            + knownType.getName());
      }
      try {
        value = convert(value, knownType);
      }
      catch (Exception e) {
        throw new RuntimeException("cannot convert value of " + value.getClass().getName()
            + " to: " + knownType.getName());
      }
    }

    //noinspection unchecked
    variableMap.put(name, value);
  }

  public Object getValue() {
    return variableMap.get(name);
  }

  /** 没有特殊的标记 */
  public int getFlags() {
    return 0;
  }
}
