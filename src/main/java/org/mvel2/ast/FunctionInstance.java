package org.mvel2.ast;

import org.mvel2.integration.VariableResolverFactory;

/**
 * 描述一个通常的函数实例
 * @author Mike Brock
 */
public class FunctionInstance {
  /** 相对应的函数定义 */
  protected final Function function;

  public FunctionInstance(Function function) {
    this.function = function;
  }

  public Function getFunction() {
    return function;
  }

  /** 执行调用过程，即调用当前函数 */
  public Object call(Object ctx, Object thisValue, VariableResolverFactory factory, Object[] parms) {
    return function.call(ctx, thisValue, factory, parms);
  }
}
