/**
 * MVEL (The MVFLEX Expression Language)
 * <p>
 * Copyright (C) 2007 Christopher Brock, MVFLEX/Valhalla Project and the Codehaus
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
package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.ast.Function;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;


/**
 * 动态函数调用，表示当前的函数由ctx提供(即ctx即函数句柄),当前只需要直接调用相应函数即可,
 * 同时这里的函数是对原函数定义的一个句柄引用,即并不是一个实际的函数定义,因此称之为dynamic
 */
public class DynamicFunctionAccessor extends BaseAccessor {
  // private Function function;
  /** 相对应的值访问器 */
  private Accessor[] parameters;

  /** 根据相应的参数值访问器来构建相应的函数访问器 */
  public DynamicFunctionAccessor(Accessor[] parms) {
    this.parameters = parms;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    //整个处理过程,即先获取相应的参数信息,将所有的参数收集之后,再直接调用相应的函数call即可
    //在调用时,当前ctx即表示函数对象本身
    Object[] parms = null;

    Function function = (Function) ctx;

    if (parameters != null && parameters.length != 0) {
      parms = new Object[parameters.length];
      for (int i = 0; i < parms.length; i++) {
        parms[i] = parameters[i].getValue(ctx, elCtx, variableFactory);
      }
    }

    if (nextNode != null) {
      return nextNode.getValue(function.call(ctx, elCtx, variableFactory, parms), elCtx, variableFactory);
    }
    else {
      return function.call(ctx, elCtx, variableFactory, parms);
    }
  }

  /** 函数只能被调用,因此没有设置函数一说 */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    throw new RuntimeException("can't write to function");
  }

  /** 返回类型未知,为object类型 */
  public Class getKnownEgressType() {
    return Object.class;
  }
}