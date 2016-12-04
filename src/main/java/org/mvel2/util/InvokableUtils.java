/* Created by flym at 12/4/16 */
package org.mvel2.util;

import org.mvel2.compiler.ExecutableStatement;

/**
 * 可执行对象工具类
 *
 * @author flym
 */
public class InvokableUtils {
    /** 调用名+相应的参数信息，返回整个完整的调用名 */
    public static String fullInvokeName(String selfName, ExecutableStatement[] parameters) {
        StringBuilder builder = new StringBuilder(selfName);
        builder.append("(");

        if(parameters != null) {
            for(int i = 0; i < parameters.length; i++) {
                if(i != 0)
                    builder.append(",");
                builder.append(parameters[i].nodeExpr());
            }
        }

        builder.append(")");

        return builder.toString();
    }
}
