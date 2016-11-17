package org.mvel2.util;

import org.mvel2.integration.VariableResolverFactory;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

/**
 * 描述一个静态的句柄引用,并且可以直接进行调用的
 * 如方法 ,字段
 * 这里的静态的意思是说方法或字段是static import的,而不是指静态方法或静态字段
 *
 * @author Mike Brock <cbrock@redhat.com>
 */
public interface StaticStub extends Serializable {
  /** 通过指定的对象上下文,this值,解析器工厂,以及相应的参数进行程序调用 */
  public Object call(Object ctx, Object thisCtx, VariableResolverFactory factory, Object[] parameters)
      throws IllegalAccessException, InvocationTargetException;
}
