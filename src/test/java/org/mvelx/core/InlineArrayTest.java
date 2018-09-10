/* Created by flym at 12/26/16 */
package org.mvelx.core;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mvelx.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * 内联数组测试
 *
 * @author flym
 */
public class InlineArrayTest {
    /** 测试默认创建为集合的情况 */
    @Test
    public void testListCreate() {
        //字符串集合
        assertTrue(test("[\"test\"]") instanceof List);

        //数字集合
        assertTrue(test("[66]") instanceof List);

        //空集合
        assertTrue(test("[]") instanceof List);
    }

    /** 测试内联数据的长度 */
    @Test
    public void testLength() {
        //集合长度
        assertEquals(1, test("[\"apple\"].size()"));
        assertEquals(2, test("Array.getLength({'foo', 'bar'})"));

        //数组长度
        assertTrue(((Object[]) test("{}")).length == 0);

        //空数组
        assertTrue(((Object[]) test("{    }")).length == 0);
    }

    /** 测试获取值的问题 */
    @Test
    public void testValue() {
        //数组
        assertEquals(0, test("arrayTest = {{1, 2, 3}, {2, 1, 0}}; arrayTest[1][2]"));

        //map
        assertEquals("foo", test("map = [1 : 'foo']; map[1]"));
        assertEquals("sarah", test("map = ['mike':'sarah','tom':'jacquelin']; map['mike']"));

        //级联map
        assertEquals("pear", test("map = ['test' : 'poo', 'foo' : ['c', 'pear']]; map['foo'][1]"));
    }

    /** 测试相应的操作 */
    @Test
    public void testOp() {
        //直接集合相加
        Object result = test("[1,2,3] + [4,5,6]");
        assertTrue(result instanceof List);
        List list = (List) result;
        assertEquals(6, list.size());

        //集合+单个对象
        result = test("[1,2,3] + 4");
        assertTrue(result instanceof List);
        list = (List) result;
        assertEquals(4, list.size());

        //赋值
        assertEquals("foo", test("a = {'f00', 'bar'}; a[0] = 'foo'; a[0]"));

        //contains操作
        assertEquals(false, test("!( [\"X\", \"Y\"] contains \"Y\" )"));

        //内联map对象创建
        Map m = (Map) test("[new String('foo') : new String('bar')]");
        assertEquals("bar", m.get("foo"));

        //for循环判定
        assertEquals(true, test("a = {1,2,3}; foreach (i : a) { if (i == 1) { return true; } }"));
    }
}
