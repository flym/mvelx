package org.mvelx.optimizers.impl.asm;

import com.google.common.base.Strings;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.mvelx.*;
import org.mvelx.asm.*;
import org.mvelx.asm.Type;
import org.mvelx.asm.commons.GeneratorAdapter;
import org.mvelx.ast.FunctionInstance;
import org.mvelx.ast.TypeDescriptor;
import org.mvelx.compiler.*;
import org.mvelx.integration.*;
import org.mvelx.optimizers.AbstractOptimizer;
import org.mvelx.optimizers.AccessorOptimizer;
import org.mvelx.optimizers.OptimizationNotSupported;
import org.mvelx.optimizers.impl.refl.nodes.DelegatedAccessorNode;
import org.mvelx.optimizers.impl.refl.nodes.Union;
import org.mvelx.util.*;

import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.mvelx.asm.Opcodes.*;


/**
 * 实现基于asm字节码处理的优化器，通过直接分析字节码来达到执行的目的
 */
@SuppressWarnings({"TypeParameterExplicitlyExtendsObject", "unchecked", "UnusedDeclaration"})
@NoArgsConstructor
@Slf4j
public class AsmAccessorOptimizer extends AbstractOptimizer implements AccessorOptimizer {
    private static final String NAMESPACE = "org/mvelx/";
    private static final int OPCODES_VERSION = Opcodes.V1_8;

    private static final org.mvelx.asm.commons.Method METHOD_GET_VALUE = org.mvelx.asm.commons.Method.getMethod("Object getValue(Object, Object, org.mvelx.integration.VariableResolverFactory)");
    private static final org.mvelx.asm.commons.Method METHOD_SET_VALUE = org.mvelx.asm.commons.Method.getMethod("Object setValue(Object, Object, org.mvelx.integration.VariableResolverFactory, Object)");
    private static final org.mvelx.asm.commons.Method METHOD_GET_KNOWN_EGRESS_TYPE = org.mvelx.asm.commons.Method.getMethod("Class getKnownEgressType()");
    private static final org.mvelx.asm.commons.Method METHOD_TO_STRING = org.mvelx.asm.commons.Method.getMethod("String toString()");

    private static final int ACCESSOR_LOCAL_IDX_CTX = 1;
    private static final int ACCESSOR_LOCAL_IDX_EL_CTX = 2;
    private static final int ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY = 3;
    private static final int ACCESSOR_LOCAL_IDX_SET_VALUE = 4;

    private static final AtomicLong CLASS_NAME_POSTFIX = new AtomicLong(System.currentTimeMillis());

    private Object ctx;
    /** 当前执行过程中的this对象引用 */
    private Object thisRef;

    private VariableResolverFactory variableFactory;

    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final Class[] EMPTY_CLASSES = new Class[0];

    /** 表示当前处理属性刚开始(即处理位置在首位) */
    private boolean first = true;

    /** 相应的jit是否还没有初始化 */
    private boolean notInit = false;

    private boolean deferFinish = false;
    private boolean literal = false;

    /** 是否生成属性访问的null值引用字段，此字段用于引用 {@link PropertyHandler} 对象 */
    private boolean propertyNullField = false;
    /** 是否生成方法访问的null值引用字段, 此字段用于引用 {@link PropertyHandler} 对象 */
    private boolean methodNullField = false;

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

    @Setter
    private StringAppender buildLog;

