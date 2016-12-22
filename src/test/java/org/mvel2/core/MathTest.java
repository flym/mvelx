/* Created by flym at 12/20/16 */
package org.mvel2.core;

import com.google.common.collect.Maps;
import org.mvel2.MVEL;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.mvel2.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/**
 * 测试各种数学运算
 *
 * @author flym
 */
public class MathTest {

    /** 各种情况应该能够工作 */
    @Test
    public void testWork() {
        //各种常量运算
        double val = ((100d % 3d) * 2d - 1d / 1d + 8d + (5d * 2d));
        assertEquals(val, test("(100 % 3) * 2 - 1 / 1 + 8 + (5 * 2)"));
    }

    /** 测试乘方 */
    @Test
    public void testPowerOf() {
        assertEquals(25, test("5 ** 2"));
    }

    /** 测试取反 */
    @Test
    public void testSignOperator() {
        String expr = "int x = 15; -x";
        assertEquals(-15, test(expr));
    }

    /** 测试使用变量进行数学运算 */
    @Test
    public void testUseVar() {
        String ex = "a = 100d; b = 50d; c = 60d; d = 30d; e = 2d; (a * b) * c / d * e";
        System.out.println("Expression: " + ex);

        Serializable s = MVEL.compileExpression(ex);

        assertEquals((100d * 50d) * 60d / 30d * 2d, MVEL.executeExpression(s, new HashMap(), Maps.newHashMap()));
    }

    /** 测试乘方的优先级,与乘法在一起时的处理 */
    @Test
    public void testPowerOfPre() {
        String expression = "50 + 30 * 80 * 20 ** 3 * 51";
        double val = 50 + 30 * 80 * Math.pow(20, 3) * 51;
        Object result = test(expression);
        assertEquals((int) val, result);
    }

    /** 测试相应的各种优先级 */
    @Test
    public void testPre() {
        String expression = "a+b-c*x/y-z";

        Map<String, Integer> map = new HashMap<>();
        map.put("a", 200);
        map.put("b", 100);
        map.put("c", 150);
        map.put("x", 400);
        map.put("y", 300);
        map.put("z", 75);

        assertEquals((double) 200 + 100 - 150 * 400 / 300 - 75, test(expression, map));
    }

    /** 测试取模正常工作 */
    @Test
    public void testModule() {
        assertEquals(38392 % 2, test("38392 % 2"));
    }

    private static Map<String, Integer> _createFiveMap() {
        Map<String, Integer> map = Maps.newHashMap();
        map.put("five", 5);

        return map;
    }

    /** 测试或操作 */
    @Test
    public void testBitOrOp() {
        //普通运算
        assertEquals(6, test("2|4"));

        //结果再与其它操作数处理
        assertEquals(true, test("(2 | 1) > 0"));
        assertEquals(true, test("(2|1) == 3"));

        //与变量一起运算
        assertEquals(2 | 5, test("2|five", _createFiveMap()));
    }

    /** 测试 与 操作 */
    @Test
    public void testBitAndOp() {
        //普通运算
        assertEquals(2, test("2 & 3"));

        //与变量一起运算
        assertEquals(5 & 3, test("five & 3", _createFiveMap()));
    }

    /** 测试左移 */
    @Test
    public void testShiftLeft() {
        //普通运算
        assertEquals(4, test("2 << 1"));

        //带变量
        assertEquals(5 << 1, test("five << 1", _createFiveMap()));
    }

    /** 测试右移 */
    @Test
    public void testShiftRight() {
        //普通运算
        assertEquals(128, test("256 >> 1"));

        //带变量
        assertEquals(5 >> 1, test("five >> 1", _createFiveMap()));
    }

    /** 测试无符号右移 */
    @Test
    public void testUnsignedShiftRight() {
        //普通运算
        assertEquals(-5 >>> 1, test("-5 >>> 1"));

        //带变量
        assertEquals(-5 >>> 1, test("(five - 10) >>> 1", _createFiveMap()));
    }

    /** 测试右移赋值 */
    @Test
    public void testShiftRightAssign() {
        assertEquals(5 >> 2, test("_zZz = 5; _zZz >>= 2"));
    }

    /** 测试左移赋值 */
    @Test
    public void testShiftLeftAssign() {
        assertEquals(10 << 2, test("_yYy = 10; _yYy <<= 2"));
    }

    /** 测试 异或 操作 */
    @Test
    public void testXOR() {
        //普通操作
        assertEquals(3, test("1 ^ 2"));

        //带变量
        assertEquals(5 ^ 2, test("five ^ 2", _createFiveMap()));
    }

    /** 测试 取反 操作 */
    @Test
    public void testInvert() {
        //普通运算
        assertEquals(~10, test("~10"));
        assertEquals(~(10 + 1), test("~(10 + 1)"));

        //多次异或
        assertEquals(~10 + (1 + ~50), test("~10 + (1 + ~50)"));
    }

    /** 测试 操作符+赋值 运算符 */
    @Test
    public void testOperativeAssignMod() {
        //%=
        int val = 5;
        val %= 2;
        assertEquals(val, test("int val = 5; val %= 2; val"));

        // /=
        val = 10;
        val /= 2;
        assertEquals(val, test("int val = 10; val /= 2; val"));

        //<<=
        val = 5;
        val <<= 2;
        assertEquals(val, test("int val = 5; val <<= 2; val"));

        // >>=
        val = 5;
        val >>= 2;
        assertEquals(val, test("int val = 5; val >>= 2; val"));

        // >>>=
        val = -5;
        val >>>= 2;
        assertEquals(val, test("int val = -5; val >>>= 2; val"));
    }


}
