/* Created by flym at 12/2/16 */
package org.mvel2.core.map_test;


import java.util.HashMap;
import java.util.Map;

/** @author flym */
public class Base {
    public Map<String, Object> funMap = new HashMap<>();

    public Base() {
        funMap.put("foo", new Foo());
        funMap.put("foo_bar", new Foo());
    }
}
