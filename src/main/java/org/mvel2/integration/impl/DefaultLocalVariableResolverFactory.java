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
import java.util.Map;

/**
 * 一个基本能够实现完整的变量+下标的变量工厂，即完成原BaseVar中所不能完成的实现(主要为下标类处理)
 * 此类实现LocalVariable，意思即处理的变量为一个在指定区间内工作的,是有作用域限制的
 */
public class DefaultLocalVariableResolverFactory extends MapVariableResolverFactory implements LocalVariableResolverFactory {
  public DefaultLocalVariableResolverFactory() {
    super(new HashMap<String, Object>());
  }

  public DefaultLocalVariableResolverFactory(Map<String, Object> variables) {
    super(variables);
  }

  public DefaultLocalVariableResolverFactory(Map<String, Object> variables, VariableResolverFactory nextFactory) {
    super(variables, nextFactory);
  }

  public DefaultLocalVariableResolverFactory(Map<String, Object> variables, boolean cachingSafe) {
    super(variables);
  }

  public DefaultLocalVariableResolverFactory(VariableResolverFactory nextFactory) {
    super(new HashMap<String, Object>(), nextFactory);
  }

  public DefaultLocalVariableResolverFactory(VariableResolverFactory nextFactory, String[] indexedVariables) {
    super(new HashMap<String, Object>(), nextFactory);
    this.indexedVariableNames = indexedVariables;
    this.indexedVariableResolvers = new VariableResolver[indexedVariables.length];
  }


  /** 实现基于下标访问变量解析器的语义 */
  public VariableResolver getIndexedVariableResolver(int index) {
    if (indexedVariableNames == null) return null;

    //这里表示相应的变量是存在的,但没有相应的解析器,因此进行相应的创建
    if (indexedVariableResolvers[index] == null) {
      /**
       * If the register is null, this means we need to forward-allocate the variable onto the
       * register table.
       */
      return indexedVariableResolvers[index] = super.getVariableResolver(indexedVariableNames[index]);
    }
    return indexedVariableResolvers[index];
  }

  public VariableResolver getVariableResolver(String name) {
    if (indexedVariableNames == null) return super.getVariableResolver(name);

    //以下表示开启相应的下标解析,因此会首先从当前下标解析器中进行处理
    int idx;
    //   if (variableResolvers.containsKey(name)) return variableResolvers.get(name);
    //相应的下标存在
    if ((idx = variableIndexOf(name)) != -1) {
      //没有解析器,则马上创建一个
      if (indexedVariableResolvers[idx] == null) {
        indexedVariableResolvers[idx] = new SimpleValueResolver(null);
      }
      //同时放到相应的map解析器中,双向存储
      variableResolvers.put(indexedVariableNames[idx], null);
      return indexedVariableResolvers[idx];
    }

    return super.getVariableResolver(name);
  }

  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    if (indexedVariableNames == null) return super.createVariable(name, value, type);

    VariableResolver vr;
    boolean newVar = false;

    try {
      int idx;
      //如果当前已存在相应的变量,则创建起相应的解析器
      if ((idx = variableIndexOf(name)) != -1) {
        vr = new SimpleValueResolver(value);
        if (indexedVariableResolvers[idx] == null) {
          indexedVariableResolvers[idx] = vr;
        }
        variableResolvers.put(indexedVariableNames[idx], vr);
        vr = indexedVariableResolvers[idx];

        newVar = true;
      }
      else {
        return super.createVariable(name, value, type);
      }

    }
    catch (UnresolveablePropertyException e) {
      vr = null;
    }

    //进行相应的类型判定,即不允许重复添加
    if (!newVar && vr != null && vr.getType() != null) {
      throw new RuntimeException("variable already defined within scope: " + vr.getType() + " " + name);
    }
    else {
      addResolver(name, vr = new MapVariableResolver(variables, name, type)).setValue(value);
      return vr;
    }
  }

  /** 不要终止委托类标记,即当前内部的提前返回,并不需要外层同样进行处理 */
  private boolean noTilt = false;

  public VariableResolverFactory setNoTilt(boolean noTilt) {
    this.noTilt = noTilt;
    return this;
  }

  @Override
  public void setTiltFlag(boolean tiltFlag) {
    if (!noTilt) super.setTiltFlag(tiltFlag);
  }
}
