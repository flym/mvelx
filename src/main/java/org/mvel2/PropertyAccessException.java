package org.mvel2;

/**
 * 描述在属性访问期间碰到的错误，如属性为null或者是属性找不到等
 */
public class PropertyAccessException extends CompileException {

    public PropertyAccessException(String message, char[] expr, int cursor, Throwable e, ParserContext pCtx) {
        super(message, expr, cursor, e);
    }

    public PropertyAccessException(String message, char[] expr, int cursor, ParserContext pCtx) {
        super(message, expr, cursor);
    }

}
