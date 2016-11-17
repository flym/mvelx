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

import org.mvel2.UnresolveablePropertyException;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 与MapVarResolver相对应，但实际上此类的实现与<code>{@link org.mvel2.integration.impl.CachedMapVariableResolverFactory}</code>
 * 是一样的，因此任何使用此类的地方，均可以使用Cached来进行替换
 * 这里的map,可以认为用在一些context感知的地方,即需要重建scope,在用完之后,马上就会释放的场景,如执行时新建上下文,
 * 主要是在如for循环 while循环 if语句等这些需要处理语法块,但处理完之后,作用域就抛弃的情况
 * 执行完这个map就不会再使用的场景
 */
@SuppressWarnings({"unchecked"})
public class MapVariableResolverFactory extends BaseVariableResolverFactory {
  /**
   * 用来存储当前作用域的参数名信息,则通过当前map来判定是否能解析变量.而具体的解析器由放在父类的resolverMap中
   * Holds the instance of the variables.
   */
  protected Map<String, Object> variables;

  /** 使用内部map不对外公开 */
  public MapVariableResolverFactory() {
    this.variables = new HashMap();
  }

  /** 使用外部的map来存储数据值,即与外部共用map */
  public MapVariableResolverFactory(Map variables) {
    this.variables = variables;
  }

  /** 使用外部map以及委托解析器工厂进行创建 */
  public MapVariableResolverFactory(Map<String, Object> variables, VariableResolverFactory nextFactory) {
    this.variables = variables;
    this.nextFactory = nextFactory;
  }

  /**
   * 与下面方法相同
   * @see MapVariableResolverFactory(Map)
   * @param cachingSafe 无用参数
   */
  public MapVariableResolverFactory(Map<String, Object> variables, boolean cachingSafe) {
    this.variables = variables;
  }

  public VariableResolver createVariable(String name, Object value) {
    VariableResolver vr;

    //采用try catch来决定是否新建变量
    try {
      (vr = getVariableResolver(name)).setValue(value);
      return vr;
    }
    catch (UnresolveablePropertyException e) {
      addResolver(name, vr = new MapVariableResolver(variables, name)).setValue(value);
      return vr;
    }
  }

  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    VariableResolver vr;
    try {
      vr = getVariableResolver(name);
    }
    catch (UnresolveablePropertyException e) {
      vr = null;
    }

    //不允许重复创建对象
    if (vr != null && vr.getType() != null) {
      throw new RuntimeException("variable already defined within scope: " + vr.getType() + " " + name);
    }
    else {
      //使用map变量解析器,即相应的解析器与外部map共用数据
      addResolver(name, vr = new MapVariableResolver(variables, name, type)).setValue(value);
      return vr;
    }
  }

  public VariableResolver getVariableResolver(String name) {
    //先判定当前是否已经有解析器
    VariableResolver vr = variableResolvers.get(name);
    if (vr != null) {
      return vr;
    }
    //当前没有解析器,但当前变量域中存在此变量
    else if (variables.containsKey(name)) {
      variableResolvers.put(name, vr = new MapVariableResolver(variables, name));
      return vr;
    }
    //都没有,则转由next处理
    else if (nextFactory != null) {
      return nextFactory.getVariableResolver(name);
    }

    throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
  }


  /** 通过解析器map+变量名map+next共同判定是否能解析相应的name */
  public boolean isResolveable(String name) {
    return (variableResolvers.containsKey(name))
        || (variables != null && variables.containsKey(name))
        || (nextFactory != null && nextFactory.isResolveable(name));
  }

  protected VariableResolver addResolver(String name, VariableResolver vr) {
    variableResolvers.put(name, vr);
    return vr;
  }


  public boolean isTarget(String name) {
    return variableResolvers.containsKey(name);
  }

  public Set<String> getKnownVariables() {
    if (nextFactory == null) {
      if (variables != null) return new HashSet<String>(variables.keySet());
      return new HashSet<String>(0);
    }
    else {
      if (variables != null) return new HashSet<String>(variables.keySet());
      return new HashSet<String>(0);
    }
  }

  public void clear() {
    variableResolvers.clear();
    variables.clear();
  }
}
