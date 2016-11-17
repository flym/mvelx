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

import java.util.Map;

import static org.mvel2.util.ParseTools.subCompileExpression;

/**
 * map类型的访问器，属性为计算单元
 * @author Christopher Brock
 */
public class MapAccessorNest implements AccessorNode {
  private AccessorNode nextNode;
  /** 描述属性值的计算单元 */
  private ExecutableStatement property;
  /** 期望的值类型(在set时需要作类型转换) */
  private Class conversionType;

  public MapAccessorNest() {
  }

  /** 根据相应的属性计算单元+相应的值类型来构建访问器 */
  public MapAccessorNest(ExecutableStatement property, Class conversionType) {
    this.property = property;
    this.conversionType = conversionType;
  }

  /** 根据相应的属性表达式+相应的值类型来构建访问器 */
  public MapAccessorNest(String property, Class conversionType) {
    this.property = (ExecutableStatement) subCompileExpression(property.toCharArray());
    this.conversionType = conversionType;
  }


  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
    //直接先计算出属性值,再调用相应的map.get来获取相应的值
    if (nextNode != null) {
      return nextNode.getValue(((Map) ctx).get(property.getValue(ctx, elCtx, vrf)), elCtx, vrf);
    }
    else {
      return ((Map) ctx).get(property.getValue(ctx, elCtx, vrf));
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vars, Object value) {
    //如果有next节点,则将set操作转交由next来完成
    if (nextNode != null) {
      return nextNode.setValue(((Map) ctx).get(property.getValue(ctx, elCtx, vars)), elCtx, vars, value);
    }
    else {
      //自己进行put,判断是否需要根据转换类型来决定是否需要进行数据转换
      if (conversionType != null) {
        ((Map) ctx).put(property.getValue(ctx, elCtx, vars), value = DataConversion.convert(value, conversionType));
      }
      else {
        ((Map) ctx).put(property.getValue(ctx, elCtx, vars), value);
      }
      return value;
    }
  }

  /** 得到相应的属性访问器 */
  public ExecutableStatement getProperty() {
    return property;
  }

  public void setProperty(ExecutableStatement property) {
    this.property = property;
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  public String toString() {
    return "Map Accessor -> [" + property + "]";
  }

  /** 类型未知,因此声明类型为Object */
  public Class getKnownEgressType() {
    return Object.class;
  }
}
