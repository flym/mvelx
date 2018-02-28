/* Created by flym at 12/26/16 */
package org.mvel2.core;

import org.mvel2.core.util.MvelUtils;
import org.mvel2.optimizers.OptimizerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/** @author flym */
public class BaseTest {

    @AfterMethod(alwaysRun = true)
    public void afterTest() {
        MvelUtils.clearCache();
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeTest() {
//        OptimizerFactory.setDefaultOptimizer("ASM");
    }
}
