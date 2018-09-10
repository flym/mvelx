package org.mvelx;

/**
 * 描述一个不能正确解析指定属性变量的异常
 * 即当前解析器工厂不能解析此变量
 *
 * @author Christopher Brock
 */
public class UnresolveablePropertyException extends RuntimeException {

    /** 不能解析的变量名 */
    private String name;

    public UnresolveablePropertyException(String name) {
        super("unable to resolve token: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
