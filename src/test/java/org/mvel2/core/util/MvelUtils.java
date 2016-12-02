/* Created by flym at 12/2/16 */
package org.mvel2.core.util;

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
        ExpressionCompiler compiler = new ExpressionCompiler(expr, ParserContext.create());
        CompiledExpression compiled = compiler.compile();
        return compiled.getValue(ctx, new MapVariableResolverFactory());
    }

    /** 编译并执行 */
    public static Object test(String expr, Object ctx, Map<String, Object> ctxMap) {
        ExpressionCompiler compiler = new ExpressionCompiler(expr, ParserContext.create());
        CompiledExpression compiled = compiler.compile();
        return compiled.getValue(ctx, new MapVariableResolverFactory(ctxMap));
    }
}
