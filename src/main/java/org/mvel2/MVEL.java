/**
 * MVEL 2.0
 * Copyright (C) 2007  MVFLEX/Valhalla Project and the Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvel2;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.mvel2.compiler.CompiledAccExpression;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.integration.Interceptor;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.CachingMapVariableResolverFactory;
import org.mvel2.integration.impl.ClassImportResolverFactory;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.optimizers.impl.refl.nodes.GetterAccessor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static java.lang.Boolean.getBoolean;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.MVELRuntime.execute;
import static org.mvel2.util.ParseTools.optimizeTree;

/**
 * MVEL主类
 * The MVEL convienence class is a collection of static methods that provides a set of easy integration points for
 * MVEL.  The vast majority of MVEL's core functionality can be directly accessed through methods in this class.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MVEL {
    /** 在抛出有cause异常的执行异常时是否throw相应的的cause异常,而不是直接将当前异常进行包装显示 */
    public static boolean INVOKED_METHOD_EXCEPTIONS_BUBBLE = getBoolean("mvel2.invoked_meth_exceptions_bubble");
    /** 是否支持java样式的class常量调用，即通过A.class这种方式来获取相应的class属性 */
    public static boolean COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS = getBoolean("mvel2.compiler.support_java_style_class_literals");
    /**
     * 即是否通过引入一个类之后，在后面的类型匹配中使用简写的类名进行cast等类型处理
     * 如 import java.lang.String;String 这种处理
     */
    public static boolean COMPILER_OPT_ALLOCATE_TYPE_LITERALS_TO_SHARED_SYMBOL_TABLE = getBoolean("mvel2.compiler.allocate_type_literals_to_shared_symbol_table");

    /**
     * 对指定表达式+指定上下文进行语法上的分析，并进行验证，即仅验证语法上是否正确
     * Performs an analysis compileShared, which will populate the ParserContext with type, input and variable information,
     * but will not produce a payload.
     *
     * @param expression - the expression to analyze
     * @param ctx        - the parser context
     */
    public static void analysisCompile(char[] expression, ParserContext ctx) {
        ExpressionCompiler compiler = new ExpressionCompiler(expression, ctx);
        compiler.setVerifyOnly(true);
        compiler.compile();
    }

    /** 对指定表达式+指定上下文进行语法上的分析，并进行验证 */
    public static void analysisCompile(String expression, ParserContext ctx) {
        analysisCompile(expression.toCharArray(), ctx);
    }

    /** 分析一个表达式，进行语法验证分析，编译，并返回最终的处理结果 */
    public static Class analyze(char[] expression, ParserContext ctx) {
        ExpressionCompiler compiler = new ExpressionCompiler(expression, ctx);
        compiler.setVerifyOnly(true);
        compiler.compile();
        return compiler.getReturnType();
    }

    public static Class analyze(String expression, ParserContext ctx) {
        return analyze(expression.toCharArray(), ctx);
    }

    /**
     * 将指定的表达式进行编译，无源文件，无引入，无拦截器
     * Compiles an expression and returns a Serializable object containing the compiled expression.  The returned value
     * can be reused for higher-performance evaluation of the expression.  It is used in a straight forward way:
     * <pre><code>
     * <p/>
     * // Compile the expression
     * Serializable compiled = MVEL.compileExpression("x * 10");
     * <p/>
     * // Create a Map to hold the variables.
     * Map vars = new HashMap();
     * <p/>
     * // Create a factory to envelop the variable map
     * VariableResolverFactory factory = new MapVariableResolverFactory(vars);
     * <p/>
     * int total = 0;
     * for (int i = 0; i < 100; i++) {
     * // Update the 'x' variable.
     * vars.put("x", i);
     * <p/>
     * // Execute the expression against the compiled payload and factory, and add the result to the total variable.
     * total += (Integer) MVEL.executeExpression(compiled, factory);
     * }
     * <p/>
     * // Total should be 49500
     * assert total == 49500;
     * </code></pre>
     * <p/>
     * The above example demonstrates a compiled expression being reused ina tight, closed, loop.  Doing this greatly
     * improves performance as re-parsing of the expression is not required, and the runtime can dynamically compileShared
     * the expression to bytecode of necessary.
     *
     * @param expression A String contaiing the expression to be compiled.
     * @return The cacheable compiled payload.
     */
    public static Serializable compileExpression(String expression) {
        return compileExpression(expression, null, null);
    }

    /**
     * 将指定表达式+指定的import进行编译，无源文件，无拦截器
     * Compiles an expression and returns a Serializable object containing the compiled expression.  This method
     * also accept a Map of imports.  The Map's keys are String's representing the imported, short-form name of the
     * Classes or Methods imported.  An import of a Method is essentially a static import.  This is a substitute for
     * needing to declare <tt>import</tt> statements within the actual script.
     * <p/>
     * <pre><code>
     * Map imports = new HashMap();
     * imports.put("HashMap", java.util.HashMap.class); // import a class
     * imports.put("time", MVEL.getStaticMethod(System.class, "currentTimeMillis", new Class[0])); // import a static method
     * <p/>
     * // Compile the expression
     * Serializable compiled = MVEL.compileExpression("map = new HashMap(); map.put('time', time()); map.time");
     * <p/>
     * // Execute with a blank Map to allow vars to be declared.
     * Long val = (Long) MVEL.executeExpression(compiled, new HashMap());
     * <p/>
     * assert val > 0;
     * </code></pre>
     *
     * @param expression A String contaiing the expression to be compiled.
     * @param imports    A String-Class/String-Method pair Map containing imports for the compiler.
     * @return The cacheable compiled payload.
     */
    public static Serializable compileExpression(String expression, Map<String, Object> imports) {
        return compileExpression(expression, imports, null);
    }

    /**
     * 使用表达式+解析上下文进行编译
     * Compiles an expression, and accepts a {@link ParserContext} instance.  The ParserContext object is the
     * fine-grained configuration object for the MVEL parser and compiler.
     *
     * @param expression A string containing the expression to be compiled.
     * @param ctx        The parser context
     * @return A cacheable compiled payload.
     */
    public static Serializable compileExpression(String expression, ParserContext ctx) {
        return optimizeTree(new ExpressionCompiler(expression, ctx).compile());
    }

    /** 对指定的字符数组+解析上下文进行编译 */
    public static Serializable compileExpression(char[] expression, int start, int offset, ParserContext ctx) {
        ExpressionCompiler c = new ExpressionCompiler(expression, start, offset, ctx);
        return optimizeTree(c._compile());
    }

    /** 对指定的字符串+导入+拦截器+源进行编译 */
    public static Serializable compileExpression(String expression, Map<String, Object> imports, Map<String, Interceptor> interceptors) {
        return compileExpression(expression, new ParserContext(imports, interceptors));
    }

    /** 使用字符数组+上下文进行编译 */
    public static Serializable compileExpression(char[] expression, ParserContext ctx) {
        return optimizeTree(new ExpressionCompiler(expression, ctx).compile());
    }

    /**
     * 使用字符数组+导入+拦截器+源进行编译
     * Compiles an expression and returns a Serializable object containing the compiled
     * expression.
     *
     * @param expression   The expression to be compiled
     * @param imports      Imported classes
     * @param interceptors Map of named interceptors
     * @return The cacheable compiled payload
     */
    public static Serializable compileExpression(char[] expression, Map<String, Object> imports, Map<String, Interceptor> interceptors) {
        return compileExpression(expression, new ParserContext(imports, interceptors));
    }

    /** 使用字符数组进行编译,无导入，无拦截器 */
    public static Serializable compileExpression(char[] expression) {
        return compileExpression(expression, null, null);
    }

    /** 使用字符数组+导入进行编译，无拦截器 */
    public static Serializable compileExpression(char[] expression, Map<String, Object> imports) {
        return compileExpression(expression, imports, null);
    }

    /** 将字符串编译为一个单个获取值的编译表达式 */
    public static Serializable compileGetExpression(String expression) {
        return new CompiledAccExpression(expression.toCharArray(), Object.class, new ParserContext());
    }

    /** 使用字符串+解析上下文编译为单个取值的编译表达式 */
    public static Serializable compileGetExpression(String expression, ParserContext ctx) {
        return new CompiledAccExpression(expression.toCharArray(), Object.class, ctx);
    }

    /** 将字符数组编译为单个取值的访问表达式 */
    public static Serializable compileGetExpression(char[] expression) {
        return new CompiledAccExpression(expression, Object.class, new ParserContext());
    }

    /** 将字符数组+解析上下文编译为单个取值的访问表达式 */
    public static Serializable compileGetExpression(char[] expression, ParserContext ctx) {
        return new CompiledAccExpression(expression, Object.class, ctx);
    }

    /** 将字符串编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(String expression) {
        return new CompiledAccExpression(expression.toCharArray(), Object.class, new ParserContext());
    }

    /** 将字符串+解析上下文编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(String expression, ParserContext ctx) {
        return new CompiledAccExpression(expression.toCharArray(), Object.class, ctx);
    }

    /** 将字符串+入参类型+上下文编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(String expression, Class ingressType, ParserContext ctx) {
        return new CompiledAccExpression(expression.toCharArray(), ingressType, ctx);
    }

    /** 将字符数组编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(char[] expression) {
        return new CompiledAccExpression(expression, Object.class, new ParserContext());
    }

    /** 将字符数组+解析上下文编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(char[] expression, ParserContext ctx) {
        return new CompiledAccExpression(expression, Object.class, ctx);
    }

    /** 将字符数组(指定区间)+解析上下文编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(char[] expression, int start, int offset, ParserContext ctx) {
        return new CompiledAccExpression(expression, start, offset, Object.class, ctx);
    }

    /** 将字符数组+入参类型+上下文编译为单个设置值的访问表达式 */
    public static Serializable compileSetExpression(char[] expression, Class ingressType, ParserContext ctx) {
        return new CompiledAccExpression(expression, ingressType, ctx);
    }

    /** 对之前的set访问表达式进行执行，使用指定上下文+值 */
    public static void executeSetExpression(Serializable compiledSet, Object ctx, Object value) {
        ((CompiledAccExpression) compiledSet).setValue(ctx, ctx, new ImmutableDefaultFactory(), value);
    }

    /** 对之前的set访问表达式进行执行，使用指定上下文，变量工厂+值 */
    public static void executeSetExpression(Serializable compiledSet, Object ctx, VariableResolverFactory vrf, Object value) {
        ((CompiledAccExpression) compiledSet).setValue(ctx, ctx, vrf, value);
    }

    /** 执行之前编译好的表达式,无变量工厂 */
    public static Object executeExpression(Object compiledExpression) {
        return ((ExecutableStatement) compiledExpression).getValue(null, new ImmutableDefaultFactory());
    }

    /**
     * 使用指定上下文+初始map变量工厂执行编译表达式
     * Executes a compiled expression.
     *
     * @param compiledExpression -
     * @param ctx                -
     * @param vars               -
     * @return -
     * @see #compileExpression(String)
     */
    @SuppressWarnings({"unchecked"})
    public static Object executeExpression(final Object compiledExpression, final Object ctx, final Map vars) {
        CachingMapVariableResolverFactory factory = vars != null ? new CachingMapVariableResolverFactory(vars) : null;
        try{
            return ((ExecutableStatement) compiledExpression).getValue(ctx, factory);
        } finally {
            if(factory != null) {
                factory.externalize();
            }
        }
    }

    /** 使用上下文+变量工厂执行编译表达式 */
    public static Object executeExpression(final Object compiledExpression, final Object ctx, final VariableResolverFactory resolverFactory) {
        return ((ExecutableStatement) compiledExpression).getValue(ctx, resolverFactory);
    }

    /**
     * 使用变量工厂执行编译表达式
     * Executes a compiled expression.
     *
     * @param compiledExpression -
     * @param factory            -
     * @return -
     * @see #compileExpression(String)
     */
    public static Object executeExpression(final Object compiledExpression, final VariableResolverFactory factory) {
        return ((ExecutableStatement) compiledExpression).getValue(null, factory);
    }

    /**
     * 使用指定上下文执行编译表达式
     * Executes a compiled expression.
     *
     * @param compiledExpression -
     * @param ctx                -
     * @return -
     * @see #compileExpression(String)
     */
    public static Object executeExpression(final Object compiledExpression, final Object ctx) {
        return ((ExecutableStatement) compiledExpression).getValue(ctx, new ImmutableDefaultFactory());
    }

    /**
     * 使用初始map变量工厂执行编译表达式
     * Executes a compiled expression.
     *
     * @param compiledExpression -
     * @param vars               -
     * @return -
     * @see #compileExpression(String)
     */
    @SuppressWarnings({"unchecked"})
    public static Object executeExpression(final Object compiledExpression, final Map vars) {
        CachingMapVariableResolverFactory factory = new CachingMapVariableResolverFactory(vars);
        try{
            return ((ExecutableStatement) compiledExpression).getValue(null, factory);
        } finally {
            factory.externalize();
        }
    }

    /**
     * 使用上下文+初始map变量工厂执行编译表达式，将结果转换为指定类型
     * Execute a compiled expression and convert the result to a type
     *
     * @param compiledExpression -
     * @param ctx                -
     * @param vars               -
     * @param toType             -
     * @return -
     */
    @SuppressWarnings({"unchecked"})
    public static <T> T executeExpression(final Object compiledExpression, final Object ctx, final Map vars, Class<T> toType) {
        return convert(executeExpression(compiledExpression, ctx, vars), toType);
    }

    /** 使用指定上下文+变量工厂执行编译表达式，将结果转换为指定类型 */
    public static <T> T executeExpression(final Object compiledExpression, final Object ctx, final VariableResolverFactory vars, Class<T> toType) {
        return convert(executeExpression(compiledExpression, ctx, vars), toType);
    }

    /**
     * 使用初始map变量工厂执行编译表达式，将结果转换为指定类型
     * Execute a compiled expression and convert the result to a type
     *
     * @param compiledExpression -
     * @param vars               -
     * @param toType             -
     * @return -
     */
    @SuppressWarnings({"unchecked"})
    public static <T> T executeExpression(final Object compiledExpression, Map vars, Class<T> toType) {
        return convert(executeExpression(compiledExpression, vars), toType);
    }

    /**
     * 使用上下文执行编译表达式，将结果转换为指定类型
     * Execute a compiled expression and convert the result to a type.
     *
     * @param compiledExpression -
     * @param ctx                -
     * @param toType             -
     * @return -
     */
    public static <T> T executeExpression(final Object compiledExpression, final Object ctx, Class<T> toType) {
        return convert(executeExpression(compiledExpression, ctx), toType);
    }

    /** 批量执行编译表达式,无上下文，无变量工厂 */
    public static void executeExpression(Iterable<CompiledExpression> compiledExpression) {
        for(CompiledExpression ce : compiledExpression) {
            ce.getValue(null, null);
        }
    }

    /** 使用同一个上下文批量执行编译表达式,意味着多个编译表达式可能修改同一个上下文 */
    public static void executeExpression(Iterable<CompiledExpression> compiledExpression, Object ctx) {
        for(CompiledExpression ce : compiledExpression) {
            ce.getValue(ctx, null);
        }
    }

    /** 使用同一个初始map变量工厂执行多个编译表达式 */
    public static void executeExpression(Iterable<CompiledExpression> compiledExpression, Map vars) {
        CachingMapVariableResolverFactory factory = new CachingMapVariableResolverFactory(vars);
        executeExpression(compiledExpression, null, factory);
        factory.externalize();
    }

    /** 使用初始上下文+初始map变量工厂批量执行多个编译表达式,上下文+变量共享 */
    public static void executeExpression(Iterable<CompiledExpression> compiledExpression, Object ctx, Map vars) {
        CachingMapVariableResolverFactory factory = new CachingMapVariableResolverFactory(vars);
        executeExpression(compiledExpression, ctx, factory);
        factory.externalize();
    }

    /** 使用初始上下文+变量工厂批量执行多个编译表达式 */
    public static void executeExpression(Iterable<CompiledExpression> compiledExpression, Object ctx, VariableResolverFactory vars) {
        for(CompiledExpression ce : compiledExpression) {
            ce.getValue(ctx, vars);
        }
    }

    /** 使用上下文+变量工厂执行多个编译表达式，并将每个编译表达式的结果进行组合返回 */
    public static Object[] executeAllExpression(Serializable[] compiledExpressions, Object ctx, VariableResolverFactory vars) {
        if(compiledExpressions == null) {
            return GetterAccessor.EMPTY;
        }
        Object[] o = new Object[compiledExpressions.length];
        for(int i = 0; i < compiledExpressions.length; i++) {
            o[i] = executeExpression(compiledExpressions[i], ctx, vars);
        }
        return o;
    }

    /** 以debug模式使用上下文+变量工厂执行编译表达式 */
    public static Object executeDebugger(CompiledExpression expression, Object ctx, VariableResolverFactory vars) {
        if(expression.isImportInjectionRequired()) {
            return execute(true, expression, ctx, new ClassImportResolverFactory(expression.getParserConfiguration(), vars, false));
        } else {
            return execute(true, expression, ctx, vars);
        }
    }

    /** 使用预处理器来处理表达式 */
    public static String preprocess(char[] input, PreProcessor[] preprocessors) {
        char[] ex = input;
        for(PreProcessor proc : preprocessors) {
            ex = proc.parse(ex);
        }
        return new String(ex);
    }

    /** 使用预处理器来处理字符串表达式 */
    public static String preprocess(String input, PreProcessor[] preprocessors) {
        return preprocess(input.toCharArray(), preprocessors);
    }

    /**
     * 从指定类上根据方法名+签名获取相应的静态方法对象
     * 将相应的异常转换为runtimeException
     * 工具方法
     * A simple utility method to get a static method from a class with no checked exception.  With throw a
     * RuntimeException if the method is not found or is not a static method.
     *
     * @param cls        The class containing the static method
     * @param methodName The method name
     * @param signature  The signature of the method
     * @return An instance of the Method
     */
    public static Method getStaticMethod(Class cls, String methodName, Class[] signature) {
        try{
            Method m = cls.getMethod(methodName, signature);
            if((m.getModifiers() & Modifier.STATIC) == 0) {
                throw new RuntimeException("method not a static method: " + methodName);
            }
            return m;
        } catch(NoSuchMethodException e) {
            throw new RuntimeException("no such method: " + methodName);
        }
    }
}
