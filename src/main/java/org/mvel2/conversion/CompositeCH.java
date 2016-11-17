package org.mvel2.conversion;

import org.mvel2.ConversionHandler;

/**
 * 组合转换，即内部有多个转换器，如果任意一个能转换就采用此转换器
 * 此转换器不会级联式处理，即有一个处理了，即会提前返回
 */
public class CompositeCH implements ConversionHandler {

  /** 内部使用的多个转换器 */
  private final ConversionHandler[] converters;

  public CompositeCH(ConversionHandler... converters) {
    this.converters = converters;
  }

  public Object convertFrom(Object in) {
    //只要任意一个成功了,即返回其结果
    for (ConversionHandler converter : converters) {
      if (converter.canConvertFrom(in.getClass())) return converter.convertFrom(in);
    }
    return null;
  }

  public boolean canConvertFrom(Class cls) {
    for (ConversionHandler converter : converters) {
      if (converter.canConvertFrom(cls)) return true;
    }
    return false;
  }
}
