/**
 * MVEL 2.0
 * Copyright (C) 2007  MVFLEX/Valhalla Project and the Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2;

/** 描述一个具体的错误信息 */
public class ErrorDetail {

  /** 整个表达式 */
  private char[] expr;
  /** 出错表达式的起始偏移量 */
  private int cursor;
  /** 此错误是否是严重的 */
  private boolean critical;
  /** 错误描述信息 */
  private String message;

  /** 出错的代码行,第几行 */
  private int lineNumber;
  /** 出错的列,在这一行的第几列 */
  private int column;


  public ErrorDetail(char[] expr, int cursor, boolean critical, String message) {
    this.expr = expr;
    this.cursor = cursor;
    this.critical = critical;
    this.message = message;

    calcRowAndColumn();
  }

  public boolean isCritical() {
    return critical;
  }

  public void setCritical(boolean critical) {
    this.critical = critical;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public int getCursor() {
    return cursor;
  }

  /** 计算出相应的出错行,和出错列 */
  public void calcRowAndColumn() {
    int row = 1;
    int col = 1;

    if ((lineNumber != 0 && column != 0) || expr == null || expr.length == 0) return;

    //即从0开始,直到到达出错的位置,然后碰到换行符就+1,然后换行之后,相应的列清0,重新计算
    for (int i = 0; i < cursor; i++) {
      switch (expr[i]) {
        case '\r':
          continue;
        case '\n':
          row++;
          col = 0;
          break;

        default:
          col++;
      }
    }

    this.lineNumber = row;
    this.column = col;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public int getColumn() {
    return column;
  }

  public void setCursor(int cursor) {
    this.cursor = cursor;
  }

  public void setExpr(char[] expr) {
    this.expr = expr;
  }

  public char[] getExpr() {
    return expr;
  }

  public void setLineNumber(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  public void setColumn(int column) {
    this.column = column;
  }

  public String toString() {
    if (critical) {
      return "(" + lineNumber + "," + column + ") " + message;
    }
    else {
      return "(" + lineNumber + "," + column + ") WARNING: " + message;
    }
  }
}
