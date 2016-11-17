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

import org.mvel2.DataTypes;
import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.math.MathProcessor;

/**
 * 在指定上下文中指定位置的变量 ++操作节点
 * @author Christopher Brock
 */
public class IndexedPostFixIncNode extends ASTNode {
  /** 变量的位置 */
  private int register;

  public IndexedPostFixIncNode(int register, ParserContext pCtx) {
    super(pCtx);
    this.register = register;
    //已注册下标,则直接从相应的上下文中获取到变量名,再获取类型即可
    this.egressType = pCtx.getVarOrInputType(pCtx.getIndexedVarNames()[register]);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    VariableResolver vResolver = factory.getIndexedVariableResolver(register);
    //后置计算,先赋值给result,再计算,返回至解析器,最后返回result
    vResolver.setValue(MathProcessor.doOperations(ctx = vResolver.getValue(), Operator.ADD, DataTypes.INTEGER, 1));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译执行相同
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }
}