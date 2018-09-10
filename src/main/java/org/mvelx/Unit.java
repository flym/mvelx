package org.mvelx;

/**
 * 表示一个特别的处理单元，即可以通过set和get分别将要处理的数据传入和传出
 * 这里传出的数据，即是已经转换好的数据
 */
public interface Unit extends ConversionHandler {
    /** 传出数据 */
    double getValue();

    /** 传入数据 */
    void setValue(double value);
}
