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
 * 表示一个前值操作处理的节点，如--x,当前属性已在上下文中下标定位(入参或本地变量)
 * @author Christopher Brock
 */
public class IndexedPreFixDecNode extends ASTNode {
  /** 当前变量所在的下标值 */
  private int register;

  public IndexedPreFixDecNode(int register, ParserContext pCtx) {
    super(pCtx);
    this.register = register;
    //因为之前已注册下标,则从之前的下标变量中获取即可
    this.egressType = pCtx.getVarOrInputType(pCtx.getIndexedVarNames()[register]);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    VariableResolver vResolver = factory.getIndexedVariableResolver(register);
    //前置计算,则先获取值,再计算,然后赋值给result,赋值给解析器,最后返回result
    vResolver.setValue(ctx = MathProcessor.doOperations(vResolver.getValue(), Operator.SUB, DataTypes.INTEGER, 1));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译执行相同
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }
}