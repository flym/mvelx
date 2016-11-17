package org.mvel2.integration;

/** 监听器,用于在进行set或get时触发相应的操作 */
public interface Listener {
  /** 具体的处理 */
  public void onEvent(Object context, String contextName, VariableResolverFactory variableFactory, Object value);
}
