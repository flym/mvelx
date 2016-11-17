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

package org.mvel2.integration;

import java.io.Serializable;
import java.util.Set;

/**
 * 变量解析器工厂，用于在不同的场景中创建各种不同的解析器，然后根据相应的变量名再获取相应的解析器信息
 * 主要的工作围绕着相应的变量名以及相应的下标(处理数组)进行处理
 * 多个变量解析器工厂可以协同工作，通过next来进行委托处理(类似于classLoader的机制)
 * 根据内部实现的不同，不同的解析器工厂在其中所处理的数据以及所处理变量也会有所不同
 * 同时变量工厂也承担了一个称之为作用域和概念,即不同的变量工厂,可以叠加以描述不同的作用域概念
 * A VariableResolverFactory is the primary integration point for tying in external variables.  The factory is
 * responsible for returing {@link org.mvel2.integration.VariableResolver}'s to the MVEL runtime.  Factories are
 * also structured in a chain to maintain locality-of-reference.
 */
public interface VariableResolverFactory extends Serializable {
  /**
   * 根据变量名和相应的值创建出一个变量解析器
   * 此方法还有修改对象值的作用,如果相应的变量已经存在,则进行数据修改
   * 此方法可以理解为save or update
   * Creates a new variable.  This probably doesn't need to be implemented in most scenarios.  This is
   * used for variable assignment.
   *
   * @param name  - name of the variable being created
   * @param value - value of the variable
   * @return instance of the variable resolver associated with the variable
   */
  public VariableResolver createVariable(String name, Object value);

  /** 根据下标，变量名，值创建出一个变量解析器,以便后续通过下标可以拿到相应的解析器 */
  public VariableResolver createIndexedVariable(int index, String name, Object value);


  /**
   * 根据变量名+相应的值以及期望的类型创建出变量解析器
   * 调用此创建的前提必须是相应的变量名必须是新出现的,如果是之前已经创建好了,则会报相应的错误
   * 可以理解为此方法为创建对象的语义
   * Creates a new variable, and assigns a static type. It is expected the underlying factory and resolver
   * will enforce this.
   *
   * @param name  - name of the variable being created
   * @param value - value of the variable
   * @param type  - the static type
   * @return instance of the variable resolver associated with the variable
   */
  public VariableResolver createVariable(String name, Object value, Class<?> type);

  /** 根据变量名，下标，值，以及期望的类型创建出相应的变量解析器 */
  public VariableResolver createIndexedVariable(int index, String name, Object value, Class<?> typee);

  /** 根据下标重新设置相应的变量解析器，以达到替换或者设置的目的 */
  public VariableResolver setIndexedVariableResolver(int index, VariableResolver variableResolver);


  /**
   * 拿到在当前解析工厂中的下一个工厂，即拿到相委托的处理工厂
   * Returns the next factory in the factory chain.  MVEL uses a hierarchical variable resolution strategy,
   * much in the same way as ClassLoaders in Java.   For performance reasons, it is the responsibility of
   * the individual VariableResolverFactory to pass off to the next one.
   *
   * @return instance of the next factory - null if none.
   */
  public VariableResolverFactory getNextFactory();

  /**
   * 设置相应的委托处理工厂
   * Sets the next factory in the chain. Proper implementation:
   * <code>
   * <p/>
   * return this.nextFactory = resolverFactory;
   * </code>
   *
   * @param resolverFactory - instance of next resolver factory
   * @return - instance of next resolver factory
   */
  public VariableResolverFactory setNextFactory(VariableResolverFactory resolverFactory);

  /**
   * 获取指定变量名的变量解析器
   * Return a variable resolver for the specified variable name.  This method is expected to traverse the
   * heirarchy of ResolverFactories.
   *
   * @param name - variable name
   * @return - instance of the VariableResolver for the specified variable
   */
  public VariableResolver getVariableResolver(String name);


  /** 根据之前存储的变量下标来获取相应的变量解析器 */
  public VariableResolver getIndexedVariableResolver(int index);

  /**
   * 判断当前解析工厂是否就是指定属性的直接解析器(因为它有多个解析链)
   * Determines whether or not the current VariableResolverFactory is the physical target for the actual
   * variable.
   *
   * @param name - variable name
   * @return - boolean indicating whether or not factory is the physical target
   */
  public boolean isTarget(String name);


  /**
   * 判定变量解析器工厂(以及委托工厂)是否能够解析此变量
   * Determines whether or not the variable is resolver in the chain of factories.
   *
   * @param name - variable name
   * @return - boolean
   */
  public boolean isResolveable(String name);


  /**
   * 获取当前解析器工厂(不包括委托)能够处理的变量名列表
   * 上面的不包括委托 在当前的实现中尽量不包括，但有些可能仍包括委托中的变量名，这取决于具体的实现
   * Return a list of known variables inside the factory.  This method should not recurse into other factories.
   * But rather return only the variables living inside this factory.
   *
   * @return 已知能解析的变量名集合
   */
  //备注 此接口因为实现存在二义性，实际上没有多在用处，不会在核心逻辑中起作用
  public Set<String> getKnownVariables();

  /** 读取指定属性或参数的索引下标位置(然后可以通过getIndexedVariableResolver来获取解析器进行处理) */
  public int variableIndexOf(String name);

  /** 当前解析工厂是否是基于索引进行处理的 */
  public boolean isIndexedFactory();

  /** 当前工厂是否已处理结束(如果已处理结束，则整个结果将返回) */
  public boolean tiltFlag();

  /** 设置当前处理结束标记 */
  public void setTiltFlag(boolean tilt);
}
