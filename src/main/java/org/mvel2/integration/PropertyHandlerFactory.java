/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mvel2.integration;

import java.util.HashMap;
import java.util.Map;

/** 用于描述针对特定类的属性处理器,即采用属性处理器来代替指定的类的属性访问 */
public class PropertyHandlerFactory {
  /** 类型映射 */
  protected static Map<Class, PropertyHandler> propertyHandlerClass =
      new HashMap<Class, PropertyHandler>();

  /** 空属性处理器,指当属性返回值为null时处理 */
  protected static PropertyHandler nullPropertyHandler;
  /** 空方法处理器,指当方法返回值为null时处理 */
  protected static PropertyHandler nullMethodHandler;

  /**
   * 获取指定类的处理器
   * 为保证能获取，在调用前通过hasPropertyHandler进行副作用处理
   */
  public static PropertyHandler getPropertyHandler(Class clazz) {
    return propertyHandlerClass.get(clazz);
  }

  /**
   * 查看是否有相应的类型的属性处理器，并级联查找(如果有父类的，也认为可以处理当前类)
   * 此方法每次会级联查找，因此认为是有副作用的
   */
  public static boolean hasPropertyHandler(Class clazz) {
    if (clazz == null) return false;
    if (!propertyHandlerClass.containsKey(clazz)) {
      Class clazzWalk = clazz;
      do {
        if (clazz != clazzWalk && propertyHandlerClass.containsKey(clazzWalk)) {
          propertyHandlerClass.put(clazz, propertyHandlerClass.get(clazzWalk));
          return true;
        }
        for (Class c : clazzWalk.getInterfaces()) {
          if (propertyHandlerClass.containsKey(c)) {
            propertyHandlerClass.put(clazz, propertyHandlerClass.get(c));
            return true;
          }
        }
      }
      while ((clazzWalk = clazzWalk.getSuperclass()) != null && clazzWalk != Object.class);
      return false;
    }
    else {
      return true;
    }
  }

  /** 注册处理器，同时将相应的父类以及接口均注册上相应的处理器 */
  public static void registerPropertyHandler(Class clazz, PropertyHandler propertyHandler) {
    do {
      propertyHandlerClass.put(clazz, propertyHandler);

      for (Class c : clazz.getInterfaces()) {
        propertyHandlerClass.put(c, propertyHandler);
      }
    }
    while ((clazz = clazz.getSuperclass()) != null && clazz != Object.class);
  }

  public static void setNullPropertyHandler(PropertyHandler handler) {
    nullPropertyHandler = handler;
  }

  /** 是否存在null值处理器 */
  public static boolean hasNullPropertyHandler() {
    return nullPropertyHandler != null;
  }

  /** 返回相应的null值处理器 */
  public static PropertyHandler getNullPropertyHandler() {
    return nullPropertyHandler;
  }

  public static void setNullMethodHandler(PropertyHandler handler) {
    nullMethodHandler = handler;
  }

  public static boolean hasNullMethodHandler() {
    return nullMethodHandler != null;
  }

  public static PropertyHandler getNullMethodHandler() {
    return nullMethodHandler;
  }

  /** 取消之前对某个类型的注册 */
  public static void unregisterPropertyHandler(Class clazz) {
    propertyHandlerClass.remove(clazz);
  }

  /** 取消所有之前的注册 */
  public static void disposeAll() {
    nullMethodHandler = null;
    nullPropertyHandler = null;
    propertyHandlerClass.clear();
  }
}
