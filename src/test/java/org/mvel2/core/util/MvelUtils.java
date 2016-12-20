/* Created by flym at 12/2/16 */
package org.mvel2.core.util;

import com.google.common.collect.Maps;
import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.Map;

/** @author flym */
public class MvelUtils {

    /** 编译并执行 */
    public static Object test(String expr) {
        return test(expr, null);
    }

    /** 编译并执行 */
    public static Object test(String expr, Object ctx) {
        return test(expr, ctx, Maps.newHashMap());
    }

    /** 编译并执行 */
    public static Object test(String expr, Object ctx, Map<String, Object> ctxMap) {
        return test(expr, ctx, ctxMap, new ParserContext());
    }

    /** 编译并执行，并使用自己的编译上下文 */
    public static Object test(String expr, Object ctx, Map<String, Object> ctxMap, ParserContext parserContext) {
        ExpressionCompiler compiler = new ExpressionCompiler(expr, parserContext);
        CompiledExpression compiled = compiler.compile();
        return compiled.getValue(ctx, new MapVariableResolverFactory(ctxMap));
    }

    /** 对指定的操作执行多次，以保证在不同的执行时结果或流程应该一样 */
    public static void doTwice(Runnable runnable) {
        for(int i = 0; i < 2; i++)
            runnable.run();
    }
}
