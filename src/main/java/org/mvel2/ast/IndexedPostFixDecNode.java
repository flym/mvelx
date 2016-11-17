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

package org.mvel2.ast;

import org.mvel2.DataTypes;
import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.math.MathProcessor;
import org.mvel2.util.ParseTools;

/**
 * 描述一个之前在上下文中进行注册过的入参的--操作节点
 * @author Christopher Brock
 */
public class IndexedPostFixDecNode extends ASTNode {
  /** 相应的注册下标值 */
  private int register;

  public IndexedPostFixDecNode(int register, ParserContext pCtx) {
    super(pCtx);
    this.register = register;
    //因为这里是下标,因此从之前的下标变量中获取到相应的类型即可
    this.egressType = pCtx.getVarOrInputType(pCtx.getIndexedVarNames()[register]);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //因为下标即当前变量工厂的下标一致,获取相应的处理器直接进行a = a - 1即可
    VariableResolver vResolver = factory.getIndexedVariableResolver(register);
    //  ctx = vResolver.getValue();
    //后置计算,先赋值给result,再计算,最后返回相应的result即可
    vResolver.setValue(MathProcessor.doOperations(ParseTools.resolveType(ctx = vResolver.getValue()),
        ctx, Operator.SUB, DataTypes.INTEGER, 1));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译执行相同
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }
}