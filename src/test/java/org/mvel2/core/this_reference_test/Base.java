package org.mvel2.core.this_reference_test;

import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public class Base {
    public String number = "101";
    public Map<String, Object> funMap = new HashMap<>();
}
