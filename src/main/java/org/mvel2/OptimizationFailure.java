package org.mvel2;

/** 描述尝试进行优化访问时(如将beanProperty修改为asm时)出现的程序异常 */
public class OptimizationFailure extends RuntimeException {

  public OptimizationFailure(String message) {
    super(message);
  }

  public OptimizationFailure(String message, Throwable cause) {
    super(message, cause);
  }
}
