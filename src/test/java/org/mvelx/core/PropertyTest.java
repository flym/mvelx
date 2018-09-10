/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.mvelx.core.property_test.Base;
import org.mvelx.core.property_test.Sub;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mvelx.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/**
 * 对属性引用进行测试
 *
 * @author flym
 */
public class PropertyTest {

    private Object createCtx() {
        return new Base();
    }

    /** 单个属性访问 */
    @Test
    public void testSingleProperty() {
        Assert.assertEquals(false, test("fun", createCtx()));
    }

    /** 级联属性访问 */
    @Test
    public void testNestProperty() {
        Assert.assertEquals("dog", test("foo.bar.name", createCtx()));
    }

    /** 单个属性访问，但是是通过getter进行访问 */
    @Test
    public void testUseGetter() {
        Assert.assertEquals("cat", test("DATA", createCtx()));
    }

    /** 通过子类来访问相应的属性 */
    @Test
    public void testUseSub() {
        Map<String, Object> map = new HashMap<>();
        map.put("derived", new Sub());

        Assert.assertEquals("cat", test("derived.data", map));
    }

    /** 直接访问未初始化的属性 */
    @Test
    public void testUnInstance() {
        assertEquals(0, test("sarahl", createCtx()));
    }
}