/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.mvelx.core.util.MvelUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * 对象实例化测试
 *
 * @author flym
 */
public class InstanceTest {

    /** 正常的对象创建 */
    @Test
    public void testCreate() {
        MvelUtils.test("new java.lang.String('foobie')", null);
    }

    /** 对象对象，并且是预期的值 */
    @Test
    public void testObjectCreation() {
        assertEquals(6, MvelUtils.test("new Integer( 6 )", null));
    }

    /** 对象创建并且调用方法 */
    @Test
    public void testCreateAndInvoke() {
        assertEquals("FOOBIE", MvelUtils.test("new String('foobie')  . toUpperCase()", null));
    }

    /** 对象创建并且调用操作符 */
    @Test
    public void testCreateAndOperate() {
        MvelUtils.test("new String() is String", null);
    }

    /** 多个对象同时创建 */
    @Test
    public void testMultiCreate() {
        MvelUtils.test("new java.text.SimpleDateFormat('yyyy').format(new java.util.Date(System.currentTimeMillis()))", null);
    }
}
