/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.mvelx.core.array_test.Base;
import org.mvelx.core.util.MvelUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * 测试对于数组的访问
 *
 * @author flym
 */
public class ArrayTest {

    private Object createCtx() {
        return new Base();
    }

    /** 带null值的集合常量调用 */
    @Test
    public void testConstant() {
        // Map
        assertNull(MvelUtils.test("['test1' : null].test1", null));
        assertNull(MvelUtils.test("['test1' : null].get('test1')", null));
        assertNull(MvelUtils.test("a=['test1' : null];a.test1", null));
        assertNull(MvelUtils.test("a=['test1' : null];a.get('test1')", null));

        // List
        assertNull(MvelUtils.test("[null][0]", null));
        assertNull(MvelUtils.test("[null].get(0)", null));
        assertNull(MvelUtils.test("a=[null];a[0]", null));
        assertNull(MvelUtils.test("a=[null];a.get(0)", null));

        // Array
        assertNull(MvelUtils.test("{null}[0]", null));
        assertNull(MvelUtils.test("a={null};a[0]", null));
    }

    /** 数组长度 */
    @Test
    public void testSizeString() {
        assertEquals(5, MvelUtils.test("stringArray.size()", createCtx()));
    }

    /** 数组长度调用 */
    @Test
    public void testSizeInt() {
        assertEquals(5, MvelUtils.test("intArray.size()", createCtx()));
    }
}
