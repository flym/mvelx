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
package org.mvel2.optimizers.impl.refl.collection;

import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 集合创建器,使用已知的值访问器信息直接创建出相应的列表
 * 用于直接以内联方式创建集合时处理,如 a = [a,b,c]这种创建时,在这种情况下,值信息是已知的
 * @author Christopher Brock
 */
public class ListCreator implements Accessor {
  /** 预先处理的值访问器信息 */
  private Accessor[] values;

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    //实现时,即依次访问相应的值访问器,构建出数组,然后转换为list,即可
    //这里采用ArrayList来声明具体的实现类型
    Object[] template = new Object[getValues().length];
    for (int i = 0; i < getValues().length; i++) {
      template[i] = getValues()[i].getValue(ctx, elCtx, variableFactory);
    }
    return new ArrayList<Object>(Arrays.asList(template));
  }

  /** 通过已知的值访问器来创建出相应的访问器 */
  public ListCreator(Accessor[] values) {
    this.values = values;
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    return null;
  }

  /** 声明类型为集合,即List */
  public Class getKnownEgressType() {
    return List.class;
  }

  /** 返回相应的值列表 */
  public Accessor[] getValues() {
    return values;
  }
}
