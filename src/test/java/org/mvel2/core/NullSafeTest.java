/* Created by flym at 12/20/16 */
package org.mvel2.core;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.mvel2.ParserContext;
import org.mvel2.core.nullsafe_test.Order;
import org.mvel2.core.nullsafe_test.Trade;
import org.mvel2.core.nullsafe_test.User;
import org.mvel2.core.util.MvelUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * 针对nullsafe的测试，支持通过上下文处理相应的nullSafe
 *
 * @author flym
 */
@Slf4j
public class NullSafeTest {
    private static Map<String, User> createUser() {
        val map = Maps.<String, User>newHashMap();
        map.put("u", new User());
        return map;
    }

    /** 在有相应的nullSafe时，获取数据能够正常处理 */
    @Test
    public void testNullSafe() {
        val ctx = createUser();
        //正常的带?的处理方式
        String expr = "u.?level.?level";
        MvelUtils.doTwice(() -> {
            Object obj = MvelUtils.test(expr, ctx, null);
            Assert.assertNull(obj);
        });

        //通过上下文进行传递处理
        String expr2 = "u.level.level";
        MvelUtils.doTwice(() -> {
            ParserContext parserContext = new ParserContext();
            parserContext.getParserConfiguration().setNullSafe(true);

            Object obj = MvelUtils.test(expr2, ctx, null, parserContext);
            Assert.assertNull(obj);
        });

        //计算复杂的表达式
        String expr3 = "var v = 0;for(ord : t.orders){v += ord.num;} return v; ";
        MvelUtils.doTwice(() -> {
            ParserContext parserContext = new ParserContext();
            parserContext.getParserConfiguration().setNullSafe(true);

            Map<String, Object> ctxMap = Maps.newHashMap();
            Trade trade = new Trade(Lists.newArrayList(new Order(2), new Order(3)));
            ctxMap.put("t", trade);

            Object obj = MvelUtils.test(expr3, ctxMap, ctxMap, parserContext);
            Assert.assertEquals(obj, 5);
        });
    }

    /** 在没有nullSafe时，进行处理应该会报相应的异常信息 */
    @Test
    public void testNoNullSafe() {
        try{
            val ctx = createUser();
            String expr = "u.level.level";
            Object obj = MvelUtils.test(expr, ctx);
            log.debug("result:{}", obj);
        } catch(Exception e) {
            log.debug("期望的异常:{}", e.getMessage(), e);
            return;
        }

        throw new RuntimeException("相应的执行流程并没有throw相应的异常");
    }
}
