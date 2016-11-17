package org.mvel2;

/**
 * 用于描述在整个脚本运行期间(而不是编译)的运行错误，主要是数据不匹配或者是类型不匹配的一些原因
 *
 * @author Mike Brock .
 */
public class ScriptRuntimeException extends RuntimeException {
  public ScriptRuntimeException() {
  }

  public ScriptRuntimeException(String message) {
    super(message);
  }

  public ScriptRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public ScriptRuntimeException(Throwable cause) {
    super(cause);
  }
}
