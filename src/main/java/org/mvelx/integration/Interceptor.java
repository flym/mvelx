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
package org.mvelx.integration;

import org.mvelx.ast.ASTNode;

/**
 * 拦截器，用于执行某些额外的操作，在代码中通过 @name的方式进行调用
 * An interceptor can be used to decorate functionality into an expression or to hook into external functionality, such
 * as to log an event or fire some other event.
 *
 * @author Christopher Brock
 */
public interface Interceptor {
    int NORMAL_FLOW = 0;
    int SKIP = 1;
    int END = 2;

    /**
     * 在实际的node调用前执行
     * 当前的返回值没有任何意义
     * This method is executed before the wrapped statement.
     *
     * @param node    The ASTNode wrapped by the interceptor
     * @param factory The variable factory
     * @return The response code.  Should return 0.
     */
    int doBefore(ASTNode node, VariableResolverFactory factory);

    /**
     * 在实际的node调用后执行
     * 当前的返回值没有任何意义
     * This method is called after the wrapped statement has completed.  A copy of the top-value of the execution
     * stack is also availablehere.
     *
     * @param exitStackValue The value on the top of the stack after executing the statement.
     * @param node           The ASTNode wrapped by the interceptor
     * @param factory        The variable factory
     * @return The response code.  Should return 0.
     */
    int doAfter(Object exitStackValue, ASTNode node, VariableResolverFactory factory);
}
