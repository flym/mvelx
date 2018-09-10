/* Created by flym at 12/20/16 */
package org.mvelx.core;

import org.mvelx.core.util.MvelUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 支持对正则表达式操作符的测试
 *
 * @author flym
 */
public class RegTest {

    /** 正则操作符能够正常工作 */
    @Test
    public void testWork() throws Exception {
        Assert.assertEquals(Boolean.TRUE, MvelUtils.test("'Hello'.toUpperCase() ~= '[A-Z]{0,5}'"));
        Assert.assertEquals(Boolean.TRUE, MvelUtils.test("1 == 0 || ('Hello'.toUpperCase() ~= '[A-Z]{0,5}')"));
        Assert.assertEquals(Boolean.TRUE, MvelUtils.test("'Hello' ~= '[a-zA-Z]{0,5}'"));
    }

    /** 正则操作符后接括号能够正常工作 */
    @Test
    public void testSurroundedByBrackets() {
        Map<String, Object> map = new HashMap<>();
        map.put("x", "foobie");

        Assert.assertEquals(Boolean.TRUE, MvelUtils.test("x ~= ('f.*')", map));
    }

    /** 测试正则接括号并且返回结果的情况 */
    @Test
    public void testBracketsAndReturnResult() {
        Assert.assertEquals(true, MvelUtils.test("vv=\"Edson\"; !(vv ~= \"Mark\")"));
    }

    /** 测试正则表达式中的 | 符号能够正常工作 */
    @Test
    public void testORWork() {
        //获取map中的一个属性
        Map<String, Object> map = new HashMap<>();
        map.put("os", "windows");
        Assert.assertTrue((Boolean) MvelUtils.test("os ~= 'windows|unix'", map));

        //获取bean中的一个属性
        Assert.assertFalse((Boolean) MvelUtils.test("time ~= 'windows|unix'", new java.util.Date()));
    }

    /** 测试2个表达式直接进行匹配的情况 */
    @Test
    public void testTwoVarMatchWork() {
        Assert.assertEquals(true, MvelUtils.test("$test = 'foo'; $ex = 'f.*'; $test ~= $ex", new HashMap()));
    }
}
