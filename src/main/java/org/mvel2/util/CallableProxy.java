package org.mvel2.util;

import org.mvel2.integration.VariableResolverFactory;

/** 描述一个可以进行调用,并且是代理调用的对象,即是通过代理来委托调用的处理对象 */
public interface CallableProxy {
  /** 相应的调用 */
  public Object call(Object ctx, Object thisCtx, VariableResolverFactory factory, Object[] parameters);
}
