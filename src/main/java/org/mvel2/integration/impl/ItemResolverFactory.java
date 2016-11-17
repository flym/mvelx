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
import org.mvel2.integration.VariableResolverFactory;


/** 用于描述单个值的变量解析工厂，即可以理解为单个变量指定一个工厂 */
public class ItemResolverFactory extends BaseVariableResolverFactory {
  /** 相应单个解析的封装 */
  private final ItemResolver resolver;

  /** 由一个单值解析器和委托工厂创建起相应的作用域 */
  public ItemResolverFactory(ItemResolver resolver, VariableResolverFactory nextFactory) {
    this.resolver = resolver;
    this.nextFactory = nextFactory;
  }

  /** 创建变量,因为当前为单值作用域,因此如果当前单值变量名不相同,则委托给next处理 */
  public VariableResolver createVariable(String name, Object value) {
    if (isTarget(name)) {
      resolver.setValue(value);
      return resolver;
    }
    else {
      return nextFactory.createVariable(name, value);
    }
  }

  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    //不允许重复新建
    if (isTarget(name)) {
      throw new RuntimeException("variable already defined in scope: " + name);
    }
    else {
      return nextFactory.createVariable(name, value);
    }
  }

  public VariableResolver getVariableResolver(String name) {
    return isTarget(name) ? resolver : nextFactory.getVariableResolver(name);
  }

  /** 当前是否能解析,即当前单值所存储的变量名是否与参数名相同 */
  public boolean isTarget(String name) {
    return resolver.getName().equals(name);
  }

  /** 通过当前单值的变量名和委托解析来判定是否可解析参数名 */
  public boolean isResolveable(String name) {
    return resolver.getName().equals(name) || (nextFactory != null && nextFactory.isResolveable(name));
  }

  /** 通过变量名+值+类型来进行变量解析的解析器 */
  public static class ItemResolver implements VariableResolver {
    /** 相应的变量名 */
    private final String name;
    /** 变量的类型 */
    private Class type = Object.class;
    /** 相应的值 */
    public Object value;

    public ItemResolver(String name, Class type) {
      this.name = name;
      this.type = type;
    }

    public ItemResolver(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public Class getType() {
      return type;
    }

    public void setStaticType(Class type) {
      this.type = type;
    }

    /** 无特殊的标记 */
    public int getFlags() {
      return 0;
    }

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }
  }
}
