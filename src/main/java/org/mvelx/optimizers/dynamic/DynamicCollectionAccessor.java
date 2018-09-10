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

package org.mvelx.optimizers.dynamic;

import org.mvelx.ParserContext;
import org.mvelx.compiler.AccessorNode;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.optimizers.AccessorOptimizeType;
import org.mvelx.optimizers.OptimizerFactory;
import org.mvelx.optimizers.impl.refl.nodes.BaseAccessor;

import static java.lang.System.currentTimeMillis;

/**
 * 针对内联集合InlineCollection的动态访问器
 *
 * @see org.mvelx.ast.InlineCollectionNode
 * @see org.mvelx.optimizers.impl.refl.nodes.Union
 * 整个逻辑与DynamicGetAccessor或setAccessor相一致
 */
public class DynamicCollectionAccessor extends BaseAccessor implements DynamicAccessor {
    private ParserContext pCtx;
    private Object rootObject;
    private Class colType;

    private char[] property;
    private int start;
    private int offset;

    private long stamp;
    private AccessorOptimizeType type;

    private int runCount;

    private boolean opt = false;

    private AccessorNode _safeAccessor;
    private AccessorNode _accessor;

    public DynamicCollectionAccessor(ParserContext pCtx, Object rootObject, Class colType, char[] property, int start, int offset, AccessorOptimizeType type, Class ctxClass, AccessorNode _accessor) {
        super(_accessor.nodeExpr(), pCtx);

        this.pCtx = pCtx;
        this.rootObject = rootObject;
        this.colType = colType;
        this._safeAccessor = this._accessor = _accessor;
        this.type = type;

        this.property = property;
        this.start = start;
        this.offset = offset;

        stamp = currentTimeMillis();
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //todo 暂时屏蔽
        /*
        if(!opt) {
            if(++runCount > DynamicOptimizer.tenuringThreshold) {
                if((currentTimeMillis() - stamp) < DynamicOptimizer.timeSpan) {
                    opt = true;

                    return optimize(pCtx, ctx, elCtx, variableFactory);
                } else {
                    runCount = 0;
                    stamp = currentTimeMillis();
                }
            }
        }
        */

        return _accessor.getValue(ctx, elCtx, variableFactory);
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        runCount++;
        return _accessor.setValue(ctx, elCtx, variableFactory, value);
    }

    private Object optimize(ParserContext pCtx, Object ctx, Object elCtx, VariableResolverFactory variableResolverFactory) {

        if(DynamicOptimizer.isOverloaded()) {
            DynamicOptimizer.enforceTenureLimit();
        }

        _accessor = OptimizerFactory.getAccessorCompiler("ASM").optimizeCollection(pCtx, rootObject, colType,
                property, start, offset, ctx, elCtx, variableResolverFactory);
        return _accessor.getValue(ctx, elCtx, variableResolverFactory);
    }


    public void deoptimize() {
        this._accessor = this._safeAccessor;
        opt = false;
        runCount = 0;
        stamp = currentTimeMillis();
    }

    public Class getKnownEgressType() {
        return colType;
    }

    @Override
    public AccessorNode setNextNode(AccessorNode accessorNode, Class<?> currentCtxType) {
        return _accessor.setNextNode(accessorNode, currentCtxType);
    }

    @Override
    public Class<?> getLastCtxType() {
        return _accessor.getLastCtxType();
    }
}