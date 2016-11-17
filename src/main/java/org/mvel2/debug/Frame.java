/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
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

package org.mvel2.debug;

import org.mvel2.ParserContext;
import org.mvel2.ast.LineLabel;
import org.mvel2.integration.VariableResolverFactory;
/** 用于表示一个特定的代码执行帧(参考java执行栈帧),相应的执行情况,可以查看到第几行,以及当前的一些变量信息 */
public class Frame {
  /** 相应的代码行 */
  private LineLabel label;
  /** 当前的变量解析工厂 */
  private VariableResolverFactory factory;

  public Frame(LineLabel label, VariableResolverFactory factory) {
    this.label = label;
    this.factory = factory;
  }

  /** 获取相应的脚本源文件 */
  public String getSourceName() {
    return label.getSourceFile();
  }

  /** 获取当前解析在第几行 */
  public int getLineNumber() {
    return label.getLineNumber();
  }

  public VariableResolverFactory getFactory() {
    return factory;
  }

  public void setFactory(VariableResolverFactory factory) {
    this.factory = factory;
  }
}
