package org.mvel2.optimizers.impl.asm;

import lombok.val;
import org.mvel2.*;
import org.mvel2.asm.*;
import org.mvel2.asm.Type;
import org.mvel2.asm.commons.GeneratorAdapter;
import org.mvel2.ast.FunctionInstance;
import org.mvel2.ast.TypeDescriptor;
import org.mvel2.compiler.*;
import org.mvel2.integration.GlobalListenerFactory;
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AbstractOptimizer;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizationNotSupported;
import org.mvel2.optimizers.impl.refl.nodes.DelegatedAccessorNode;
import org.mvel2.optimizers.impl.refl.nodes.Union;
import org.mvel2.util.*;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.lang.String.valueOf;
import static java.lang.Thread.currentThread;
import static java.lang.reflect.Array.getLength;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.STATIC;
import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.asm.Opcodes.*;
import static org.mvel2.asm.Type.*;
import static org.mvel2.ast.TypeDescriptor.getClassReference;
import static org.mvel2.integration.GlobalListenerFactory.hasGetListeners;
import static org.mvel2.integration.GlobalListenerFactory.notifyGetListeners;
import static org.mvel2.integration.PropertyHandlerFactory.*;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.PropertyTools.getFieldOrAccessor;
import static org.mvel2.util.PropertyTools.getFieldOrWriteAccessor;
import static org.mvel2.util.ReflectionUtil.toNonPrimitiveArray;
import static org.mvel2.util.ReflectionUtil.toNonPrimitiveType;
import static org.mvel2.util.Varargs.normalizeArgsForVarArgs;
import static org.mvel2.util.Varargs.paramTypeVarArgsSafe;


/**
 * 实现基于asm字节码处理的优化器，通过直接分析字节码来达到执行的目的
 */
@SuppressWarnings({"TypeParameterExplicitlyExtendsObject", "unchecked", "UnusedDeclaration"})
public class AsmAccessorOptimizer extends AbstractOptimizer implements AccessorOptimizer {
    private static final String MAP_IMPL = "java/util/HashMap";

    private static String LIST_IMPL;
    private static String NAMESPACE;
    private static final int OPCODES_VERSION = Opcodes.V1_8;

    private static final org.mvel2.asm.commons.Method METHOD_GET_VALUE = org.mvel2.asm.commons.Method.getMethod("Object getValue(Object, Object, org.mvel2.integration.VariableResolverFactory)");
    private static final org.mvel2.asm.commons.Method METHOD_SET_VALUE = org.mvel2.asm.commons.Method.getMethod("Object setValue(Object, Object, org.mvel2.integration.VariableResolverFactory, Object)");
    private static final org.mvel2.asm.commons.Method METHOD_GET_KNOWN_EGRESS_TYPE = org.mvel2.asm.commons.Method.getMethod("Class getKnownEgressType()");
    private static final org.mvel2.asm.commons.Method METHOD_TO_STRING = org.mvel2.asm.commons.Method.getMethod("String toString()");

    static {
        String defaultNameSpace = System.getProperty("mvel2.namespace");
        if(defaultNameSpace == null) {
            NAMESPACE = "org/mvel2/";
        } else {
            NAMESPACE = defaultNameSpace;
        }

        String jitListImpl = System.getProperty("mvel2.jit.list_impl");
        if(jitListImpl == null) {
            LIST_IMPL = NAMESPACE + "util/FastList";
        } else {
            LIST_IMPL = jitListImpl;
        }
    }

    private Object ctx;
    private Object thisRef;

    private VariableResolverFactory variableFactory;

    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final Class[] EMPTY_CLASSES = new Class[0];

    private boolean first = true;
    /** 相应的jit是否还没有初始化 */
    private boolean notInit = false;
    private boolean deferFinish = false;
    private boolean literal = false;

    private boolean propNull = false;
    private boolean methNull = false;

    private String className;
    private ClassWriter cw;
    private GeneratorAdapter mv;

    private Object resultValue;
    private int stacksize = 1;
    private int maxlocals = 1;
    private long time;

    private ArrayList<ExecutableStatement> compiledInputs;

    private Class ingressType;
    private Class returnType;

    private int compileDepth = 0;

    @SuppressWarnings({"StringBufferField"})
    private StringAppender buildLog = new StringAppender();

    public AsmAccessorOptimizer() {
        //自动计算最大栈深和最大本地变量数
        new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    }

    private AsmAccessorOptimizer(ClassWriter cw, MethodVisitor mv,
                                 ArrayList<ExecutableStatement> compiledInputs, String className,
                                 StringAppender buildLog, int compileDepth) {
        this.cw = cw;
//        this.mv = mv;
        this.compiledInputs = compiledInputs;
        this.className = className;
        this.buildLog = buildLog;
        this.compileDepth = compileDepth + 1;

        notInit = true;
        deferFinish = true;
    }

