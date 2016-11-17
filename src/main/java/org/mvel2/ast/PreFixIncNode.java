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


/**
 * 前置的 ++ 操作信息，并且提供相应的变量信息
 * @author Christopher Brock
 */
public class PreFixIncNode extends ASTNode {
  /** 相应的变量值 */
  private String name;

  public PreFixIncNode(String name, ParserContext pCtx) {
    super(pCtx);
    this.name = name;
    if (pCtx != null) {
      this.egressType = pCtx.getVarOrInputType(name);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //获取之前的变量, 先运算,最终返回运算后的值,同时将相应的值重新设置
    //整个流程为 var tmp = get(a); a = tmp + 1; set(a);return a;
    VariableResolver vResolver = factory.getVariableResolver(name);
    vResolver.setValue(ctx = MathProcessor.doOperations(vResolver.getValue(), Operator.ADD, DataTypes.INTEGER, 1));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译期相同
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }
}
