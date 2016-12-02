/* Created by flym at 12/2/16 */
package org.mvel2.core.map_test;

import org.mvel2.core.method_test.Bar;

/** @author flym */
public class Foo {
    public org.mvel2.core.method_test.Bar bar = new Bar();

    public String toUC(String s) {
        return s.toUpperCase();
    }

    public String happy() {
        return "happyBar";
    }
}
