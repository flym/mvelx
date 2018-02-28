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

package org.mvel2.optimizers.impl.asm;

import org.mvel2.asm.MethodVisitor;
import org.mvel2.integration.VariableResolverFactory;

/**
 * 用于描述一个可以自定义如何产生自定义字节码的标记接口,当一个propertyHandler被使用时,并且此处理器实现了此接口,则就会在asm优化时
 * 生成相应的自定义字节码来代替原来的反射访问
 * <br/>
 * 参考代码如下所示:<br/>
 * <pre><code>
 *   public void produceBytecodeGet(MethodVisitor mv, String propertyName, VariableResolverFactory variableResolverFactory) {
 * mv.visitTypeInsn(CHECKCAST, "org/mvel/tests/main/res/SampleBean");
 * mv.visitLdcInsn(propertyName);
 * mv.visitMethodInsn(INVOKEVIRTUAL, "org/mvel/tests/main/res/SampleBean", "getProperty", "(Ljava/lang/String;)Ljava/lang/Object;");
 * }
 * </code></pre><br/>
 * 这个示例代码即在调用get时进行相应的对象检查，如果当前对象是一个sampleBean，则执行相应的方法
 */
public interface ProducesBytecode {
    /** 在进行get调用时产生相应的字节码 */
    void produceBytecodeGet(MethodVisitor mv, String propertyName, VariableResolverFactory factory);

    /** 在进行set调用时产生相应的字节码 */
    void produceBytecodePut(MethodVisitor mv, String propertyName, VariableResolverFactory factory);
}
