/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvel2;

/**
 * 描述在属性访问期间碰到的错误，如属性为null或者是属性找不到等
 */
public class PropertyAccessException extends CompileException {

  public PropertyAccessException(String message, char[] expr, int cursor, Throwable e, ParserContext pCtx) {
    super(message, expr, cursor, e);
    setParserContext(pCtx);
  }

  public PropertyAccessException(String message, char[] expr, int cursor, ParserContext pCtx) {
    super(message, expr, cursor);
    setParserContext(pCtx);
  }

  private void setParserContext(ParserContext pCtx) {
    if (pCtx != null) {
      setEvaluationContext(pCtx.getEvaluationContext());
    }
  }
}
