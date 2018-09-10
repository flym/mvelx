/* Created by flym at 12/2/16 */
package org.mvelx.core.map_test;

import org.mvelx.core.method_test.Bar;

/** @author flym */
public class Foo {
    public org.mvelx.core.method_test.Bar bar = new Bar();

    public String toUC(String s) {
        return s.toUpperCase();
    }

    public String happy() {
        return "happyBar";
    }
}
