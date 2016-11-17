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

package org.mvel2.ast;

import org.mvel2.compiler.ExecutableStatement;

/** 赋值节点的抽象描述 */
public interface Assignment {
  /** 获取要进行赋值的变量名 */
  public String getAssignmentVar();

  /** 当前节点的表达式 */
  public char[] getExpression();

  /** 是否是new 新建声明,如 a = new X()这种 */
  public boolean isNewDeclaration();

  /** 具体赋值后面的值信息 */
  public void setValueStatement(ExecutableStatement stmt);
}
