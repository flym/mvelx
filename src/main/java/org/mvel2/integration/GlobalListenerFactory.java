package org.mvel2.integration;

import com.google.common.collect.Lists;

import java.util.List;

/** 全局使用的监听器工厂，用于维护全局的set和get监听器 */
public class GlobalListenerFactory {
    /** 相应的get类监听器 */
    private static List<Listener> propertyGetListeners;
    /** 相应的set类监听器 */
    private static List<Listener> propertySetListeners;

    /** 添加一个get类监听器 */
    public void addGetListener(Listener listener) {
        if(propertyGetListeners == null)
            propertyGetListeners = Lists.newArrayList();

        propertyGetListeners.add(listener);
    }

    /** 添加一个set类监听器 */
    public void addSetListener(Listener listener) {
        if(propertySetListeners == null)
            propertySetListeners = Lists.newArrayList();

        propertySetListeners.add(listener);
    }

    public static boolean hasGetListeners() {
        return propertyGetListeners != null && !propertyGetListeners.isEmpty();
    }

    public static boolean hasSetListeners() {
        return propertySetListeners != null && !propertySetListeners.isEmpty();
    }

    /** 通知相应的get访问调用 */
    public static void notifyGetListeners(Object target, String name, VariableResolverFactory variableFactory) {
        if(propertyGetListeners != null) {
            for(Listener l : propertyGetListeners) {
                l.onEvent(target, name, variableFactory, null);
            }
        }
    }

    /** 通知相应的set访问调用 */
    public static void notifySetListeners(Object target, String name, VariableResolverFactory variableFactory, Object value) {
        if(propertySetListeners != null) {
            for(Listener l : propertySetListeners) {
                l.onEvent(target, name, variableFactory, value);
            }
        }
    }
}