    /** jit初始化样板代码，即初始化类以及相应方法 */
    private void _initJit4GetValue() {
        //新类名
        className = generateNewAccessorClassName();
        //新classWriter
        cw = createNewAccessorClassWriter(className);
        //构造方法
        generateDefaultConstructor(cw);

        val sourceMv = cw.visitMethod(ACC_PUBLIC, METHOD_GET_VALUE.getName(), METHOD_GET_VALUE.getDescriptor(), null, null);
        mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_GET_VALUE, sourceMv);
        mv.visitCode();
    }

    private void _initJit4SetValue() {
        //新类名
        className = generateNewAccessorClassName();

        //新classWriter
        cw = createNewAccessorClassWriter(className);

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

    /** 进行相应的设置值访问器创建 */
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
            root = ParseTools.subset(property, 0, split);
        }

        AccessorNode rootAccessor = null;

        _initJit4SetValue();

        //有前半部分，则先处理前半部分，即前半部分可以理解为是一个 get操作
        if(root != null) {
            int _length = this.length;
            int _end = this.end;
            char[] _expr = this.expr;

            this.length = end = (this.expr = root).length;

            //设置标记，以避免提前处理结束
            deferFinish = true;
            notInit = true;

            compileAccessor();
            ctx = this.resultValue;

            this.expr = _expr;
            this.cursor = start + root.length + 1;
            this.length = _length - root.length - 1;
            this.end = this.cursor + this.length;
        }
        //无前半部分，则直接使用ctx对象
        else {
            debug("ALOAD 1");
            mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
        }

        try{
            skipWhitespace();

            //集合类操作
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

                checkCast(ctx.getClass());

                //map类调用
                if(ctx instanceof Map) {
                    //以下主要生成 map.put(k,v),map已经在栈中
                    //noinspection unchecked
                    ExecutableStatement keyEs = (ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx);
                    Object key = keyEs.getValue(ctx, variableFactory);
                    ((Map) ctx).put(key, DataConversion.convert(value, returnType = verifier.analyze()));

                    //key
                    generateLiteralOrExecuteStatement(keyEs, null, null);
                    //value
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                    //可能的值类型转换
                    if(value != null && returnType != value.getClass()) {
                        generateDataConversionCode(returnType);
                        checkCast(returnType);
                    }
                    //op map.put
                    debug("INVOKEINTERFACE Map.put");
                    mv.invokeInterface(Type.getType(Map.class), org.mvelx.asm.commons.Method.getMethod("Object put(Object,Object)"));
                    //删除返回值
                    debug("POP");
                    mv.pop();

                    //返回参数值 与 org.mvelx.optimizers.impl.refl.nodes.MapAccessor 相一致
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                }
                //list类调用
                else if(ctx instanceof List) {
                    //以下主要生成 list.set(int,value),list已经在栈中
                    //noinspection unchecked
                    ExecutableStatement idxEs = (ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx);
                    Integer idx = (Integer) idxEs.getValue(ctx, variableFactory);
                    ((List) ctx).set(idx, DataConversion.convert(value, returnType = verifier.analyze()));

                    //idx
                    generateLiteralOrExecuteStatement(ParseTools.subCompileExpression(ex.toCharArray(), pCtx), null, null);
                    unwrapPrimitive(int.class);
                    //value
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                    //可能的值类型转换
                    if(value != null && !value.getClass().isAssignableFrom(returnType)) {
                        generateDataConversionCode(returnType);
                        checkCast(returnType);
                    }
                    //op list.set
                    debug("INVOKEINTERFACE List.set");
                    mv.invokeInterface(Type.getType(List.class), org.mvelx.asm.commons.Method.getMethod("Object set(int,Object)"));

                    //返回 set value
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                }
                //自定义属性类操作
                else if(PropertyHandlerFactory.hasPropertyHandler(ctx.getClass())) {
                    generatePhByteCode4Set(ex, ctx, ctx.getClass(), value);
                }
                //数组类操作
                else if(ctx.getClass().isArray()) {
                    //以下代码主要实现 arrays[i] = value arrays已经在栈中
                    Class type = ParseTools.getBaseComponentType(ctx.getClass());

                    Object idx = ((ExecutableStatement) ParseTools.subCompileExpression(ex.toCharArray(), pCtx)).getValue(ctx, variableFactory);

                    //i
                    generateLiteralOrExecuteStatement(ParseTools.subCompileExpression(ex.toCharArray(), pCtx), int.class, null);
                    //非int类型，需要进行转换
                    if(!(idx instanceof Integer)) {
                        generateDataConversionCode(Integer.class);
                        idx = DataConversion.convert(idx, Integer.class);
                        unwrapPrimitive(int.class);
                    }

                    //value
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                    //value类型转换
                    if(type.isPrimitive()) {
                        unwrapPrimitive(type);
                    } else if(!type.equals(value.getClass())) {
                        generateDataConversionCode(type);
                    }
                    //op arrays[i] = value
                    arrayStore(type);

                    //noinspection unchecked
                    Array.set(ctx, (Integer) idx, DataConversion.convert(value, type));

                    //返回参数值
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
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

            //以下开始为属性类调用，即a.b=xx，这种调用方式

            String tk = new String(expr, this.cursor, this.length);
            Member member = PropertyTools.getFieldOrWriteAccessor(ctx.getClass(), tk, value == null ? null : ingressType);

            //触发全局set/get监听器
            if(GlobalListenerFactory.hasSetListeners()) {
                //调用 GlobalListenerFactory void notifySetListeners(Object target, String name, VariableResolverFactory variableFactory, Object value)
                mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
                mv.push(tk);
                mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);
                mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                mv.invokeStatic(Type.getType(GlobalListenerFactory.class), org.mvelx.asm.commons.Method.getMethod("void notifySetListeners(Object, String, org.mvelx.integration.VariableResolverFactory, Object)"));

                GlobalListenerFactory.notifySetListeners(ctx, tk, variableFactory, value);
            }

            //字段
            if(member instanceof Field) {
                checkCast(ctx.getClass());

                Field fld = (Field) member;
                val fieldType = fld.getType();

                //以下的主要代码为
                /*
                if(fieldType.isPrimitive()) {
                    if(value == null)
                        field = 0; //各种转换，可以
                        return;
                    else {
                        field = value.unwrap();
                    }
                } else {
                    value = convert(value,fieldType)
                    field = value;
                }

                */

                Label primitiveAndNotNullLabel;
                Label valueLoadLabel = new Label();

                if(fieldType.isPrimitive()) {
                    //current.field = value

                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);

                    //以下逻辑实现
                    /*
                     if(value == null)
                        value = 0
                     else
                        value = value.unwrap()
                    */
                    primitiveAndNotNullLabel = new Label();
                    debug("IFNOTNULL jmp");
                    mv.ifNonNull(primitiveAndNotNullLabel);

                    //空值转0
                    debug("ICONST_0");
                    mv.push(0);
                    debug("GOTO valueLoadLabel");
                    mv.goTo(valueLoadLabel);

                    debug("label:primitiveAndNotNull");
                    mv.visitLabel(primitiveAndNotNullLabel);

                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                    //解包
                    unwrapPrimitive(fld.getType());

                    //以下为当前值处理

                    //空值处理,避免设置时NPE
                    if(value == null) {
                        value = PropertyTools.getPrimitiveInitialValue(fld.getType());
                    }
                    //字段赋值
                    fld.set(ctx, value);
                } else {
                    debug("ALOAD 4");
                    mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);

                    //潜在的类型转换
                    if(value != null && !fld.getType().isAssignableFrom(value.getClass())) {
                        if(!DataConversion.canConvert(fld.getType(), value.getClass())) {
                            throw new CompileException("cannot convert type: "
                                    + value.getClass() + ": to " + fld.getType(), expr, start);
                        }

                        generateDataConversionCode(fld.getType());
                        //字段赋值
                        fld.set(ctx, DataConversion.convert(value, fld.getType()));
                    }

                    checkCast(fld.getType());
                }

                debug("label:valueLoadLabel:");
                mv.visitLabel(valueLoadLabel);

                //op field = value
                debug(() -> "PUTFIELD " + Type.getInternalName(fld.getDeclaringClass()) + "." + tk);
                mv.putField(Type.getType(fld.getDeclaringClass()), tk, Type.getType(fld.getType()));

                //返回参数值
                debug("ALOAD 4");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
            }
            //方法
            else if(member != null) {
                checkCast(ctx.getClass());

                Method method = (Method) member;

                //以下主要逻辑为
                /*
                if(type not match) {
                    value = convert(value)
                    value = value.unwrap()
                } else {
                    if(type is primitive) {
                        if(value == null)
                            value = 0
                        else
                            value = value.unwrap()
                    }
                }

                invokemethod(value)
                 */

                debug("ALOAD 4");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);

                Class methodParamType = method.getParameterTypes()[0];

                Label valueLoadLabel = new Label();

                if(value != null && !methodParamType.isAssignableFrom(value.getClass())) {
                    if(!DataConversion.canConvert(methodParamType, value.getClass())) {
                        throw new CompileException("cannot convert type: "
                                + value.getClass() + ": to " + method.getParameterTypes()[0], expr, start);
                    }

                    generateDataConversionCode(toWrapperClass(methodParamType));
                    if(methodParamType.isPrimitive()) {
                        unwrapPrimitive(methodParamType);
                    } else {
                        checkCast(methodParamType);
                    }
                    method.invoke(ctx, DataConversion.convert(value, method.getParameterTypes()[0]));
                } else {
                    if(methodParamType.isPrimitive()) {
                        if(value == null) {
                            value = PropertyTools.getPrimitiveInitialValue(methodParamType);
                        }

                        Label primitiveNotNullLabel = new Label();
                        debug("IFNOTNULL primitiveNotNullLabel");
                        mv.ifNonNull(primitiveNotNullLabel);
                        //null转常量0
                        debug("ICONST_0");
                        mv.push(0);
                        debug("GOTO valueLoadLabel");
                        mv.visitJumpInsn(GOTO, valueLoadLabel);

                        debug("label:primitiveNotNullLabel");
                        mv.visitLabel(primitiveNotNullLabel);
                        //加载参数值，并解馋
                        debug("ALOAD 4");
                        mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                        unwrapPrimitive(methodParamType);
                    } else {
                        checkCast(methodParamType);
                    }

                    method.invoke(ctx, value);
                }

                debug("label:valueLoadLabel");
                mv.visitLabel(valueLoadLabel);

                debug(() -> "INVOKEVIRTUAL " + Type.getInternalName(method.getDeclaringClass()) + "." + method.getName());
                mv.invokeVirtual(Type.getType(method.getDeclaringClass()), org.mvelx.asm.commons.Method.getMethod(method));

                //返回参数值
                debug("ALOAD 4");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
            }
            //支持map的.式调用，如map.a=b，这种调用方式
            else if(ctx instanceof Map) {
                checkCast(ctx.getClass());

                //以下代码生成
                // map.put("key",value) 其中map已在栈中，key为字符串

                //key
                debug("LDC '" + tk + "'");
                mv.push(tk);
                //value
                debug("ALOAD 4");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);
                //op put(k,v)
                debug("INVOKEINTERFACE java/util/Map.put");
                mv.invokeInterface(Type.getType(Map.class), org.mvelx.asm.commons.Method.getMethod("Object put(Object,Object)"));
                //丢弃返回值
                mv.pop();

                //返回参数值
                debug("ALOAD 4");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_SET_VALUE);

                //noinspection unchecked
                ((Map) ctx).put(tk, value);
            } else {
                throw new PropertyAccessException("could not access property (" + tk + ") in: "
                        + ingressType.getName(), expr, start, pCtx);
            }
        } catch(InvocationTargetException | IllegalAccessException e) {
            throw new PropertyAccessException("could not access property", expr, start, e, pCtx);
        }

        //最终结束
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

        //因为accessor的返回类型统一为object，这里如果原返回类型为基本类型，这里进行包装处理
        if(returnType != null && returnType.isPrimitive()) {
            wrapPrimitive(returnType);
        }

        //void返回类型
        if(returnType == void.class) {
            debug("ACONST_NULL");
            mv.visitInsn(Opcodes.ACONST_NULL);
        }

        debug("ARETURN");
        mv.returnValue();

        debug("\n{METHOD STATS (maxstack=" + stacksize + ")}\n");

        // 打印debug信息
        dumpAdvancedDebugging();

        mv.visitMaxs(stacksize, maxlocals);
        mv.visitEnd();
        //以上生成主要的set/getValue方法结束

        //---生成 getKnownEgressType start

        mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_GET_KNOWN_EGRESS_TYPE, cw.visitMethod(ACC_PUBLIC, METHOD_GET_KNOWN_EGRESS_TYPE.getName(), METHOD_GET_KNOWN_EGRESS_TYPE.getDescriptor(), null, null));
        mv.visitCode();
        pushClass(returnType == null ? Object.class : returnType);
        mv.visitInsn(ARETURN);

        mv.visitMaxs(1, 1);
        mv.visitEnd();

        //---生成 getKnownEgressType end

        //生成属性nullHandler
        if(propertyNullField) {
            cw.visitField(ACC_PUBLIC, "nullPropertyHandler", "L" + NAMESPACE + "integration/PropertyHandler;", null, null).visitEnd();
        }

        //生成方法nullHandler
        if(methodNullField) {
            cw.visitField(ACC_PUBLIC, "nullMethodHandler", "L" + NAMESPACE + "integration/PropertyHandler;", null, null).visitEnd();
        }

        //带参构造器
        generateInputsConstructor();

        //在调试模式下将指令写入toString中
        if(buildLog != null && buildLog.length() != 0 && expr != null) {
            mv = new GeneratorAdapter(ACC_PUBLIC, METHOD_TO_STRING, cw.visitMethod(ACC_PUBLIC, METHOD_TO_STRING.getName(), METHOD_TO_STRING.getDescriptor(), null, null));
            mv.visitCode();

            mv.push(buildLog.toString() + "\n\n## { " + new String(expr) + " }");
            mv.returnValue();

            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
    }

    /** 实例化相应的访问器对象 */
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
            //根据参数决定如何来实例化新对象
            if(compiledInputs.size() == 0) {
                o = cls.newInstance();
            } else {
                Class[] params = new Class[compiledInputs.size()];
                Arrays.fill(params, ExecutableStatement.class);

                ExecutableStatement[] executableStatements = compiledInputs.toArray(new ExecutableStatement[compiledInputs.size()]);
                o = cls.getConstructor(params).newInstance((Object[]) executableStatements);
            }

            //填充相应的nullHandler
            if(propertyNullField) {
                cls.getField("nullPropertyHandler").set(o, PropertyHandlerFactory.getNullPropertyHandler());
            }
            if(methodNullField) {
                cls.getField("nullMethodHandler").set(o, PropertyHandlerFactory.getNullMethodHandler());
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
                    //属性访问
                    case BEAN:
                        curr = getBeanProperty(curr, capture());
                        break;
                    //方法调用
                    case METH:
                        curr = getMethod(curr, capture());
                        break;
                    //集合属性调用
                    case COL:
                        curr = getCollectionProperty(curr, capture());
                        break;
                }

                //在第一次解析整个表达式时，asm优化是不支持 nullSafe的，因为这种情况下调用提前返回，会导致后续的调用并没有生成相应的指令
                //这里的异常信息会返回给astNode，从而转由reflect来重新解析并处理
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

                //这里当前对象并不为null，因此为支持nullSafe生成相应指令
                if(nullSafe && cursor < end) {
                    debug("DUP");
                    mv.dup();

                    Label endLabel = new Label();

                    debug("IFNONNULL : jump");
                    mv.ifNonNull(endLabel);

                    debug("ARETURN");
                    mv.returnValue();

                    debug("LABEL:jump");
                    mv.visitLabel(endLabel);
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

    private Object getBeanProperty(Object ctx, String property) throws IllegalAccessException, InvocationTargetException {
        debug("\n  **  ENTER -> {bean: " + property + "; ctx=" + ctx + "}");

        //如果当前类型为通用类型，或者相应的解析上下文并不是强类型的，则将当前类型设置为null，即非强类型处理
        if((pCtx == null ? currType : pCtx.getVarOrInputTypeOrNull(property)) == Object.class
                && (pCtx != null && !pCtx.isStrongTyping())) {
            currType = null;
        }

        //上一步返回的数据为基本类型，这里为获取相应的属性，因此将其转换为包装类型
        if(returnType != null && returnType.isPrimitive()) {
            wrapPrimitive(returnType);
        }

        boolean classRef = false;

        Class<?> currentCtxClass;
        if(ctx instanceof Class) {
            //支持.class属性
            if(MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS && "class".equals(property)) {
                pushClass((Class<?>) ctx);

                return ctx;
            }

            currentCtxClass = (Class<?>) ctx;
            classRef = true;
        } else if(ctx != null) {
            currentCtxClass = ctx.getClass();
        } else {
            currentCtxClass = null;
        }

        //支持自定义属性处理器
        if(PropertyHandlerFactory.hasPropertyHandler(currentCtxClass)) {
            return generatePhByteCode4Get(property, ctx, currentCtxClass);
        }

        Member member = currentCtxClass != null ? PropertyTools.getFieldOrAccessor(currentCtxClass, property) : null;

        //如果当前成员找到了，但当前处理对象为类类型，并且当前成员并不是静态成员，
        //则表示找到的成员不能满足要求，则设置为null，避免错误处理
        if(member != null && classRef && (member.getModifiers() & Modifier.STATIC) == 0) {
            member = null;
        }

        //支持全局监听器
        if(member != null && GlobalListenerFactory.hasGetListeners()) {
            //GlobalListenerFactory.notifyGetListeners(Object target, String name, VariableResolverFactory variableFactory)
            mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
            mv.push(member.getName());
            mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);

            mv.invokeStatic(Type.getType(GlobalListenerFactory.class), org.mvelx.asm.commons.Method.getMethod("void notifyGetListeners(Object, String, org.mvelx.integration.VariableResolverFactory)"));

            GlobalListenerFactory.notifyGetListeners(ctx, member.getName(), variableFactory);
        }

        if(first) {
            //支持首单词为this,即访问当前对象
            if("this".equals(property)) {
                debug("ALOAD 2");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_EL_CTX);
                return thisRef;
            }
            //如果变量解析器能够解析此变量，则使用变量解析器，变量解析器敢只有在first时才能解析，
            //如a.b中，只有a才可能在变量工厂中使用,b是不能使用的
            else if(variableFactory != null && variableFactory.isResolvable(property)) {
                //2种处理方式，一种是基于下标处理，另一种是基于属性直接映射处理
                if(variableFactory.isIndexedFactory() && variableFactory.isTarget(property)) {
                    int idx;
                    try{
                        generateLoadVariableByIdx(idx = variableFactory.variableIndexOf(property));
                    } catch(Exception e) {
                        throw new OptimizationFailure(property);
                    }

                    return variableFactory.getIndexedVariableResolver(idx).getValue();
                }
                //这里表示变量工厂能够直接解析此变量(并且不是基于下标处理的)，这里添加变量访问器，并直接访问此变量
                else {
                    try{
                        generateLoadVariableByName(property);
                    } catch(Exception e) {
                        throw new OptimizationFailure("critical error in JIT", e);
                    }

                    return variableFactory.getVariableResolver(property).getValue();
                }
            }
            //其它情况下，因为要访问此属性，先把当前对象加入栈中
            else {
                debug("ALOAD 1");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
            }
        }

        val thisMember = member;

        //字段处理
        if(member instanceof Field) {
            return optimizeFieldProperty(ctx, property, currentCtxClass, member);
        }
        //这里即为相应的方法调用处理
        else if(member != null) {
            Object o;

            if(first) {
                debug("ALOAD 1 (B)");
                mv.visitVarInsn(ALOAD, 1);
            }

            try{
                //这里先调用相应的方法，如果这里失败，则直接跳到catch(IllegalAccessException)处，那么下面就不会生成错误的代码
                o = ((Method) member).invoke(ctx, EMPTY_ARGS);

                val memberClass = member.getDeclaringClass();

                //上一步的结果对象强制类型检查
                if(returnType != member.getDeclaringClass()) {
                    checkCast(memberClass);
                }

                returnType = ((Method) member).getReturnType();

                //调用相应的方法 无参数
                debug(() -> "INVOKEVIRTUAL " + thisMember.getName() + ":" + returnType);
                mv.invokeVirtual(Type.getType(memberClass), org.mvelx.asm.commons.Method.getMethod((Method) member));
            } catch(IllegalAccessException e) {
                //调用失败，说明方法查找出错，重新查找
                Method interfaceMethod = ParseTools.determineActualTargetMethod((Method) member);
                if(interfaceMethod == null) {
                    throw new PropertyAccessException("could not access field: " + currentCtxClass.getName() + "." + property, expr, st, e, pCtx);
                }

                val interfaceMethodClass = interfaceMethod.getDeclaringClass();
                checkCast(interfaceMethodClass);

                returnType = interfaceMethod.getReturnType();

                debug(() -> "INVOKEINTERFACE " + thisMember.getName() + ":" + returnType);
                mv.invokeInterface(Type.getType(interfaceMethodClass), org.mvelx.asm.commons.Method.getMethod(interfaceMethod));

                o = interfaceMethod.invoke(ctx, EMPTY_ARGS);
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

            //支持nullHandler
            if(PropertyHandlerFactory.hasNullPropertyHandler()) {
                if(o == null) {
                    o = PropertyHandlerFactory.getNullPropertyHandler().getProperty(member.getName(), ctx, variableFactory);
                }
                generateNullPropertyHandler(member, NullPropertyHandlerType.FIELD);
            }

            currType = ReflectionUtil.toNonPrimitiveType(returnType);
            return o;
        }
        //map属性获取的方式(前提是有此key或者是允许null安全),即如果map没有此属性，也仍然不能访问此值
        else if(ctx instanceof Map && (((Map) ctx).containsKey(property) || nullSafe)) {
            checkCast(Map.class);

            debug("LDC: \"" + property + "\"");
            mv.push(property);

            debug("INVOKEINTERFACE: get");
            mv.invokeInterface(Type.getType(Map.class), org.mvelx.asm.commons.Method.getMethod("Object get(Object)"));

            return ((Map) ctx).get(property);
        }
        //读取数组长度的方式
        else if("length".equals(property) && ctx != null && ctx.getClass().isArray()) {
            arrayCheckCast(ctx.getClass());

            debug("ARRAYLENGTH");
            mv.arrayLength();

            wrapPrimitive(int.class);
            return Array.getLength(ctx);
        }
        //静态常量引用
        else if(LITERALS.containsKey(property)) {
            Object lit = LITERALS.get(property);

            if(lit instanceof Class) {
                pushClass((Class) lit);
            }

            return lit;
        } else {
            //尝试获取静态方法引用，如果该属性即是一个静态方法，则直接返回此,即类.方法名的形式
            Object ts = tryStaticAccess();

            if(ts != null) {
                //静态类
                if(ts instanceof Class) {
                    pushClass((Class) ts);
                    return ts;
                }
                //直接访问类的方法信息,因此生成相应的方法句柄
                else if(ts instanceof Method) {
                    writeFunctionPointerStub(((Method) ts).getDeclaringClass(), (Method) ts);
                    return ts;
                }
                //直接访问类的字段
                else {
                    Field f = (Field) ts;
                    return optimizeFieldProperty(ctx, property, currentCtxClass, f);
                }
            } else if(ctx instanceof Class) {
                //这里与上面不同，上面是直接有类名，这里是只有方法名，如当前ctx为T 这里的属性名为 abc，则表示访问T.abc这个方法
                Class c = (Class) ctx;
                for(Method m : c.getMethods()) {
                    if(property.equals(m.getName())) {
                        writeFunctionPointerStub(c, m);
                        return m;
                    }
                }

                //这里最后认为是 T$abc这个内部类的引用，一般情况下这里不会到达，即这里只写一个abc，最后引用到T$abc这个内部类
                try{
                    Class subClass = ParseTools.findClass(variableFactory, c.getName() + "$" + property, pCtx);
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

    /** 针对字段的优化访问 */
    private Object optimizeFieldProperty(Object ctx, String property, Class<?> cls, Member member) throws IllegalAccessException {
        Object o = ((Field) member).get(ctx);

        val fieldType = ((Field) member).getType();
        returnType = fieldType;

        //静态字段
        if(Modifier.isStatic(member.getModifiers())) {
            //静态常量优化，如果是常量，则直接使用const，避免反复调用
            if(Modifier.isFinal(member.getModifiers()) && (o instanceof String || ((Field) member).getType().isPrimitive())) {
                o = ((Field) member).get(null);
                debug("LDC " + String.valueOf(o));
                mv.visitLdcInsn(o);

                //类型包装
                if(o != null) {
                    wrapPrimitive(o.getClass());
                }

                //支持null处理,这里为属性支持
                if(PropertyHandlerFactory.hasNullPropertyHandler()) {
                    if(o == null) {
                        o = PropertyHandlerFactory.getNullPropertyHandler().getProperty(member.getName(), ctx, variableFactory);
                    }

                    generateNullPropertyHandler(member, NullPropertyHandlerType.FIELD);
                }

                return o;
            }
            //普通静态字段
            else {
                debug(() -> "GETSTATIC " + Type.getDescriptor(member.getDeclaringClass()) + "." + member.getName()
                        + "::" + Type.getDescriptor(fieldType));

                mv.getStatic(Type.getType(member.getDeclaringClass()), member.getName(), Type.getType(fieldType));
            }
        }
        //普通字段
        else {
            //当前对象强制类型检查
            checkCast(cls);

            debug(() -> "GETFIELD " + property + ":" + Type.getDescriptor(fieldType));
            mv.getField(Type.getType(cls), property, Type.getType(fieldType));
        }

        //支持null处理
        if(PropertyHandlerFactory.hasNullPropertyHandler()) {
            if(o == null) {
                o = PropertyHandlerFactory.getNullPropertyHandler().getProperty(member.getName(), ctx, variableFactory);
            }

            generateNullPropertyHandler(member, NullPropertyHandlerType.FIELD);
        }

        currType = ReflectionUtil.toNonPrimitiveType(returnType);
        return o;
    }

    /** 生成获取指定方法的代码处理,即根据名字获取相应的方法 */
    private void writeFunctionPointerStub(Class c, Method m) {
        val typeMethod = Type.getType(Method.class);
        //以下的主要逻辑为
        /*
        Method[] methods = class.getMethods();
        for(int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if(m.getName().equals(name)
                return m;
        }
        return null;
         */

        //Method[] methods = class.getMethods(); 并存储在本地变量中
        pushClass(c);
        mv.invokeVirtual(Type.getType(Class.class), org.mvelx.asm.commons.Method.getMethod("java.lang.reflect.Method[] getMethods()"));
        int localIdxMethods = mv.newLocal(Type.getType(Method[].class));
        mv.storeLocal(localIdxMethods);

        //处理循环部分的判断变量声明部分 i, length 等
        mv.push(0);
        int localIdxI = mv.newLocal(Type.INT_TYPE);
        mv.storeLocal(localIdxI);

        mv.loadLocal(localIdxMethods);
        mv.arrayLength();
        int localIdxLength = mv.newLocal(Type.INT_TYPE);
        mv.storeLocal(localIdxLength);

        //处理循环判断部分，使用goto来完成循环处理

        Label forStartLabel = new Label();
        mv.visitJumpInsn(GOTO, forStartLabel);

        //循环主体部分
        Label bodyStartLabel = new Label();
        mv.visitLabel(bodyStartLabel);

        //Method m = methods[i];
        mv.loadLocal(localIdxMethods);
        mv.loadLocal(localIdxI);
        mv.arrayLoad(typeMethod);
        int localIdxMethod = mv.newLocal(typeMethod);
        mv.storeLocal(localIdxMethod);

        //name.equals(method.getName())
        mv.push(m.getName());
        mv.loadLocal(localIdxMethod);
        mv.invokeVirtual(typeMethod, org.mvelx.asm.commons.Method.getMethod("String getName()"));
        mv.invokeVirtual(Type.getType(String.class), org.mvelx.asm.commons.Method.getMethod("boolean equals(Object)"));


        //if(false) 继续 else 返回
        Label falseLabel = new Label();
        mv.visitJumpInsn(IFEQ, falseLabel);

        //返回数据
        mv.visitVarInsn(ALOAD, 4);
        mv.loadLocal(localIdxMethod);
        mv.returnValue();

        //i++, if(i < length) 就继续
        mv.visitLabel(falseLabel);
        mv.iinc(localIdxI, 1);
        mv.visitLabel(forStartLabel);
        mv.loadLocal(localIdxI);
        mv.loadLocal(localIdxLength);
        mv.ifICmp(GeneratorAdapter.LT, bodyStartLabel);

        //退出循环，直接返回null
        mv.visitInsn(ACONST_NULL);
        mv.returnValue();
    }

    /** 获取一个集合的值信息 */
    private Object getCollectionProperty(Object ctx, String prop) throws IllegalAccessException, InvocationTargetException {
        //集合前的属性信息，如 a.bc[2]，先拿到a.bc信息
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

        //跳到相应的]结束符位置
        if(scanTo(']')) {
            throw new CompileException("unterminated '['", expr, st);
        }

        String tk = new String(expr, start, cursor - start);

        debug("{collection token: [" + tk + "]}");

        if(ctx == null) {
            return null;
        }

        //如果是首解析，先加载当前对象至栈中，以便访问数据
        if(first) {
            debug("ALOAD 1");
            mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
        }

        ExecutableStatement compiled = (ExecutableStatement) ParseTools.subCompileExpression(tk.toCharArray(), pCtx);
        Object item = compiled.getValue(ctx, variableFactory);

        ++cursor;

        //处理map访问的形式,如a[b]
        if(ctx instanceof Map) {
            //强制类型检查
            checkCast(Map.class);

            //生成相应的执行单元代码
            Class c = generateLiteralOrExecuteStatement(compiled, null, null);
            if(c != null && c.isPrimitive()) {
                wrapPrimitive(c);
            }

            debug("INVOKEINTERFACE: get");
            mv.invokeInterface(Type.getType(Map.class), org.mvelx.asm.commons.Method.getMethod("Object get(Object)"));

            return ((Map) ctx).get(item);
        }
        //处理list访问
        else if(ctx instanceof List) {
            checkCast(List.class);

            //预期的执行结果为int类型，因为是get(int)
            generateLiteralOrExecuteStatement(compiled, int.class, null);

            debug("INVOKEINTERFACE: java/util/List.get");
            mv.invokeInterface(Type.getType(List.class), org.mvelx.asm.commons.Method.getMethod("Object get(int)"));

            return ((List) ctx).get(DataConversion.convert(item, Integer.class));
        }
        //处理数组访问的形式
        else if(ctx.getClass().isArray()) {
            checkCast(ctx.getClass());

            generateLiteralOrExecuteStatement(compiled, int.class, item.getClass());

            Class cls = ParseTools.getBaseComponentType(ctx.getClass());
            debug("XALOAD:" + cls);
            mv.arrayLoad(Type.getType(cls));

            return Array.get(ctx, DataConversion.convert(item, Integer.class));
        }
        //处理字符串访问的形式
        else if(ctx instanceof CharSequence) {
            checkCast(CharSequence.class);

            generateLiteralOrExecuteStatement(compiled, int.class, null);

            debug("INVOKEINTERFACE java/lang/CharSequence.charAt");
            mv.invokeInterface(Type.getType(CharSequence.class), org.mvelx.asm.commons.Method.getMethod("char charAt(int)"));

            wrapPrimitive(char.class);

            return ((CharSequence) ctx).charAt(DataConversion.convert(item, Integer.class));
        }
        //最后认为是一个数组类型描述符,当前数组内的内容忽略
        else {
            TypeDescriptor tDescr = new TypeDescriptor(expr, this.start, length, 0);
            if(tDescr.isArray()) {
                try{
                    Class cls = TypeDescriptor.getClassReference((Class) ctx, tDescr, variableFactory, pCtx);
                    pushClass(cls);
                    return cls;
                } catch(Exception e) {
                    //fall through
                }
            }

            throw new CompileException("illegal use of []: unknown type: " + ctx.getClass().getName(), expr, st);
        }
    }

    /** 这里进行方法式访问和调用 */
    @SuppressWarnings({"unchecked"})
    private Object getMethod(Object ctx, String name) throws IllegalAccessException, InvocationTargetException {
        debug("\n  **  {method: " + name + "}");

        int st = cursor;
        //tk表示捕获到()内的相应参数内容信息,如(a,b,c)就拿到a,b,c
        String tk = cursor != end && expr[cursor] == '(' && ((cursor = ParseTools.balancedCapture(expr, cursor, '(')) - st) > 1 ?
                new String(expr, st + 1, cursor - st - 1) : "";
        cursor++;

        //已经根据调用方法的实际参数类型进行处理过的参数集(支持泛型处理)，主要用于处理常量值,以及用于生成指令
        Object[] preConvertedArgs;
        //当前实际调用时的参数信息(经过类型转换)
        Object[] args;
        //参数类型
        Class[] argTypes;
        //分组的参数执行单元
        ExecutableStatement[] subEss;
        //分组的参数
        List<char[]> subTokenList;

        //空参数的情况
        if(tk.length() == 0) {
            args = preConvertedArgs = ParseTools.EMPTY_OBJ_ARR;
            argTypes = ParseTools.EMPTY_CLS_ARR;
            subEss = null;
            subTokenList = null;
        } else {
            subTokenList = ParseTools.parseParameterList(tk.toCharArray(), 0, -1);

            subEss = new ExecutableStatement[subTokenList.size()];
            args = new Object[subTokenList.size()];
            argTypes = new Class[subTokenList.size()];
            preConvertedArgs = new Object[subTokenList.size()];

            //每个参数段分别编译并执行
            for(int i = 0; i < subTokenList.size(); i++) {
                debug("subtoken[" + i + "] { " + new String(subTokenList.get(i)) + " }");
                subEss[i] = (ExecutableStatement) ParseTools.subCompileExpression(subTokenList.get(i), pCtx);
                preConvertedArgs[i] = args[i] = subEss[i].getValue(this.thisRef, this.thisRef, variableFactory);

                //如果这个参数是类似(Abc) xxx的调用，则使用转型之后的出参信息
                if(subEss[i].isExplicitCast()) {
                    argTypes[i] = subEss[i].getKnownEgressType();
                }
            }

            //设置参数类型信息
            //这里为严格类型调用，因此准备相应的类型信息
            if(pCtx.isStrictTypeEnforcement()) {
                for(int i = 0; i < args.length; i++) {
                    argTypes[i] = subEss[i].getKnownEgressType();
                }
            } else {
                for(int i = 0; i < args.length; i++) {
                    if(argTypes[i] != null) {
                        continue;
                    }

                    if(subEss[i].getKnownEgressType() == Object.class) {
                        argTypes[i] = args[i] == null ? null : args[i].getClass();
                    } else {
                        argTypes[i] = subEss[i].getKnownEgressType();
                    }
                }
            }
        }

        //如果是起始调用，并且变量能够解析，则表示此变量是一个方法句柄类信息，则通过此方法句柄进行调用
        if(first && variableFactory != null && variableFactory.isResolvable(name)) {
            Object ptr = variableFactory.getVariableResolver(name).getValue();

            //变量为一个方法
            if(ptr instanceof Method) {
                ctx = ((Method) ptr).getDeclaringClass();
                name = ((Method) ptr).getName();
            }
            //方法句柄
            else if(ptr instanceof MethodStub) {
                ctx = ((MethodStub) ptr).getClassReference();
                name = ((MethodStub) ptr).getMethodName();
            }
            //函数定义
            else if(ptr instanceof FunctionInstance) {

                //4 localIdxParams
                int localIdxParams = mv.newLocal(Type.getType(Object[].class));

                //如果存在参数的情况，那么即会将这些参数在实例化对象 initAccessor时注入到字段中,并且按照参数顺序进行命名 p1,p2,p3这样
                if(subEss != null && subEss.length != 0) {
                    compiledInputs.addAll(Arrays.asList(subEss));

                    //准备为functionInstance中params设置值

                    //Object[] params = new Object[length];
                    pushInt(subEss.length);
                    debug("ANEWARRAY [" + subEss.length + "]");
                    mv.newArray(Type.getType(Object.class));
                    debug("ASTORE 4");

                    mv.storeLocal(localIdxParams);

                    //执行N次 params[i] = es.getValue(ctx,factory)
                    for(int i = 0; i < subEss.length; i++) {
                        debug("ALOAD 4");
                        mv.visitVarInsn(ALOAD, 4);
                        pushInt(i);

                        //es.getValue(ctx,factory), 其中es使用位置字段代替
                        generateEsGetValue(i);

                        //params[i]=value
                        arrayStore(Object.class);
                    }
                }
                //没有params参数值，则直接设置为null
                else {
                    debug("ACONST_NULL");
                    mv.visitInsn(ACONST_NULL);

                    debug("ASTORE 4");
                    mv.storeLocal(localIdxParams);
                }

                //生成相应的获取实际functionInstance的方法
                if(variableFactory.isIndexedFactory() && variableFactory.isTarget(name)) {
                    generateLoadVariableByIdx(variableFactory.variableIndexOf(name));
                } else {
                    generateLoadVariableByName(name);
                }

                checkCast(FunctionInstance.class);

                //正式调用 functionInstance 的 public Object call(Object ctx, Object thisValue, VariableResolverFactory factory, Object[] parms)
                //相应的functionInstance已经在栈中了,接下来准备相应的参数

                debug("ALOAD 1");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
                debug("ALOAD 2");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_EL_CTX);
                debug("ALOAD 3");
                mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);
                debug("ALOAD 4");
                mv.loadLocal(localIdxParams);

                debug("INVOKEVIRTUAL Function.call");
                mv.invokeVirtual(Type.getType(FunctionInstance.class), org.mvelx.asm.commons.Method.getMethod("Object call(Object, Object, org.mvelx.integration.VariableResolverFactory, Object[])"));

                return ((FunctionInstance) ptr).call(ctx, thisRef, variableFactory, args);
            } else {
                throw new OptimizationFailure("attempt to optimize a method call for a reference that does not point to a method: "
                        + name + " (reference is type: " + (ctx != null ? ctx.getClass().getName() : null) + ")");
            }

            first = false;
        }
        //必要的包装处理
        else if(returnType != null && returnType.isPrimitive()) {
            //noinspection unchecked
            wrapPrimitive(returnType);
        }

        //当前上下文为静态类，即静态方法调用
        boolean classTarget = false;
        Class<?> cls = currType != null ? currType : ((classTarget = ctx instanceof Class) ? (Class<?>) ctx : ctx.getClass());

        currType = null;

        Method m;
        Class[] parameterTypes = null;

        //重新尝试获取最匹配的方法，并且重置相应的参数类型
        if((m = ParseTools.getBestCandidate(argTypes, name, cls, cls.getMethods(), false, classTarget)) != null) {
            parameterTypes = m.getParameterTypes();
        }

        //静态方法，并且还没找到方法，尝试查找Class类上的方法,如getClass等
        if(m == null && classTarget) {
            if((m = ParseTools.getBestCandidate(argTypes, name, cls, Class.class.getMethods(), false)) != null) {
                parameterTypes = m.getParameterTypes();
            }
        }

        //还没有找到，则从实际对象的类型上找，则不是从声明类型中查找
        if(m == null && cls != ctx.getClass() && !(ctx instanceof Class)) {
            cls = ctx.getClass();
            if((m = ParseTools.getBestCandidate(argTypes, name, cls, cls.getMethods(), false, false)) != null) {
                parameterTypes = m.getParameterTypes();
            }
        }

        //重新处理方法的参数信息，以支持泛型调用
        if(subEss != null && m != null && m.isVarArgs() && (subEss.length != parameterTypes.length || !(subEss[subEss.length - 1] instanceof ExecutableAccessor))) {
            // normalize ExecutableStatement for varargs
            //重置实际的各项数据
            ExecutableStatement[] varArgEs = new ExecutableStatement[parameterTypes.length];
            int varArgStart = parameterTypes.length - 1;
            System.arraycopy(subEss, 0, varArgEs, 0, varArgStart);

            //将泛型参数的最后一个翻译为类似 new Object[]{a,b,c}这样的写法，然后，再重新进行编译为执行单元
            String varargsTypeName = parameterTypes[parameterTypes.length - 1].getComponentType().getName();
            String varArgExpr;
            //优化为null的情况
            if("null".equals(tk)) {
                varArgExpr = tk;
            } else {
                //生成new xx.X[]{a,b,c},这里跳过之前的参数位,如 a,b,c,d,e, 方法声明为(a,b,c,...d)，那么这里处理需要从下标3开始
                StringBuilder sb = new StringBuilder("new ").append(varargsTypeName).append("[] {");
                for(int i = varArgStart; i < subTokenList.size(); i++) {
                    sb.append(subTokenList.get(i));
                    if(i < subTokenList.size() - 1) {
                        sb.append(",");
                    }
                }
                varArgExpr = sb.append("}").toString();
            }
            char[] token = varArgExpr.toCharArray();
            varArgEs[varArgStart] = ((ExecutableStatement) ParseTools.subCompileExpression(token, pCtx));
            //重新设定参数集
            subEss = varArgEs;

            //处理声明为(a,...b)，但实际传入为(a)的情况，这种相当于没有传递泛型参数，这里进行补充上参数信息,补充为空数组
            //上面处理的执行单元，这里处理的是实际的参数值信息
            if(preConvertedArgs.length == parameterTypes.length - 1) {
                // empty vararg
                Object[] preConvertedArgsForVarArg = new Object[parameterTypes.length];
                System.arraycopy(preConvertedArgs, 0, preConvertedArgsForVarArg, 0, preConvertedArgs.length);
                preConvertedArgsForVarArg[parameterTypes.length - 1] = Array.newInstance(parameterTypes[parameterTypes.length - 1].getComponentType(), 0);
                preConvertedArgs = preConvertedArgsForVarArg;
            }
        }

        //相应的参数下标数从原字段起始位置开始，可以认为这里不会占用原有的某些字段下标位置
        int inputsOffset = compiledInputs.size();

        if(subEss != null) {
            for(ExecutableStatement e : subEss) {
                //跳过常量，不会为常量生成相应的字段位,并且此部分在执行时是直接pushConstant，不会再调用语句
                //如 a,3,b，会生成2个字段 p1,p2,p1对应a, p2对应b,然后，执行时为 getField(p1),const(3),getField(p2)
                if(e instanceof ExecutableLiteral) {
                    continue;
                }

                compiledInputs.add(e);
            }
        }

        //首次调用，调用方法需要 实例对象，因此加载 实例
        if(first) {
            debug("ALOAD 1 (D) ");
            mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);
        }

        if(m == null) {
            //支持特殊的size调用，如int[].size()这种写法
            if("size".equals(name) && args.length == 0 && cls.isArray()) {
                arrayCheckCast(cls);

                debug("ARRAYLENGTH");
                mv.arrayLength();

                wrapPrimitive(int.class);
                return Array.getLength(ctx);
            }

            //直接出错，throw异常
            StringAppender errorBuild = new StringAppender();

            if(parameterTypes != null) {
                for(int i = 0; i < args.length; i++) {
                    errorBuild.append(parameterTypes[i] != null ? parameterTypes[i].getClass().getName() : null);
                    if(i < args.length - 1) {
                        errorBuild.append(", ");
                    }
                }
            }

            throw new CompileException("unable to resolve method: " + cls.getName() + "."
                    + name + "(" + errorBuild.toString() + ") [arglength=" + args.length + "]", expr, st);
        } else {
            m = ParseTools.getWidenedTarget(m);

            //当前调用参数类型转换处理
            if(subEss != null) {
                ExecutableStatement cExpr;
                for(int i = 0; i < subEss.length; i++) {
                    if((cExpr = subEss[i]).getKnownIngressType() == null) {
                        cExpr.setKnownIngressType(parameterTypes[i]);
                        cExpr.computeTypeConversionRule();
                    }
                    if(!cExpr.isConvertableIngressEgress() && i < args.length) {
                        args[i] = DataConversion.convert(args[i], Varargs.paramTypeVarArgsSafe(parameterTypes, i, m.isVarArgs()));
                    }
                }
            } else {
                /*
                 * Coerce any types if required.
                 */
                for(int i = 0; i < args.length; i++) {
                    args[i] = DataConversion.convert(args[i], Varargs.paramTypeVarArgsSafe(parameterTypes, i, m.isVarArgs()));
                }
            }

            //准备生成相应的代码指令

            //无参方法
            if(m.getParameterTypes().length == 0) {
                val typeMethod = m.getDeclaringClass();
                //静态方法
                if(Modifier.isStatic(m.getModifiers())) {
                    debug("INVOKESTATIC " + m.getName());
                    mv.invokeStatic(Type.getType(typeMethod), org.mvelx.asm.commons.Method.getMethod(m));
                } else {
                    checkCast(typeMethod);

                    if(typeMethod.isInterface()) {
                        debug("INVOKEINTERFACE " + m.getName());
                        mv.invokeInterface(Type.getType(typeMethod), org.mvelx.asm.commons.Method.getMethod(m));
                    } else {
                        debug("INVOKEVIRTUAL " + m.getName());
                        mv.invokeVirtual(Type.getType(typeMethod), org.mvelx.asm.commons.Method.getMethod(m));
                    }
                }

                returnType = m.getReturnType();

                stacksize++;
            }
            //有参方法
            else {
                //强制类型检查
                if(!Modifier.isStatic(m.getModifiers())) {
                    checkCast(cls);
                }

                Class<?> lastParamClass = m.getParameterTypes()[m.getParameterTypes().length - 1];
                //修正指令单元比参数长度少的问题，即泛型参数少1个的问题
                if(m.isVarArgs()) {
                    if(subEss == null || subEss.length == (m.getParameterTypes().length - 1)) {
                        ExecutableStatement[] executableStatements = new ExecutableStatement[m.getParameterTypes().length];
                        if(subEss != null) {
                            System.arraycopy(subEss, 0, executableStatements, 0, subEss.length);
                        }
                        executableStatements[executableStatements.length - 1] = new ExecutableLiteral(Array.newInstance(lastParamClass, 0));
                        subEss = executableStatements;
                    }
                }

                for(int i = 0; subEss != null && i < subEss.length; i++) {
                    //处理常量,常量直接生成常量代码
                    if(subEss[i] instanceof ExecutableLiteral) {
                        ExecutableLiteral literal = (ExecutableLiteral) subEss[i];

                        if(literal.getLiteral() == null) {
                            debug("ICONST_NULL");
                            mv.visitInsn(ACONST_NULL);
                            continue;
                        }
                        //整数
                        else if(parameterTypes[i] == int.class && literal.intOptimized()) {
                            pushInt(literal.getInteger32());
                            continue;
                        }
                        //整数包装类型
                        else if(parameterTypes[i] == int.class && preConvertedArgs[i] instanceof Integer) {
                            pushInt((Integer) preConvertedArgs[i]);
                            continue;
                        }
                        //bool值处理
                        else if(parameterTypes[i] == boolean.class) {
                            boolean bool = DataConversion.convert(literal.getLiteral(), Boolean.class);
                            debug(bool ? "ICONST_1" : "ICONST_0");
                            mv.visitInsn(bool ? ICONST_1 : ICONST_0);
                            continue;
                        } else {
                            Object lit = literal.getLiteral();

                            if(parameterTypes[i] == Object.class) {
                                if(ParseTools.isPrimitiveWrapper(lit.getClass())) {
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
                                } else {
                                    log.warn("不支持的常量类型，声明为object,但不能放入常量池。实际类型:{}", lit.getClass());
                                    throw new OptimizationNotSupported();
                                }
                                continue;
                            }
                            //声明不为object,进行类型转换处理
                            else if(DataConversion.canConvert(parameterTypes[i], lit.getClass())) {
                                Object c = DataConversion.convert(lit, parameterTypes[i]);
                                if(c instanceof Class) {
                                    pushClass((Class) c);
                                } else {
                                    debug("LDC " + lit + " (" + lit.getClass().getName() + ")");

                                    mv.visitLdcInsn(c);
                                    if(ParseTools.isPrimitiveWrapper(parameterTypes[i])) {
                                        wrapPrimitive(lit.getClass());
                                    }
                                }
                                continue;
                            } else {
                                throw new OptimizationNotSupported();
                            }
                        }
                    }

                    //以下进行非常量的参数调用及解析，生成相应的代码

                    //以下生成相应的 ExecutableStatement Object getValue(Object staticContext, VariableResolverFactory factory);的调用代码
                    //因为相应的参数单元已经被放到字段中，因此直接获取字段即可

                    //获取 es
                    generateEsGetValue(inputsOffset);

                    inputsOffset++;

                    //参数转换，已转换为正确的声明类型

                    //参数转换为基本类型
                    if(parameterTypes[i].isPrimitive()) {
                        //以下的代码，如果原值为null，以进行强转，以在运行时产生 NPE 异常
                        //因为是基本类型，因为转换时要先转为包装类型，再解包
                        if(preConvertedArgs[i] == null || !parameterTypes[i].isAssignableFrom(preConvertedArgs[i].getClass())) {
                            generateDataConversionCode(toWrapperClass(parameterTypes[i]));
                        }

                        unwrapPrimitive(parameterTypes[i]);
                    }
                    //非基本类型转换,非基本类型转换时，null直接转换成功
                    else if(preConvertedArgs[i] == null ||
                            (parameterTypes[i] != String.class && !parameterTypes[i].isAssignableFrom(preConvertedArgs[i].getClass()))) {
                        generateDataConversionCode(parameterTypes[i]);

                        //转换之后，再强行类型检查
                        checkCast(parameterTypes[i]);
                    }
                    //参数为字符串，则直接String.valueOf即可
                    // 并且这里的当前参数不会为null，避免了 valueOf为 字符串 "null" 的问题
                    else if(parameterTypes[i] == String.class) {
                        debug("<<<DYNAMIC TYPE OPTIMIZATION STRING>>");
                        mv.invokeStatic(Type.getType(String.class), org.mvelx.asm.commons.Method.getMethod("String valueOf(Object)"));
                    }
                    //其它情况，强制cast,以在类型不正确时直接throw castException
                    else {
                        debug("<<<DYNAMIC TYPING BYPASS>>>");
                        debug("<<<OPT. JUSTIFICATION " + parameterTypes[i] + "=" + preConvertedArgs[i].getClass() + ">>>");

                        checkCast(parameterTypes[i]);
                    }
                }

                //生成实际的方法调用代码

                //静态方法
                if(Modifier.isStatic(m.getModifiers())) {
                    debug("INVOKESTATIC: " + m.getName());
                    mv.invokeStatic(Type.getType(m.getDeclaringClass()), org.mvelx.asm.commons.Method.getMethod(m));
                }
                //非静态方法
                else {
                    val thisM = m;
                    if(m.getDeclaringClass().isInterface() && (m.getDeclaringClass() != cls
                            || (ctx != null && ctx.getClass() != m.getDeclaringClass()))) {
                        debug(() -> "INVOKEINTERFACE: " + Type.getInternalName(thisM.getDeclaringClass()) + "." + thisM.getName());
                        mv.invokeInterface(Type.getType(m.getDeclaringClass()), org.mvelx.asm.commons.Method.getMethod(m));
                    } else {
                        val thisCls = cls;
                        debug(() -> "INVOKEVIRTUAL: " + Type.getInternalName(thisCls) + "." + thisM.getName());
                        mv.invokeVirtual(Type.getType(cls), org.mvelx.asm.commons.Method.getMethod(m));
                    }
                }

                returnType = m.getReturnType();

                stacksize++;
            }

            //实际的调用及返回
            Object o = m.invoke(ctx, Varargs.normalizeArgsForVarArgs(parameterTypes, args, m.isVarArgs()));

            //直接nullHandler
            if(PropertyHandlerFactory.hasNullMethodHandler()) {
                generateNullPropertyHandler(m, NullPropertyHandlerType.METHOD);
                if(o == null) {
                    o = PropertyHandlerFactory.getNullMethodHandler().getProperty(m.getName(), ctx, variableFactory);
                }
            }

            currType = ReflectionUtil.toNonPrimitiveType(m.getReturnType());
            return o;
        }
    }

    /** 生成调用executeStatement的代码 */
    private void generateSubStatementCode(ExecutableStatement stmt) {
        if(stmt instanceof ExecutableAccessor) {
            ExecutableAccessor ea = (ExecutableAccessor) stmt;
            if(ea.getNode().isIdentifier() && !ea.getNode().isDeepProperty()) {
                log.debug("asm生成es代码时，优化，直接生成localVariableByName代码");
                generateLoadVariableByName(ea.getNode().getName());
                return;
            }
        }

        compiledInputs.add(stmt);
        int stmtIdx = compiledInputs.size() - 1;
        generateEsGetValue(stmtIdx);
    }

    /** 构建有参数的构造函数 */
    private void generateInputsConstructor() {
        if(compiledInputs.size() == 0) {
            return;
        }

        debug("\n{SETTING UP MEMBERS...}\n");

        StringAppender constructorSignature = new StringAppender("(");
        int size = compiledInputs.size();

        //生成相应的字段
        for(int i = 0; i < size; i++) {
            debug("ACC_PRIVATE p" + i);
            cw.visitField(ACC_PRIVATE, "p" + i, "Lorg/mvelx/compiler/ExecutableStatement;", null, null).visitEnd();

            constructorSignature.append("Lorg/mvelx/compiler/ExecutableStatement;");
        }
        constructorSignature.append(")V");

        debug("\n{CREATING INJECTION CONSTRUCTOR}\n");

        GeneratorAdapter cv = new GeneratorAdapter(cw.visitMethod(ACC_PUBLIC, "<init>", constructorSignature.toString(), null, null),
                ACC_PUBLIC, "<init>", constructorSignature.toString()
        );
        cv.visitCode();

        //调用父类
        debug("ALOAD 0");
        cv.loadThis();
        debug("INVOKESPECIAL java/lang/Object.<init>");
        cv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        //挨个给字段赋值
        for(int i = 0; i < size; i++) {
            //this.px=xx
            debug("ALOAD 0");
            cv.loadThis();
            debug("ALOAD " + (i + 1));
            cv.loadLocal(i + 1);
            debug("PUTFIELD p" + i);
            cv.putField(Type.getType(className), "p" + i, Type.getType(ExecutableStatement.class));
        }

        debug("RETURN");
        cv.returnValue();
        cv.visitMaxs(0, 0);
        cv.visitEnd();

        debug("}");
    }

    /** 根据当前的值以及具体的子类型的参考类型创建起相应的值创建访问器,这里的type类型为参考类型 */
    private void _getAccessor(Object o, Class type) {
        if(type == null) {
            throw new RuntimeException("必须给出类型信息");
        }

        //当前值为list，通过一个子值访问器的列表来构造一个list的创建访问器
        if(o instanceof List) {
            //以下主要的逻辑代码为
            /*
            List list = new ArrayList<>();
            for(Object item: o) {
                list.add(item)
            }
            //栈上为list
             */
            generateNewInstance(mv, FastList.class);

            for(Object item : (List) o) {
                //以下代码调用 list.add(item)

                //list
                debug("DUP");
                mv.dup();
                //item
                _getAccessor(item, type);
                //op add
                debug("INVOKEINTERFACE java/util/List.add");
                mv.invokeInterface(Type.getType(List.class), org.mvelx.asm.commons.Method.getMethod("boolean add(Object)"));

                //删除返回的数据,因为无意义
                debug("POP");
                mv.pop();
            }

            returnType = List.class;
        } else if(o instanceof Map) {
            generateNewInstance(mv, HashMap.class);

            for(Object item : ((Map) o).keySet()) {
                //以下代码调用 map.put(key,value)

                //map
                debug("DUP");
                mv.dup();
                //key
                _getAccessor(item, type);
                //value
                _getAccessor(((Map) o).get(item), type);
                //op put
                debug("INVOKEINTERFACE java/util/Map.put");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                mv.invokeInterface(Type.getType(Map.class), org.mvelx.asm.commons.Method.getMethod("Object put(Object,Object)"));

                //删除返回数据
                debug("POP");
                mv.pop();
            }

            returnType = Map.class;
        } else if(o instanceof Object[]) {
            Accessor[] a = new Accessor[((Object[]) o).length];

            int dim = 0;

            String nm = type.getName();
            while(nm.charAt(dim) == '[') dim++;

            try{
                pushInt(((Object[]) o).length);
                debug(() -> "ANEWARRAY " + Type.getInternalName(ParseTools.getSubComponentType(type)) + " (" + ((Object[]) o).length + ")");
                mv.newArray(Type.getType(ParseTools.getSubComponentType(type)));

                Class cls = dim > 1 ? ParseTools.findClass(null, ParseTools.repeatChar('[', dim - 1)
                        + "L" + ParseTools.getBaseComponentType(type).getName() + ";", pCtx)
                        : type;

                int i = 0;
                for(Object item : (Object[]) o) {
                    //以下的代码执行 arrays[i] = v;

                    //arrays
                    debug("DUP");
                    mv.dup();
                    //i
                    pushInt(i);
                    //v
                    _getAccessor(item, cls);
                    //op =
                    arrayStore(Object.class);

                    i++;
                }

            } catch(ClassNotFoundException e) {
                throw new RuntimeException("this error should never throw:" + ParseTools.getBaseComponentType(type).getName(), e);
            }
        } else {
            if(type.isArray()) {
                generateLiteralOrExecuteStatement(ParseTools.subCompileExpression(((String) o).toCharArray(), pCtx), ParseTools.getSubComponentType(type), null);
            } else {
                generateLiteralOrExecuteStatement(ParseTools.subCompileExpression(((String) o).toCharArray(), pCtx), null, null);
            }
        }
    }

    /**
     * 生成常量或者是参数单元的代码
     *
     * @param desiredTarget    预期的单元的值类型    如果为null则表示不作强制限定
     * @param knownIngressType 当前单元的实际参数类型  如果为null则表示不作强制限定
     * @return 返回此单元作为入参时的类型信息, 即传入其它调用时的参数类型
     */
    private Class generateLiteralOrExecuteStatement(Object stmt, Class desiredTarget, Class knownIngressType) {
        //常量
        if(stmt instanceof ExecutableLiteral) {
            Object literalValue = ((ExecutableLiteral) stmt).getLiteral();

            //专门处理null值
            if(literalValue == null) {
                mv.visitInsn(ACONST_NULL);
                return null;
            }

            Class type = literalValue.getClass();

            debug("*** type:" + type + ";desired:" + desiredTarget);

            //处理调用如数组时的int类型转换
            if(type == Integer.class && desiredTarget == int.class) {
                pushInt(((ExecutableLiteral) stmt).getInteger32());
                type = int.class;
            }
            //预期类型不相同，将其实际转换之后，生成相应的常量指令
            else if(desiredTarget != null && desiredTarget != type) {
                debug("*** Converting because desiredType(" + desiredTarget.getClass() + ") is not: " + type);


                if(!DataConversion.canConvert(type, desiredTarget)) {
                    throw new CompileException("was expecting type: " + desiredTarget.getName()
                            + "; but found type: " + type.getName(), expr, st);
                }
                pushLiteralWrapped(DataConversion.convert(literalValue, desiredTarget));
            } else {
                pushLiteralWrapped(literalValue);
            }

            return type;
        }
        //执行单元
        else {
            literal = false;

            generateSubStatementCode((ExecutableStatement) stmt);

            //类型以优先传入的声明入参类型为准
            Class type;
            if(knownIngressType == null) {
                type = ((ExecutableStatement) stmt).getKnownEgressType();
            } else {
                type = knownIngressType;
            }

            //类型不相同，则针对基本类型生成相应的解包代码
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

    /** 优化直接集合变量的访问 */
    public AccessorNode optimizeCollection(ParserContext pCtx, Object o, Class type, char[] property, int start, int offset,
                                           Object ctx, Object thisRef, VariableResolverFactory factory) {
        this.expr = property;
        this.cursor = this.start = start;
        this.end = start + offset;
        this.length = offset;

        type = ReflectionUtil.toNonPrimitiveArray(type);
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

            //这里表示如果集合变量后面还有更多的操作，如[1,2,3].length这种，则将当前的访问器和后面
            //如果length仅与start相同，即表示没有不是继续联合访问
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

    /** 优化对象的创建过程，提供对象创建访问器 */
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

        //将构造函数参数信息和后续调用分开
        String[] cnsRes = ParseTools.captureConstructorAndResidual(property, start, offset);
        //这里拿到相应的参数信息
        List<char[]> constructorParams = ParseTools.parseMethodOrConstructor(cnsRes[0].toCharArray());

        try{
            //有相应的参数信息，因此进行有参数构造函数的处理
            if(constructorParams != null) {
                //将相应参数集加入到构造函数集中
                for(char[] constructorParam : constructorParams) {
                    compiledInputs.add((ExecutableStatement) ParseTools.subCompileExpression(constructorParam, pCtx));
                }

                //以下准备生成相应的构建函数代码

                Class cls = ParseTools.findClass(factory, new String(ParseTools.subset(property, 0, ArrayTools.findFirst('(', start, length, property))), pCtx);

                debug("NEW " + Type.getInternalName(cls));
                mv.newInstance(Type.getType(cls));
                debug("DUP");
                mv.dup();

                Object[] parmas = new Object[constructorParams.size()];

                int i = 0;
                for(ExecutableStatement es : compiledInputs) {
                    parmas[i++] = es.getValue(ctx, factory);
                }

                //这里根据参数信息进行构造函数匹配
                Constructor cns = ParseTools.getBestConstructorCandidate(parmas, cls, pCtx.isStrongTyping());

                if(cns == null) {
                    StringBuilder error = new StringBuilder();
                    for(int x = 0; x < parmas.length; x++) {
                        error.append(parmas[x].getClass().getName());
                        if(x + 1 < parmas.length) {
                            error.append(", ");
                        }
                    }

                    throw new CompileException("unable to find constructor: " + cls.getName()
                            + "(" + error.toString() + ")", expr, st);
                }

                this.returnType = cns.getDeclaringClass();

                //准备各个调用构建函数的参数信息
                Class tg;
                for(i = 0; i < constructorParams.size(); i++) {
                    generateEsGetValue(i);

                    val parameterTypeI = cns.getParameterTypes()[i];

                    tg = parameterTypeI.isPrimitive() ? toWrapperClass(parameterTypeI) : parameterTypeI;

                    //处理可能的类型转换
                    if(parmas[i] != null && !parmas[i].getClass().isAssignableFrom(parameterTypeI)) {
                        generateDataConversionCode(tg);

                        //还原基本类型
                        if(parameterTypeI.isPrimitive()) {
                            unwrapPrimitive(parameterTypeI);
                        } else {
                            checkCast(tg);
                        }
                    } else {
                        checkCast(parameterTypeI);
                    }
                }

                debug("INVOKESPECIAL " + Type.getInternalName(cls) + ".<init> : " + Type.getConstructorDescriptor(cns));
                mv.invokeConstructor(Type.getType(cls), org.mvelx.asm.commons.Method.getMethod(cns));

                _finishJIT();

                AccessorNode acc = _initializeAccessor();

                //后续调用
                if(cnsRes.length > 1 && !Strings.isNullOrEmpty(cnsRes[1])) {
                    assert acc != null;
                    return new Union(pCtx, acc, cnsRes[1].toCharArray(), 0, cnsRes[1].length());
                }

                return acc;
            } else {
                Class cls = ParseTools.findClass(factory, new String(property), pCtx);

                //构造无参实例
                generateNewInstance(mv, cls);

                _finishJIT();
                AccessorNode acc = _initializeAccessor();

                //后续调用
                if(cnsRes.length > 1 && !Strings.isNullOrEmpty(cnsRes[1])) {
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

    //---------------------------- 非主要接口方法 start ------------------------------//

    public Object getResultOptPass() {
        return resultValue;
    }

    public Class getEgressType() {
        return returnType;
    }

    public boolean isLiteralOnly() {
        return literal;
    }
    //---------------------------- 非主要接口方法 end ------------------------------//

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

    /** 产生一个新的访问器类名 */
    private static String generateNewAccessorClassName() {
        return "AsmAccessorImpl_" + CLASS_NAME_POSTFIX.getAndIncrement();
    }

    /** 创建新的默认访问器类writer */
    private static ClassWriter createNewAccessorClassWriter(String className) {
        val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        synchronized(Runtime.getRuntime()) {
            writer.visit(OPCODES_VERSION, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, className,
                    null, "java/lang/Object", new String[]{NAMESPACE + "compiler/Accessor"});
        }

        return writer;
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
    private void generateNewInstance(GeneratorAdapter mvWriter, Class clazz) {
        val type = Type.getType(clazz);

        debug(() -> "NEW " + Type.getInternalName(clazz));
        mvWriter.newInstance(type);

        debug("DUP");
        mvWriter.dup();

        debug("INVOKESPECIAL <init>");
        mvWriter.invokeConstructor(type, new org.mvelx.asm.commons.Method("<init>", "()V"));
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
    private void wrapPrimitive(Class cls) {
        if(!cls.isPrimitive()) {
            return;
        }

        if(cls == boolean.class) {
            debug("INVOKESTATIC java/lang/Boolean.valueOf");
            mv.invokeStatic(Type.getType(Boolean.class), org.mvelx.asm.commons.Method.getMethod("Boolean valueOf(boolean)"));
        } else if(cls == int.class) {
            debug("INVOKESTATIC java/lang/Integer.valueOf");
            mv.invokeStatic(Type.getType(Integer.class), org.mvelx.asm.commons.Method.getMethod("Integer valueOf(int)"));
        } else if(cls == float.class) {
            debug("INVOKESTATIC java/lang/Float.valueOf");
            mv.invokeStatic(Type.getType(Float.class), org.mvelx.asm.commons.Method.getMethod("Float valueOf(float)"));
        } else if(cls == double.class) {
            debug("INVOKESTATIC java/lang/Double.valueOf");
            mv.invokeStatic(Type.getType(Double.class), org.mvelx.asm.commons.Method.getMethod("Double valueOf(double)"));
        } else if(cls == short.class) {
            debug("INVOKESTATIC java/lang/Short.valueOf");
            mv.invokeStatic(Type.getType(Short.class), org.mvelx.asm.commons.Method.getMethod("Short valueOf(short)"));
        } else if(cls == long.class) {
            debug("INVOKESTATIC java/lang/Long.valueOf");
            mv.invokeStatic(Type.getType(Long.class), org.mvelx.asm.commons.Method.getMethod("Long valueOf(long)"));
        } else if(cls == byte.class) {
            debug("INVOKESTATIC java/lang/Byte.valueOf");
            mv.invokeStatic(Type.getType(Byte.class), org.mvelx.asm.commons.Method.getMethod("Byte valueOf(byte)"));
        } else if(cls == char.class) {
            debug("INVOKESTATIC java/lang/Character.valueOf");
            mv.invokeStatic(Type.getType(Character.class), org.mvelx.asm.commons.Method.getMethod("Character valueOf(char)"));
        }
    }

    /** 将包装类型重新解包为基本类型 */
    private void unwrapPrimitive(Class cls) {
        if(cls == boolean.class) {
            val type = Type.getType(Boolean.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Boolean.booleanValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("boolean booleanValue()"));
        } else if(cls == int.class) {
            val type = Type.getType(Integer.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Integer.intValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("int intValue()"));
        } else if(cls == float.class) {
            val type = Type.getType(Float.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Float.floatValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("float floatValue()"));
        } else if(cls == double.class) {
            val type = Type.getType(Double.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Double.doubleValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("double doubleValue()"));
        } else if(cls == short.class) {
            val type = Type.getType(Short.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Short.shortValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("short shortValue()"));
        } else if(cls == long.class) {
            val type = Type.getType(Long.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Long.longValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("long longValue()"));
        } else if(cls == byte.class) {
            val type = Type.getType(Byte.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Byte.byteValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("byte byteValue()"));
        } else if(cls == char.class) {
            val type = Type.getType(Character.class);
            checkCast(type);

            debug("INVOKEVIRTUAL java/lang/Character.charValue");
            mv.invokeVirtual(type, org.mvelx.asm.commons.Method.getMethod("char charValue()"));
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
        debug(() -> "LDC " + Type.getType(cls));

        mv.push(Type.getType(cls));
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
        checkCast(Type.getType(cls));
    }

    /** 断言当前对象必须是相应的类型 */
    private void checkCast(Type type) {
        debug(() -> "CHECKCAST " + type.getInternalName());

        mv.checkCast(type);
    }

    /** 对一个数组类型进行类型检查 */
    private void arrayCheckCast(Class cls) {
        Class checkClass = cls;
        if(!cls.getComponentType().isPrimitive()) {
            checkClass = Object[].class;
        }

        checkCast(checkClass);
    }

    /** 生成自定义代码生成器的属性处理 */
    private Object generatePhByteCode4Get(String property, Object ctx, Class handler) {
        PropertyHandler ph = PropertyHandlerFactory.getPropertyHandler(handler);
        if(ph instanceof ProducesByteCode) {
            debug("<<3rd-Party Code Generation>>");
            ((ProducesByteCode) ph).produceByteCodeGet(mv, property, variableFactory);
            return ph.getProperty(property, ctx, variableFactory);
        } else {
            throw new RuntimeException("unable to compileShared: custom accessor does not support producing bytecode: " + ph.getClass());
        }
    }

    /** 生成自定义代码生成器的set属性处理 */
    private void generatePhByteCode4Set(String property, Object ctx, Class handler, Object value) {
        PropertyHandler ph = PropertyHandlerFactory.getPropertyHandler(handler);
        if(ph instanceof ProducesByteCode) {
            debug("<<3rd-Party Code Generation>>");
            ((ProducesByteCode) ph).produceByteCodeSut(mv, property, variableFactory);
            ph.setProperty(property, ctx, variableFactory, value);
        } else {
            throw new RuntimeException("unable to compileShared: custom accessor does not support producing bytecode: " + ph.getClass());
        }
    }

    /** 从变量工厂中通过下标获取相应的值 */
    private void generateLoadVariableByIdx(int pos) {
        debug("ALOAD 3");
        mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);

        debug("PUSH IDX VAL =" + pos);
        pushInt(pos);

        //VariableResolverFactory 中 VariableResolver getIndexedVariableResolver(int index);
        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolverFactory.getIndexedVariableResolver");
        mv.invokeInterface(Type.getType(VariableResolverFactory.class), org.mvelx.asm.commons.Method.getMethod("org.mvelx.integration.VariableResolver getIndexedVariableResolver(int)"));

        //VariableResolver 中 Object getValue();
        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolver.getValue");
        mv.invokeInterface(Type.getType(VariableResolver.class), org.mvelx.asm.commons.Method.getMethod("Object getValue()"));

        returnType = Object.class;
    }

    /** 根据名字从变量工厂中获取相应的数据值 */
    private void generateLoadVariableByName(String name) {
        debug("ALOAD 3");
        mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);

        debug("LDC \"" + name + "\"");
        mv.push(name);

        //VariableResolverFactory 中 VariableResolver getVariableResolver(String name);
        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolverFactory.getVariableResolver");
        mv.invokeInterface(Type.getType(VariableResolverFactory.class), org.mvelx.asm.commons.Method.getMethod("org.mvelx.integration.VariableResolver getVariableResolver(String)"));

        //VariableResolver 中 Object getValue();
        debug("INVOKEINTERFACE " + NAMESPACE + "integration/VariableResolver.getValue");
        mv.invokeInterface(Type.getType(VariableResolver.class), org.mvelx.asm.commons.Method.getMethod("Object getValue()"));

        returnType = Object.class;
    }

    /** 生成null值属性处理的相应代码 */
    private void generateNullPropertyHandler(Member member, NullPropertyHandlerType type) {
        debug("DUP");
        mv.dup();

        Label endLabel = mv.newLabel();

        debug("IFNONNULL : jump");
        mv.ifNonNull(endLabel);

        //原来的值为null,因此直接出栈，丢弃
        debug("POP");
        mv.pop();

        //准备调用 this.nullPropertyHandler.getProperty

        debug("ALOAD 0");
        mv.loadThis();

        if(type == NullPropertyHandlerType.FIELD) {
            this.propertyNullField = true;

            debug("GETFIELD 'nullPropertyHandler'");
            mv.getField(Type.getObjectType(className), "nullPropertyHandler", Type.getType(PropertyHandler.class));
        } else {
            this.methodNullField = true;

            debug("GETFIELD 'nullMethodHandler'");
            mv.getField(Type.getObjectType(className), "nullMethodHandler", Type.getType(PropertyHandler.class));
        }

        //调用方法 Object getProperty(String name, Object contextObj, VariableResolverFactory variableFactory);

        debug("LDC '" + member.getName() + "'");
        mv.push(member.getName());

        debug("ALOAD 1");
        mv.loadLocal(ACCESSOR_LOCAL_IDX_CTX);

        debug("ALOAD 3");
        mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);

        debug("INVOKEINTERFACE PropertyHandler.getProperty");
        mv.invokeInterface(Type.getType(PropertyHandler.class), org.mvelx.asm.commons.Method.getMethod("Object getProperty(String, Object, org.mvelx.integration.VariableResolverFactory)"));

        debug("LABEL:jump");
        mv.visitLabel(endLabel);
    }

    /** 生成获取指定参数下标的字段的代码 */
    private void generateGetEsField(int esParamIdx) {
        debug("ALOAD 0");
        mv.loadThis();

        debug("GETFIELD p" + esParamIdx);
        mv.getField(Type.getType(className), "p" + esParamIdx, Type.getType(ExecutableStatement.class));
    }

    /** 生成通过es获取数据值的代码,相应的es使用字段下标来代替 */
    private void generateEsGetValue(int esIdx) {
        generateGetEsField(esIdx);
        //获取相应的参数信息
        debug("ALOAD 2");
        mv.loadLocal(ACCESSOR_LOCAL_IDX_EL_CTX);
        debug("ALOAD 3");
        mv.loadLocal(ACCESSOR_LOCAL_IDX_VARIABLE_FACTORY);
        debug("INVOKEINTERFACE ExecutableStatement.getValue");
        mv.invokeInterface(Type.getType(ExecutableStatement.class), org.mvelx.asm.commons.Method.getMethod("Object getValue(Object, org.mvelx.integration.VariableResolverFactory)"));
    }

    /** 生成类型转换的代码 */
    private void generateDataConversionCode(Class target) {
        if(target.equals(Object.class)) {
            return;
        }

        pushClass(target);
        debug("INVOKESTATIC DataConversion.convert");
        mv.invokeStatic(Type.getType(DataConversion.class), org.mvelx.asm.commons.Method.getMethod("Object convert(Object, Class)"));
    }

    /** 输出调试信息 */
    private void dumpAdvancedDebugging() {
        if(buildLog == null) {
            return;
        }

        System.out.println("JIT Compiler Dump for: <<" + (expr == null ? null : new String(expr))
                + ">>\n-------------------------------\n");
        System.out.println(buildLog.toString());
        System.out.println("\n<END OF DUMP>\n");
    }

    //---------------------------- 辅助及工具方法 end ------------------------------//

    //---------------------------- 类加载 start ------------------------------//

    private static MvelClassLoader classLoader;

    public static void setMvelClassLoader(MvelClassLoader cl) {
        classLoader = cl;
    }

    public void init() {
        try{
            classLoader = new JitClassLoader(Thread.currentThread().getContextClassLoader());
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
