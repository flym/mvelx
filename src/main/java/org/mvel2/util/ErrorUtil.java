package org.mvel2.util;

import org.mvel2.CompileException;
import org.mvel2.ErrorDetail;

/**
 * 异常工具类,主要用于纠正一些异常的描述信息中的不一致问题
 * @author Mike Brock .
 */
public class ErrorUtil {
  /** 重写编译异常信息 */
  public static CompileException rewriteIfNeeded(CompileException caught, char[] outer, int outerCursor) {
    if (outer != caught.getExpr()) {
      if (caught.getExpr().length <= caught.getCursor()) {
        caught.setCursor(caught.getExpr().length - 1);
      }

      //按照重写错误信息的逻辑重新设置相应的表达式以及相应的偏移量
      try {
      String innerExpr = new String(caught.getExpr()).substring(caught.getCursor());
      caught.setExpr(outer);

      String outerStr = new String(outer);

      int newCursor = outerStr.substring(outerStr.indexOf(new String(caught.getExpr())))
          .indexOf(innerExpr);

      caught.setCursor(newCursor);
      }
      catch (Throwable t) {
        t.printStackTrace();
      }
    }
    return caught;
  }

  /** 使用外部的表达式,在指定的下标开始处重写相应的错误信息 */
  public static ErrorDetail rewriteIfNeeded(ErrorDetail detail, char[] outer, int outerCursor) {
    if (outer != detail.getExpr()) {
      //找到之前在原错误信息中出错的起始点
      String innerExpr = new String(detail.getExpr()).substring(detail.getCursor());
      //设置新的错误表达式
      detail.setExpr(outer);

      //然后根据外部信息重新进行查找到错误点,并重新进行定位处理
      int newCursor = outerCursor;
      newCursor += new String(outer).substring(outerCursor).indexOf(innerExpr);

      detail.setCursor(newCursor);
    }
    return detail;
  }
}
