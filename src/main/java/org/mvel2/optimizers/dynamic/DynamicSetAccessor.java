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

package org.mvel2.optimizers.dynamic;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;

import static java.lang.System.currentTimeMillis;

/** 处理对象设置值之类的动态优化访问器 */
public class DynamicSetAccessor implements DynamicAccessor {
  /** 处理的表达式 */
  private char[] property;
  /** 当前语句起始下标 */
  private int start;
  /** 当前语句长度位 */
  private int offset;

  /** 是否优化过 */
  private boolean opt = false;
  /** 统计运行次数 */
  private int runcount = 0;
  /** 上限统计计时 */
  private long stamp;

  private ParserContext context;
  /** 可安全调用的访问器 */
  private final Accessor _safeAccessor;
  /** 当前使用的访问器(可能为优化版本) */
  private Accessor _accessor;
  /** 描述(没什么用) */
  private String description;

  public DynamicSetAccessor(ParserContext context, char[] property, int start, int offset, Accessor _accessor) {
    assert _accessor != null;
    this._safeAccessor = this._accessor = _accessor;
    this.context = context;

    this.property = property;
    this.start = start;
    this.offset = offset;

    this.stamp = System.currentTimeMillis();
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //如果未优化,则按照处理策略进行优化
    //处理策略即在指定时间内运行了多少次
    if (!opt) {
      if (++runcount > DynamicOptimizer.tenuringThreshold) {
        if ((currentTimeMillis() - stamp) < DynamicOptimizer.timeSpan) {
          opt = true;
          return optimize(ctx, elCtx, variableFactory, value);
        }
        else {
          runcount = 0;
          stamp = currentTimeMillis();
        }
      }
    }

    _accessor.setValue(ctx, elCtx, variableFactory, value);
    return value;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    throw new RuntimeException("value cannot be read with this accessor");
  }

  /** 对相应的表达式进行优化 */
  private Object optimize(Object ctx, Object elCtx, VariableResolverFactory variableResolverFactory, Object value) {
    if (DynamicOptimizer.isOverloaded()) {
      DynamicOptimizer.enforceTenureLimit();
    }

    //采用asm进行优化处理
    AccessorOptimizer ao = OptimizerFactory.getAccessorCompiler("ASM");
    _accessor = ao.optimizeSetAccessor(context, property, start, offset, ctx, elCtx,
        variableResolverFactory, false, value, value != null ? value.getClass() : Object.class);
    assert _accessor != null;

    return value;
  }

  /** 反优化处理 */
  public void deoptimize() {
    this._accessor = this._safeAccessor;
    opt = false;
    runcount = 0;
    stamp = currentTimeMillis();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /** 相应的声明类型即安全访问器的声明类型 */
  public Class getKnownEgressType() {
    return _safeAccessor.getKnownEgressType();
  }
}