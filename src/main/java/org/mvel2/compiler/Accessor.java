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

package org.mvel2.compiler;

import org.mvel2.integration.VariableResolverFactory;

/** 描述一个属性访问器，在指定的 上下文中获取相应的值信息 */
public interface Accessor {
  /**
   * 获取相应的值信息
   *
   * @param ctx   当前对象上下文(可理解为当前处理对象)
   * @param elCtx 特定的参数上下文(this值)
   */
  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory);

  /**
   * 设置值信息
   *
   * @param ctx   当前对象上下文(可理解为当前处理对象)
   * @param elCtx 特定的参数上下文对象(this值)
   */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value);

  /** 获取已知的返回结果类型 */
  public Class getKnownEgressType();
}
