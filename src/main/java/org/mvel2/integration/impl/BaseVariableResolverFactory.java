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
 * 基础实现类，实现解析器工厂的主要处理逻辑
 * 其它实现均继承自此类
 * Use this class to extend you own VariableResolverFactories. It contains most of the baseline implementation needed
 * for the vast majority of integration needs.
 */
public abstract class BaseVariableResolverFactory implements VariableResolverFactory {
  /** 当前解析器工厂能够处理的变量解析器 */
  protected Map<String, VariableResolver> variableResolvers = new HashMap<String, VariableResolver>();
  /** 委托的下一个解析器工厂 */
  protected VariableResolverFactory nextFactory;

  /** 用于描述在基于下标的存储中，当前工厂的处理偏移量，即相应的存储数组并不是完全用于存储当前变量作用域的 */
  protected int indexOffset = 0;
  /** 用于存储当前能够解析的基于下标的变量名集合 */
  protected String[] indexedVariableNames;
  /** 用于存储当前能够解析的基于下标的解析器数组 */
  protected VariableResolver[] indexedVariableResolvers;

  /** 是否终止的标记位 */
  private boolean tiltFlag;

  public VariableResolverFactory getNextFactory() {
    return nextFactory;
  }

  public VariableResolverFactory setNextFactory(VariableResolverFactory resolverFactory) {
    return nextFactory = resolverFactory;
  }

  public VariableResolver getVariableResolver(String name) {
    if (isResolveable(name)) {
      //如果当前能处理,则从当前作用域中获取
      if (variableResolvers.containsKey(name)) {
        return variableResolvers.get(name);
      }
      //从委托(父级)获取
      else if (nextFactory != null) {
        return nextFactory.getVariableResolver(name);
      }
    }

    throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
  }

  /** 委托解析器工厂能够解析此变量名 */
  public boolean isNextResolveable(String name) {
    return nextFactory != null && nextFactory.isResolveable(name);
  }

  /** 将相应的解析器工厂追加到委托的最后,即保底处理 */
  public void appendFactory(VariableResolverFactory resolverFactory) {
    if (nextFactory == null) {
      nextFactory = resolverFactory;
    }
    else {
      VariableResolverFactory vrf = nextFactory;
      while (vrf.getNextFactory() != null) {
        vrf = vrf.getNextFactory();
      }
      vrf.setNextFactory(nextFactory);
    }
  }

  /** 将特定的委托类插入到当前委托 之间，即先走参数的委托，再走原来的委托  */
  public void insertFactory(VariableResolverFactory resolverFactory) {
    if (nextFactory == null) {
      nextFactory = resolverFactory;
    }
    else {
      resolverFactory.setNextFactory(nextFactory = resolverFactory);
    }
  }


  public Set<String> getKnownVariables() {
    if (nextFactory == null) {
      return new HashSet<String>(variableResolvers.keySet());
      //   return new HashSet<String>(0);
    }
    else {
      HashSet<String> vars = new HashSet<String>(variableResolvers.keySet());
      vars.addAll(nextFactory.getKnownVariables());
      return vars;
    }
  }

  public VariableResolver createIndexedVariable(int index, String name, Object value) {
    //因为当前默认不支持下标存储,则委托给next来处理
    if (nextFactory != null) {
      return nextFactory.createIndexedVariable(index - indexOffset, name, value);
    }
    else {
      throw new RuntimeException("cannot create indexed variable: " + name + "(" + index + "). operation not supported by resolver: " + this.getClass().getName());
    }
  }

  public VariableResolver getIndexedVariableResolver(int index) {
    //当前默认不支持下标,委托给子级处理
    if (nextFactory != null) {
      return nextFactory.getIndexedVariableResolver(index - indexOffset);
    }
    else {
      throw new RuntimeException("cannot access indexed variable: " + index + ".  operation not supported by resolver: " + this.getClass().getName());
    }
  }

  public VariableResolver createIndexedVariable(int index, String name, Object value, Class<?> type) {
    //当前默认不支持下标,委托给子级处理
    if (nextFactory != null) {
      return nextFactory.createIndexedVariable(index - indexOffset, name, value, type);
    }
    else {
      throw new RuntimeException("cannot access indexed variable: " + name + "(" + index + ").  operation not supported by resolver.: " + this.getClass().getName());
    }
  }

  /** 返回当前作用域的变量解析器 */
  public Map<String, VariableResolver> getVariableResolvers() {
    return variableResolvers;
  }

  public void setVariableResolvers(Map<String, VariableResolver> variableResolvers) {
    this.variableResolvers = variableResolvers;
  }

  /** 所获当前工厂已经的基于下标工作的变量名列表 */
  public String[] getIndexedVariableNames() {
    return indexedVariableNames;
  }

  public void setIndexedVariableNames(String[] indexedVariableNames) {
    this.indexedVariableNames = indexedVariableNames;
  }

  /** 基于下标的变量列表，拿到指定变量名的下标信息 */
  public int variableIndexOf(String name) {
    if (indexedVariableNames != null)
      for (int i = 0; i < indexedVariableNames.length; i++) {
        if (name.equals(indexedVariableNames[i])) {
          return i;
        }
      }
    return -1;
  }

  public VariableResolver setIndexedVariableResolver(int index, VariableResolver resolver) {
    if (indexedVariableResolvers == null) {
      return (indexedVariableResolvers = new VariableResolver[indexedVariableNames.length])[index - indexOffset] = resolver;
    }
    else {
      return indexedVariableResolvers[index - indexOffset] = resolver;
    }
  }

  /** 默认不是全基于下标工作的 */
  public boolean isIndexedFactory() {
    return false;
  }

  public boolean tiltFlag() {
    return tiltFlag;
  }

  public void setTiltFlag(boolean tiltFlag) {
    this.tiltFlag = tiltFlag;
    //级联处理
    if (nextFactory != null) nextFactory.setTiltFlag(tiltFlag);
  }
}
