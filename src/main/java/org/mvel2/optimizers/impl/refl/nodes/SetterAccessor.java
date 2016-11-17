package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.PropertyTools;

import java.lang.reflect.Method;

import static org.mvel2.DataConversion.convert;
import static org.mvel2.util.ParseTools.getBestCandidate;

/** 描述一个setter方法的访问器 */
public class SetterAccessor implements AccessorNode {
  /** 无用字段,因为set方法都返回void,因此不可能继续往下传递 */
  @Deprecated
  private AccessorNode nextNode;

  /** 当前所对应的方法 */
  private final Method method;
  /** 目标参数类型 */
  private Class<?> targetType;
  /** 参数是否是基本类型的 */
  private boolean primitive;

  /** 是否需要可变参数转换,逻辑处理变量 */
  private boolean coercionRequired = false;

  public static final Object[] EMPTY = new Object[0];

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    try {
      //根据是否要进行参数转换进行处理
      if (coercionRequired) {
        return method.invoke(ctx, convert(value, targetType));
      }
      else {
        //如果参数为null,则设置null值.但基本类型需要传递相应的基本类型值,不能是null
        return method.invoke(ctx, value == null && primitive ? PropertyTools.getPrimitiveInitialValue(targetType) : value);
      }
    }
    catch (IllegalArgumentException e) {
      //这里有可能是子类重载了相应的方法,并且相应的类型进行了处理,如使用了其它的定义,这里为重载,不是重写.即有多个同名方法,但参数定义不同
      if (ctx != null && method.getDeclaringClass() != ctx.getClass()) {
        Method o = getBestCandidate(EMPTY, method.getName(), ctx.getClass(), ctx.getClass().getMethods(), true);
        if (o != null) {
          return executeOverrideTarget(o, ctx, value);
        }
      }

      if (!coercionRequired) {
        coercionRequired = true;
        return setValue(ctx, elCtx, variableFactory, value);
      }
      throw new RuntimeException("unable to bind property", e);
    }
    catch (Exception e) {
      throw new RuntimeException("error calling method: " + method.getDeclaringClass().getName() + "." + method.getName(), e);
    }
  }

  /** 此方法为set,因为不支持获取值 */
  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vars) {
    return null;
  }

  /** 使用一个set方法来进行构建相应的访问器 */
  public SetterAccessor(Method method) {
    this.method = method;
    assert method != null;
    primitive = (this.targetType = method.getParameterTypes()[0]).isPrimitive();
  }

  /** 返回相应的set方法 */
  public Method getMethod() {
    return method;
  }

  @Deprecated
  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  @Deprecated
  public AccessorNode getNextNode() {
    return nextNode;
  }

  public String toString() {
    return method.getDeclaringClass().getName() + "." + method.getName();
  }

  public Class getKnownEgressType() {
    return method.getReturnType();
  }

  /** 调用子类重写过的相应方法 */
  private Object executeOverrideTarget(Method o, Object ctx, Object value) {
    try {
      return o.invoke(ctx, convert(value, targetType));
    }
    catch (Exception e2) {
      throw new RuntimeException("unable to invoke method", e2);
    }
  }
}
