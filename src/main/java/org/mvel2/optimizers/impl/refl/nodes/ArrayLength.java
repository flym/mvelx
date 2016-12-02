/**
 * MVEL (The MVFLEX Expression Language)
 * <p>
 * Copyright (C) 2007 Christopher Brock, MVFLEX/Valhalla Project and the Codehaus
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
package org.mvel2.optimizers.impl.refl.nodes;

import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;

import static java.lang.reflect.Array.getLength;

/**
 * 用于实现访问数组长度的访问器
 *
 * @author Christopher Brock
 */
public class ArrayLength extends BaseAccessor {

    public ArrayLength(String property, ParserContext parserContext) {
        super(property, parserContext);
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //采用原生Array反射调用的方式获取到相应的长度信息
        val length = getLength(ctx);
        if(nextNode != null) {
            return nextNode.getValue(length, elCtx, variableFactory);
        }

        return length;
    }

    /** 数组长度不支持相应的修改 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return null;
    }

    /** 数组长度返回类型为整数 */
    public Class getKnownEgressType() {
        return Integer.class;
    }
}
