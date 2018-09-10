/* Created by flym at 12/4/16 */
package org.mvelx.core;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.mvelx.MVEL;
import org.mvelx.core.base_accessor_test.String2List1;
import org.mvelx.core.base_accessor_test.String2List2;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 针对主要访问器的各项动态感知测试
 * 即相同的表达式在不同的入参下的表现
 *
 * @author flym
 */
@Slf4j
public class BaseAccessorTest {

    @DataProvider
    public Object[][] p4doBaseTest() throws Exception {
        try{
            List<Object[]> objectsList = Lists.newArrayList();
            final int paramLength = 5;
            Object[] objects;

            //字符串下标 VS 集合访问
            objects = new Object[paramLength];
            objects[0] = "a.username[1]";
            objects[1] = ImmutableMap.of("a", new String2List1("abc"));
            objects[2] = ImmutableMap.of("a", new String2List2(Lists.newArrayList("aa", "bb", "cc")));
            objects[3] = 'b';
            objects[4] = "bb";
            objectsList.add(objects);

            //2个不同类型的同一个属性

            return objectsList.toArray(new Object[objectsList.size()][]);
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    @Test(dataProvider = "p4doBaseTest")
    public void doBaseTest(String expr, Map<String, Object> ctx1, Map<String, Object> ctx2, Object expectV1, Object expectV2) {
        Serializable expression = MVEL.compileExpression(expr);
        for(int i = 0; i < 2; i++) {
            Object v1 = MVEL.executeExpression(expression, ctx1);
            Assert.assertEquals(v1, expectV1);
        }

        Object v2 = MVEL.executeExpression(expression, ctx2);
        Assert.assertEquals(v2, expectV2);
    }
}
