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

import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;

/**
 * 用于描述抽象的语法块节点,即内部会有一个单独的代码块内容,如if for等
 * @author Christopher Brock
 */
public class BlockNode extends ASTNode {
  /** 当前语法块起始点 */
  protected int blockStart;
  /** 当前语法块结束点 */
  protected int blockOffset;

  /** 表示当前的执行块 */
  protected ExecutableStatement compiledBlock;

  public BlockNode(ParserContext pCtx) {
    super(pCtx);
  }

  public ExecutableStatement getCompiledBlock() {
    return compiledBlock;
  }

  public int getBlockStart() {
    return blockStart;
  }

  public int getBlockOffset() {
    return blockOffset;
  }
}

