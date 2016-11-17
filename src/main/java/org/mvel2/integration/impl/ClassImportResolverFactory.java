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

package org.mvel2.integration.impl;

import org.mvel2.ParserConfiguration;
import org.mvel2.UnresolveablePropertyException;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;

import java.util.*;

/**
 * 用于保存类名与真实类信息的变量解析工厂,即拿来解析类名信息
 * 其部分功能与parserConfiguration相同，不过两个的作用域不同，
 * 一个用于在执行期处理变量的解析，另一个用于在整个编译期的处理
 */
public class ClassImportResolverFactory extends BaseVariableResolverFactory {
  /** 引用的包名(不一定全是包，也可能是类名,以用于引用类中的字段,枚举等) */
  private Set<String> packageImports;
  /** 用于加载类的类加载器，直接使用于parseConfig */
  private ClassLoader classLoader;
  /** 各种引用数据，类名，方法名，枚举等 */
  private Map<String, Object> imports;
  /** 专门用于类名的引用 */
  private Map<String, Object> dynImports;

  /** 使用相应的解析配置信息和委托工厂进行相应的构建 */
  public ClassImportResolverFactory(ParserConfiguration pCfg, VariableResolverFactory nextFactory, boolean compiled) {
    if (pCfg != null) {
      //仅在非编译期才将相应的引用拉过来,因为其数据还可能在变
      if (!compiled) {
        packageImports = pCfg.getPackageImports();
      }
      classLoader = pCfg.getClassLoader();
      imports = Collections.unmodifiableMap(pCfg.getImports());
    }
    else {
      classLoader = Thread.currentThread().getContextClassLoader();
    }

    this.nextFactory = nextFactory;
  }

  /**
   * 创建变量，因为当前工厂并不处理普通的变量，因此交由next来处理
   * 如果委托不存在，则尝试创建,使用map变量工厂来处理
   */
  public VariableResolver createVariable(String name, Object value) {
    if (nextFactory == null) {
      nextFactory = new MapVariableResolverFactory(new HashMap());
    }

    return nextFactory.createVariable(name, value);
  }

  public VariableResolver createVariable(String name, Object value, Class type) {
    if (nextFactory == null) {
      nextFactory = new MapVariableResolverFactory(new HashMap());
    }

    return nextFactory.createVariable(name, value);
  }

  /** 直接使用类名引用一个类,即动态期引用,在执行过程中才进行引用 */
  public Class addClass(Class clazz) {
    if (dynImports == null) dynImports = new HashMap<String, Object>();
    dynImports.put(clazz.getSimpleName(), clazz);
    return clazz;
  }

  public boolean isTarget(String name) {
    if (name == null) return false;
    //当前引用由静态编译期引用和动态引用来进行支持
    return (imports != null && imports.containsKey(name)) || (dynImports != null && dynImports.containsKey(name));
  }

  /** 通过import,类名引用，以及包引用来判定指定的变量名是否能被成功解析 */
  public boolean isResolveable(String name) {
    if (name == null) return false;
    if ((imports != null && imports.containsKey(name)) || (dynImports != null && dynImports.containsKey(name))
        || isNextResolveable(name)) {
      return true;
    }
    //尝试从相应的包引用中找到相应的类,如果找到成功,则加入动态引用当中
    else if (packageImports != null) {
      for (String s : packageImports) {
        try {
          addClass(classLoader.loadClass(s + "." + name));
          return true;
        }
        catch (ClassNotFoundException e) {
          // do nothing;
        }
        catch (NoClassDefFoundError e) {
          // do nothing;
        }
      }
    }
    return false;
  }

  @Override
  public VariableResolver getVariableResolver(String name) {
    //由3部分构建,静态引用,动态引用,以及委托工厂
    if (isResolveable(name)) {
      if (imports != null && imports.containsKey(name)) {
        return new SimpleValueResolver(imports.get(name));
      }
      else if (dynImports != null && dynImports.containsKey(name)) {
        return new SimpleValueResolver(dynImports.get(name));
      }
      else if (nextFactory != null) {
        return nextFactory.getVariableResolver(name);
      }
    }

    throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
  }

  public void clear() {
    //   variableResolvers.clear();
  }

  /**
   * 此处为获取到所有已加载的东西，并不单指类
   * 可以理解为相应的方法定义存在问题
   */
  public Map<String, Object> getImportedClasses() {
    return imports;
  }

  /** 添加包引用 */
  public void addPackageImport(String packageName) {
    if (packageImports == null) packageImports = new HashSet<String>();
    packageImports.add(packageName);
  }

  @Override
  public Set<String> getKnownVariables() {
    return nextFactory == null ? new HashSet(0) : nextFactory.getKnownVariables();
  }
}
