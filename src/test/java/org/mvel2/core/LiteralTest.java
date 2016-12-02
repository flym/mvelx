/* Created by flym at 12/2/16 */
package org.mvel2.core;

import org.testng.annotations.Test;

import static org.mvel2.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/**
 * 常量计算和测试
 *
 * @author flym
 */
public class LiteralTest {
    /** 常量 true */
    @Test
    public void testTrue() {
        assertEquals(true, test("true"));
    }

    /** 常量 false */
    @Test
    public void testFalse() {
        assertEquals(false, test("false"));
    }

    /** 常量 null */
    @Test
    public void testNull() {
        assertEquals(null, test("null"));
    }

    /** 常量直接操作 */
    @Test
    public void testReduc() {
        assertEquals("foo", test("null or 'foo'"));
    }

    /** 测试直接用静态方法加载常量信息 */
    @Test
    public void testStaticMethodUseLiteral() {
        assertEquals(String.class.getName(), test("String.valueOf(Class.forName('java.lang.String').getName())"));
    }

    /** 测试数字常量的处理 */
    @Test
    public void testNumberReduc() {
        assertEquals(1000, test("10 * 100"));
    }
}
