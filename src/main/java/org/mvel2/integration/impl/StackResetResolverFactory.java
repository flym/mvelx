package org.mvel2.integration.impl;

import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;

import java.util.Set;

/**
 * 全程委托调用，但在使用委托类之前会将相应的结束标记重新标记为false
 * 即重用之前可能被停止使用的工厂(如在上一条调用链中认为不再使用了，这里又重新使用)
 * 主要工作在栈式处理当中
 *
 * @author Mike Brock
 */
public class StackResetResolverFactory implements VariableResolverFactory {
  private VariableResolverFactory delegate;

  public StackResetResolverFactory(VariableResolverFactory delegate) {
    //这里重新设置相应的处理逻辑,即重置,以保证相应的栈式处理能够按预期工作,而不是因为标识位提前返回
    delegate.setTiltFlag(false);
    this.delegate = delegate;
  }

  public VariableResolver createVariable(String name, Object value) {
    return delegate.createVariable(name, value);
  }

  public VariableResolver createIndexedVariable(int index, String name, Object value) {
    return delegate.createIndexedVariable(index, name, value);
  }

  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    return delegate.createVariable(name, value, type);
  }

  public VariableResolver createIndexedVariable(int index, String name, Object value, Class<?> typee) {
    return delegate.createIndexedVariable(index, name, value, typee);
  }

  public VariableResolver setIndexedVariableResolver(int index, VariableResolver variableResolver) {
    return delegate.setIndexedVariableResolver(index, variableResolver);
  }

  public VariableResolverFactory getNextFactory() {
    return delegate.getNextFactory();
  }

  public VariableResolverFactory setNextFactory(VariableResolverFactory resolverFactory) {
    return delegate.setNextFactory(resolverFactory);
  }

  public VariableResolver getVariableResolver(String name) {
    return delegate.getVariableResolver(name);
  }

  public VariableResolver getIndexedVariableResolver(int index) {
    return delegate.getIndexedVariableResolver(index);
  }

  public boolean isTarget(String name) {
    return delegate.isTarget(name);
  }

  public boolean isResolveable(String name) {
    return delegate.isResolveable(name);
  }

  public Set<String> getKnownVariables() {
    return delegate.getKnownVariables();
  }

  public int variableIndexOf(String name) {
    return delegate.variableIndexOf(name);
  }

  public boolean isIndexedFactory() {
    return delegate.isIndexedFactory();
  }

  public boolean tiltFlag() {
    return delegate.tiltFlag();
  }

  public void setTiltFlag(boolean tilt) {
    //仅当委托类为false时才处理,以保证不会多次改变设置
    if (!delegate.tiltFlag()) {
      delegate.setTiltFlag(tilt);
    }
  }

  public VariableResolverFactory getDelegate() {
    return delegate;
  }
}
