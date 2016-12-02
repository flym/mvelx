package org.mvel2;

/** 描述在数据转换过程中的各种异常信息 */
public class ConversionException extends RuntimeException {


    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
