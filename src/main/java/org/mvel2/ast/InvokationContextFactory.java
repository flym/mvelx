package org.mvel2.ast;

import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

/**
 * 描述一个采用原定义作用域+当前执行作用域的双重作用域工厂
 * 即在定义时,此对象有一个内部作用域(可以理解为对象内部属性范围),然后在执行时,加入了外部的作用域,
 * 这2者都能控制相应的变量处理
 *
 * @author Mike Brock
 */
public class InvokationContextFactory extends MapVariableResolverFactory {
  /** 原定义作用域 */
  private VariableResolverFactory protoContext;

  public InvokationContextFactory(VariableResolverFactory next, VariableResolverFactory protoContext) {
    this.nextFactory = next;
    this.protoContext = protoContext;
  }

  @Override
  public VariableResolver createVariable(String name, Object value) {
    //处理变量时,需要根据2者,看哪个能解析,如果外部不能解析,则退回到定义作用域来处理
    //即在处理时,首先判断是否已之前定义过的,如果确定是之前定义过的,则跳回到之前的逻辑当中
    if (isResolveable(name) && !protoContext.isResolveable(name)) {
      return nextFactory.createVariable(name, value);
    }
    else {
      return protoContext.createVariable(name, value);
    }
  }

  @Override
  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    if (isResolveable(name) && !protoContext.isResolveable(name)) {
      return nextFactory.createVariable(name, value, type);
    }
    else {
      return protoContext.createVariable(name, value, type);
    }
  }

  @Override
  public VariableResolver getVariableResolver(String name) {
    //获取相应的值信息时也是采用双重处理,先外部后内部
    if (isResolveable(name) && !protoContext.isResolveable(name)) {
      return nextFactory.getVariableResolver(name);
    }
    else {
      return protoContext.getVariableResolver(name);
    }
  }

  @Override
  public boolean isTarget(String name) {
    return protoContext.isTarget(name);
  }

  @Override
  public boolean isResolveable(String name) {
    return protoContext.isResolveable(name) || nextFactory.isResolveable(name);
  }

  @Override
  public boolean isIndexedFactory() {
    return true;
  }
}