    /** jit初始化样板代码，即初始化类以及相应方法 */
    private void _initJit4GetValue() {
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        synchronized(Runtime.getRuntime()) {
            cw.visit(OPCODES_VERSION, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, className = "ASMAccessorImpl_"
                            + valueOf(cw.hashCode()).replaceAll("-", "_") + (System.currentTimeMillis() / 10) +
                            ((int) (Math.random() * 100)),
                    null, "java/lang/Object", new String[]{NAMESPACE + "compiler/Accessor"});
        }

        //构造方法
        generateDefaultConstructor(cw);

        val sourceMv = cw.visitMethod(ACC_PUBLIC, METHOD_GET_VALUE.getName(), METHOD_GET_VALUE.getDescriptor(), null, null);
        mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_GET_VALUE, sourceMv);
        mv.visitCode();
    }

    private void _initJit4SetValue() {
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        synchronized(Runtime.getRuntime()) {
            cw.visit(OPCODES_VERSION, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, className = "ASMAccessorImpl_"
                            + valueOf(cw.hashCode()).replaceAll("-", "_") + (System.currentTimeMillis() / 10) +
                            ((int) (Math.random() * 100)),
                    null, "java/lang/Object", new String[]{NAMESPACE + "compiler/Accessor"});
        }

        //构造方法
        generateDefaultConstructor(cw);

        val sourceMv = cw.visitMethod(ACC_PUBLIC, METHOD_SET_VALUE.getName(), METHOD_SET_VALUE.getDescriptor(), null, null);
        mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_SET_VALUE, sourceMv);
        mv.visitCode();
    }

    public AccessorNode optimizeAccessor(ParserContext pCtx, char[] property, int start, int offset, Object staticContext,
                                         Object thisRef, VariableResolverFactory factory, Class ingressType) {
        time = System.currentTimeMillis();

        if(compiledInputs == null) {
            compiledInputs = new ArrayList<>();
        }

        this.start = cursor = start;
        this.end = start + offset;
        this.length = end - this.start;

        this.first = true;
        this.resultValue = null;

        this.pCtx = pCtx;
        this.expr = property;
        this.ctx = staticContext;
        this.thisRef = thisRef;
        this.variableFactory = factory;
        this.ingressType = ingressType;

        if(!notInit) {
            _initJit4GetValue();
        }
        return compileAccessor();
    }

    public AccessorNode optimizeSetAccessor(ParserContext pCtx, char[] property, int start, int offset, Object ctx,
                                            Object thisRef, VariableResolverFactory factory, boolean rootThisRef,
                                            Object value, Class ingressType) {
        this.expr = property;
        this.start = this.cursor = start;
        this.end = start + offset;
        this.length = start + offset;

        this.first = true;
        this.ingressType = ingressType;

        compiledInputs = new ArrayList<>();

        this.pCtx = pCtx;
        this.ctx = ctx;
        this.thisRef = thisRef;
        this.variableFactory = factory;

        char[] root = null;

        PropertyVerifier verifier = new PropertyVerifier(property, this.pCtx = pCtx);

        int split = findLastUnion();

        if(split != -1) {
            root = subset(property, 0, split);
        }

        AccessorNode rootAccessor = null;

        _initJit4SetValue();

        if(root != null) {
            int _length = this.length;
            int _end = this.end;
            char[] _expr = this.expr;

            this.length = end = (this.expr = root).length;

            // run the compiler but don't finish building.
            deferFinish = true;
            notInit = true;

            compileAccessor();
            ctx = this.resultValue;

            this.expr = _expr;
            this.cursor = start + root.length + 1;
            this.length = _length - root.length - 1;
            this.end = this.cursor + this.length;
        } else {
            debug("ALOAD 1");
            mv.visitVarInsn(ALOAD, 1);
        }

        try{

            skipWhitespace();

            if(collection) {
                int st = cursor;
                whiteSpaceSkip();

                if(st == end) {
                    throw new PropertyAccessException("unterminated '['", expr, start, pCtx);
                }

                if(scanTo(']')) {
                    throw new PropertyAccessException("unterminated '['", expr, start, pCtx);
                }

                String ex = new String(expr, st, cursor - st).trim();

                debug("CHECKCAST " + ctx.getClass().getName());
                mv.visitTypeInsn(CHECKCAST, getInternalName(ctx.getClass()));


                if(ctx instanceof Map) {
                    //noinspection unchecked
                    Object key = ((ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx)).getValue(ctx, variableFactory);
                    ((Map) ctx).put(key, convert(value, returnType = verifier.analyze()));

                    writeLiteralOrSubexpression(subCompileExpression(ex.toCharArray(), pCtx));

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);

                    if(value != null && returnType != value.getClass()) {
                        dataConversion(returnType);
                        checkCast(returnType);
                    }

                    debug("INVOKEINTERFACE Map.put");
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);

                    debug("POP");
                    mv.visitInsn(POP);

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);
                } else if(ctx instanceof List) {
                    //noinspection unchecked
                    Integer idx = (Integer) ((ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx)).getValue(ctx, variableFactory);
                    ((List) ctx).set(idx, convert(value, returnType = verifier.analyze()));

                    writeLiteralOrSubexpression(subCompileExpression(ex.toCharArray(), pCtx));
                    unwrapPrimitive(int.class);

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);

                    if(value != null && !value.getClass().isAssignableFrom(returnType)) {
                        dataConversion(returnType);
                        checkCast(returnType);
                    }

                    debug("INVOKEINTERFACE List.set");
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true);

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);
                } else if(hasPropertyHandler(ctx.getClass())) {
                    propHandlerByteCodePut(ex, ctx, ctx.getClass(), value);
                } else if(ctx.getClass().isArray()) {
                    Class type = getBaseComponentType(ctx.getClass());

                    Object idx = ((ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx)).getValue(ctx, variableFactory);

                    writeLiteralOrSubexpression(subCompileExpression(ex.toCharArray(), pCtx), int.class);
                    if(!(idx instanceof Integer)) {
                        dataConversion(Integer.class);
                        idx = DataConversion.convert(idx, Integer.class);
                        unwrapPrimitive(int.class);
                    }

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);

                    if(type.isPrimitive()) {
                        unwrapPrimitive(type);
                    } else if(!type.equals(value.getClass())) {
                        dataConversion(type);
                    }

                    arrayStore(type);

                    //noinspection unchecked
                    Array.set(ctx, (Integer) idx, convert(value, type));

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);
                } else {
                    throw new PropertyAccessException("cannot bind to collection property: " + new String(expr)
                            + ": not a recognized collection type: " + ctx.getClass(), expr, start, pCtx);
                }

                deferFinish = false;
                notInit = false;

                _finishJIT();

                try{
                    deferFinish = false;
                    return _initializeAccessor();
                } catch(Exception e) {
                    throw new CompileException("could not generate accessor", expr, start, e);
                }
            }

            String tk = new String(expr, this.cursor, this.length);
            Member member = getFieldOrWriteAccessor(ctx.getClass(), tk, value == null ? null : ingressType);

            if(GlobalListenerFactory.hasSetListeners()) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(tk);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitVarInsn(ALOAD, 4);
                mv.visitMethodInsn(INVOKESTATIC, NAMESPACE + "integration/GlobalListenerFactory",
                        "notifySetListeners", "(Ljava/lang/Object;Ljava/lang/String;L" + NAMESPACE
                                + "integration/VariableResolverFactory;Ljava/lang/Object;)V", false);

                GlobalListenerFactory.notifySetListeners(ctx, tk, variableFactory, value);
            }

            if(member instanceof Field) {
                checkCast(ctx.getClass());

                Field fld = (Field) member;

                Label jmp = null;
                Label jmp2 = new Label();

                if(fld.getType().isPrimitive()) {
                    debug("ASTORE 5");
                    mv.visitVarInsn(ASTORE, 5);

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);

                    if(value == null) {
                        value = PropertyTools.getPrimitiveInitialValue(fld.getType());
                    }

                    jmp = new Label();
                    debug("IFNOTNULL jmp");
                    mv.visitJumpInsn(IFNONNULL, jmp);

                    debug("ALOAD 5");
                    mv.visitVarInsn(ALOAD, 5);

                    debug("ICONST_0");
                    mv.visitInsn(ICONST_0);

                    debug("PUTFIELD " + getInternalName(fld.getDeclaringClass()) + "." + tk);
                    mv.visitFieldInsn(PUTFIELD, getInternalName(fld.getDeclaringClass()), tk, getDescriptor(fld.getType()));

                    debug("GOTO jmp2");
                    mv.visitJumpInsn(GOTO, jmp2);

                    debug("jmp:");
                    mv.visitLabel(jmp);

                    debug("ALOAD 5");
                    mv.visitVarInsn(ALOAD, 5);

                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);

                    unwrapPrimitive(fld.getType());
                } else {
                    debug("ALOAD 4");
                    mv.visitVarInsn(ALOAD, 4);
                    checkCast(fld.getType());
                }

                if(jmp == null && value != null && !fld.getType().isAssignableFrom(value.getClass())) {
                    if(!canConvert(fld.getType(), value.getClass())) {
                        throw new CompileException("cannot convert type: "
                                + value.getClass() + ": to " + fld.getType(), expr, start);
                    }

                    dataConversion(fld.getType());
                    fld.set(ctx, convert(value, fld.getType()));
                } else {
                    fld.set(ctx, value);
                }

                debug("PUTFIELD " + getInternalName(fld.getDeclaringClass()) + "." + tk);
                mv.visitFieldInsn(PUTFIELD, getInternalName(fld.getDeclaringClass()), tk, getDescriptor(fld.getType()));

                debug("jmp2:");
                mv.visitLabel(jmp2);

                debug("ALOAD 4");
                mv.visitVarInsn(ALOAD, 4);

            } else if(member != null) {
                debug("CHECKCAST " + getInternalName(ctx.getClass()));
                mv.visitTypeInsn(CHECKCAST, getInternalName(ctx.getClass()));

                Method meth = (Method) member;

                debug("ALOAD 4");
                mv.visitVarInsn(ALOAD, 4);

                Class targetType = meth.getParameterTypes()[0];

                Label jmp;
                Label jmp2 = new Label();
                if(value != null && !targetType.isAssignableFrom(value.getClass())) {
                    if(!canConvert(targetType, value.getClass())) {
                        throw new CompileException("cannot convert type: "
                                + value.getClass() + ": to " + meth.getParameterTypes()[0], expr, start);
                    }

                    dataConversion(toWrapperClass(targetType));
                    if(targetType.isPrimitive()) {
                        unwrapPrimitive(targetType);
                    } else {
                        checkCast(targetType);
                    }
                    meth.invoke(ctx, convert(value, meth.getParameterTypes()[0]));
                } else {
                    if(targetType.isPrimitive()) {

                        if(value == null) {
                            value = PropertyTools.getPrimitiveInitialValue(targetType);
                        }

                        jmp = new Label();
                        debug("IFNOTNULL jmp");
                        mv.visitJumpInsn(IFNONNULL, jmp);

                        debug("ICONST_0");
                        mv.visitInsn(ICONST_0);

                        debug("INVOKEVIRTUAL " + getInternalName(meth.getDeclaringClass()) + "." + meth.getName());
                        mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(meth.getDeclaringClass()), meth.getName(),
                                getMethodDescriptor(meth), false);

                        debug("GOTO jmp2");
                        mv.visitJumpInsn(GOTO, jmp2);

                        debug("jmp:");
                        mv.visitLabel(jmp);

                        debug("ALOAD 4");
                        mv.visitVarInsn(ALOAD, 4);

                        unwrapPrimitive(targetType);
                    } else {
                        checkCast(targetType);
                    }

                    meth.invoke(ctx, value);
                }

                debug("INVOKEVIRTUAL " + getInternalName(meth.getDeclaringClass()) + "." + meth.getName());
                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(meth.getDeclaringClass()), meth.getName(),
                        getMethodDescriptor(meth), false);

                debug("jmp2:");
                mv.visitLabel(jmp2);

                debug("ALOAD 4");
                mv.visitVarInsn(ALOAD, 4);
            } else if(ctx instanceof Map) {
                debug("CHECKCAST " + getInternalName(ctx.getClass()));
                mv.visitTypeInsn(CHECKCAST, getInternalName(ctx.getClass()));

                debug("LDC '" + tk + "'");
                mv.visitLdcInsn(tk);

                debug("ALOAD 4");
                mv.visitVarInsn(ALOAD, 4);

                debug("INVOKEINTERFACE java/util/Map.put");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);

                debug("ALOAD 4");
                mv.visitVarInsn(ALOAD, 4);

                //noinspection unchecked
                ((Map) ctx).put(tk, value);
            } else {
                throw new PropertyAccessException("could not access property (" + tk + ") in: "
                        + ingressType.getName(), expr, start, pCtx);
            }
        } catch(InvocationTargetException | IllegalAccessException e) {
            throw new PropertyAccessException("could not access property", expr, start, e, pCtx);
        }

        try{
            deferFinish = false;
            notInit = false;

            _finishJIT();
            return _initializeAccessor();
        } catch(Exception e) {

            throw new CompileException("could not generate accessor", expr, start, e);
        }
    }

    private void _finishJIT() {
        if(deferFinish) {
            return;
        }

        if(returnType != null && returnType.isPrimitive()) {
            //noinspection unchecked
            wrapPrimitive(returnType);
        }

        if(returnType == void.class) {
            debug("ACONST_NULL");
            mv.visitInsn(ACONST_NULL);
        }

        debug("ARETURN");
        mv.visitInsn(ARETURN);

        debug("\n{METHOD STATS (maxstack=" + stacksize + ")}\n");


        dumpAdvancedDebugging(); // dump advanced debugging if necessary


        mv.visitMaxs(stacksize, maxlocals);
        mv.visitEnd();

        mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_GET_KNOWN_EGRESS_TYPE, cw.visitMethod(ACC_PUBLIC, METHOD_GET_KNOWN_EGRESS_TYPE.getName(), METHOD_GET_KNOWN_EGRESS_TYPE.getDescriptor(), null, null));
        mv.visitCode();
        visitConstantClass(returnType);
        mv.visitInsn(ARETURN);

        mv.visitMaxs(1, 1);
        mv.visitEnd();

        if(propNull) {
            cw.visitField(ACC_PUBLIC, "nullPropertyHandler", "L" + NAMESPACE + "integration/PropertyHandler;", null, null).visitEnd();
        }

        if(methNull) {
            cw.visitField(ACC_PUBLIC, "nullMethodHandler", "L" + NAMESPACE + "integration/PropertyHandler;", null, null).visitEnd();
        }

        buildInputs();

        if(buildLog != null && buildLog.length() != 0 && expr != null) {
            mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_TO_STRING, cw.visitMethod(ACC_PUBLIC, METHOD_TO_STRING.getName(), METHOD_TO_STRING.getDescriptor(), null, null));
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLdcInsn(buildLog.toString() + "\n\n## { " + new String(expr) + " }");
            mv.visitInsn(ARETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
    }

    /** 加载常量 class类型 */
    private void visitConstantClass(Class<?> clazz) {
        if(clazz == null) {
            clazz = Object.class;
        }

        mv.push(Type.getType(clazz));
    }

    @SuppressWarnings("unchecked")
    private AccessorNode _initializeAccessor() throws Exception {
        if(deferFinish) {
            return null;
        }
        //Hot load the class we just generated.
        Class cls = loadClass(className, cw.toByteArray());

        debug("[MVEL JIT Completed Optimization <<" + (expr != null ? new String(expr) : "") + ">>]::" + cls
                + " (time: " + (System.currentTimeMillis() - time) + "ms)");

        Object o;

        try{
            if(compiledInputs.size() == 0) {
                o = cls.newInstance();
            } else {
                Class[] parms = new Class[compiledInputs.size()];
                for(int i = 0; i < compiledInputs.size(); i++) {
                    parms[i] = ExecutableStatement.class;
                }
                ExecutableStatement[] executableStatements = compiledInputs.toArray(new ExecutableStatement[compiledInputs.size()]);
                o = cls.getConstructor(parms).newInstance((Object[]) executableStatements);
            }

            if(propNull) {
                cls.getField("nullPropertyHandler").set(o, getNullPropertyHandler());
            }
            if(methNull) {
                cls.getField("nullMethodHandler").set(o, getNullMethodHandler());
            }

        } catch(VerifyError e) {
            System.out.println("**** COMPILER BUG! REPORT THIS IMMEDIATELY AT http://jira.codehaus.org/browse/MVEL");
            System.out.println("Expression: " + (expr == null ? null : new String(expr)));
            throw e;
        }

        Accessor accessor = (Accessor) o;

        //todo 这里临时通过，使用一个简化的处理以让accessor转换为accessorNode
        return new DelegatedAccessorNode(new String(expr, start, end - start), pCtx, accessor);
    }

    private AccessorNode compileAccessor() {
        debug("<<INITIATE COMPILE>>");

        Object curr = ctx;

        try{
            while(cursor < end) {
                switch(nextSubToken()) {
                    case BEAN:
                        curr = getBeanProperty(curr, capture());
                        break;
                    case METH:
                        curr = getMethod(curr, capture());
                        break;
                    case COL:
                        curr = getCollectionProperty(curr, capture());
                        break;
                }

                // check to see if a null safety is enabled on this property.
                if(fields == -1) {
                    if(curr == null) {
                        if(nullSafe) {
                            throw new OptimizationNotSupported();
                        }
                        break;
                    } else {
                        fields = 0;
                    }
                }

                first = false;

                if(nullSafe && cursor < end) {

                    debug("DUP");
                    mv.visitInsn(DUP);

                    Label j = new Label();

                    debug("IFNONNULL : jump");
                    mv.visitJumpInsn(IFNONNULL, j);

                    debug("ARETURN");
                    mv.visitInsn(ARETURN);

                    debug("LABEL:jump");
                    mv.visitLabel(j);
                }
            }

            resultValue = curr;

            _finishJIT();

            return _initializeAccessor();
        } catch(InvocationTargetException | IndexOutOfBoundsException | IllegalAccessException | NullPointerException e) {
            throw new PropertyAccessException(new String(expr), expr, st, e, pCtx);
        } catch(PropertyAccessException e) {
            throw new CompileException(e.getMessage(), expr, st, e);
        } catch(CompileException | OptimizationNotSupported e) {
            throw e;
        } catch(Exception e) {
            throw new CompileException(e.getMessage(), expr, st, e);
        }
    }

    private Object getBeanPropertyAO(Object ctx, String property)
            throws IllegalAccessException, InvocationTargetException {
        if(ctx != null && hasPropertyHandler(ctx.getClass())) {
            return propHandlerByteCode(property, ctx, ctx.getClass());
        }
        return getBeanProperty(ctx, property);
    }

    private Object getBeanProperty(Object ctx, String property)
            throws IllegalAccessException, InvocationTargetException {

        debug("\n  **  ENTER -> {bean: " + property + "; ctx=" + ctx + "}");

        if((pCtx == null ? currType : pCtx.getVarOrInputTypeOrNull(property)) == Object.class
                && (pCtx != null && !pCtx.isStrongTyping())) {
            currType = null;
        }

        if(returnType != null && returnType.isPrimitive()) {
            //noinspection unchecked
            wrapPrimitive(returnType);
        }


        boolean classRef = false;

        Class<?> cls;
        if(ctx instanceof Class) {
            if(MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS
                    && "class".equals(property)) {
                pushClass((Class<?>) ctx);

                return ctx;
            }

            cls = (Class<?>) ctx;
            classRef = true;
        } else if(ctx != null) {
            cls = ctx.getClass();
        } else {
            cls = null;
        }

        if(hasPropertyHandler(cls)) {
            PropertyHandler prop = getPropertyHandler(cls);
            if(prop instanceof ProducesBytecode) {
                ((ProducesBytecode) prop).produceBytecodeGet(mv, property, variableFactory);
                return prop.getProperty(property, ctx, variableFactory);
            } else {
                throw new RuntimeException("unable to compileShared: custom accessor does not support producing bytecode: "
                        + prop.getClass().getName());
            }
        }

        Member member = cls != null ? getFieldOrAccessor(cls, property) : null;

        if(member != null && classRef && (member.getModifiers() & Modifier.STATIC) == 0) {
            member = null;
        }

        if(member != null && hasGetListeners()) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(member.getName());
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKESTATIC, NAMESPACE + "integration/GlobalListenerFactory", "notifyGetListeners",
                    "(Ljava/lang/Object;Ljava/lang/String;L" + NAMESPACE + "integration/VariableResolverFactory;)V", false);

            notifyGetListeners(ctx, member.getName(), variableFactory);
        }

        if(first) {
            if("this".equals(property)) {
                debug("ALOAD 2");
                mv.visitVarInsn(ALOAD, 2);
                return thisRef;
            } else if(variableFactory != null && variableFactory.isResolveable(property)) {

                if(variableFactory.isIndexedFactory() && variableFactory.isTarget(property)) {
                    int idx;
                    try{
                        loadVariableByIndex(idx = variableFactory.variableIndexOf(property));
                    } catch(Exception e) {
                        throw new OptimizationFailure(property);
                    }


                    return variableFactory.getIndexedVariableResolver(idx).getValue();
                } else {
                    try{
                        loadVariableByName(property);
                    } catch(Exception e) {
                        throw new OptimizationFailure("critical error in JIT", e);
                    }

                    return variableFactory.getVariableResolver(property).getValue();
                }
            } else {
                debug("ALOAD 1");
                mv.visitVarInsn(ALOAD, 1);
            }
        }

        if(member instanceof Field) {
            return optimizeFieldMethodProperty(ctx, property, cls, member);
        } else if(member != null) {
            Object o;

            if(first) {
                debug("ALOAD 1 (B)");
                mv.visitVarInsn(ALOAD, 1);
            }

            try{
                o = ((Method) member).invoke(ctx, EMPTY_ARGS);

                if(returnType != member.getDeclaringClass()) {
                    debug("CHECKCAST " + getInternalName(member.getDeclaringClass()));
                    mv.visitTypeInsn(CHECKCAST, getInternalName(member.getDeclaringClass()));
                }

                returnType = ((Method) member).getReturnType();

                debug("INVOKEVIRTUAL " + member.getName() + ":" + returnType);
                mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(member.getDeclaringClass()), member.getName(),
                        getMethodDescriptor((Method) member), false);
            } catch(IllegalAccessException e) {
                Method iFaceMeth = determineActualTargetMethod((Method) member);
                if(iFaceMeth == null) {
                    throw new PropertyAccessException("could not access field: " + cls.getName() + "." + property, expr, st, e, pCtx);
                }

                debug("CHECKCAST " + getInternalName(iFaceMeth.getDeclaringClass()));
                mv.visitTypeInsn(CHECKCAST, getInternalName(iFaceMeth.getDeclaringClass()));

                returnType = iFaceMeth.getReturnType();

                debug("INVOKEINTERFACE " + member.getName() + ":" + returnType);
                mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(iFaceMeth.getDeclaringClass()), member.getName(),
                        getMethodDescriptor((Method) member), true);

                o = iFaceMeth.invoke(ctx, EMPTY_ARGS);
            } catch(IllegalArgumentException e) {
                if(member.getDeclaringClass().equals(ctx)) {
                    try{
                        Class c = Class.forName(member.getDeclaringClass().getName() + "$" + property);

                        throw new CompileException("name collision between innerclass: " + c.getCanonicalName()
                                + "; and bean accessor: " + property + " (" + member.toString() + ")", expr, tkStart);
                    } catch(ClassNotFoundException e2) {
                        //fallthru
                    }
                }
                throw e;
            }

            if(hasNullPropertyHandler()) {
                if(o == null) {
                    o = getNullPropertyHandler().getProperty(member.getName(), ctx, variableFactory);
                }
                writeOutNullHandler(member, 0);
            }

            currType = toNonPrimitiveType(returnType);
            return o;
        } else if(ctx instanceof Map && (((Map) ctx).containsKey(property) || nullSafe)) {
            debug("CHECKCAST java/util/Map");
            mv.visitTypeInsn(CHECKCAST, "java/util/Map");

            debug("LDC: \"" + property + "\"");
            mv.push(property);

            debug("INVOKEINTERFACE: get");
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            return ((Map) ctx).get(property);
        } else if(first && "this".equals(property)) {
            debug("ALOAD 2");
            mv.visitVarInsn(ALOAD, 2); // load the thisRef value.

            return this.thisRef;
        } else if("length".equals(property) && ctx != null && ctx.getClass().isArray()) {
            arrayCheckCast(ctx.getClass());

            debug("ARRAYLENGTH");
            mv.visitInsn(ARRAYLENGTH);

            wrapPrimitive(int.class);
            return getLength(ctx);
        } else if(LITERALS.containsKey(property)) {
            Object lit = LITERALS.get(property);

            if(lit instanceof Class) {
                pushClass((Class) lit);
            }

            return lit;
        } else {
            Object ts = tryStaticAccess();

            if(ts != null) {
                if(ts instanceof Class) {
                    pushClass((Class) ts);
                    return ts;
                } else if(ts instanceof Method) {
                    writeFunctionPointerStub(((Method) ts).getDeclaringClass(), (Method) ts);
                    return ts;
                } else {
                    Field f = (Field) ts;
                    return optimizeFieldMethodProperty(ctx, property, cls, f);
                }
            } else if(ctx instanceof Class) {
                /*
                 * This is our ugly support for function pointers.  This works but needs to be re-thought out at some
                 * point.
                 */
                Class c = (Class) ctx;
                for(Method m : c.getMethods()) {
                    if(property.equals(m.getName())) {
                        writeFunctionPointerStub(c, m);
                        return m;
                    }
                }

                try{
                    Class subClass = findClass(variableFactory, c.getName() + "$" + property, pCtx);
                    pushClass(subClass);
                    return subClass;
                } catch(ClassNotFoundException cnfe) {
                    // fall through.
                }

            }

            if(ctx == null) {
                throw new PropertyAccessException("unresolvable property or identifier: " + property, expr, st, pCtx);
            } else {
                throw new PropertyAccessException("could not access: " + property + "; in class: "
                        + ctx.getClass().getName(), expr, st, pCtx);
            }
        }
    }

    private Object optimizeFieldMethodProperty(Object ctx, String property, Class<?> cls, Member member)
            throws IllegalAccessException {
        Object o = ((Field) member).get(ctx);

        if(((member.getModifiers() & STATIC) != 0)) {
            // Check if the static field reference is a constant and a primitive.
            if((member.getModifiers() & FINAL) != 0 && (o instanceof String || ((Field) member).getType().isPrimitive())) {
                o = ((Field) member).get(null);
                debug("LDC " + valueOf(o));
                mv.visitLdcInsn(o);
                if(o != null) {
                    wrapPrimitive(o.getClass());
                }

                if(hasNullPropertyHandler()) {
                    if(o == null) {
                        o = getNullPropertyHandler().getProperty(member.getName(), ctx, variableFactory);
                    }

                    writeOutNullHandler(member, 0);
                }
                return o;
            } else {
                debug("GETSTATIC " + getDescriptor(member.getDeclaringClass()) + "."
                        + member.getName() + "::" + getDescriptor(((Field) member).getType()));

                mv.visitFieldInsn(GETSTATIC, getInternalName(member.getDeclaringClass()),
                        member.getName(), getDescriptor(returnType = ((Field) member).getType()));
            }
        } else {
            debug("CHECKCAST " + getInternalName(cls));
            mv.visitTypeInsn(CHECKCAST, getInternalName(cls));

            debug("GETFIELD " + property + ":" + getDescriptor(((Field) member).getType()));
            mv.visitFieldInsn(GETFIELD, getInternalName(cls), property, getDescriptor(returnType = ((Field) member)
                    .getType()));
        }

        returnType = ((Field) member).getType();

        if(hasNullPropertyHandler()) {
            if(o == null) {
                o = getNullPropertyHandler().getProperty(member.getName(), ctx, variableFactory);
            }

            writeOutNullHandler(member, 0);
        }

        currType = toNonPrimitiveType(returnType);
        return o;
    }


    private void writeFunctionPointerStub(Class c, Method m) {
        pushClass(c);

        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", false);
        mv.visitVarInsn(ASTORE, 7);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, 5);
        mv.visitVarInsn(ALOAD, 7);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitVarInsn(ISTORE, 6);
        Label l1 = new Label();
        mv.visitJumpInsn(GOTO, l1);
        Label l2 = new Label();
        mv.visitLabel(l2);
        mv.visitVarInsn(ALOAD, 7);
        mv.visitVarInsn(ILOAD, 5);
        mv.visitInsn(AALOAD);
        mv.visitVarInsn(ASTORE, 4);
        Label l3 = new Label();
        mv.visitLabel(l3);
        mv.visitLdcInsn(m.getName());
        mv.visitVarInsn(ALOAD, 4);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label l4 = new Label();
        mv.visitJumpInsn(IFEQ, l4);
        Label l5 = new Label();
        mv.visitLabel(l5);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(ARETURN);
        mv.visitLabel(l4);
        mv.visitIincInsn(5, 1);
        mv.visitLabel(l1);
        mv.visitVarInsn(ILOAD, 5);
        mv.visitVarInsn(ILOAD, 6);
        mv.visitJumpInsn(IF_ICMPLT, l2);
        Label l6 = new Label();
        mv.visitLabel(l6);
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);

        //   deferFinish = true;
    }


    private Object getCollectionProperty(Object ctx, String prop)
            throws IllegalAccessException, InvocationTargetException {
        if(prop.trim().length() > 0) {
            ctx = getBeanProperty(ctx, prop);
            first = false;
        }

        currType = null;

        debug("\n  **  ENTER -> {collection:<<" + prop + ">>; ctx=" + ctx + "}");

        int start = ++cursor;

        skipWhitespace();

        if(cursor == end) {
            throw new CompileException("unterminated '['", expr, st);
        }

        if(scanTo(']')) {
            throw new CompileException("unterminated '['", expr, st);
        }

        String tk = new String(expr, start, cursor - start);

        debug("{collection token: [" + tk + "]}");

        if(ctx == null) {
            return null;
        }
        if(first) {
            debug("ALOAD 1");
            mv.visitVarInsn(ALOAD, 1);
        }

        ExecutableStatement compiled = (ExecutableStatement) subCompileExpression(tk.toCharArray(), pCtx);
        Object item = compiled.getValue(ctx, variableFactory);

        ++cursor;

        if(ctx instanceof Map) {
            debug("CHECKCAST java/util/Map");
            mv.visitTypeInsn(CHECKCAST, "java/util/Map");

            Class c = writeLiteralOrSubexpression(compiled);
            if(c != null && c.isPrimitive()) {
                wrapPrimitive(c);
            }

            debug("INVOKEINTERFACE: get");
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);

            return ((Map) ctx).get(item);
        } else if(ctx instanceof List) {
            debug("CHECKCAST java/util/List");
            mv.visitTypeInsn(CHECKCAST, "java/util/List");

            writeLiteralOrSubexpression(compiled, int.class);

            debug("INVOKEINTERFACE: java/util/List.get");
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);

            return ((List) ctx).get(convert(item, Integer.class));

        } else if(ctx.getClass().isArray()) {
            debug("CHECKCAST " + getDescriptor(ctx.getClass()));
            mv.visitTypeInsn(CHECKCAST, getDescriptor(ctx.getClass()));

            writeLiteralOrSubexpression(compiled, int.class, item.getClass());

            Class cls = getBaseComponentType(ctx.getClass());
            if(cls.isPrimitive()) {
                if(cls == int.class) {
                    debug("IALOAD");
                    mv.visitInsn(IALOAD);
                } else if(cls == char.class) {
                    debug("CALOAD");
                    mv.visitInsn(CALOAD);
                } else if(cls == boolean.class) {
                    debug("BALOAD");
                    mv.visitInsn(BALOAD);
                } else if(cls == double.class) {
                    debug("DALOAD");
                    mv.visitInsn(DALOAD);
                } else if(cls == float.class) {
                    debug("FALOAD");
                    mv.visitInsn(FALOAD);
                } else if(cls == short.class) {
                    debug("SALOAD");
                    mv.visitInsn(SALOAD);
                } else if(cls == long.class) {
                    debug("LALOAD");
                    mv.visitInsn(LALOAD);
                } else if(cls == byte.class) {
                    debug("BALOAD");
                    mv.visitInsn(BALOAD);
                }

                wrapPrimitive(cls);
            } else {
                debug("AALOAD");
                mv.visitInsn(AALOAD);
            }

            return Array.get(ctx, convert(item, Integer.class));
        } else if(ctx instanceof CharSequence) {
            debug("CHECKCAST java/lang/CharSequence");
            mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");

            if(item instanceof Integer) {
                pushInt((Integer) item);

                debug("INVOKEINTERFACE java/lang/CharSequence.charAt");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "charAt", "(I)C", true);

                wrapPrimitive(char.class);

                return ((CharSequence) ctx).charAt((Integer) item);
            } else {
                writeLiteralOrSubexpression(compiled, Integer.class);
                unwrapPrimitive(int.class);

                debug("INVOKEINTERFACE java/lang/CharSequence.charAt");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "charAt", "(I)C", true);

                wrapPrimitive(char.class);

                return ((CharSequence) ctx).charAt(convert(item, Integer.class));
            }
        } else {
            TypeDescriptor tDescr = new TypeDescriptor(expr, this.start, length, 0);
            if(tDescr.isArray()) {
                try{
                    Class cls = getClassReference((Class) ctx, tDescr, variableFactory, pCtx);
                    //   rootNode = new StaticReferenceAccessor(cls);
                    pushClass(cls);
                    return cls;
                } catch(Exception e) {
                    //fall through
                }
            }

            throw new CompileException("illegal use of []: unknown type: " + ctx.getClass().getName(), expr, st);
        }
    }

    private Object getCollectionPropertyAO(Object ctx, String prop)
            throws IllegalAccessException, InvocationTargetException {
        if(prop.length() > 0) {
            ctx = getBeanProperty(ctx, prop);
            first = false;
        }

        currType = null;

        debug("\n  **  ENTER -> {collection:<<" + prop + ">>; ctx=" + ctx + "}");

        int _start = ++cursor;

        skipWhitespace();

        if(cursor == end) {
            throw new CompileException("unterminated '['", expr, st);
        }

        if(scanTo(']')) {
            throw new CompileException("unterminated '['", expr, st);
        }

        String tk = new String(expr, _start, cursor - _start);

        debug("{collection token:<<" + tk + ">>}");

        if(ctx == null) {
            return null;
        }

        ExecutableStatement compiled = (ExecutableStatement) subCompileExpression(tk.toCharArray());
        Object item = compiled.getValue(ctx, variableFactory);

        ++cursor;

        if(ctx instanceof Map) {
            if(hasPropertyHandler(Map.class)) {
                return propHandlerByteCode(tk, ctx, Map.class);
            } else {
                if(first) {
                    debug("ALOAD 1");
                    mv.visitVarInsn(ALOAD, 1);
                }

                debug("CHECKCAST java/util/Map");
                mv.visitTypeInsn(CHECKCAST, "java/util/Map");

                Class c = writeLiteralOrSubexpression(compiled);
                if(c != null && c.isPrimitive()) {
                    wrapPrimitive(c);
                }

                debug("INVOKEINTERFACE: Map.get");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            }

            return ((Map) ctx).get(item);
        } else if(ctx instanceof List) {
            if(hasPropertyHandler(List.class)) {
                return propHandlerByteCode(tk, ctx, List.class);
            } else {
                if(first) {
                    debug("ALOAD 1");
                    mv.visitVarInsn(ALOAD, 1);
                }

                debug("CHECKCAST java/util/List");
                mv.visitTypeInsn(CHECKCAST, "java/util/List");

                writeLiteralOrSubexpression(compiled, int.class);

                debug("INVOKEINTERFACE: java/util/List.get");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);

                return ((List) ctx).get(convert(item, Integer.class));
            }
        } else if(ctx.getClass().isArray()) {
            if(hasPropertyHandler(Array.class)) {
                return propHandlerByteCode(tk, ctx, Array.class);
            } else {
                if(first) {
                    debug("ALOAD 1");
                    mv.visitVarInsn(ALOAD, 1);
                }

                debug("CHECKCAST " + getDescriptor(ctx.getClass()));
                mv.visitTypeInsn(CHECKCAST, getDescriptor(ctx.getClass()));

                writeLiteralOrSubexpression(compiled, int.class, item.getClass());

                Class cls = getBaseComponentType(ctx.getClass());
                if(cls.isPrimitive()) {
                    if(cls == int.class) {
                        debug("IALOAD");
                        mv.visitInsn(IALOAD);
                    } else if(cls == char.class) {
                        debug("CALOAD");
                        mv.visitInsn(CALOAD);
                    } else if(cls == boolean.class) {
                        debug("BALOAD");
                        mv.visitInsn(BALOAD);
                    } else if(cls == double.class) {
                        debug("DALOAD");
                        mv.visitInsn(DALOAD);
                    } else if(cls == float.class) {
                        debug("FALOAD");
                        mv.visitInsn(FALOAD);
                    } else if(cls == short.class) {
                        debug("SALOAD");
                        mv.visitInsn(SALOAD);
                    } else if(cls == long.class) {
                        debug("LALOAD");
                        mv.visitInsn(LALOAD);
                    } else if(cls == byte.class) {
                        debug("BALOAD");
                        mv.visitInsn(BALOAD);
                    }

                    wrapPrimitive(cls);
                } else {
                    debug("AALOAD");
                    mv.visitInsn(AALOAD);
                }

                return Array.get(ctx, convert(item, Integer.class));
            }
        } else if(ctx instanceof CharSequence) {
            if(hasPropertyHandler(CharSequence.class)) {
                return propHandlerByteCode(tk, ctx, CharSequence.class);
            } else {
                if(first) {
                    debug("ALOAD 1");
                    mv.visitVarInsn(ALOAD, 1);
                }

                debug("CHECKCAST java/lang/CharSequence");
                mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");

                if(item instanceof Integer) {
                    pushInt((Integer) item);

                    debug("INVOKEINTERFACE java/lang/CharSequence.charAt");
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "charAt", "(I)C", true);

                    wrapPrimitive(char.class);

                    return ((CharSequence) ctx).charAt((Integer) item);
                } else {
                    writeLiteralOrSubexpression(compiled, Integer.class);
                    unwrapPrimitive(int.class);

                    debug("INVOKEINTERFACE java/lang/CharSequence.charAt");
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "charAt", "(I)C", true);

                    wrapPrimitive(char.class);

                    return ((CharSequence) ctx).charAt(convert(item, Integer.class));
                }
            }
        } else {
            TypeDescriptor tDescr = new TypeDescriptor(expr, start, end - start, 0);
            if(tDescr.isArray()) {
                try{
                    Class cls = getClassReference((Class) ctx, tDescr, variableFactory, pCtx);
                    //   rootNode = new StaticReferenceAccessor(cls);
                    pushClass(cls);
                    return cls;
                } catch(Exception e) {
                    //fall through
                }
            }

            throw new CompileException("illegal use of []: unknown type: " + ctx.getClass().getName(), expr, st);
        }
    }


    @SuppressWarnings({"unchecked"})
    private Object getMethod(Object ctx, String name)
            throws IllegalAccessException, InvocationTargetException {
        debug("\n  **  {method: " + name + "}");

        int st = cursor;
        String tk = cursor != end && expr[cursor] == '(' && ((cursor = balancedCapture(expr, cursor, '(')) - st) > 1 ?
                new String(expr, st + 1, cursor - st - 1) : "";
        cursor++;

        Object[] preConvArgs;
        Object[] args;
        Class[] argTypes;
        ExecutableStatement[] es;
        List<char[]> subtokens;

        if(tk.length() == 0) {
            args = preConvArgs = ParseTools.EMPTY_OBJ_ARR;
            argTypes = ParseTools.EMPTY_CLS_ARR;
            es = null;
            subtokens = null;
        } else {
            subtokens = parseParameterList(tk.toCharArray(), 0, -1);

            es = new ExecutableStatement[subtokens.size()];
            args = new Object[subtokens.size()];
            argTypes = new Class[subtokens.size()];
            preConvArgs = new Object[es.length];

            for(int i = 0; i < subtokens.size(); i++) {
                debug("subtoken[" + i + "] { " + new String(subtokens.get(i)) + " }");
                preConvArgs[i] = args[i] = (es[i] = (ExecutableStatement) subCompileExpression(subtokens.get(i), pCtx))
                        .getValue(this.thisRef, this.thisRef, variableFactory);

                if(es[i].isExplicitCast()) {
                    argTypes[i] = es[i].getKnownEgressType();
                }
            }

            if(pCtx.isStrictTypeEnforcement()) {
                for(int i = 0; i < args.length; i++) {
                    argTypes[i] = es[i].getKnownEgressType();
                }
            } else {
                for(int i = 0; i < args.length; i++) {
                    if(argTypes[i] != null) {
                        continue;
                    }

                    if(es[i].getKnownEgressType() == Object.class) {
                        argTypes[i] = args[i] == null ? null : args[i].getClass();
                    } else {
                        argTypes[i] = es[i].getKnownEgressType();
                    }
                }
            }
        }

        if(first && variableFactory != null && variableFactory.isResolveable(name)) {
            Object ptr = variableFactory.getVariableResolver(name).getValue();

            if(ptr instanceof Method) {
                ctx = ((Method) ptr).getDeclaringClass();
                name = ((Method) ptr).getName();
            } else if(ptr instanceof MethodStub) {
                ctx = ((MethodStub) ptr).getClassReference();
                name = ((MethodStub) ptr).getMethodName();
            } else if(ptr instanceof FunctionInstance) {

                if(es != null && es.length != 0) {
                    compiledInputs.addAll(Arrays.asList(es));

                    pushInt(es.length);

                    debug("ANEWARRAY [" + es.length + "]");
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

                    debug("ASTORE 4");
                    mv.visitVarInsn(ASTORE, 4);

                    for(int i = 0; i < es.length; i++) {
                        debug("ALOAD 4");
                        mv.visitVarInsn(ALOAD, 4);
                        pushInt(i);
                        loadField(i);

                        debug("ALOAD 1");
                        mv.visitVarInsn(ALOAD, 1);

                        debug("ALOAD 3");
                        mv.visitIntInsn(ALOAD, 3);

                        debug("INVOKEINTERFACE ExecutableStatement.getValue");
                        mv.visitMethodInsn(INVOKEINTERFACE, NAMESPACE + "compiler/ExecutableStatement", "getValue",
                                "(Ljava/lang/Object;L" + NAMESPACE + "integration/VariableResolverFactory;)Ljava/lang/Object;", true);

                        debug("AASTORE");
                        mv.visitInsn(AASTORE);
                    }
                } else {
                    debug("ACONST_NULL");
                    mv.visitInsn(ACONST_NULL);

                    debug("CHECKCAST java/lang/Object");
                    mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");

                    debug("ASTORE 4");
                    mv.visitVarInsn(ASTORE, 4);
                }

                if(variableFactory.isIndexedFactory() && variableFactory.isTarget(name)) {
                    loadVariableByIndex(variableFactory.variableIndexOf(name));
                } else {
                    loadVariableByName(name);
                }

                checkCast(FunctionInstance.class);

                debug("ALOAD 1");
                mv.visitVarInsn(ALOAD, 1);

                debug("ALOAD 2");
                mv.visitVarInsn(ALOAD, 2);

                debug("ALOAD 3");
                mv.visitVarInsn(ALOAD, 3);

                debug("ALOAD 4");
                mv.visitVarInsn(ALOAD, 4);

                debug("INVOKEVIRTUAL Function.call");
                mv.visitMethodInsn(INVOKEVIRTUAL,
                        getInternalName(FunctionInstance.class),
                        "call",
                        "(Ljava/lang/Object;Ljava/lang/Object;L" + NAMESPACE
                                + "integration/VariableResolverFactory;[Ljava/lang/Object;)Ljava/lang/Object;", false);

                return ((FunctionInstance) ptr).call(ctx, thisRef, variableFactory, args);
            } else {
                throw new OptimizationFailure("attempt to optimize a method call for a reference that does not point to a method: "
                        + name + " (reference is type: " + (ctx != null ? ctx.getClass().getName() : null) + ")");
            }

            first = false;
        } else if(returnType != null && returnType.isPrimitive()) {
            //noinspection unchecked
            wrapPrimitive(returnType);
        }

        /*
         * If the target object is an instance of java.lang.Class itself then do not
         * adjust the Class scope target.
         */

        boolean classTarget = false;
        Class<?> cls = currType != null ? currType : ((classTarget = ctx instanceof Class) ? (Class<?>) ctx : ctx.getClass());

        currType = null;

        Method m;
        Class[] parameterTypes = null;

        /*
         * Try to find an instance method from the class target.
         */
        if((m = getBestCandidate(argTypes, name, cls, cls.getMethods(), false, classTarget)) != null) {
            parameterTypes = m.getParameterTypes();
        }

        if(m == null && classTarget) {
            /*
             * If we didn't find anything, maybe we're looking for the actual java.lang.Class methods.
             */
            if((m = getBestCandidate(argTypes, name, cls, Class.class.getMethods(), false)) != null) {
                parameterTypes = m.getParameterTypes();
            }
        }

        // If we didn't find anything and the declared class is different from the actual one try also with the actual one
        if(m == null && cls != ctx.getClass() && !(ctx instanceof Class)) {
            cls = ctx.getClass();
            if((m = getBestCandidate(argTypes, name, cls, cls.getMethods(), false, false)) != null) {
                parameterTypes = m.getParameterTypes();
            }
        }

        if(es != null && m != null && m.isVarArgs() && (es.length != parameterTypes.length || !(es[es.length - 1] instanceof ExecutableAccessor))) {
            // normalize ExecutableStatement for varargs
            ExecutableStatement[] varArgEs = new ExecutableStatement[parameterTypes.length];
            int varArgStart = parameterTypes.length - 1;
            System.arraycopy(es, 0, varArgEs, 0, varArgStart);

            String varargsTypeName = parameterTypes[parameterTypes.length - 1].getComponentType().getName();
            String varArgExpr;
            if("null".equals(tk)) { //if null is the token no need for wrapping
                varArgExpr = tk;
            } else {
                StringBuilder sb = new StringBuilder("new ").append(varargsTypeName).append("[] {");
                for(int i = varArgStart; i < subtokens.size(); i++) {
                    sb.append(subtokens.get(i));
                    if(i < subtokens.size() - 1) {
                        sb.append(",");
                    }
                }
                varArgExpr = sb.append("}").toString();
            }
            char[] token = varArgExpr.toCharArray();
            varArgEs[varArgStart] = ((ExecutableStatement) subCompileExpression(token, pCtx));
            es = varArgEs;

            if(preConvArgs.length == parameterTypes.length - 1) {
                // empty vararg
                Object[] preConvArgsForVarArg = new Object[parameterTypes.length];
                System.arraycopy(preConvArgs, 0, preConvArgsForVarArg, 0, preConvArgs.length);
                preConvArgsForVarArg[parameterTypes.length - 1] = Array.newInstance(parameterTypes[parameterTypes.length - 1].getComponentType(), 0);
                preConvArgs = preConvArgsForVarArg;
            }
        }

        int inputsOffset = compiledInputs.size();

        if(es != null) {
            for(ExecutableStatement e : es) {
                if(e instanceof ExecutableLiteral) {
                    continue;
                }

                compiledInputs.add(e);
            }
        }

        if(first) {
            debug("ALOAD 1 (D) ");
            mv.visitVarInsn(ALOAD, 1);
        }

        if(m == null) {
            StringAppender errorBuild = new StringAppender();

            if(parameterTypes != null) {
                for(int i = 0; i < args.length; i++) {
                    errorBuild.append(parameterTypes[i] != null ? parameterTypes[i].getClass().getName() : null);
                    if(i < args.length - 1) {
                        errorBuild.append(", ");
                    }
                }
            }

            if("size".equals(name) && args.length == 0 && cls.isArray()) {
                arrayCheckCast(cls);

                debug("ARRAYLENGTH");
                mv.visitInsn(ARRAYLENGTH);

                wrapPrimitive(int.class);
                return getLength(ctx);
            }

            throw new CompileException("unable to resolve method: " + cls.getName() + "."
                    + name + "(" + errorBuild.toString() + ") [arglength=" + args.length + "]", expr, st);
        } else {
            m = getWidenedTarget(m);

            if(es != null) {
                ExecutableStatement cExpr;
                for(int i = 0; i < es.length; i++) {
                    if((cExpr = es[i]).getKnownIngressType() == null) {
                        cExpr.setKnownIngressType(parameterTypes[i]);
                        cExpr.computeTypeConversionRule();
                    }
                    if(!cExpr.isConvertableIngressEgress() && i < args.length) {
                        args[i] = convert(args[i], paramTypeVarArgsSafe(parameterTypes, i, m.isVarArgs()));
                    }
                }
            } else {
                /*
                 * Coerce any types if required.
                 */
                for(int i = 0; i < args.length; i++) {
                    args[i] = convert(args[i], paramTypeVarArgsSafe(parameterTypes, i, m.isVarArgs()));
                }
            }

            if(m.getParameterTypes().length == 0) {
                if((m.getModifiers() & STATIC) != 0) {
                    debug("INVOKESTATIC " + m.getName());
                    mv.visitMethodInsn(INVOKESTATIC, getInternalName(m.getDeclaringClass()), m.getName(), getMethodDescriptor(m), false);
                } else {
                    debug("CHECKCAST " + getInternalName(m.getDeclaringClass()));
                    mv.visitTypeInsn(CHECKCAST, getInternalName(m.getDeclaringClass()));

                    if(m.getDeclaringClass().isInterface()) {
                        debug("INVOKEINTERFACE " + m.getName());
                        mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(m.getDeclaringClass()), m.getName(),
                                getMethodDescriptor(m), true);

                    } else {
                        debug("INVOKEVIRTUAL " + m.getName());
                        mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(m.getDeclaringClass()), m.getName(),
                                getMethodDescriptor(m), false);
                    }
                }

                returnType = m.getReturnType();

                stacksize++;
            } else {
                if((m.getModifiers() & STATIC) == 0) {
                    debug("CHECKCAST " + getInternalName(cls));
                    mv.visitTypeInsn(CHECKCAST, getInternalName(cls));
                }

                Class<?> aClass = m.getParameterTypes()[m.getParameterTypes().length - 1];
                if(m.isVarArgs()) {
                    if(es == null || es.length == (m.getParameterTypes().length - 1)) {
                        ExecutableStatement[] executableStatements = new ExecutableStatement[m.getParameterTypes().length];
                        if(es != null) {
                            System.arraycopy(es, 0, executableStatements, 0, es.length);
                        }
                        executableStatements[executableStatements.length - 1] = new ExecutableLiteral(Array.newInstance(aClass, 0));
                        es = executableStatements;
                    }
                }

                for(int i = 0; es != null && i < es.length; i++) {
                    if(es[i] instanceof ExecutableLiteral) {
                        ExecutableLiteral literal = (ExecutableLiteral) es[i];

                        if(literal.getLiteral() == null) {
                            debug("ICONST_NULL");
                            mv.visitInsn(ACONST_NULL);
                            continue;
                        } else if(parameterTypes[i] == int.class && literal.intOptimized()) {
                            pushInt(literal.getInteger32());
                            continue;
                        } else if(parameterTypes[i] == int.class && preConvArgs[i] instanceof Integer) {
                            pushInt((Integer) preConvArgs[i]);
                            continue;
                        } else if(parameterTypes[i] == boolean.class) {
                            boolean bool = DataConversion.convert(literal.getLiteral(), Boolean.class);
                            debug(bool ? "ICONST_1" : "ICONST_0");
                            mv.visitInsn(bool ? ICONST_1 : ICONST_0);
                            continue;
                        } else {
                            Object lit = literal.getLiteral();

                            if(parameterTypes[i] == Object.class) {
                                if(isPrimitiveWrapper(lit.getClass())) {
                                    if(lit.getClass() == Integer.class) {
                                        pushInt((Integer) lit);
                                    } else {
                                        debug("LDC " + lit);
                                        mv.visitLdcInsn(lit);
                                    }

                                    wrapPrimitive(lit.getClass());
                                } else if(lit instanceof String) {
                                    mv.visitLdcInsn(lit);
                                    checkCast(Object.class);
                                }
                                continue;
                            } else if(canConvert(parameterTypes[i], lit.getClass())) {
                                Object c = convert(lit, parameterTypes[i]);
                                if(c instanceof Class) {
                                    pushClass((Class) c);
                                } else {
                                    debug("LDC " + lit + " (" + lit.getClass().getName() + ")");

                                    mv.visitLdcInsn(convert(lit, parameterTypes[i]));

                                    if(isPrimitiveWrapper(parameterTypes[i])) {
                                        wrapPrimitive(lit.getClass());
                                    }
                                }
                                continue;
                            } else {
                                throw new OptimizationNotSupported();
                            }
                        }
                    }

                    debug("ALOAD 0");
                    mv.visitVarInsn(ALOAD, 0);

                    debug("GETFIELD p" + inputsOffset);
                    mv.visitFieldInsn(GETFIELD, className, "p" + inputsOffset, "L" + NAMESPACE + "compiler/ExecutableStatement;");

                    inputsOffset++;

                    debug("ALOAD 2");
                    mv.visitVarInsn(ALOAD, 2);

                    debug("ALOAD 3");
                    mv.visitVarInsn(ALOAD, 3);

                    debug("INVOKEINTERFACE ExecutableStatement.getValue");
                    mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ExecutableStatement.class), "getValue",
                            "(Ljava/lang/Object;L" + NAMESPACE + "integration/VariableResolverFactory;)Ljava/lang/Object;", true);

                    if(parameterTypes[i].isPrimitive()) {
                        if(preConvArgs[i] == null ||
                                (parameterTypes[i] != String.class &&
                                        !parameterTypes[i].isAssignableFrom(preConvArgs[i].getClass()))) {

                            pushClass(toWrapperClass(parameterTypes[i]));

                            debug("INVOKESTATIC DataConversion.convert");
                            mv.visitMethodInsn(INVOKESTATIC, NAMESPACE + "DataConversion", "convert",
                                    "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false);
                        }

                        unwrapPrimitive(parameterTypes[i]);
                    } else if(preConvArgs[i] == null ||
                            (parameterTypes[i] != String.class &&
                                    !parameterTypes[i].isAssignableFrom(preConvArgs[i].getClass()))) {

                        pushClass(parameterTypes[i]);

                        debug("INVOKESTATIC DataConversion.convert");
                        mv.visitMethodInsn(INVOKESTATIC, NAMESPACE + "DataConversion", "convert",
                                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false);

                        debug("CHECKCAST " + getInternalName(parameterTypes[i]));
                        mv.visitTypeInsn(CHECKCAST, getInternalName(parameterTypes[i]));
                    } else if(parameterTypes[i] == String.class) {
                        debug("<<<DYNAMIC TYPE OPTIMIZATION STRING>>");
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf",
                                "(Ljava/lang/Object;)Ljava/lang/String;", false);
                    } else {
                        debug("<<<DYNAMIC TYPING BYPASS>>>");
                        debug("<<<OPT. JUSTIFICATION " + parameterTypes[i] + "=" + preConvArgs[i].getClass() + ">>>");

                        debug("CHECKCAST " + getInternalName(parameterTypes[i]));
                        mv.visitTypeInsn(CHECKCAST, getInternalName(parameterTypes[i]));
                    }
                }

                if((m.getModifiers() & STATIC) != 0) {
                    debug("INVOKESTATIC: " + m.getName());
                    mv.visitMethodInsn(INVOKESTATIC, getInternalName(m.getDeclaringClass()), m.getName(), getMethodDescriptor(m), false);
                } else {
                    if(m.getDeclaringClass().isInterface() && (m.getDeclaringClass() != cls
                            || (ctx != null && ctx.getClass() != m.getDeclaringClass()))) {
                        debug("INVOKEINTERFACE: " + getInternalName(m.getDeclaringClass()) + "." + m.getName());
                        mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(m.getDeclaringClass()), m.getName(),
                                getMethodDescriptor(m), true);
                    } else {
                        debug("INVOKEVIRTUAL: " + getInternalName(cls) + "." + m.getName());
                        mv.visitMethodInsn(INVOKEVIRTUAL, getInternalName(cls), m.getName(),
                                getMethodDescriptor(m), false);
                    }
                }

                returnType = m.getReturnType();

                stacksize++;
            }

            Object o = m.invoke(ctx, normalizeArgsForVarArgs(parameterTypes, args, m.isVarArgs()));


            if(hasNullMethodHandler()) {
                writeOutNullHandler(m, 1);
                if(o == null) {
                    o = getNullMethodHandler().getProperty(m.getName(), ctx, variableFactory);
                }
            }

            currType = toNonPrimitiveType(m.getReturnType());
            return o;

        }
    }

    private void dataConversion(Class target) {
        if(target.equals(Object.class)) {
            return;
        }

        pushClass(target);
        debug("INVOKESTATIC " + NAMESPACE + "DataConversion.convert");
        mv.visitMethodInsn(INVOKESTATIC, NAMESPACE + "DataConversion", "convert",
                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false);
    }


    public Object getResultOptPass() {
        return resultValue;
    }


    private Object addSubStatement(ExecutableStatement stmt) {
        if(stmt instanceof ExecutableAccessor) {
            ExecutableAccessor ea = (ExecutableAccessor) stmt;
            if(ea.getNode().isIdentifier() && !ea.getNode().isDeepProperty()) {
                loadVariableByName(ea.getNode().getName());
                return null;
            }
        }

        compiledInputs.add(stmt);

        debug("ALOAD 0");
        mv.visitVarInsn(ALOAD, 0);

        debug("GETFIELD p" + (compiledInputs.size() - 1));
        mv.visitFieldInsn(GETFIELD, className, "p" + (compiledInputs.size() - 1),
                "L" + NAMESPACE + "compiler/ExecutableStatement;");

        debug("ALOAD 2");
        mv.visitVarInsn(ALOAD, 2);

        debug("ALOAD 3");
        mv.visitVarInsn(ALOAD, 3);

        debug("INVOKEINTERFACE ExecutableStatement.getValue");
        mv.visitMethodInsn(INVOKEINTERFACE, getInternalName(ExecutableStatement.class), "getValue",
                "(Ljava/lang/Object;L" + NAMESPACE + "integration/VariableResolverFactory;)Ljava/lang/Object;", true);

        return null;
    }


    private void loadVariableByName(String name) {
        debug("ALOAD 3");
        mv.visitVarInsn(ALOAD, 3);

        debug("LDC \"" + name + "\"");
        mv.visitLdcInsn(name);

        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolverFactory.getVariableResolver");
        mv.visitMethodInsn(INVOKEINTERFACE, "" + NAMESPACE + "integration/VariableResolverFactory",
                "getVariableResolver", "(Ljava/lang/String;)L" + NAMESPACE + "integration/VariableResolver;", true);

        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolver.getValue");
        mv.visitMethodInsn(INVOKEINTERFACE, "" + NAMESPACE + "integration/VariableResolver",
                "getValue", "()Ljava/lang/Object;", true);

        returnType = Object.class;
    }

    private void loadVariableByIndex(int pos) {
        debug("ALOAD 3");
        mv.visitVarInsn(ALOAD, 3);

        debug("PUSH IDX VAL =" + pos);
        pushInt(pos);

        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolverFactory.getIndexedVariableResolver");
        mv.visitMethodInsn(INVOKEINTERFACE, "" + NAMESPACE + "integration/VariableResolverFactory",
                "getIndexedVariableResolver", "(I)L" + NAMESPACE + "integration/VariableResolver;", true);

        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolver.getValue");
        mv.visitMethodInsn(INVOKEINTERFACE, "" + NAMESPACE + "integration/VariableResolver",
                "getValue", "()Ljava/lang/Object;", true);

        returnType = Object.class;
    }

    private void loadField(int number) {
        debug("ALOAD 0");
        mv.loadThis();

        debug("GETFIELD p" + number);
        mv.visitFieldInsn(GETFIELD, className, "p" + number, "L" + NAMESPACE + "compiler/ExecutableStatement;");
    }

    /** 构建有参数的构造函数 */
    private void buildInputs() {
        if(compiledInputs.size() == 0) {
            return;
        }

        debug("\n{SETTING UP MEMBERS...}\n");

        StringAppender constSig = new StringAppender("(");
        int size = compiledInputs.size();

        for(int i = 0; i < size; i++) {
            debug("ACC_PRIVATE p" + i);
            cw.visitField(ACC_PRIVATE, "p" + i, "L" + NAMESPACE + "compiler/ExecutableStatement;", null, null).visitEnd();

            constSig.append("L" + NAMESPACE + "compiler/ExecutableStatement;");
        }
        constSig.append(")V");

        debug("\n{CREATING INJECTION CONSTRUCTOR}\n");

        MethodVisitor cv = cw.visitMethod(ACC_PUBLIC, "<init>", constSig.toString(), null, null);
        cv.visitCode();
        debug("ALOAD 0");
        cv.visitVarInsn(ALOAD, 0);
        debug("INVOKESPECIAL java/lang/Object.<init>");
        cv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        for(int i = 0; i < size; i++) {
            debug("ALOAD 0");
            cv.visitVarInsn(ALOAD, 0);
            debug("ALOAD " + (i + 1));
            cv.visitVarInsn(ALOAD, i + 1);
            debug("PUTFIELD p" + i);
            cv.visitFieldInsn(PUTFIELD, className, "p" + i, "L" + NAMESPACE + "compiler/ExecutableStatement;");
        }

        debug("RETURN");
        cv.visitInsn(RETURN);
        cv.visitMaxs(0, 0);
        cv.visitEnd();

        debug("}");
    }

    private static final int ARRAY = 0;
    private static final int LIST = 1;
    private static final int MAP = 2;
    private static final int VAL = 3;

    private int _getAccessor(Object o, Class type) {
        if(o instanceof List) {
            debug("NEW " + LIST_IMPL);
            mv.visitTypeInsn(NEW, LIST_IMPL);

            debug("DUP");
            mv.visitInsn(DUP);

            debug("DUP");
            mv.visitInsn(DUP);

            pushInt(((List) o).size());
            debug("INVOKESPECIAL " + LIST_IMPL + ".<init>");
            mv.visitMethodInsn(INVOKESPECIAL, LIST_IMPL, "<init>", "(I)V", false);

            for(Object item : (List) o) {
                if(_getAccessor(item, type) != VAL) {
                    debug("POP");
                    mv.visitInsn(POP);
                }

                debug("INVOKEINTERFACE java/util/List.add");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);

                debug("POP");
                mv.visitInsn(POP);

                debug("DUP");
                mv.visitInsn(DUP);
            }

            returnType = List.class;

            return LIST;
        } else if(o instanceof Map) {
            debug("NEW " + MAP_IMPL);
            mv.visitTypeInsn(NEW, MAP_IMPL);

            debug("DUP");
            mv.visitInsn(DUP);

            debug("DUP");
            mv.visitInsn(DUP);

            pushInt(((Map) o).size());

            debug("INVOKESPECIAL " + MAP_IMPL + ".<init>");
            mv.visitMethodInsn(INVOKESPECIAL, MAP_IMPL, "<init>", "(I)V", false);

            for(Object item : ((Map) o).keySet()) {
                mv.visitTypeInsn(CHECKCAST, "java/util/Map");

                if(_getAccessor(item, type) != VAL) {
                    debug("POP");
                    mv.visitInsn(POP);
                }
                if(_getAccessor(((Map) o).get(item), type) != VAL) {
                    debug("POP");
                    mv.visitInsn(POP);
                }

                debug("INVOKEINTERFACE java/util/Map.put");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);

                debug("POP");
                mv.visitInsn(POP);

                debug("DUP");
                mv.visitInsn(DUP);
            }

            returnType = Map.class;

            return MAP;
        } else if(o instanceof Object[]) {
            Accessor[] a = new Accessor[((Object[]) o).length];
            int i = 0;
            int dim = 0;

            if(type != null) {
                String nm = type.getName();
                while(nm.charAt(dim) == '[') dim++;
            } else {
                type = Object[].class;
                dim = 1;
            }

            try{
                pushInt(((Object[]) o).length);
                debug("ANEWARRAY " + getInternalName(getSubComponentType(type)) + " (" + ((Object[]) o).length + ")");
                mv.visitTypeInsn(ANEWARRAY, getInternalName(getSubComponentType(type)));

                Class cls = dim > 1 ? findClass(null, repeatChar('[', dim - 1)
                        + "L" + getBaseComponentType(type).getName() + ";", pCtx)
                        : type;


                debug("DUP");
                mv.visitInsn(DUP);

                for(Object item : (Object[]) o) {
                    pushInt(i);

                    if(_getAccessor(item, cls) != VAL) {
                        debug("POP");
                        mv.visitInsn(POP);
                    }

                    debug("AASTORE (" + o.hashCode() + ")");
                    mv.visitInsn(AASTORE);

                    debug("DUP");
                    mv.visitInsn(DUP);

                    i++;
                }

            } catch(ClassNotFoundException e) {
                throw new RuntimeException("this error should never throw:" + getBaseComponentType(type).getName(), e);
            }

            return ARRAY;
        } else {
            if(type.isArray()) {
                writeLiteralOrSubexpression(subCompileExpression(((String) o).toCharArray(), pCtx), getSubComponentType(type));
            } else {
                writeLiteralOrSubexpression(subCompileExpression(((String) o).toCharArray(), pCtx));
            }
            return VAL;
        }
    }

    private Class writeLiteralOrSubexpression(Object stmt) {
        return writeLiteralOrSubexpression(stmt, null, null);
    }

    private Class writeLiteralOrSubexpression(Object stmt, Class desiredTarget) {
        return writeLiteralOrSubexpression(stmt, desiredTarget, null);
    }

    private Class writeLiteralOrSubexpression(Object stmt, Class desiredTarget, Class knownIngressType) {
        if(stmt instanceof ExecutableLiteral) {
            Object literalValue = ((ExecutableLiteral) stmt).getLiteral();

            // Handle the case when the literal is null MVEL-312
            if(literalValue == null) {
                mv.visitInsn(ACONST_NULL);
                return null;
            }

            Class type = literalValue.getClass();

            debug("*** type:" + type + ";desired:" + desiredTarget);

            if(type == Integer.class && desiredTarget == int.class) {
                pushInt(((ExecutableLiteral) stmt).getInteger32());
                type = int.class;
            } else if(desiredTarget != null && desiredTarget != type) {
                debug("*** Converting because desiredType(" + desiredTarget.getClass() + ") is not: " + type);


                if(!DataConversion.canConvert(type, desiredTarget)) {
                    throw new CompileException("was expecting type: " + desiredTarget.getName()
                            + "; but found type: " + type.getName(), expr, st);
                }
                pushLiteralWrapped(convert(literalValue, desiredTarget));
            } else {
                pushLiteralWrapped(literalValue);
            }

            return type;
        } else {
            literal = false;

            addSubStatement((ExecutableStatement) stmt);

            Class type;
            if(knownIngressType == null) {
                type = ((ExecutableStatement) stmt).getKnownEgressType();
            } else {
                type = knownIngressType;
            }

            if(desiredTarget != null && type != desiredTarget) {
                if(desiredTarget.isPrimitive()) {
                    if(type == null) {
                        throw new OptimizationFailure("cannot optimize expression: " + new String(expr) +
                                ": cannot determine ingress type for primitive output");
                    }

                    checkCast(type);
                    unwrapPrimitive(desiredTarget);
                }
            }

            return type;
        }
    }

    public AccessorNode optimizeCollection(ParserContext pCtx, Object o, Class type, char[] property, int start, int offset,
                                           Object ctx, Object thisRef, VariableResolverFactory factory) {
        this.expr = property;
        this.cursor = this.start = start;
        this.end = start + offset;
        this.length = offset;

        type = toNonPrimitiveArray(type);
        this.returnType = type;

        this.compiledInputs = new ArrayList<>();

        this.ctx = ctx;
        this.thisRef = thisRef;
        this.variableFactory = factory;

        _initJit4GetValue();

        literal = true;

        _getAccessor(o, type);

        _finishJIT();

        try{
            AccessorNode compiledAccessor = _initializeAccessor();

            if(property != null && length > start) {
                assert compiledAccessor != null;
                return new Union(pCtx, compiledAccessor, property, start, length);
            } else {
                return compiledAccessor;
            }

        } catch(Exception e) {
            throw new OptimizationFailure("could not optimize collection", e);
        }
    }

    public AccessorNode optimizeObjectCreation(ParserContext pCtx, char[] property, int start, int offset, Object ctx,
                                               Object thisRef, VariableResolverFactory factory) {
        _initJit4GetValue();

        compiledInputs = new ArrayList<>();
        this.start = cursor = start;
        this.end = start + offset;
        this.length = this.end - this.start;
        this.ctx = ctx;
        this.thisRef = thisRef;
        this.variableFactory = factory;
        this.pCtx = pCtx;

        String[] cnsRes = captureConstructorAndResidual(property, start, offset);
        List<char[]> constructorParms = parseMethodOrConstructor(cnsRes[0].toCharArray());

        try{
            if(constructorParms != null) {
                for(char[] constructorParm : constructorParms) {
                    compiledInputs.add((ExecutableStatement) subCompileExpression(constructorParm, pCtx));
                }

                Class cls = findClass(factory, new String(subset(property, 0, findFirst('(', start, length, property))), pCtx);

                debug("NEW " + getInternalName(cls));
                mv.newInstance(Type.getType(cls));
                debug("DUP");
                mv.dup();

                Object[] parms = new Object[constructorParms.size()];

                int i = 0;
                for(ExecutableStatement es : compiledInputs) {
                    parms[i++] = es.getValue(ctx, factory);
                }

                Constructor cns = getBestConstructorCandidate(parms, cls, pCtx.isStrongTyping());

                if(cns == null) {
                    StringBuilder error = new StringBuilder();
                    for(int x = 0; x < parms.length; x++) {
                        error.append(parms[x].getClass().getName());
                        if(x + 1 < parms.length) {
                            error.append(", ");
                        }
                    }

                    throw new CompileException("unable to find constructor: " + cls.getName()
                            + "(" + error.toString() + ")", expr, st);
                }

                this.returnType = cns.getDeclaringClass();

                Class tg;
                for(i = 0; i < constructorParms.size(); i++) {
                    debug("ALOAD 0");
                    mv.visitVarInsn(ALOAD, 0);
                    debug("GETFIELD p" + i);
                    mv.visitFieldInsn(GETFIELD, className, "p" + i, "L" + NAMESPACE + "compiler/ExecutableStatement;");
                    debug("ALOAD 2");
                    mv.visitVarInsn(ALOAD, 2);
                    debug("ALOAD 3");
                    mv.visitVarInsn(ALOAD, 3);
                    debug("INVOKEINTERFACE " + NAMESPACE + "compiler/ExecutableStatement.getValue");
                    mv.visitMethodInsn(INVOKEINTERFACE, "" + NAMESPACE
                            + "compiler/ExecutableStatement", "getValue", "(Ljava/lang/Object;L" + NAMESPACE
                            + "integration/VariableResolverFactory;)Ljava/lang/Object;", true);

                    tg = cns.getParameterTypes()[i].isPrimitive()
                            ? toWrapperClass(cns.getParameterTypes()[i]) : cns.getParameterTypes()[i];

                    if(parms[i] != null && !parms[i].getClass().isAssignableFrom(cns.getParameterTypes()[i])) {
                        pushClass(tg);

                        debug("INVOKESTATIC " + NAMESPACE + "DataConversion.convert");
                        mv.visitMethodInsn(INVOKESTATIC, "" + NAMESPACE + "DataConversion", "convert",
                                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false);

                        if(cns.getParameterTypes()[i].isPrimitive()) {
                            unwrapPrimitive(cns.getParameterTypes()[i]);
                        } else {
                            debug("CHECKCAST " + getInternalName(tg));
                            mv.visitTypeInsn(CHECKCAST, getInternalName(tg));
                        }

                    } else {
                        debug("CHECKCAST " + getInternalName(cns.getParameterTypes()[i]));
                        mv.visitTypeInsn(CHECKCAST, getInternalName(cns.getParameterTypes()[i]));
                    }

                }

                debug("INVOKESPECIAL " + getInternalName(cls) + ".<init> : " + getConstructorDescriptor(cns));
                mv.visitMethodInsn(INVOKESPECIAL, getInternalName(cls), "<init>", getConstructorDescriptor(cns), false);

                _finishJIT();

                AccessorNode acc = _initializeAccessor();

                if(cnsRes.length > 1 && cnsRes[1] != null && !cnsRes[1].trim().equals("")) {
                    assert acc != null;
                    return new Union(pCtx, acc, cnsRes[1].toCharArray(), 0, cnsRes[1].length());
                }

                return acc;
            } else {
                Class cls = findClass(factory, new String(property), pCtx);

                //构造无参实例
                generateNewInstance(mv, cls);

                _finishJIT();
                AccessorNode acc = _initializeAccessor();

                if(cnsRes.length > 1 && cnsRes[1] != null && !cnsRes[1].trim().equals("")) {
                    assert acc != null;
                    return new Union(pCtx, acc, cnsRes[1].toCharArray(), 0, cnsRes[1].length());
                }

                return acc;
            }
        } catch(ClassNotFoundException e) {
            throw new CompileException("class or class reference not found: "
                    + new String(property), property, st);
        } catch(Exception e) {
            throw new OptimizationFailure("could not optimize constructor: " + new String(property), e);
        }
    }

    public Class getEgressType() {
        return returnType;
    }

    private void dumpAdvancedDebugging() {
        if(buildLog == null) {
            return;
        }

        System.out.println("JIT Compiler Dump for: <<" + (expr == null ? null : new String(expr))
                + ">>\n-------------------------------\n");
        System.out.println(buildLog.toString());
        System.out.println("\n<END OF DUMP>\n");
    }

    private Object propHandlerByteCode(String property, Object ctx, Class handler) {
        PropertyHandler ph = getPropertyHandler(handler);
        if(ph instanceof ProducesBytecode) {
            debug("<<3rd-Party Code Generation>>");
            ((ProducesBytecode) ph).produceBytecodeGet(mv, property, variableFactory);
            return ph.getProperty(property, ctx, variableFactory);
        } else {
            throw new RuntimeException("unable to compileShared: custom accessor does not support producing bytecode: "
                    + ph.getClass().getName());
        }
    }

    private void propHandlerByteCodePut(String property, Object ctx, Class handler, Object value) {
        PropertyHandler ph = getPropertyHandler(handler);
        if(ph instanceof ProducesBytecode) {
            debug("<<3rd-Party Code Generation>>");
            ((ProducesBytecode) ph).produceBytecodePut(mv, property, variableFactory);
            ph.setProperty(property, ctx, variableFactory, value);
        } else {
            throw new RuntimeException("unable to compileShared: custom accessor does not support producing bytecode: "
                    + ph.getClass().getName());
        }
    }

    private void writeOutNullHandler(Member member, int type) {

        debug("DUP");
        mv.dup();

        Label endLabel = mv.newLabel();

        debug("IFNONNULL : jump");
        mv.ifNonNull(endLabel);

        debug("POP");
        mv.pop();

        debug("ALOAD 0");
        mv.loadThis();

        if(type == 0) {
            this.propNull = true;

            debug("GETFIELD 'nullPropertyHandler'");
            mv.visitFieldInsn(GETFIELD, className, "nullPropertyHandler", "L" + NAMESPACE + "integration/PropertyHandler;");
        } else {
            this.methNull = true;

            debug("GETFIELD 'nullMethodHandler'");
            mv.visitFieldInsn(GETFIELD, className, "nullMethodHandler", "L" + NAMESPACE + "integration/PropertyHandler;");
        }


        debug("LDC '" + member.getName() + "'");
        mv.push(member.getName());

        debug("ALOAD 1");
        mv.loadLocal(1);

        debug("ALOAD 3");
        mv.loadLocal(3);

        debug("INVOKEINTERFACE PropertyHandler.getProperty");
        mv.invokeInterface(Type.getType(PropertyHandler.class), org.mvel2.asm.commons.Method.getMethod("Object getProperty(String, Object, org.mvel2.integration.VariableResolverFactory)"));

        debug("LABEL:jump");
        mv.visitLabel(endLabel);
    }

    public boolean isLiteralOnly() {
        return literal;
    }

    //---------------------------- 辅助及工具方法 start ------------------------------//

    private void debug(String instruction) {
        if(buildLog != null) {
            buildLog.append(instruction).append(System.lineSeparator());
        }
    }

    private void debug(Supplier<String> call) {
        if(buildLog != null) {
            buildLog.append(call.get()).append(System.lineSeparator());
        }
    }

    /** 生成默认构造方法 */
    private static void generateDefaultConstructor(ClassWriter writer) {
        MethodVisitor m = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);

        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitInsn(RETURN);

        m.visitMaxs(1, 1);
        m.visitEnd();
    }

    /** 构建指定类型的实例对象 */
    private void generateNewInstance(GeneratorAdapter mvWriter, Class clazz) throws NoSuchMethodException {
        val type = Type.getType(clazz);
        val constructor = clazz.getConstructor(EMPTY_CLASSES);

        debug(() -> "NEW " + getInternalName(clazz));
        mvWriter.newInstance(type);

        debug("DUP");
        mvWriter.dup();

        debug("INVOKESPECIAL <init>");
        mvWriter.invokeConstructor(type, org.mvel2.asm.commons.Method.getMethod(constructor));
    }

    /** 基本类型转包装类型 */
    private Class toWrapperClass(Class cls) {
        if(cls == boolean.class) {
            return Boolean.class;
        } else if(cls == int.class) {
            return Integer.class;
        } else if(cls == float.class) {
            return Float.class;
        } else if(cls == double.class) {
            return Double.class;
        } else if(cls == short.class) {
            return Short.class;
        } else if(cls == long.class) {
            return Long.class;
        } else if(cls == byte.class) {
            return Byte.class;
        } else if(cls == char.class) {
            return Character.class;
        }

        return cls;
    }

    /** 将基本类型包装为包装类型,在当前调用栈中已经是基本类型了 */
    private void wrapPrimitive(Class<? extends Object> cls) {
        if(!cls.isPrimitive()) {
            return;
        }

        if(cls == boolean.class) {
            debug("INVOKESTATIC java/lang/Boolean.valueOf");
            mv.invokeStatic(Type.getType(Boolean.class), org.mvel2.asm.commons.Method.getMethod("Boolean valueOf(boolean)"));
        } else if(cls == int.class) {
            debug("INVOKESTATIC java/lang/Integer.valueOf");
            mv.invokeStatic(Type.getType(Integer.class), org.mvel2.asm.commons.Method.getMethod("Integer valueOf(int)"));
        } else if(cls == float.class) {
            debug("INVOKESTATIC java/lang/Float.valueOf");
            mv.invokeStatic(Type.getType(Float.class), org.mvel2.asm.commons.Method.getMethod("Float valueOf(float)"));
        } else if(cls == double.class) {
            debug("INVOKESTATIC java/lang/Double.valueOf");
            mv.invokeStatic(Type.getType(Double.class), org.mvel2.asm.commons.Method.getMethod("Double valueOf(double)"));
        } else if(cls == short.class) {
            debug("INVOKESTATIC java/lang/Short.valueOf");
            mv.invokeStatic(Type.getType(Short.class), org.mvel2.asm.commons.Method.getMethod("Short valueOf(short)"));
        } else if(cls == long.class) {
            debug("INVOKESTATIC java/lang/Long.valueOf");
            mv.invokeStatic(Type.getType(Long.class), org.mvel2.asm.commons.Method.getMethod("Long valueOf(long)"));
        } else if(cls == byte.class) {
            debug("INVOKESTATIC java/lang/Byte.valueOf");
            mv.invokeStatic(Type.getType(Byte.class), org.mvel2.asm.commons.Method.getMethod("Byte valueOf(byte)"));
        } else if(cls == char.class) {
            debug("INVOKESTATIC java/lang/Character.valueOf");
            mv.invokeStatic(Type.getType(Character.class), org.mvel2.asm.commons.Method.getMethod("Character valueOf(char)"));
        }
    }

    /** 将包装类型重新解包为基本类型 */
    private void unwrapPrimitive(Class cls) {
        if(cls == boolean.class) {
            val type = Type.getType(Boolean.class);

            debug("CHECKCAST java/lang/Boolean");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Boolean.booleanValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("boolean booleanValue()"));
        } else if(cls == int.class) {
            val type = Type.getType(Integer.class);

            debug("CHECKCAST java/lang/Integer");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Integer.intValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("int intValue()"));
        } else if(cls == float.class) {
            val type = Type.getType(Float.class);

            debug("CHECKCAST java/lang/Float");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Float.floatValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("float floatValue()"));
        } else if(cls == double.class) {
            val type = Type.getType(Double.class);

            debug("CHECKCAST java/lang/Double");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Double.doubleValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("double doubleValue()"));
        } else if(cls == short.class) {
            val type = Type.getType(Short.class);

            debug("CHECKCAST java/lang/Short");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Short.shortValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("short shortValue()"));
        } else if(cls == long.class) {
            val type = Type.getType(Long.class);

            debug("CHECKCAST java/lang/Long");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Long.longValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("long longValue()"));
        } else if(cls == byte.class) {
            val type = Type.getType(Byte.class);

            debug("CHECKCAST java/lang/Byte");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Byte.byteValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("byte byteValue()"));
        } else if(cls == char.class) {
            val type = Type.getType(Character.class);

            debug("CHECKCAST java/lang/Character");
            mv.checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Character.charValue");
            mv.invokeVirtual(type, org.mvel2.asm.commons.Method.getMethod("char charValue()"));
        }
    }

    /** 往栈中放一个int常量 */
    private void pushInt(int index) {
        mv.push(index);

        debug(() -> {
            if(index >= 0 && index < 6) {
                return "ICONST_" + index;
            } else if(index > -127 && index < 128) {
                return "BIPUSH " + index;
            } else if(index > Short.MAX_VALUE) {
                return "LDC " + index;
            } else {
                return "SIPUSH " + index;
            }
        });
    }

    /** 往栈中放类常量 */
    private void pushClass(Class cls) {
        debug("LDC " + getType(cls));
        mv.push(getType(cls));
    }

    /** 输出常量值 */
    private void pushLiteralWrapped(Object lit) {
        debug("LDC " + lit);

        if(lit instanceof Integer) {
            pushInt((Integer) lit);
            wrapPrimitive(int.class);
            return;
        }

        if(lit instanceof String) {
            mv.visitLdcInsn(lit);
        } else if(lit instanceof Long) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(long.class);
        } else if(lit instanceof Float) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(float.class);
        } else if(lit instanceof Double) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(double.class);
        } else if(lit instanceof Short) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(short.class);
        } else if(lit instanceof Character) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(char.class);
        } else if(lit instanceof Boolean) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(boolean.class);
        } else if(lit instanceof Byte) {
            mv.visitLdcInsn(lit);
            wrapPrimitive(byte.class);
        }
    }

    /** 执行数组存储指令 */
    public void arrayStore(Class cls) {
        debug(() -> {
            if(cls.isPrimitive()) {
                if(cls == int.class) {
                    return "IASTORE";
                } else if(cls == char.class) {
                    return "CASTORE";
                } else if(cls == boolean.class) {
                    return "BASTORE";
                } else if(cls == double.class) {
                    return "DASTORE";
                } else if(cls == float.class) {
                    return "FASTORE";
                } else if(cls == short.class) {
                    return "SASTORE";
                } else if(cls == long.class) {
                    return "LASTORE";
                } else if(cls == byte.class) {
                    return "BASTORE";
                } else {
                    throw new RuntimeException("不支持的基本类型:" + cls);
                }
            } else {
                return "AASTORE";
            }
        });

        mv.arrayStore(Type.getType(cls));
    }

    /** 断言当前对象必须是相应的类型 */
    private void checkCast(Class cls) {
        debug(() -> "CHECKCAST " + getInternalName(cls));

        mv.checkCast(Type.getType(cls));
    }

    /** 对一个数组类型进行类型检查 */
    private void arrayCheckCast(Class cls) {
        Class checkClass = cls;
        if(!cls.getComponentType().isPrimitive()) {
            checkClass = Object[].class;
        }

        val type = Type.getType(checkClass);
        debug(() -> "CHECKCAST " + type.getDescriptor());

        mv.checkCast(type);
    }

    //---------------------------- 辅助及工具方法 end ------------------------------//

    //---------------------------- 类加载 start ------------------------------//

    private static MvelClassLoader classLoader;

    public static void setMVELClassLoader(MvelClassLoader cl) {
        classLoader = cl;
    }

    public void init() {
        try{
            classLoader = new JitClassLoader(currentThread().getContextClassLoader());
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ContextClassLoader getContextClassLoader() {
        return pCtx == null ? null : new ContextClassLoader(pCtx.getClassLoader());
    }

    private static class ContextClassLoader extends ClassLoader {
        ContextClassLoader(ClassLoader classLoader) {
            super(classLoader);
        }

        Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private java.lang.Class loadClass(String className, byte[] b) throws Exception {
        Files.write(Paths.get("/tmp", className + ".class"), b, StandardOpenOption.CREATE);
        /*
         * This must be synchronized.  Two classes cannot be simultaneously deployed in the JVM.
         */
        ContextClassLoader contextClassLoader = getContextClassLoader();
        return contextClassLoader == null ?
                classLoader.defineClassX(className, b, 0, b.length) :
                contextClassLoader.defineClass(className, b);
    }

    //---------------------------- 类加载 end ------------------------------//
}
