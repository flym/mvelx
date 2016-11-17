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
import org.mvel2.ErrorDetail;
import org.mvel2.ParserContext;
import org.mvel2.PropertyAccessor;
import org.mvel2.compiler.Accessor;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.compiler.PropertyVerifier;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.ArrayTools;
import org.mvel2.util.ErrorUtil;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static java.lang.reflect.Array.newInstance;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.MVEL.analyze;
import static org.mvel2.MVEL.eval;
import static org.mvel2.optimizers.OptimizerFactory.getThreadAccessorOptimizer;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.CompilerTools.getInjectedImports;
import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.ReflectionUtil.toPrimitiveArrayType;

/**
 * 表示一个新建的节点,即使用New进行数据创建的节点
 * @author Christopher Brock
 */
@SuppressWarnings({"ManualArrayCopy"})
public class NewObjectNode extends ASTNode {
  /** 相应的new 处理优化器,在第一次执行时创建 */
  private transient Accessor newObjectOptimizer;
  /** 类型描述符 */
  private TypeDescriptor typeDescr;
  /** 当前类型类名信息 */
  private char[] name;

  private static final Class[] EMPTYCLS = new Class[0];

  public NewObjectNode(TypeDescriptor typeDescr, int fields, ParserContext pCtx) {
    super(pCtx);
    this.typeDescr = typeDescr;
    this.fields = fields;
    this.expr = typeDescr.getExpr();
    this.start = typeDescr.getStart();
    this.offset = typeDescr.getOffset();

    if (offset < expr.length) {
      this.name = subArray(expr, start, start + offset);
    }
    else {
      this.name = expr;
    }

    //编译期处理
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      //之前有引用此类名,则直接使用相应的信息
      if (pCtx != null && pCtx.hasImport(typeDescr.getClassName())) {
        pCtx.setAllowBootstrapBypass(false);
        egressType = pCtx.getImport(typeDescr.getClassName());
      }
      else {
        //尝试初始化
        try {
          egressType = Class.forName(typeDescr.getClassName(), true, getClassLoader());
        }
        catch (ClassNotFoundException e) {
          //初始化失败,则表示相应的类型并不存在,因此添加严重错误
          if (pCtx.isStrongTyping())
            pCtx.addError(new ErrorDetail(expr, start, true, "could not resolve class: " + typeDescr.getClassName()));
          return;
          // do nothing.
        }
      }

      if (egressType != null) {
        rewriteClassReferenceToFQCN(fields);
        //如果是数组,则重新按数组的方式进行处理
        if (typeDescr.isArray()) {
          try {
            egressType = egressType.isPrimitive() ?
                toPrimitiveArrayType(egressType) :
                findClass(null, repeatChar('[', typeDescr.getArrayLength()) + "L" + egressType.getName() + ";", pCtx);
          }
          catch (Exception e) {
            e.printStackTrace();
            // for now, don't handle this.
          }
        }
      }

      if (pCtx != null) {
        //上面的getImport并没有找到相应的类型信息
        if (egressType == null) {
          pCtx.addError(new ErrorDetail(expr, start, true, "could not resolve class: " + typeDescr.getClassName()));
          return;
        }

        //非数组，准备处理相应的构造器以及相应的参数信息
        if (!typeDescr.isArray()) {
          String[] cnsResid = captureContructorAndResidual(expr, start, offset);

          //构建参数
          final List<char[]> constructorParms
              = parseMethodOrConstructor(cnsResid[0].toCharArray());

          //处理构建的参数类型信息,准备查找到最能够满足要求的constructor
          //如果不能找到,则表示相应的语法出了问题
          final Class[] parms = new Class[constructorParms.size()];
          for (int i = 0; i < parms.length; i++) {
            parms[i] = analyze(constructorParms.get(i), pCtx);
          }

          if (getBestConstructorCandidate(parms, egressType, true) == null) {
            if (pCtx.isStrongTyping())
              pCtx.addError(new ErrorDetail(expr, start, pCtx.isStrongTyping(), "could not resolve constructor " + typeDescr.getClassName()
                  + Arrays.toString(parms)));
          }

          //这里表示当前的处理节点其实还需要处理相应的属性数据，这里不仅仅是进行一个new操作，实际上是获取new对象之后的属性或数据信息
          //如 new Abc().efg 就是取efg这个属性的信息，因此这里的实际类型就是 efg属性的类型
          if (cnsResid.length == 2) {
            String residualProperty =
                cnsResid[1].trim();

            if (residualProperty.length() == 0) return;

            this.egressType = new PropertyVerifier(residualProperty, pCtx, egressType).analyze();
          }
        }
      }
    }
  }

  /** 重写为全类型名(暂时没看出有什么用) */
  private void rewriteClassReferenceToFQCN(int fields) {
    String FQCN = egressType.getName();

    if (typeDescr.getClassName().indexOf('.') == -1) {
      int idx = ArrayTools.findFirst('(', 0, name.length, name);

      char[] fqcn = FQCN.toCharArray();

      if (idx == -1) {
        this.name = new char[idx = fqcn.length];
        for (int i = 0; i < idx; i++)
          this.name[i] = fqcn[i];
      }
      else {
        char[] newName = new char[fqcn.length + (name.length - idx)];

        for (int i = 0; i < fqcn.length; i++)
          newName[i] = fqcn[i];

        int i0 = name.length - idx;
        int i1 = fqcn.length;
        for (int i = 0; i < i0; i++)
          newName[i + i1] = name[i + idx];

        this.name = newName;
      }

      this.typeDescr.updateClassName(name, 0, name.length, fields);
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //第一次,开始创建起优化器
    if (newObjectOptimizer == null) {
      //如果之前类型未能成功解析,则重新解析相应的类型信息
      if (egressType == null) {
        /**
         * This means we couldn't resolve the type at the time this AST node was created, which means
         * we have to attempt runtime resolution.
         */

        if (factory != null && factory.isResolveable(typeDescr.getClassName())) {
          try {
            //这里强行获取相应的变量并认为是类信息,后续有classCast判定
            egressType = (Class) factory.getVariableResolver(typeDescr.getClassName()).getValue();
            rewriteClassReferenceToFQCN(COMPILE_IMMEDIATE);

            if (typeDescr.isArray()) {
              try {
                egressType = findClass(factory,
                    repeatChar('[', typeDescr.getArrayLength()) + "L" + egressType.getName() + ";", pCtx);
              }
              catch (Exception e) {
                // for now, don't handle this.
              }
            }

          }
          catch (ClassCastException e) {
            throw new CompileException("cannot construct object: " + typeDescr.getClassName()
                + " is not a class reference", expr, start, e);
          }
        }
      }

      //如果是数组,则使用数组优化器
      if (typeDescr.isArray()) {
        return (newObjectOptimizer = new NewObjectArray(getBaseComponentType(egressType.getComponentType()), typeDescr.getCompiledArraySize()))
            .getValue(ctx, thisValue, factory);
      }

      //不是数组,则由优化器本身创建起new Object优化器
      try {
        AccessorOptimizer optimizer = getThreadAccessorOptimizer();

        ParserContext pCtx = this.pCtx;
        if (pCtx == null) {
          pCtx = new ParserContext();
          pCtx.getParserConfiguration().setAllImports(getInjectedImports(factory));
        }

        newObjectOptimizer = optimizer.optimizeObjectCreation(pCtx, name, 0, name.length, ctx, thisValue, factory);

        /**
         * Check to see if the optimizer actually produced the object during optimization.  If so,
         * we return that value now.
         */
        //如果相应的计算结果已经确定,则直接返回相应的处理结果
        if (optimizer.getResultOptPass() != null) {
          egressType = optimizer.getEgressType();
          return optimizer.getResultOptPass();
        }
      }
      catch (CompileException e) {
        throw ErrorUtil.rewriteIfNeeded(e, expr, start);
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }

    return newObjectOptimizer.getValue(ctx, thisValue, factory);
  }


  /** 以解释运行的方式来解析相应的new 对象的过程 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    try {
      //如果是对象,则仍按照对象的初始化来进行
      if (typeDescr.isArray()) {
        Class cls = findClass(factory, typeDescr.getClassName(), pCtx);

        //以解释运行的方式处理对象构建
        int[] s = new int[typeDescr.getArrayLength()];
        ArraySize[] arraySize = typeDescr.getArraySize();

        for (int i = 0; i < s.length; i++) {
          s[i] = convert(eval(arraySize[i].value, ctx, factory), Integer.class);
        }

        return newInstance(cls, s);
      }
      else {
        String[] cnsRes = captureContructorAndResidual(name, 0, name.length);
        List<char[]> constructorParms = parseMethodOrConstructor(cnsRes[0].toCharArray());

        //这里表示是存在函数或方法调用的
        if (constructorParms != null) {
          //从第一个( 往前找出相应的类型信息
          Class cls = findClass(factory, new String(subset(name, 0, findFirst('(', 0, name.length, name))).trim(), pCtx);

          //参数使用解释模式来进行处理
          Object[] parms = new Object[constructorParms.size()];
          for (int i = 0; i < constructorParms.size(); i++) {
            parms[i] = eval(constructorParms.get(i), ctx, factory);
          }

          //查找到有效的构造函数
          Constructor cns = getBestConstructorCandidate(parms, cls, false);

          if (cns == null)
            throw new CompileException("unable to find constructor for: " + cls.getName(), expr, start);

          //可能存在的参数类型转换
          for (int i = 0; i < parms.length; i++) {
            //noinspection unchecked
            parms[i] = convert(parms[i], cns.getParameterTypes()[i]);
          }

          //因为可能存在new a(xx).b 这样的级联访问,因此继续采用解释模式来获取相应的属性
          if (cnsRes.length > 1) {
            return PropertyAccessor.get(cnsRes[1], cns.newInstance(parms), factory, thisValue, pCtx);
          }
          else {
            return cns.newInstance(parms);
          }
        }
        else {
          Constructor<?> cns = Class.forName(typeDescr.getClassName(), true, pCtx.getParserConfiguration().getClassLoader())
              .getConstructor(EMPTYCLS);

          if (cnsRes.length > 1) {
            return PropertyAccessor.get(cnsRes[1], cns.newInstance(), factory, thisValue, pCtx);
          }
          else {
            return cns.newInstance();
          }
        }
      }
    }
    catch (CompileException e) {
      throw e;
    }
    catch (ClassNotFoundException e) {
      throw new CompileException("unable to resolve class: " + e.getMessage(), expr, start, e);
    }
    catch (NoSuchMethodException e) {
      throw new CompileException("cannot resolve constructor: " + e.getMessage(), expr, start, e);
    }
    catch (Exception e) {
      throw new CompileException("could not instantiate class: " + e.getMessage(), expr, start, e);
    }
  }

  private boolean isPrototypeFunction() {
    return pCtx.getFunctions().containsKey(typeDescr.getClassName());
  }

  private Object createPrototypalObject(Object ctx, Object thisRef, VariableResolverFactory factory) {
    final Function function = pCtx.getFunction(typeDescr.getClassName());
    return function.getReducedValueAccelerated(ctx, thisRef, factory);
  }

  /** 描述一个new Integer[] 创建数组对象的访问器 */
  public static class NewObjectArray implements Accessor, Serializable {
    /** 多维数组的长度信息 */
    private ExecutableStatement[] sizes;
    /** 相应的原类型信息 */
    private Class arrayType;

    public NewObjectArray(Class arrayType, ExecutableStatement[] sizes) {
      this.arrayType = arrayType;
      this.sizes = sizes;
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
      //这里即直接根据长度信息,创建起数组对象即可
      int[] s = new int[sizes.length];
      for (int i = 0; i < s.length; i++) {
        s[i] = convert(sizes[i].getValue(ctx, elCtx, variableFactory), Integer.class);
      }

      return newInstance(arrayType, s);
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
      return null;
    }

    public Class getKnownEgressType() {
      try {
        //里面为数组的构建方式
        return Class.forName("[L" + arrayType.getName() + ";");
      }
      catch (ClassNotFoundException cne) {
        return null;
      }
    }
  }

  public TypeDescriptor getTypeDescr() {
    return typeDescr;
  }

  public Accessor getNewObjectOptimizer() {
    return newObjectOptimizer;
  }
}
