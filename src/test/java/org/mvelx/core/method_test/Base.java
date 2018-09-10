/* Created by flym at 12/2/16 */
package org.mvelx.core.method_test;

/** @author flym */
public class Base {
    public Foo foo = new Foo();

    public boolean equalityCheck(Object a, Object b) {
        return a.equals(b);
    }

    public String readBack(String test) {
        return test;
    }

    public String appendTwoStrings(String a, String b) {
        return a + b;
    }
}
