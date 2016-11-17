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

import static org.mvel2.util.PropertyTools.getFieldOrAccessor;

/**
 * 定义 isDef节点，以判断指定的变量是否存在
 * 或者判断当前字段或方法在this值中是否存在
 * */
public class IsDef extends ASTNode {
  public IsDef(char[] expr, int start, int offset, ParserContext pCtx) {
    super(pCtx);
    //计算出当前要处理的变量名
    this.nameCache = new String(this.expr = expr, this.start = start, this.offset = offset);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //先判断此变量名是否在解析上下文中是否存在.
    //否则判断当前类中是否有此字段或方法,即形成一个简单的当前对象方法或字段引用
    return factory.isResolveable(nameCache) || (thisValue != null && getFieldOrAccessor(thisValue.getClass(), nameCache) != null);
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //与编译运行相同
    return factory.isResolveable(nameCache) || (thisValue != null && getFieldOrAccessor(thisValue.getClass(), nameCache) != null);

  }

  /** isDef 返回类型为boolean */
  public Class getEgressType() {
    return Boolean.class;
  }
}
