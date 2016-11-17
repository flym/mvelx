/**
 * MVEL (The MVFLEX Expression Language)
 *
 * Copyright (C) 2007 Christopher Brock, MVFLEX/Valhalla Project and the Codehaus
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
 *
 */
package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.DataConversion;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import java.util.List;

import static org.mvel2.util.ParseTools.subCompileExpression;

/** list集合访问器，并且下标为执行单元的情况 */
public class ListAccessorNest implements AccessorNode {
  private AccessorNode nextNode;
  /** 下标执行单元 */
  private ExecutableStatement index;
  /** 当前集合中存储的值的类型(用于设置值时进行参数转换),即在set需要转换为哪个类型 */
  private Class conversionType;


  public ListAccessorNest() {
  }

  /** 通过下标表达式+相应的值类型构建出相应的访问器 */
  public ListAccessorNest(String index, Class conversionType) {
    this.index = (ExecutableStatement) subCompileExpression(index.toCharArray());
    this.conversionType = conversionType;
  }

  /** 通过下标计算单元+相应的值类型构建出相应的访问器 */
  public ListAccessorNest(ExecutableStatement index, Class conversionType) {
    this.index = index;
    this.conversionType = conversionType;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    //直接计算出相应的下标值,再使用list.get(int)来获取相应的值
    if (nextNode != null) {
      return nextNode.getValue(((List) ctx).get((Integer) index.getValue(ctx, elCtx, vars)), elCtx, vars);
    }
    else {
      return ((List) ctx).get((Integer) index.getValue(ctx, elCtx, vars));
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
    //noinspection unchecked
    //根据是否有next来决定是否转发请求
    //有next,因此由next来处理相应值的类型,当前list只负责取值即可
    if (nextNode != null) {
      return nextNode.setValue(((List) ctx).get((Integer) index.getValue(ctx, elCtx, vars)), elCtx, vars, value);
    }
    else {
      //没有next,因此为自己设置值,需要根据类型决定是否需要进行类型转换
      if (conversionType != null) {
        ((List) ctx).set((Integer) index.getValue(ctx, elCtx, vars), value = DataConversion.convert(value, conversionType));
      }
      else {
        ((List) ctx).set((Integer) index.getValue(ctx, elCtx, vars), value);
      }
      return value;
    }

  }

  /** 获取相应的下标计算单元 */
  public ExecutableStatement getIndex() {
    return index;
  }

  public void setIndex(ExecutableStatement index) {
    this.index = index;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }


  public String toString() {
    return "Array Accessor -> [" + index + "]";
  }

  /** 类型未知,为Object类型 */
  public Class getKnownEgressType() {
    return Object.class;
  }
}
