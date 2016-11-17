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

import org.mvel2.ast.Function;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;

import java.util.HashMap;

/**
 * 用于支持在一个函数体内进行变量解析的处理工厂
 * 其首先是会对相应的函数参数信息进行解析和处理，然后对新的变量信息采用扩展变量表的方式进行处理
 * 即扩展原来的变量存储，以便容下相应的变量信息
 * 此变量工厂仅在函数调用(function)时使用
 * 整个体系采用下标来维护变量信息，因此父类中的map体系不再有用,
 * map在此类中用作key的占位符，仅表示存在此变量的解析(即fastpath判定)，但具体为哪个解析仍从下标体系中获取
 */
public class FunctionVariableResolverFactory extends BaseVariableResolverFactory implements LocalVariableResolverFactory {
  /** 相对应的函数 */
  private Function function;

  /** 根据相应的函数,函数定义参数名，以及传递过来的参数值，以及委托类一起进行构建出工厂实例 */
  public FunctionVariableResolverFactory(Function function, VariableResolverFactory nextFactory, String[] indexedVariables, Object[] parameters) {
    this.function = function;

    this.variableResolvers = new HashMap<String, VariableResolver>();
    this.nextFactory = nextFactory;
    //下标解析器填充
    this.indexedVariableResolvers = new VariableResolver[(this.indexedVariableNames = indexedVariables).length];
    for (int i = 0; i < parameters.length; i++) {
      //填充占位符
      variableResolvers.put(indexedVariableNames[i], null);
      //填充值变量
      this.indexedVariableResolvers[i] = new SimpleValueResolver(parameters[i]);
      //     variableResolvers.put(indexedVariableNames[i], this.indexedVariableResolvers[i] = new SimpleValueResolver(parameters[i]));
    }
  }

  public boolean isResolveable(String name) {
    return variableResolvers.containsKey(name) || (nextFactory != null && nextFactory.isResolveable(name));
  }

  /** 修改变量值信息(如果有)，否则创建新的变量解析器 */
  public VariableResolver createVariable(String name, Object value) {
    VariableResolver resolver = getVariableResolver(name);
    //之前没有相应的解析器,则准备新建一个
    if (resolver == null) {
      int idx = increaseRegisterTableSize();
      this.indexedVariableNames[idx] = name;
      this.indexedVariableResolvers[idx] = new SimpleValueResolver(value);
      variableResolvers.put(name, null);

      //     variableResolvers.put(name, this.indexedVariableResolvers[idx] = new SimpleValueResolver(value));
      return this.indexedVariableResolvers[idx];
    }
    else {
      resolver.setValue(value);
      return resolver;
    }
  }

  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    VariableResolver vr = this.variableResolvers != null ? this.variableResolvers.get(name) : null;
    if (vr != null && vr.getType() != null) {
      throw new RuntimeException("variable already defined within scope: " + vr.getType() + " " + name);
    }
    else {
      return createIndexedVariable(variableIndexOf(name), name, value);
    }
  }

  /** 修改指定下标原的解析器 */
  public VariableResolver createIndexedVariable(int index, String name, Object value) {
    index = index - indexOffset;
    //之前存在,则直接修改
    if (indexedVariableResolvers[index] != null) {
      indexedVariableResolvers[index].setValue(value);
    }
    else {
      //直接创建相应的解析器
      indexedVariableResolvers[index] = new SimpleValueResolver(value);
    }

    //快速占位处理
    variableResolvers.put(name, null);

    return indexedVariableResolvers[index];
  }

  public VariableResolver createIndexedVariable(int index, String name, Object value, Class<?> type) {
    index = index - indexOffset;
    if (indexedVariableResolvers[index] != null) {
      indexedVariableResolvers[index].setValue(value);
    }
    else {
      indexedVariableResolvers[index] = new SimpleValueResolver(value);
    }
    return indexedVariableResolvers[index];
  }

  public VariableResolver getIndexedVariableResolver(int index) {
    if (indexedVariableResolvers[index] == null) {
      /**
       * If the register is null, this means we need to forward-allocate the variable onto the
       * register table.
       */
      return indexedVariableResolvers[index] = super.getVariableResolver(indexedVariableNames[index]);
    }
    return indexedVariableResolvers[index];
  }

  /** 获取相应变量名的解析器,走下标查找 */
  public VariableResolver getVariableResolver(String name) {
    int idx;
    //   if (variableResolvers.containsKey(name)) return variableResolvers.get(name);
    if ((idx = variableIndexOf(name)) != -1) {
      if (indexedVariableResolvers[idx] == null) {
        indexedVariableResolvers[idx] = new SimpleValueResolver(null);
      }
      variableResolvers.put(indexedVariableNames[idx], null);
      return indexedVariableResolvers[idx];
    }

    return super.getVariableResolver(name);
  }

  /** 当前是基于下标工作的工厂 */
  public boolean isIndexedFactory() {
    return true;
  }

  public boolean isTarget(String name) {
    return variableResolvers.containsKey(name) || variableIndexOf(name) != -1;
  }

  /** 增长相应的变量表以及解析器表，以填充新的变量信息,返回可以填充的位置。长度每次+1 */
  private int increaseRegisterTableSize() {
    String[] oldNames = indexedVariableNames;
    VariableResolver[] oldResolvers = indexedVariableResolvers;

    int newLength = oldNames.length + 1;
    indexedVariableNames = new String[newLength];
    indexedVariableResolvers = new VariableResolver[newLength];

    for (int i = 0; i < oldNames.length; i++) {
      indexedVariableNames[i] = oldNames[i];
      indexedVariableResolvers[i] = oldResolvers[i];
    }

    return newLength - 1;
  }

  /** 使用新的参数值信息更新原来已经填充的参数值,即替换整个参数值信息 */
  public void updateParameters(Object[] parameters) {
    //    this.indexedVariableResolvers = new VariableResolver[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      this.indexedVariableResolvers[i] = new SimpleValueResolver(parameters[i]);
    }
//        for (int i = parameters.length; i < indexedVariableResolvers.length; i++) {
//            this.indexedVariableResolvers[i] = null;
//        }
  }

  public VariableResolver[] getIndexedVariableResolvers() {
    return this.indexedVariableResolvers;
  }

  public void setIndexedVariableResolvers(VariableResolver[] vr) {
    this.indexedVariableResolvers = vr;
  }

  /** 返回相应的函数 */
  public Function getFunction() {
    return function;
  }

  public void setIndexOffset(int offset) {
    this.indexOffset = offset;
  }


  /** 函数内层返回不再影响到外层的执行,默认为false,即会影响到 */
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