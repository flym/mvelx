package org.mvel2.core;

import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.core.assign_test.Base;
import org.mvel2.core.assign_test.Foo;
import org.mvel2.core.assign_test.MockClass;
import org.mvel2.core.util.MvelUtils;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mvel2.MVEL.compileExpression;
import static org.mvel2.MVEL.executeExpression;
import static org.mvel2.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/** 赋值测试 */
public class AssignTest {

    private Object createCtx() {
        return new Base();
    }

    private Map<String, Object> createCtxMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("zero", 0);
        map.put("array", new String[]{"", "blip"});

        return map;
    }

    /** 正常的赋值处理 */
    @Test
    public void testDeepAssignString() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", new Foo());

        assertEquals("crap", test("foo.bar.assignTest = 'crap'", map));
        assertEquals("crap", test("foo.bar.assignTest", map));
    }

    /** 对数字进行赋值 */
    @Test
    public void testDeepAssignInt() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", new Foo());

        ParserContext ctx = new ParserContext();

        ctx.addInput("foo", Foo.class);

        ExpressionCompiler compiler = new ExpressionCompiler("foo.bar.age = 21", ctx);
        CompiledExpression ce = compiler.compile();

        MVEL.executeExpression(ce, map);

        assertEquals(((Foo) map.get("foo")).getBar().getAge(), 21);
    }

    /** 对常量对象进行赋值处理,并进行复杂调用处理 */
    @Test
    public void testAssignLiteralComplex() {
        assertEquals("bar", test("a = 'foo'; b = 'bar'; c = 'jim'; list = {a,b,c}; list[1]"));
    }

    /** 赋值之后进行使用 */
    @Test
    public void testAssignAndUse() {
        assertEquals(true, test("populate(); blahfoo = 'sarah'; blahfoo == 'sarah'", createCtx()));
    }

    /** 赋值之后使用自己进行赋值 */
    @Test
    public void testAssignUseSelf() {
        assertEquals("sarah", test("populate(); blahfoo = barfoo", createCtx()));
    }

    /** 验证常量赋值之后的返回类型 */
    @Test
    public void testAssignReturnType() {
        assertEquals(Integer.class, test("blah = 5").getClass());
    }

    /** 使用计算常量进行赋值 */
    @Test
    public void testAssignLiteral() {
        assertEquals(102, test("a = 100 + 1 + 1"));
    }

    /** 使用变量工厂进行引用并进行数组赋值操作 */
    @Test
    public void testAssignUseCtxMap() {
        assertEquals("blip", test("array[zero] = array[zero+1]; array[zero]", null, createCtxMap()));
    }

    /** 测试赋值之后，相应的变量会用在构造函数中 */
    @Test
    public void testAssignUseToConstructor() {
        assertEquals("foo", test("a = 'foobar'; new String(a.toCharArray(), 0, 3)"));
    }

    /** 测试声明式赋值 */
    @Test
    public void testStaticVarAssignment() {
        assertEquals("1", test("String mikeBrock = 1; mikeBrock"));
    }

    /** 测试使用函数指针赋值一个方法引用 */
    @Test
    public void testFunctionPointer() {
        String ex = "squareRoot = java.lang.Math.sqrt; squareRoot(4)";

        Object o = MvelUtils.test(ex, new HashMap());

        assertEquals(2.0, o);


        assertEquals(2.0, test(ex));
    }

    /** 测试使用了函数指针，并且在赋值中使用此指针来赋值 */
    @Test
    public void testAssignUseFunctionPointer() {
        assertEquals(5.0, test("squareRoot = Math.sqrt; i = squareRoot(25); return i;"));
    }

    /** 测试 ++ 赋值操作 */
    @Test
    public void testIncrementOperator() {
        assertEquals(2, test("x = 1; x++; x"));
    }

    /** 测试 前置++ 赋值操作 */
    @Test
    public void testPreIncrementOperator() {
        assertEquals(2, test("x = 1; ++x"));
    }

    /** 测试 -- 赋值操作 */
    @Test
    public void testDecrementOperator() {
        assertEquals(1, test("x = 2; x--; x"));
    }

    /** 测试 前置-- 赋值操作 */
    @Test
    public void testPreDecrementOperator() {
        assertEquals(1, test("x = 2; --x"));
    }

    /** 测试使用静态类型赋值的操作,并且操作的结果也是相应的类型 */
    @Test
    public void testQualifiedStaticTyping() {
        Object val = test("java.math.BigDecimal a = new java.math.BigDecimal( 10.0 ); java.math.BigDecimal b = new java.math.BigDecimal( 10.0 ); java.math.BigDecimal c = a + b; return c; ");
        assertEquals(new BigDecimal(20), val);
    }

    /** 测试先使用引用之后，再赋值进行各项操作的情况 */
    @Test
    public void testUnQualifiedStaticTyping() {
        CompiledExpression ce = (CompiledExpression) compileExpression("import java.math.BigDecimal; BigDecimal a = new BigDecimal( 10.0 ); BigDecimal b = new BigDecimal( 10.0 ); BigDecimal c = a + b; return c; ");
        assertEquals(new BigDecimal(20), test("import java.math.BigDecimal; BigDecimal a = new BigDecimal( 10.0 ); BigDecimal b = new BigDecimal( 10.0 ); BigDecimal c = a + b; return c; ", new HashMap()));
    }

    /** 测试使用的赋值对象用于map的下标对象的情况 */
    @Test
    public void testAssignUseToMapIndex() {
        assertEquals("bar", test("xx = new java.util.HashMap(); xx.put('foo', 'bar'); prop = 'foo'; xx[prop];"));
    }

    /** 测试直接赋值到对象中的属性的情况 */
    @Test
    public void testAssignToObject() {
        MockClass mock = new MockClass();

        executeExpression(compileExpression("this.values = [0, 1, 2, 3, 4]"), mock);
        assertEquals(5, mock.getValues().size());
    }

}
