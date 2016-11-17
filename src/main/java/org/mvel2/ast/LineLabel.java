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
package org.mvel2.ast;

import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

/**
 * 用于描述一个特定的调试的代码行标记,同时表示当前节点为调试节点
 * @author Christopher Brock
 */
public class LineLabel extends ASTNode {
  /** 相应的源文件 */
  private String sourceFile;
  /** 代码行数 */
  private int lineNumber;

  public LineLabel(String sourceFile, int lineNumber, ParserContext pCtx) {
    super(pCtx);
    this.lineNumber = lineNumber;
    this.sourceFile = sourceFile;
    //当前节点为调试节点
    this.fields = -1;
  }

  public String getSourceFile() {
    return sourceFile;
  }

  public void setSourceFile(String sourceFile) {
    this.sourceFile = sourceFile;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(int lineNumber) {
    this.lineNumber = lineNumber;
  }


  /** 不需要处理数据 */
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    return null;
  }

  /** 不需要处理数据 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    return null;
  }


  public String toString() {
    return "[SourceLine:" + lineNumber + "]";
  }
}
