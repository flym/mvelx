/* Created by flym at 12/26/16 */
package org.mvel2.optimizers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 描述访问器优化的类型信息 */
@RequiredArgsConstructor
@Getter
public enum AccessorOptimizeType {
    /** 正常调用访问 */
    ACCESS_REGULAR(0),
    /** set类调用 */
    ACCESS_SET(1),
    /** 集合类访问 */
    ACCESS_COLLECTION(2),
    /** 对象创建类调用 */
    ACCESS_OBJ_CREATION(3),

    //
    ;

    /** 相应的值(旧值兼容表示) */
    private final int value;
}
