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
import org.mvel2.optimizers.OptimizationNotSupported;
import org.mvel2.optimizers.OptimizerFactory;

import static java.lang.System.currentTimeMillis;

/** 用于执行get访问的动态访问器(如字段读取，方法调用等) */
public class DynamicGetAccessor implements DynamicAccessor {
  /** 相应的表达式 */
  private char[] expr;
  /** 当前处理的起始点 */
  private int start;
  /** 当前处理的语句长度 */
  private int offset;

  /** 上一次优化访问时间(即在一定时间内上一次统计时间) */
  private long stamp;
  /** 处理类型，有0和3可选，分别表示获取和对象创建(2的访问将转由collection处理) */
  private int type;

  /** 在时间区间内的运行统计次数 */
  private int runcount;

  /** 是否作过优化 */
  private boolean opt = false;

  /** 当前解析上下文 */
  private ParserContext pCtx;

  /** 当前安全的访问器(即可正常执行的访问器) */
  private Accessor _safeAccessor;
  /** 当前的优化访问器 */
  private Accessor _accessor;

  /** 使用解析上下文, 当前区间的表达式,以及指定的访问器创建结构 */
  public DynamicGetAccessor(ParserContext pCtx, char[] expr, int start, int offset, int type, Accessor _accessor) {
    this._safeAccessor = this._accessor = _accessor;
    this.type = type;

    this.expr = expr;
    this.start = start;
    this.offset = offset;

    this.pCtx = pCtx;
    stamp = currentTimeMillis();
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    if (!opt) {
      //这里即尝试优化，即如果次数超过指定计数，并且时间在指定区间内，即在100ms内运行超过50次 */
      if (++runcount > DynamicOptimizer.tenuringThreshold) {
        if ((currentTimeMillis() - stamp) < DynamicOptimizer.timeSpan) {
          opt = true;
          try{
            return optimize(ctx, elCtx, variableFactory);
          }
          catch(OptimizationNotSupported ex){
        	  // If optimization fails then, rather than fail evaluation, fallback to use safe reflective accessor
          }
        }
        else {
          runcount = 0;
          stamp = currentTimeMillis();
        }
      }
    }

    return _accessor.getValue(ctx, elCtx, variableFactory);
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    runcount++;
    return _accessor.setValue(ctx, elCtx, variableFactory, value);
  }

  /** 执行实际的优化过程 */
  private Object optimize(Object ctx, Object elCtx, VariableResolverFactory variableResolverFactory) {

    //过载保护，避免无限创建新类(其实没什么用)
    if (DynamicOptimizer.isOverloaded()) {
      DynamicOptimizer.enforceTenureLimit();
    }

    //这里采用asm优化器来进行优化,即直接执行相应的字节码
    AccessorOptimizer ao = OptimizerFactory.getAccessorCompiler("ASM");
    switch (type) {
      //正常对象访问
      case DynamicOptimizer.REGULAR_ACCESSOR:
        _accessor = ao.optimizeAccessor(pCtx, expr, start, offset, ctx, elCtx, variableResolverFactory, false, null);
        return ao.getResultOptPass();
      //对象创建过程
      case DynamicOptimizer.OBJ_CREATION:
        _accessor = ao.optimizeObjectCreation(pCtx, expr, start, offset, ctx, elCtx, variableResolverFactory);
        return _accessor.getValue(ctx, elCtx, variableResolverFactory);
      //内联集合访问,这里实际上不会走到这里
      case DynamicOptimizer.COLLECTION:
        _accessor = ao.optimizeCollection(pCtx, ctx, null, expr, start, offset, ctx, elCtx, variableResolverFactory);
        return _accessor.getValue(ctx, elCtx, variableResolverFactory);
    }
    return null;
  }

  /** 反优化,即取消之前的优化 */
  public void deoptimize() {
    //重置为安全访问器,即反射访问的方式
    this._accessor = this._safeAccessor;
    opt = false;
    runcount = 0;
    stamp = currentTimeMillis();
  }

  /** 上次统计时间 */
  public long getStamp() {
    return stamp;
  }

  /** 运行计数 */
  public int getRuncount() {
    return runcount;
  }

  /** 声明类型为安全访问顺的声明类型 */
  public Class getKnownEgressType() {
    return _safeAccessor.getKnownEgressType();
  }

  /** 返回当前正在使用的访问器,可能是优化版本,也可能是优化版本 */
  public Accessor getAccessor() {
    return _accessor;
  }

  public Accessor getSafeAccessor() {
    return _safeAccessor;
  }
}
