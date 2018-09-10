/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.mvelx.core.this_reference_test.Base;
import org.mvelx.core.util.MvelUtils;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * 对this变量的引用进行测试
 *
 * @author flym
 */
public class ThisReferenceTest {
    private Base createSelf() {
        return new Base();
    }

    /** 直接访问this 引用 */
    @Test
    public void testThisReference() {
        assertEquals(true, MvelUtils.test("this", createSelf()) instanceof Base);
    }

    /** 调用this引用的属性 */
    @Test
    public void testThisProperty() {
        assertEquals(true, MvelUtils.test("this.funMap", createSelf()) instanceof Map);
    }

    /** this 引用在方法调用中使用 */
    @Test
    public void testThisInMethodCall() {
        assertEquals(101, MvelUtils.test("Integer.parseInt(this.number)", createSelf()));
    }

    /** this 引用在其它构造函数中使用 */
    @Test
    public void testThisInConstructor() {
        assertEquals("101", MvelUtils.test("new String(this.number)", createSelf()));
    }
}