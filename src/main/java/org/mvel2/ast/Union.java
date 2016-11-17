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
import org.mvel2.PropertyAccessor;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;

/** 表示基于主节点然后再继续香处理的节点信息,通常用于表示主节点之后的某个属性调用或者方法调用 */
public class Union extends ASTNode {
  /** 当前访问的主节点,即先处理主节点,再处理后续数据 */
  private ASTNode main;
  /** 相应的优化器(与父类中的accessor作用一致) */
  private transient Accessor accessor;

  public Union(char[] expr, int start, int offset, int fields, ASTNode main, ParserContext pCtx) {
    super(expr, start, offset, fields, pCtx);
    this.main = main;
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //如果之前相应的访问器已处理好,则直接通过访问处理
    if (accessor != null) {
      //先调用主节点,再调用访问器
      return accessor.getValue(main.getReducedValueAccelerated(ctx, thisValue, factory), thisValue, factory);
    }
    else {
      try {
        //构建出访问器,使用主节点的相应的值作为新访问器的上下文
        AccessorOptimizer o = OptimizerFactory.getThreadAccessorOptimizer();
        accessor = o.optimizeAccessor(pCtx, expr, start, offset,
            main.getReducedValueAccelerated(ctx, thisValue, factory), thisValue, factory, false, main.getEgressType());
        return o.getResultOptPass();
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
  }

  public ASTNode getMain() {
    return main;
  }

  public Accessor getAccessor() {
      return accessor;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //因为是解释运行,因此能够执行这里的肯定是属性访问,因此这里采用属性值获取的方式来获取相应的值
    return PropertyAccessor.get(
        expr, start, offset,
        main.getReducedValue(ctx, thisValue, factory), factory, thisValue, pCtx);
  }

  /** 返回主节点返回类型 这里也认为是左节点,因为先main,后当前节点 */
  public Class getLeftEgressType() {
    return main.getEgressType();
  }

  public String toString() {
    return (main != null ? main.toString() : "") + "-[union]->" + (accessor != null ? accessor.toString() : "");
  }
}
