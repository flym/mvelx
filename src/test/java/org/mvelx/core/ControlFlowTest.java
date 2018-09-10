package org.mvelx.core;

import com.google.common.collect.Maps;
import org.testng.annotations.Test;

import static org.mvelx.core.util.MvelUtils.test;
import static org.testng.Assert.assertEquals;


public class ControlFlowTest {

    /** 对if进行测试 */
    @Test
    public void testIf() {
        //正常调用
        test("if (true) { System.out.println(\"test!\") }  \n");

        //进入到if判定
        assertEquals(10, test("if (5 > 4) { return 10; } else { return 5; }"));

        //进入到else
        assertEquals(10, test("if (5 < 4) { return 5; } else { return 10; }"));

        //if里面判定直接为常量值
        assertEquals(true, test("if (false) { return false; } else { return true; }"));

        //多个if elseif
        assertEquals(true, test("if (false) { return false; } else if(100 < 50) { return false; } else if (10 > 5) return true;"));

        //空的if方法体
        assertEquals(5, test("a=5;if(a==5){};return a;"));

        //单行方法体
        assertEquals("foo", test("if (false) 'bar'; else 'foo';"));
    }

    /** 对for循环进行测试 */
    @Test
    public void testFor() {
        //基本循环
        String ex = "String str = ''; for(i=0;i<6;i++) { str += i }; str";
        assertEquals("012345", test(ex));

        //变量赋值操作
        String str = "int height = 100; int j = 0; for (i = 0; i < height; i++) {j++ }; return j;";
        assertEquals(100, test(str, Maps.newHashMap(), Maps.newHashMap()));

        //空循环
        assertEquals(10000, test("x = 0; for (; x < 10000; x++) {};x"));
    }

    /** 对while进行测试 */
    @Test
    public void testWhile() {
        //do while循环
        assertEquals(10, test("i = 0; do { i++ } while (i != 10); i"));
    }
}
