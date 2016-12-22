/* Created by flym at 12/22/16 */
package org.mvel2.core.nullsafe_test;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** @author flym */
@RequiredArgsConstructor
public class Trade {
    @Getter
    private final List<Order> orders;
}
