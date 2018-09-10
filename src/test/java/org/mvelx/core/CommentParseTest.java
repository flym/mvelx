/* Created by flym at 12/20/16 */
package org.mvelx.core;

import com.google.common.collect.Maps;
import org.mvelx.core.comment_parse_test.Foo;
import org.testng.annotations.Test;

import java.util.Map;

import static org.mvelx.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;

/**
 * 注释解析测试
 *
 * @author flym
 */
public class CommentParseTest {
    /** 在if后面接注释+回车可以正常工作 */
    @Test
    public void testIfWithLnComment() throws Exception {
        test("if(1 == 1) {\n" + "  // Quote & Double-quote seem to break this expression\n" + "}");
    }

    /** 注释后面接 引号无影响 */
    @Test
    public void testIfWithQuoteComment() throws Exception {
        //单引号
        test("if(1 == 1) {\n" + "  // ' seems to break this expression\n" + "}");

        //双引号
        test("if(1 == 1) {\n" + "  // ' seems to break this expression\n" + "}");
    }

    /** 测试正常工作 */
    @Test
    public void testComments() {
        //上面一行是注释
        assertEquals(10, test("// This is a comment\n5 + 5"));

        //执行结束后面接注释
        assertEquals(20, test("10 + 10; // This is a comment"));

        //多行注释
        assertEquals(30,
                test("/* This is a test of\r\n" + "MVEL's support for\r\n" + "multi-line comments\r\n" + "*/\r\n 15 + 15"));

        //多种注释串联
        assertEquals(((10 + 20) * 2) - 10,
                test("/** This is a fun test script **/\r\n" + "a = 10;\r\n" + "/**\r\n"
                        + "* Here is a useful variable\r\n" + "*/\r\n" + "b = 20; // set b to '20'\r\n"
                        + "return ((a + b) * 2) - 10;\r\n" + "// last comment\n"));
    }

    /** 在调用中间使用注释 */
    @Test
    public void testCommentsInProperty() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("foo", new Foo());

        assertEquals("dog", test("foo./*Hey!*/name", map));
    }
}
