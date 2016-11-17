package org.mvel2.integration.impl;

import org.mvel2.UnresolveablePropertyException;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * 基于一个统一的map来完成变量解析的工厂处理，即主要使用基于map的变量解析器工作的工厂类
 * 此map由外部进行传入
 *
 * @see MapVariableResolver
 */
public class CachedMapVariableResolverFactory extends BaseVariableResolverFactory {
  /**
   * 用于实际存储各种变量值信息的map对象
   * 需要由外部传入
   * Holds the instance of the variables.
   */
  protected Map<String, Object> variables;

  public CachedMapVariableResolverFactory() {
  }

  /** 基于一个已有的map来构建相应的解析器工厂,之前map中已有的值会作为预填写的变量来进行存储 */
  public CachedMapVariableResolverFactory(Map<String, Object> variables) {
    this.variables = variables;
    variableResolvers = new HashMap<String, VariableResolver>(variables.size() * 2);

    //已有的存储转换为预填充变量解析器
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      //这里使用entry解析器,来达到实时更新的目的,即在这里获取相应的解析器,设置值之后,实际相应的值即直接更新相对应的map信息
      variableResolvers.put(entry.getKey(), new PrecachedMapVariableResolver(entry, entry.getKey()));
    }

  }

  /** 基于一个已有map+委托工厂来构建实例 */
  public CachedMapVariableResolverFactory(Map<String, Object> variables, VariableResolverFactory nextFactory) {
    this.variables = variables;
    variableResolvers = new HashMap<String, VariableResolver>(variables.size() * 2);

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      variableResolvers.put(entry.getKey(), new PrecachedMapVariableResolver(entry, entry.getKey()));
    }

    this.nextFactory = nextFactory;
  }


  /** 根据变量名替换之前的值，或者是创建新的解析器 */
  public VariableResolver createVariable(String name, Object value) {
    VariableResolver vr;

    //通过try catch来进行处理,即先强制获取,并设置值.异常了再来进行添加
    try {
      (vr = getVariableResolver(name)).setValue(value);
      return vr;
    }
    catch (UnresolveablePropertyException e) {
      addResolver(name, vr = new MapVariableResolver(variables, name)).setValue(value);
      return vr;
    }
  }

  /**
   * 尝试创建新的变量解析器，如果之前已经存在并且并且相应的类型不为null，则报相应的异常
   * 即单个变量名+类型只能有一个映射信息
   */
  public VariableResolver createVariable(String name, Object value, Class<?> type) {
    VariableResolver vr;
    try {
      vr = getVariableResolver(name);
    }
    catch (UnresolveablePropertyException e) {
      vr = null;
    }

    if (vr != null && vr.getType() != null) {
      throw new RuntimeException("variable already defined within scope: " + vr.getType() + " " + name);
    }
    else {
      addResolver(name, vr = new MapVariableResolver(variables, name, type)).setValue(value);
      return vr;
    }
  }

  public VariableResolver getVariableResolver(String name) {

    //先尝试从当前已持有的解析器中获取
    VariableResolver vr = variableResolvers.get(name);
    if (vr != null) {
      return vr;
    }
    //这里可能是外部的map又发生了变化,并且已经持有此变量,则尝试将新的值重新录入到解析器当中来处理
    else if (variables.containsKey(name)) {
      variableResolvers.put(name, vr = new MapVariableResolver(variables, name));
      return vr;
    }
    //委托给next处理
    else if (nextFactory != null) {
      return nextFactory.getVariableResolver(name);
    }

    throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
  }


  public boolean isResolveable(String name) {
    //由3个部分构成,当前解析器,外部map以及next解析器工厂
    return (variableResolvers != null && variableResolvers.containsKey(name))
        || (variables != null && variables.containsKey(name))
        || (nextFactory != null && nextFactory.isResolveable(name));
  }

  /** 对指定的变量名，添加或修改相应的解析器 */
  protected VariableResolver addResolver(String name, VariableResolver vr) {
    if (variableResolvers == null) variableResolvers = new HashMap<String, VariableResolver>();
    variableResolvers.put(name, vr);
    return vr;
  }


  public boolean isTarget(String name) {
    return variableResolvers != null && variableResolvers.containsKey(name);
  }

  public Set<String> getKnownVariables() {
    if (nextFactory == null) {
      if (variables != null) return new HashSet<String>(variables.keySet());
      return new HashSet<String>(0);
    }
    else {
      if (variables != null) return new HashSet<String>(variables.keySet());
      return new HashSet<String>(0);
    }
  }
}
