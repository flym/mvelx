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

  /** 采用解释运行方式进行解释并获取相应的数据 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    parseGraph(false, egressType, pCtx);

    return execGraph(collectionGraph, egressType, ctx, factory);
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

  /** 采用解释方式来运行相应的内部集合处理 */
  private Object execGraph(Object o, Class type, Object ctx, VariableResolverFactory factory) {
    //当前对象为集合，采用集合方式运行
    if (o instanceof List) {
      ArrayList list = new ArrayList(((List) o).size());

      for (Object item : (List) o) {
        list.add(execGraph(item, type, ctx, factory));
      }

      return list;
    }
    //map,分别对key和value进行解释运行
    else if (o instanceof Map) {
      HashMap map = new HashMap();

      for (Object item : ((Map) o).keySet()) {
        map.put(execGraph(item, type, ctx, factory), execGraph(((Map) o).get(item), type, ctx, factory));
      }

      return map;
    }
    //数组处理
    else if (o instanceof Object[]) {
      int dim = 0;

      //这里根据相应的数组创建出相应的多维数据结构
      if (type != null) {
        String nm = type.getName();
        while (nm.charAt(dim) == '[') dim++;
      }
      else {
        type = Object[].class;
        dim = 1;
      }

      Object newArray = Array.newInstance(getSubComponentType(type), ((Object[]) o).length);

      try {
        Class cls = dim > 1 ? findClass(null, repeatChar('[', dim - 1) + "L" + getBaseComponentType(type).getName() + ";", pCtx) : type;

        //这里仅处理最外层的维度，因为在解析时，会自动生成相应的多维结构,然后在内层时，会在进行解析时自动进行维度的递进(如[[ 会在第一个[解析时，跳到第2个[
        int c = 0;
        for (Object item : (Object[]) o) {
          Array.set(newArray, c++, execGraph(item, cls, ctx, factory));
        }

        return newArray;
      }
      catch (IllegalArgumentException e) {
        throw new CompileException("type mismatch in array", expr, start, e);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException("this error should never throw:" + getBaseComponentType(type).getName(), e);
      }
    }
    //这里即表示在进行解释时，已经解释到了具体的每一个子项，因此直接采用eval解释运行即可
    else {
      //这里的类型为数组，但进行到这里，表示这里的类型仅表示数据的类型，并不表示当前的对象的类型，因此需要将相应的数据类型转换为实际的基本类型进行处理
      //因为如 int[][] 在处理时，前面的当前对象会进行相应数组的处理逻辑，因此这里仅表示最内部的每一项是什么类型
      if (type.isArray()) {
        return MVEL.eval((String) o, ctx, factory, getBaseComponentType(type));
      }
      //非数组，直接进行解释运行
      else {
        return MVEL.eval((String) o, ctx, factory);
      }
    }
  }
}
