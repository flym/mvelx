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

package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.integration.impl.StackResetResolverFactory;
import org.mvel2.util.ParseTools;

import static org.mvel2.util.ParseTools.findClassImportResolverFactory;

/**
 * 描述一个引入特定类以及指定包的节点
 * 引入包 import abc.*;
 * 引入类 import ab.T2;
 *
 * @author Christopher Brock
 */
public class ImportNode extends ASTNode {
  /** 当前引入的类名(如果是引入类) */
  private Class importClass;
  /** 当前是否是引入包名(或者是静态类) */
  private boolean packageImport;
  private int _offset;

  private static final char[] WC_TEST = new char[]{'.', '*'};

  public ImportNode(char[] expr, int start, int offset, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start;
    this.offset = offset;
    this.pCtx = pCtx;

    //通过最后一个标识来判定是否是包引用或静态引用
    if (ParseTools.endsWith(expr, start, offset, WC_TEST)) {
      //引入包 或者是引入单个类的静态成员
      packageImport = true;
      _offset = (short) ParseTools.findLast(expr, start, offset, '.');
      if (_offset == -1) {
        _offset = 0;
      }
    }
    else {
      String clsName = new String(expr, start, offset);

      ClassLoader classLoader = getClassLoader();

      try {
        this.importClass = Class.forName(clsName, true, classLoader);
      }
      catch (ClassNotFoundException e) {
        //这里可能是引用一个内部类,因此尝试将最后一个.号更换为$,即a.b更换为a$b
        int idx;
        clsName = (clsName.substring(0, idx = clsName.lastIndexOf('.')) + "$" + clsName.substring(idx + 1)).trim();

        try {
          this.importClass = Class.forName(clsName, true, classLoader);
        }
        catch (ClassNotFoundException e2) {
          throw new CompileException("class not found: " + new String(expr), expr, start);
        }
      }
    }
  }


  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //执行过程即找到类变量工厂,然后将其导入

    //普通的类引用,则通过类工厂直接引入此类即可
    if (!packageImport) {
      if (MVEL.COMPILER_OPT_ALLOCATE_TYPE_LITERALS_TO_SHARED_SYMBOL_TABLE) {
        factory.createVariable(importClass.getSimpleName(), importClass);
        return importClass;
      }
      return findClassImportResolverFactory(factory, pCtx).addClass(importClass);
    }

    //添加为包引用
    // if the factory is an ImmutableDefaultFactory it means this import is unused so we can skip it safely
    if (!(factory instanceof ImmutableDefaultFactory)
        && !(factory instanceof StackResetResolverFactory
        && ((StackResetResolverFactory) factory).getDelegate() instanceof ImmutableDefaultFactory)) {
      findClassImportResolverFactory(factory, pCtx).addPackageImport(new String(expr, start, _offset - start));
    }
    return null;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //解释运行与编译运行一致
    return getReducedValueAccelerated(ctx, thisValue, factory);
  }


  /** 获取已引入的类(如果是类引用) */
  public Class getImportClass() {
    return importClass;
  }

  /** 表示是否为包引用 */
  public boolean isPackageImport() {
    return packageImport;
  }

  public void setPackageImport(boolean packageImport) {
    this.packageImport = packageImport;
  }

  /** 获取相引用的包,因为在引入之后,下一步就会使用此包下的类,因此需要将其提前加入到解析上下文中 */
  public String getPackageImport() {
    return new String(expr, start, _offset - start);
  }
}

