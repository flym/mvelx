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

import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Constructor;

/**
 * 表示对象访问器(创建对象)(由NewNode对应)
 * 如new T(a,b)这种的对象创建
 */
public class ConstructorAccessor extends InvokableAccessor {
  /** 当前所引用的构建函数 */
  private Constructor constructor;

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    //对象构建,直接使用相应的构造器的newInstance处理
    try {
      //默认情况下不作可变参数处理
      if (!coercionNeeded) {
        //根据是否存在nextNode决定是否转发请求
        try {
          if (nextNode != null) {
            return nextNode.getValue(constructor.newInstance(executeAll(elCtx, variableFactory)), elCtx, variableFactory);
          }
          else {
            return constructor.newInstance(executeAll(elCtx, variableFactory));
          }
        }
        catch (IllegalArgumentException e) {
          //默认处理报错，重新设置标记位处理
          coercionNeeded = true;
          return getValue(ctx, elCtx, variableFactory);
        }

      }
      //参数是可变的,即... 可变参数的情况
      else {
        //变参处理,先根据相应的参数个数重新解析参数,再进行对象创建
        if (nextNode != null) {
          return nextNode.getValue(constructor.newInstance(executeAndCoerce(parameterTypes, elCtx, variableFactory, constructor.isVarArgs())),
              elCtx, variableFactory);
        }
        else {
          return constructor.newInstance(executeAndCoerce(parameterTypes, elCtx, variableFactory, constructor.isVarArgs()));
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException("cannot construct object", e);
    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    return null;
  }

  /** 通过额外的参数上下文执行所有的参数节点信息,并返回相应的值信息 */
  private Object[] executeAll(Object ctx, VariableResolverFactory vars) {
    //参数为0,即无参构造函数
    if (length == 0) return GetterAccessor.EMPTY;

    Object[] vals = new Object[length];
    for (int i = 0; i < length; i++) {
      vals[i] = parms[i].getValue( ctx, vars);
    }
    return vals;
  }

  /** 通过相应的构造函数,相应的参数访问器来进行构建 */
  public ConstructorAccessor(Constructor constructor, ExecutableStatement[] parms) {
    this.constructor = constructor;
    //相应的参数个数不能由参数访问器来决定,因为可能存在可变参数访问,因此由构造函数的方法声明来决定
    this.length = (this.parameterTypes = constructor.getParameterTypes()).length;
    this.parms = parms;
  }

  /** 相应的声明类型为构造函数的声明类来决定 */
  public Class getKnownEgressType() {
    //这里有问题(bug),应该返回相应的声明类型
    return constructor.getClass();
  }

  /** 返回相应的构造器 */
  public Constructor getConstructor() {
    return constructor;
  }

  /** 返回相应的参数执行表达式 */
  public ExecutableStatement[] getParameters() {
    return parms;
  }
}
