/* Created by flym at 12/2/16 */
package org.mvelx.core;

import org.testng.annotations.Test;

import static org.mvelx.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/**
 * 对字符串的各项处理测试
 *
 * @author flym
 */
public class StringTest {

    /** 字符串拼接 */
    @Test
    public void testAppend() {
        assertEquals("foobarcar", test("'foo' + 'bar' + 'car'"));
    }

    /** 字符串拼接，带数字 */
    @Test
    public void testAppendWithNum() {
        assertEquals("foobarcar1", test("'foobar' + 'car' + 1"));
    }

    /** 字符串中的转义符双引号 */
    @Test
    public void testEscapeQuote() {
        assertEquals("\"Mike Brock\"", test("\"\\\"Mike Brock\\\"\""));
    }

    /** 正常的转义符 */
    @Test
    public void testEscape() {
        assertEquals("MVEL's Parser is Fast", test("'MVEL\\'s Parser is Fast'"));
    }

    /** 字符串像数组一样访问 */
    @Test
    public void testAsArray() {
        assertEquals('o', test("abc = 'foo'; abc[1]"));
    }
}
