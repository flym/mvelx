package org.mvel2.optimizers.dynamic;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.optimizers.impl.refl.nodes.BaseAccessor;

import static java.lang.System.currentTimeMillis;

/** 处理对象设置值之类的动态优化访问器 */
public class DynamicSetAccessor extends BaseAccessor implements DynamicAccessor {
    /** 处理的表达式 */
    private char[] property;
    /** 当前语句起始下标 */
    private int start;
    /** 当前语句长度位 */
    private int offset;

    /** 是否优化过 */
    private boolean opt = false;
    /** 统计运行次数 */
    private int runcount = 0;
    /** 上限统计计时 */
    private long stamp;

    private ParserContext context;
    /** 可安全调用的访问器 */
    private final Accessor _safeAccessor;
    /** 当前使用的访问器(可能为优化版本) */
    private Accessor _accessor;

    public DynamicSetAccessor(ParserContext context, char[] property, int start, int offset, Class ctxClass, AccessorNode _accessor) {
        super(new String(property, start, offset), context);
        setNextNode(_accessor, ctxClass);

        assert _accessor != null;
        this._safeAccessor = this._accessor = _accessor;
        this.context = context;

        this.property = property;
        this.start = start;
        this.offset = offset;

        this.stamp = System.currentTimeMillis();
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        //如果未优化,则按照处理策略进行优化
        //处理策略即在指定时间内运行了多少次
        //todo 暂时屏蔽
        /*
        if(!opt) {
            if(++runcount > DynamicOptimizer.tenuringThreshold) {
                if((currentTimeMillis() - stamp) < DynamicOptimizer.timeSpan) {
                    opt = true;
                    return optimize(ctx, elCtx, variableFactory, value);
                } else {
                    runcount = 0;
                    stamp = currentTimeMillis();
                }
            }
        }
        */

        fetchNextAccessNode(ctx, elCtx, variableFactory).setValue(ctx, elCtx, variableFactory, value);
        return value;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        throw new RuntimeException("value cannot be read with this accessor");
    }

    /** 对相应的表达式进行优化 */
    private Object optimize(Object ctx, Object elCtx, VariableResolverFactory variableResolverFactory, Object value) {
        if(DynamicOptimizer.isOverloaded()) {
            DynamicOptimizer.enforceTenureLimit();
        }

        //采用asm进行优化处理
        AccessorOptimizer ao = OptimizerFactory.getAccessorCompiler("ASM");
        _accessor = ao.optimizeSetAccessor(context, property, start, offset, ctx, elCtx,
                variableResolverFactory, false, value, value != null ? value.getClass() : Object.class);
        assert _accessor != null;

        return value;
    }

    /** 反优化处理 */
    public void deoptimize() {
        this._accessor = this._safeAccessor;
        opt = false;
        runcount = 0;
        stamp = currentTimeMillis();
    }

    /** 相应的声明类型即安全访问器的声明类型 */
    public Class getKnownEgressType() {
        return _safeAccessor.getKnownEgressType();
    }
}