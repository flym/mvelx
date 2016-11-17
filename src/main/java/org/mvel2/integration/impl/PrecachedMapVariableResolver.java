package org.mvel2.integration.impl;

import org.mvel2.integration.VariableResolver;

import java.util.Map;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;

/**
 * 预先缓存值在map中的变量解析器，即相应的值已经预先存放到map中，并且表示为一个map的entry对象
 * 这里并没有使用entry中的key作为变量名，而是声明变量名，因为两者可能并不相同
 * 因为这里使用的是entry，因此对相应的修改会同时反映到其外层的map当中
 */
public class PrecachedMapVariableResolver implements VariableResolver {
  /** 相应的变量名 */
  private String name;
  /** 声明的类型 */
  private Class<?> knownType;
  /** 之前存放在map的entry对象 */
  private Map.Entry entry;

  /** 通过一个相应的entry以及相应的变量名(key值)进行构建 */
  public PrecachedMapVariableResolver(Map.Entry entry, String name) {
    this.entry = entry;
    this.name = name;
  }

  /** 通过一个相应的entry以及相应的变量名(key值),相应的值类型进行构建 */
  public PrecachedMapVariableResolver(Map.Entry entry, String name, Class knownType) {
    this.name = name;
    this.knownType = knownType;
    this.entry = entry;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setStaticType(Class knownType) {
    this.knownType = knownType;
  }


  public String getName() {
    return name;
  }

  public Class getType() {
    return knownType;
  }

  /** 根据声明的类型转换并设置相应的值 */
  public void setValue(Object value) {
    //如果有类型,则执行进行类型转换
    if (knownType != null && value != null && value.getClass() != knownType) {
      Class t = value.getClass();
      if (!canConvert(knownType, t)) {
        throw new RuntimeException("cannot assign " + value.getClass().getName() + " to type: "
            + knownType.getName());
      }
      try {
        value = convert(value, knownType);
      }
      catch (Exception e) {
        throw new RuntimeException("cannot convert value of " + value.getClass().getName()
            + " to: " + knownType.getName());
      }
    }

    //noinspection unchecked
    entry.setValue(value);
  }

  public Object getValue() {
    return entry.getValue();
  }

  /** 无特殊标记 */
  public int getFlags() {
    return 0;
  }
}
