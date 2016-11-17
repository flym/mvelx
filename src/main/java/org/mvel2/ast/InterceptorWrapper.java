/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.ast;

import org.mvel2.ParserContext;
import org.mvel2.integration.Interceptor;
import org.mvel2.integration.VariableResolverFactory;

/**
 * 表示一个拦截器节点，通过对一个节点的引用达到执行前调用和执行后调用的目的
 * @author Christopher Brock
 */
public class InterceptorWrapper extends ASTNode {
  /** 当前所使用的拦截器 */
  private Interceptor interceptor;
  /** 当前所拦截的节点信息 */
  private ASTNode node;

  public InterceptorWrapper(Interceptor interceptor, ASTNode node, ParserContext pCtx) {
    super(pCtx);
    this.interceptor = interceptor;
    this.node = node;
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //在调用前操作
    interceptor.doBefore(node, factory);
    //调用后操作
    interceptor.doAfter(ctx = node.getReducedValueAccelerated(ctx, thisValue, factory), node, factory);
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    interceptor.doBefore(node, factory);
    interceptor.doAfter(ctx = node.getReducedValue(ctx, thisValue, factory), node, factory);
    return ctx;
  }
}
