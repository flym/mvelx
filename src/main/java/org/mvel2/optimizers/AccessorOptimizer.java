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

package org.mvel2.optimizers;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;

/** 访问器的优先器定义,即对通用的方法器进行优化处理.如普通的a.b,可以优化为一个getter调用,或者是属性访问,而不必要每次都进行查找,探测等 */
public interface AccessorOptimizer {
  /**
   * 必要的初始化,此初始化在整个生命周期中只会被执行一次
   * 注:虽然在实现层面优化器被设计为不是单态的，每次在使用时均会重新newInstance来使用，但init在使用时会被小心的处理
   * 保证只会被初始化一次，也可以理解为此init是专门为静态的初始化准备的
   */
  public void init();

  /** 在相应的解析上下文中，对指定属性，在相应的当前对象以及相应的变量处理中读取相应的属性值信息,创建出相应的优化访问器,并进行访问 */
  public Accessor optimizeAccessor(ParserContext pCtx, char[] property, int start, int offset, Object ctx, Object thisRef,
                                   VariableResolverFactory factory, boolean rootThisRef, Class ingressType);

  /** 创建出相应的设置类优化访问器,对指定的属性 */
  public Accessor optimizeSetAccessor(ParserContext pCtx, char[] property, int start, int offset, Object ctx, Object thisRef,
                                      VariableResolverFactory factory, boolean rootThisRef, Object value, Class ingressType);

  /** 创建一个用于集合访问的优化器,此处的集合为内联集合,即直接{1,2,3}这种直接创建方式 */
  public Accessor optimizeCollection(ParserContext pCtx, Object collectionGraph, Class type, char[] property, int start, int offset, Object ctx, Object thisRef, VariableResolverFactory factory);

  /** 创建一个用于对象创建的访问器 */
  public Accessor optimizeObjectCreation(ParserContext pCtx, char[] property, int start, int offset, Object ctx, Object thisRef, VariableResolverFactory factory);

  /** 获取当前创建的优化访问器处理的结果值 */
  public Object getResultOptPass();

  /** 返回结果的声明类型 */
  public Class getEgressType();

  /** 是否是常量优化 */
  public boolean isLiteralOnly();
}
