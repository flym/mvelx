/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.mvelx.core.method_test.Base;
import org.mvelx.core.util.MvelUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试对方法的访问
 *
 * @author flym
 */
public class MethodTest extends BaseTest {

    private Base createCtx() {
        return new Base();
    }

    private Map<String, Object> createCtxMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("c", "cat");

        return map;
    }

    /** 正常的方法调用 */
    @Test
    public void testMethod() {
        Assert.assertEquals("DOG", MvelUtils.test("foo.bar.name.toUpperCase()", createCtx()));
    }

    /** 正常的方法调用,但调用之间有空格 */
    @Test
    public void testMethodWithSpace() {
        Assert.assertEquals("DOG", MvelUtils.test("foo. bar. name.toUpperCase()", createCtx()));
    }

    /** 方法调用，1个参数 */
    @Test
    public void testMethodOneParam() {
        Assert.assertEquals("FUBAR", MvelUtils.test("foo.toUC( 'fubar' )", createCtx()));
    }

    /** 方法调用，2个参数,并且传递一个上下文参数 */
    @Test
    public void testMethodTwoParam() {
        Assert.assertEquals(true, MvelUtils.test("equalityCheck(c, 'cat')", createCtx(), createCtxMap()));
    }

    /** 传递null的参数值 */
    @Test
    public void testMethodNullParam() {
        Assert.assertEquals(null, MvelUtils.test("readBack(null)", createCtx()));
    }

    /** 调用方法，2个参数，传递了一个null参数值 */
    @Test
    public void testMethodTwoParamAndNull() {
        Assert.assertEquals("nulltest", MvelUtils.test("appendTwoStrings(null, 'test')", createCtx()));
    }

    /** 调用方法，2个参数，中间有空格 */
    @Test
    public void testMethodTwoParamSpace() {
        Assert.assertEquals(true, MvelUtils.test("   equalityCheck(   c  \n  ,   \n   'cat'      )   ", createCtx(), createCtxMap()));
    }
}
