package org.mvel2.optimizers.dynamic;


import org.mvel2.ParserContext;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AbstractOptimizer;
import org.mvel2.optimizers.AccessorOptimizeType;
import org.mvel2.optimizers.AccessorOptimizer;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.currentThread;
import static org.mvel2.optimizers.OptimizerFactory.SAFE_REFLECTIVE;
import static org.mvel2.optimizers.OptimizerFactory.getAccessorCompiler;
import static org.mvel2.optimizers.impl.asm.ASMAccessorOptimizer.setMVELClassLoader;

/**
 * 用于描述一个可以动态切换访问方式的优化器
 * 其优化器通过创建出2个版本的访问器,并且在运行期间根据运行的效果进行相应的运行状态切换
 * 当前可用的优化器分别为反射调用和asm字节码执行,这里即是通过切换这2种来达到动态访问的目的
 */
public class DynamicOptimizer extends AbstractOptimizer implements AccessorOptimizer {
    /** 用于支持第一步的优化访问，表示先使用此优化器进行访问,这里即通过反射的方式处理 */
    private AccessorOptimizer firstStage = getAccessorCompiler(SAFE_REFLECTIVE);

    /** 当前所使用的优化器加载类 */
    private volatile static DynamicClassLoader classLoader;
    /** 优化调用次数，表示某个调用在一定区间内调用了超过多少次 */
    public static int tenuringThreshold = 50;
    /** 优化的调用区间,即表示某个方法在某个频率内调用很频繁 */
    public static long timeSpan = 100;
    /**
     * 在当前执行器内最大的优化上限，表示某些方法不会作优化处理(在达到上限之后，再次处理将会通过某种手段将原来优化的方法重新还原,
     * 原因在于保证不会大量产生新类)
     */
    public static int maximumTenure = 1500;
    /** 总共还原了多少类(即从优化到反优化) */
    public static int totalRecycled = 0;
    /**
     * 通过读写锁来进行相应的隔离处理,即在使用访问器时为读锁,而需要要修改相应的访问器时为写锁,
     * 则之前相应的访问都停住,以保证相应的反优化能够正常运行,即避免在使用asm优化器时,另一个线程又来修改相应的优化器,甚至销毁
     */
    private static ReadWriteLock lock = new ReentrantReadWriteLock();
    private static Lock readLock = lock.readLock();
    private static Lock writeLock = lock.writeLock();

    public void init() {
        _init();
    }

    /** 设置相应的加载器 */
    private static void _init() {
        setMVELClassLoader(classLoader = new DynamicClassLoader(currentThread().getContextClassLoader(), maximumTenure));
    }

    /**
     * 强制反优化所有访问器,以避免之前生成类之后
     * 但实际上没有什么作用,因此后续又会持续相应的优化过程,然后再反优化,因此会有相应的问题
     */
    public static void enforceTenureLimit() {
        writeLock.lock();
        try{
            if(classLoader.isOverloaded()) {
                classLoader.deoptimizeAll();
                totalRecycled = +classLoader.getTotalClasses();
                _init();
            }
        } finally {
            writeLock.unlock();
        }
    }

    /** 进行正常的方法调用或访问 */
    public AccessorNode optimizeAccessor(ParserContext pCtx, char[] property, int start, int offset, Object ctx, Object thisRef,
                                         VariableResolverFactory factory, Class ingressType) {
        readLock.lock();
        try{
            Class ctxClass = ctx == null ? null : ctx.getClass();
            pCtx.optimizationNotify();
            return classLoader.registerDynamicAccessor(new DynamicGetAccessor(pCtx, property, start, offset, AccessorOptimizeType.ACCESS_REGULAR, ctxClass,
                    firstStage.optimizeAccessor(pCtx, property, start, offset, ctx, thisRef, factory, ingressType)));
        } finally {
            readLock.unlock();
        }
    }

    /** 进行动态的set方法调用 */
    public AccessorNode optimizeSetAccessor(ParserContext pCtx, char[] property, int start, int offset, Object ctx, Object thisRef,
                                            VariableResolverFactory factory, boolean rootThisRef, Object value, Class valueType) {

        readLock.lock();
        try{
            Class ctxClass = ctx == null ? null : ctx.getClass();
            return classLoader.registerDynamicAccessor(new DynamicSetAccessor(pCtx, property, start, offset, ctxClass,
                    firstStage.optimizeSetAccessor(pCtx, property, start, offset, ctx, thisRef, factory, rootThisRef, value, valueType)));
        } finally {
            readLock.unlock();
        }
    }


    /** 进行动态的内联集合类访问 */
    public AccessorNode optimizeCollection(ParserContext pCtx, Object rootObject, Class type, char[] property, int start,
                                           int offset, Object ctx, Object thisRef, VariableResolverFactory factory) {
        readLock.lock();
        try{
            Class ctxClass = ctx == null ? null : ctx.getClass();
            return classLoader.registerDynamicAccessor(new DynamicCollectionAccessor(pCtx, rootObject, type, property, start, offset, AccessorOptimizeType.ACCESS_COLLECTION, ctxClass,
                    firstStage.optimizeCollection(pCtx, rootObject, type, property, start, offset, ctx, thisRef, factory)));
        } finally {
            readLock.unlock();
        }
    }


    /** 进行动态的对象创建访问 */
    public AccessorNode optimizeObjectCreation(ParserContext pCtx, char[] property, int start, int offset,
                                               Object ctx, Object thisRef, VariableResolverFactory factory) {
        readLock.lock();
        try{
            Class ctxClass = ctx == null ? null : ctx.getClass();
            return classLoader.registerDynamicAccessor(new DynamicGetAccessor(pCtx, property, start, offset, AccessorOptimizeType.ACCESS_OBJ_CREATION, ctxClass,
                    firstStage.optimizeObjectCreation(pCtx, property, start, offset, ctx, thisRef, factory)));
        } finally {
            readLock.unlock();
        }
    }

    /** 当前优化器是否已经过载 */
    public static boolean isOverloaded() {
        return classLoader.isOverloaded();
    }

    /** 获取相应的返回值,即第一个反射优化器的结果值 */
    public Object getResultOptPass() {
        return firstStage.getResultOptPass();
    }

    /** 相应的声明类型即第一个优化器处理的声明类型 */
    public Class getEgressType() {
        return firstStage.getEgressType();
    }

    public boolean isLiteralOnly() {
        return firstStage.isLiteralOnly();
    }
}
