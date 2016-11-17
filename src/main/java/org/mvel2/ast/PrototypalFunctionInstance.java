package org.mvel2.ast;

import org.mvel2.integration.VariableResolverFactory;

/**
 * 描述一个函数产生的调用实例,即每一次的function调用都是一个调用实例
 * @author Mike Brock
 */
public class PrototypalFunctionInstance extends FunctionInstance {
  /** 原定义时的作用域 */
  private final VariableResolverFactory resolverFactory;

  public PrototypalFunctionInstance(Function function, VariableResolverFactory resolverFactory) {
    super(function);
    this.resolverFactory = resolverFactory;
  }

  @Override
  public Object call(Object ctx, Object thisValue, VariableResolverFactory factory, Object[] parms) {
    return function.call(ctx, thisValue, new InvokationContextFactory(factory, resolverFactory), parms);
  }

  public VariableResolverFactory getResolverFactory() {
    return resolverFactory;
  }

  public String toString() {
    return "function_prototype:" + function.getName();
  }

}

