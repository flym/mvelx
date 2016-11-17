package org.mvel2.integration;

import java.util.LinkedList;
import java.util.List;

/** 全局使用的监听器工厂，用于维护全局的set和get监听器 */
public class GlobalListenerFactory {
  /** 相应的get类监听器 */
  private static List<Listener> propertyGetListeners;
  /** 相应的set类监听器 */
  private static List<Listener> propertySetListeners;

  public static boolean hasGetListeners() {
    return propertyGetListeners != null && !propertyGetListeners.isEmpty();
  }

  public static boolean hasSetListeners() {
    return propertySetListeners != null && !propertySetListeners.isEmpty();
  }

  /** 注册相应的get调用时监听器 */
  public static boolean registerGetListener(Listener getListener) {
    if (propertyGetListeners == null) propertyGetListeners = new LinkedList<Listener>();
    return propertyGetListeners.add(getListener);
  }

  public static boolean registerSetListener(Listener getListener) {
    if (propertySetListeners == null) propertySetListeners = new LinkedList<Listener>();
    return propertySetListeners.add(getListener);
  }


  /** 通知相应的get访问调用 */
  public static void notifyGetListeners(Object target, String name, VariableResolverFactory variableFactory) {
    if (propertyGetListeners != null) {
      for (Listener l : propertyGetListeners) {
        l.onEvent(target, name, variableFactory, null);
      }
    }
  }

  /** 通知相应的set访问调用 */
  public static void notifySetListeners(Object target, String name, VariableResolverFactory variableFactory, Object value) {
    if (propertySetListeners != null) {
      for (Listener l : propertySetListeners) {
        l.onEvent(target, name, variableFactory, value);
      }
    }
  }

  public static void disposeAll() {
    propertyGetListeners = null;
    propertySetListeners = null;
  }
}
