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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 使用当前工厂记录相应的解析器，并在特定时间将当前存储转换到外部存储的解析器工厂
 * 其遭到<code>{@link CachedMapVariableResolverFactory}</code>大部分相同，所不一样的是，添加新的解析器，新的存储值和映射
 * 并不是实时地添加到相应的map中，而是通过某个一方法<code>externalize</code>再回复到相应的map当中
 * 即一个为实时，一个是初始处理+结果返回
 */
@SuppressWarnings({"unchecked"})
public class CachingMapVariableResolverFactory extends BaseVariableResolverFactory {
  /**
   * 初始填充的变量存储信息
   * Holds the instance of the variables.
   */
  protected Map<String, Object> variables;

  /** 通过外部map来构建相应的处理器 */
  public CachingMapVariableResolverFactory(Map variables) {
    this.variables = variables;
  }

  public VariableResolver createVariable(String name, Object value) {
    VariableResolver vr;

    try {
      (vr = getVariableResolver(name)).setValue(value);
      return vr;
    }
    catch (UnresolveablePropertyException e) {
      addResolver(name, vr = new SimpleSTValueResolver(value, null, true));
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

    if (vr != null && vr.getType() != null) {
      throw new RuntimeException("variable already defined within scope: " + vr.getType() + " " + name);
    }
    else {
      addResolver(name, vr = new SimpleSTValueResolver(value, type, true));
      return vr;
    }
  }

  /** 获取变量解析器，如果当前没有，则尝试从传入的map中创建新的值信息,最终返回相应的解析器 */
  public VariableResolver getVariableResolver(String name) {
    VariableResolver vr = variableResolvers.get(name);
    if (vr != null) {
      return vr;
    }
    else if (variables.containsKey(name)) {
      //这里通过一个可更新标记的解析器来进行记录,后续即可根据标识判断是否有修改
      variableResolvers.put(name, vr = new SimpleSTValueResolver(variables.get(name), null));
      return vr;
    }
    else if (nextFactory != null) {
      return nextFactory.getVariableResolver(name);
    }

    throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
  }


  /** 从当前解析器存储+外部map+委托中共同判定是否可解析 */
  public boolean isResolveable(String name) {
    return (variableResolvers.containsKey(name))
        || (variables != null && variables.containsKey(name))
        || (nextFactory != null && nextFactory.isResolveable(name));
  }

  protected VariableResolver addResolver(String name, VariableResolver vr) {
    variableResolvers.put(name, vr);
    return vr;
  }

  /**
   * 外部化，即将当前自身的添加的解析器的值到外部的存储中
   * 由于在处理过程中可以会引用外部map中的值，导致覆盖处理，因此只处理有变化的
   * 变化的包括直接添加到自身解析器中或者是由外部map转换并且在过程中有修改值的
   */
  public void externalize() {
    for (Map.Entry<String, VariableResolver> entry : variableResolvers.entrySet()) {
      if (entry.getValue().getFlags() == -1) variables.put(entry.getKey(), entry.getValue().getValue());
    }
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

  /** 清除当前及外部的解析器 */
  public void clear() {
    variableResolvers.clear();
    variables.clear();
  }
}
