package org.mvelx.compiler;

/**
 * 主要的编译层接口描述
 *
 * @author Mike Brock .
 */
public interface Parser {
    /** 当前处理的语句的下标位置 */
    int getCursor();

    /** 描述当前处理的表达式 */
    char[] getExpression();
}
