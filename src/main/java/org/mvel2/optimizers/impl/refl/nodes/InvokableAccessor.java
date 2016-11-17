package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;

import java.lang.reflect.Array;

import static org.mvel2.DataConversion.convert;

/** 表示可以被调用访问的访问器，即通过参数进行访问的访问器，主要有构建函数和方法调用 */
public abstract class InvokableAccessor extends BaseAccessor {
  /** 当前方法声明的参数个数 */
  protected int length;
  /** 当前方法所有的参数列表(包括多个可变参数集) */
  protected ExecutableStatement[] parms;
  /** 每个参数的类型信息 */
  protected Class[] parameterTypes;
  /** 表示是否需要进行可变参数处理(默认值false，当失败时转换为true) */
  protected boolean coercionNeeded = false;

  /**
   * 对指定的目标类型参数信息将其转换为正式可用的参数列表(同时对参数进行求值操作)
   *
   * @param elCtx 最开始的this值
   */
  protected Object[] executeAndCoerce(Class[] target, Object elCtx, VariableResolverFactory vars, boolean isVarargs) {
    Object[] values = new Object[length];
    //不是可变参数,则相应的参数个数与声明相一致
    for (int i = 0; i < length && !(isVarargs && i >= length-1); i++) {
      //noinspection unchecked
      //取值并根据相应的类型进行转换
      values[i] = convert(parms[i].getValue(elCtx, vars), target[i]);
    }
    //是可变参数,则最后一个参数的类型根据声明类型来进行确定,并将相应的最后一个参数转换为数组的形式
    if (isVarargs) {
      Class<?> componentType = target[length-1].getComponentType();
      Object vararg;
      //一个参数也没有,则转换为0长度数组
      if (parms == null) {
        vararg = Array.newInstance( componentType, 0 );
      } else {
        //声明相应长度的数组并进行设置相应的值
        vararg = Array.newInstance(componentType, parms.length - length + 1);
        for (int i = length-1; i < parms.length; i++) {
          Array.set(vararg, i - length + 1, convert(parms[i].getValue(elCtx, vars), componentType));
        }
      }
      //最后将数组认为是最后一个参数值
      values[length-1] = vararg;
    }
    return values;
  }

  /** 返回相应的参数类型信息 */
  public Class[] getParameterTypes() {
    return parameterTypes;
  }
}
