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
 * 描述一个指定变量的 ++ 操作节点
 * @author Christopher Brock
 */
public class PostFixIncNode extends ASTNode {
  /** 变量名 */
  private String name;

  public PostFixIncNode(String name, ParserContext pCtx) {
    super(pCtx);
    this.name = name;
    if (pCtx != null) {
      this.egressType = pCtx.getVarOrInputType(name);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //此变量之前定义过,因此从解析工厂中获取值,处理再设置回去即可
    //整个表达式翻译为 var tmp = get(a);a = tmp + 1; set(a);return tmp;
    VariableResolver vResolver = factory.getVariableResolver(name);
    //因为是后置,所以先取值,再计算,然后返回之前的值
    vResolver.setValue(MathProcessor.doOperations(ctx = vResolver.getValue(), Operator.ADD, DataTypes.INTEGER, 1));
    return ctx;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译执行相同
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }
}
