/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static org.mvelx.core.util.MvelUtils.test;

/**
 * 命名空间测试，即能够访问到默认包下的类信息
 *
 * @author flym
 */
public class NamespaceTest {
    /** 当前空间下静态方法调用 */
    @Test
    public void testStaticMethod() {
        Assert.assertEquals("FooBar", test("java.lang.String.valueOf('FooBar')"));
    }

    /** 当前空间下静态字段调用 */
    @Test
    public void testStaticField() {
        Assert.assertEquals(Integer.MAX_VALUE, test("java.lang.Integer.MAX_VALUE"));
    }

    /** 非全路径类静态字段 */
    @Test
    public void testSimpleStaticField() {
        Assert.assertEquals(Integer.MAX_VALUE, test("Integer.MAX_VALUE"));
    }

    /** 非全路径类静态字段作为相应的参数 */
    @Test
    public void testSimpleStaticFieldParam() {
        Assert.assertEquals(String.valueOf(Integer.MAX_VALUE), test("String.valueOf(Integer.MAX_VALUE)"));
    }

    /** 直接访问空间下的类 */
    @Test
    public void testClass() {
        Assert.assertEquals(ArrayList.class, test("java.util.ArrayList"));
    }
}
