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

import org.mvel2.ast.ASTNode;

/**
 * 描述一个不能正确解析指定属性变量的异常
 * 即当前解析器工厂不能解析此变量
 *
 * @author Christopher Brock
 */
public class UnresolveablePropertyException extends RuntimeException {

  /** 不能解析的变量名 */
  private String name;

  public UnresolveablePropertyException(ASTNode astNode, Throwable throwable) {
    super("unable to resolve token: " + astNode.getName(), throwable);
    this.name = astNode.getName();
  }

  public UnresolveablePropertyException(ASTNode astNode) {
    super("unable to resolve token: " + astNode.getName());
    this.name = astNode.getName();
  }

  public UnresolveablePropertyException(String name) {
    super("unable to resolve token: " + name);
    this.name = name;
  }

  public String getName() {
    return name;
  }
}