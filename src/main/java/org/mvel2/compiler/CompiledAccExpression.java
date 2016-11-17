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

package org.mvel2.compiler;

import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.OptimizerFactory;

import java.io.Serializable;

import static org.mvel2.optimizers.OptimizerFactory.getThreadAccessorOptimizer;

/** 表示一个编译的访问器表达式，用于读取或处理一个特别的属性信息(带有相应的属性访问器) */
public class CompiledAccExpression implements ExecutableStatement, Serializable {
  /** 相应的表达式 */
  private char[] expression;
  /** 起始解析点 */
  private int start;
  /** 此表达式的长度 */
  private int offset;

  /** 用于表示此表达式的访问器 */
  private transient Accessor accessor;
  /** 相应的解析上下文 */
  private ParserContext context;
  /** 声明的入参类型 */
  private Class ingressType;

  public CompiledAccExpression(char[] expression, Class ingressType, ParserContext context) {
    this(expression, 0, expression.length, ingressType, context);
  }

  public CompiledAccExpression(char[] expression, int start, int offset, Class ingressType, ParserContext context) {
    this.expression = expression;
    this.start = start;
    this.offset = offset;

    this.context = context;
    this.ingressType = ingressType != null ? ingressType : Object.class;
  }

  /** 通过内部的访问器来设置相应的值信息 */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory vrf, Object value) {
    if (accessor == null) {
      //如果之前没设置入参类型，则初始化相应的入参类型
      if (ingressType == Object.class && value != null) ingressType = value.getClass();
      //创建并完成相应的set过程
      accessor = getThreadAccessorOptimizer()
          .optimizeSetAccessor(context, expression, 0, expression.length, ctx, ctx, vrf, false, value, ingressType);

    }
    else {
      accessor.setValue(ctx, elCtx, vrf, value);
    }
    return value;
  }

  /** 通过使用内部的访问器来获取相应的值信息 */
  public Object getValue(Object staticContext, VariableResolverFactory factory) {
    if (accessor == null) {
      try {
        //直接使用相应的优化器来创建起相应的访问器,相应的this引用直接使用相应的静态上下文
        accessor = getThreadAccessorOptimizer()
            .optimizeAccessor(context, expression, 0, expression.length, staticContext, staticContext, factory, false, ingressType);
        return getValue(staticContext, factory);
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
    return accessor.getValue(staticContext, staticContext, factory);
  }

  public void setKnownIngressType(Class type) {
    this.ingressType = type;
  }

  public void setKnownEgressType(Class type) {

  }

  public Class getKnownIngressType() {
    return ingressType;
  }

  public Class getKnownEgressType() {
    return null;
  }

  /** 入参/出参不兼容 */
  public boolean isConvertableIngressEgress() {
    return false;
  }

  public void computeTypeConversionRule() {
  }

  /** 不是加减优化的 */
  public boolean intOptimized() {
    return false;
  }

  /** 非常量信息 */
  public boolean isLiteralOnly() {
    return false;
  }

  /** 通过内部的访问器来获取相应的值信息 */
  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    if (accessor == null) {
      try {
        accessor = getThreadAccessorOptimizer().optimizeAccessor(context, expression, start, offset, ctx, elCtx,
            variableFactory, false, ingressType);
        return getValue(ctx, elCtx, variableFactory);
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
    return accessor.getValue(ctx, elCtx, variableFactory);
  }

  public Accessor getAccessor() {
    return accessor;
  }

  /** 是否是空的执行表达式 */
  public boolean isEmptyStatement() {
    return accessor == null;
  }

  /** 不是classcast节点 */
  public boolean isExplicitCast() {
    return false;
  }
}
