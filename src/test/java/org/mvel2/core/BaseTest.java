/* Created by flym at 12/26/16 */
package org.mvel2.core;

import org.mvel2.core.util.MvelUtils;
import org.testng.annotations.AfterMethod;

/** @author flym */
public class BaseTest {

    @AfterMethod(alwaysRun = true)
    public void afterTest() {
        MvelUtils.clearCache();
    }
}
