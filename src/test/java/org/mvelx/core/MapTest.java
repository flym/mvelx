/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.mvelx.core.map_test.Base;
import org.testng.annotations.Test;

import static org.mvelx.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/**
 * 对map的访问测试
 *
 * @author flym
 */
public class MapTest {
    private Object createCtx() {
        return new Base();
    }

    /** 使用中括号访问方式 */
    @Test
    public void testAccess() {
        assertEquals("dog", test("funMap['foo'].bar.name", createCtx()));
    }

    /** 使用点进行访问 */
    @Test
    public void testAccessUseDot() {
        assertEquals("dog", test("funMap.foo.bar.name", createCtx()));
    }

    @Test
    public void testMapAccessWithMethodCall() {
        assertEquals("happyBar", test("funMap['foo'].happy()", createCtx()));
    }

}
