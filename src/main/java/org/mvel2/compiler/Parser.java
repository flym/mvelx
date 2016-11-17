package org.mvel2.compiler;

/**
 * 主要的编译层接口描述
 *
 * @author Mike Brock .
 */
public interface Parser {
  /** 当前处理的语句的下标位置 */
  public int getCursor();

  /** 描述当前处理的表达式 */
  public char[] getExpression();
}
