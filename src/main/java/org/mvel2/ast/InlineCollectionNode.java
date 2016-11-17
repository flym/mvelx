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

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.CollectionParser;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mvel2.util.ParseTools.*;

/**
 * 表示一个内联的集合表达式,使用[ 或 {均可以用来表示集合,即集合直接字面量 如 new int[]{1,2} 这种
 * 或者是 [1,2,3]这种
 *
 * @author Christopher Brock
 */
public class InlineCollectionNode extends ASTNode {
  /** 用于描述数组内部的数据类型及值描述,可能是数组,集合,map的一种 */
  private Object collectionGraph;
  /** 用于描述如,在]. 或 }.后面的表达式的起始点 */
  int trailingStart;
  /** 用于描述整个表达式的结束点到后面表达式的长度,如 new int[]{1,2,3}.length中的 length长度值 */
  int trailingOffset;

  /** 初始化,但未指定数据类型 */
  public InlineCollectionNode(char[] expr, int start, int end, int fields, ParserContext pctx) {
    super(expr, start, end, fields | INLINE_COLLECTION, pctx);

    //需要编译，则马上进行编译
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      parseGraph(true, null, pctx);
      try {
        //准备相应的访问器
        AccessorOptimizer ao = OptimizerFactory.getThreadAccessorOptimizer();
        accessor = ao.optimizeCollection(pctx, collectionGraph, egressType, expr, trailingStart, trailingOffset, null, null, null);
        egressType = ao.getEgressType();
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
  }

  /** 初始化,并使用已知的内部类型进行解析 */
  public InlineCollectionNode(char[] expr, int start, int end, int fields, Class type, ParserContext pctx) {
    super(expr, start, end, fields | INLINE_COLLECTION, pctx);

    this.egressType = type;

    //需要编译，则进行编译
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      try {
        parseGraph(true, type, pctx);
        AccessorOptimizer ao = OptimizerFactory.getThreadAccessorOptimizer();
        accessor = ao.optimizeCollection(pctx, collectionGraph, egressType, expr, this.trailingStart, trailingOffset, null, null, null);
        egressType = ao.getEgressType();
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
  }

  /** 采用编译的方式进行数据访问 */
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    if (accessor != null) {
      return accessor.getValue(ctx, thisValue, factory);
    }
    else {
      try {
        AccessorOptimizer ao = OptimizerFactory.getThreadAccessorOptimizer();
        //如果没有编译，则进行编译，然后再根据优化之后的访问器来获取数据
        if (collectionGraph == null) parseGraph(true, null, null);

        accessor = ao.optimizeCollection(pCtx, collectionGraph,
            egressType, expr, trailingStart, trailingOffset, ctx, thisValue, factory);
        egressType = ao.getEgressType();

        return accessor.getValue(ctx, thisValue, factory);
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }

  }

  private void parseGraph(boolean compile, Class type, ParserContext pCtx) {
    CollectionParser parser = new CollectionParser();

    //以下因为是以内部集合的方式进行解析,即外层通过[或者{使用,即认为外层表示为集合
    //因此下层的解析返回值肯定为list,然后再单独返回内层对象
    //type仅用于表示在解析过程中的子类型的类型，因此不影响到返回的结果信息
    if (type == null) {
      collectionGraph = ((List) parser.parseCollection(expr, start, offset, compile, pCtx)).get(0);
    }
    else {
      collectionGraph = ((List) parser.parseCollection(expr, start, offset, compile, type, pCtx)).get(0);
    }

    trailingStart = parser.getCursor() + 2;
    trailingOffset = offset - (trailingStart - start);

    if (this.egressType == null) this.egressType = collectionGraph.getClass();
  }
}
