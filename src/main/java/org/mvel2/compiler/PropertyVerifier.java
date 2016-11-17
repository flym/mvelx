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

package org.mvel2.compiler;

import org.mvel2.*;
import org.mvel2.ast.Function;
import org.mvel2.optimizers.AbstractOptimizer;
import org.mvel2.optimizers.impl.refl.nodes.WithAccessor;
import org.mvel2.util.ErrorUtil;
import org.mvel2.util.NullType;
import org.mvel2.util.ParseTools;
import org.mvel2.util.StringAppender;

import java.lang.reflect.*;
import java.util.*;

import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.PropertyTools.getFieldOrAccessor;

/**
 * 属性验证器,用于验证相应的属性与指定的类型之间是否兼容
 * 一般情况下，仅在强类型处理下才有相应的作用，否则一般都返回Object，表示通用的类型调用
 * This verifier is used by the compiler to enforce rules such as type strictness.  It is, as side-effect, also
 * responsible for extracting type information.
 *
 * @author Mike Brock
 * @author Dhanji Prasanna
 */
public class PropertyVerifier extends AbstractOptimizer {
  private static final int DONE = -1;
  /** 标记位,表示属性访问 */
  private static final int NORM = 0;
  /** 标记位,方法调用 */
  private static final int METH = 1;
  /** 标记位,数组操作,对应[ */
  private static final int COL = 2;
  /** 标记位,with操作,对应.{ */
  private static final int WITH = 3;

  /** 无用属性 */
  @Deprecated
  private List<String> inputs = new LinkedList<String>();
  /** 逻辑处理,当前是否在首次处理.即在处理上刚处理到第1个节点 */
  private boolean first = false;
  /** 是否是类常量 */
  private boolean classLiteral = false;
  /** 是否需要外部参与解析(如变量工厂处理),即自己本身并不能直接分析出相应的类型信息 */
  private boolean resolvedExternally;
  /** 当前属性是否是方法调用 */
  private boolean methodCall = false;
  /** 是否有多层属性调用,如a.b.c */
  private boolean deepProperty = false;
  /** 是否是全名调用,即静态方法 */
  private boolean fqcn = false;

  /** 当前处理的字段或方法上的泛型参数 */
  private Map<String, Type> paramTypes;

  /** 当前上下文类型信息,即表示当前验证的表达式的类型,即使用哪个root类型作为起始点.可能为null */
  private Class ctx = null;

  public PropertyVerifier(char[] property, ParserContext parserContext) {
    this.length = end = (this.expr = property).length;
    this.pCtx = parserContext;
  }

  public PropertyVerifier(char[] property, int start, int offset, ParserContext parserContext) {
    this.expr = property;
    this.start = start;
    this.length = offset;
    this.end = start + offset;

    this.pCtx = parserContext;
  }

  public PropertyVerifier(String property, ParserContext parserContext) {
    this.length = end = (this.expr = property.toCharArray()).length;
    this.pCtx = parserContext;
  }

  public PropertyVerifier(String property, ParserContext parserContext, Class root) {
    this.end = this.length = (this.expr = property.toCharArray()).length;

    if (property.length() > 0 && property.charAt(0) == '.') {
      this.cursor = this.st = this.start = 1;
    }

    this.pCtx = parserContext;
    this.ctx = root;
  }

  @Deprecated
  public List<String> getInputs() {
    return inputs;
  }

  @Deprecated
  public void setInputs(List<String> inputs) {
    this.inputs = inputs;
  }

  /**
   * 分析当前的值，并且返回相应的返回类型
   * Analyze the statement and return the known egress type.
   *
   * @return known engress type
   */
  public Class analyze() {
    cursor = start;
    resolvedExternally = true;
    //根class不存在,则尝试使用object来作为root,仅作标识使用
    if (ctx == null) {
      ctx = Object.class;
      first = true;
    }

    while (cursor < end) {
      classLiteral = false;
      switch (nextSubToken()) {
        //属性,返回属性类型
        case NORM:
          ctx = getBeanProperty(ctx, capture());
          break;
        //方法,返回方法类型
        case METH:
          ctx = getMethod(ctx, capture());
          break;
        //数组,返回数组内数据类型
        case COL:
          ctx = getCollectionProperty(ctx, capture());
          break;
        //with,返回当前类型
        case WITH:
          ctx = getWithProperty(ctx);
          break;

        //已处理完
        case DONE:
          break;
      }
      //还没有处理完,即还需要继续处理,则置相应的深度标记
      if (cursor < length && !first) deepProperty = true;

      first = false;
    }
    return ctx;
  }

  /** 记录指定的属性的泛型信息,如果此属性没有泛型信息，则进行创建，同时进行记录 */
  private void recordTypeParmsForProperty(String property) {
    if (pCtx.isStrictTypeEnforcement()) {
      if ((paramTypes = pCtx.getTypeParameters(property)) == null) {
        pCtx.addTypeParameters(property, pCtx.getVarOrInputType(property));
      }
      pCtx.setLastTypeParameters(pCtx.getTypeParametersAsArray(property));
    }
  }

  /**
   * 在相应的当前上下文的类型中获取指定属性的类型信息
   * Process bean property
   *
   * @param ctx      - the ingress type
   * @param property - the property component
   * @return known egress type.
   */
  private Class getBeanProperty(Class ctx, String property) {
    //针对首节点进行特殊处理，以支持特定的属性，如this,或解析上下文中的特定引用
    if (first) {
      //解析上下文中有些变量或入参
      if (pCtx.hasVarOrInput(property)) {
        if (pCtx.isStrictTypeEnforcement()) {
          recordTypeParmsForProperty(property);
        }
        return pCtx.getVarOrInputType(property);
      }
      //有针对此属性的特殊引用，如import x.y.z;等
      else if (pCtx.hasImport(property)) {
        //已解析好，不需要外部重新解析
        resolvedExternally = false;
        return pCtx.getImport(property);
      }
      //如果上下文非强类型，则直接返回object，表示通用的类型信息,即在上下文中并没有声明此类型
      else if (!pCtx.isStrongTyping()) {
        return Object.class;
      }
      //如果上下文中存在this变量，则表示需要变量工厂参与解析，并且认为相应的当前上下文就由this来表示,即第一个属性的类型由this变量来表示
      //同时，这里即表示已经由自己解析好了相应的类型信息，因此并不需要外部处理
      else if (pCtx.hasVarOrInput("this")) {
        if (pCtx.isStrictTypeEnforcement()) {
          recordTypeParmsForProperty("this");
        }
        ctx = pCtx.getVarOrInputType("this");
        resolvedExternally = false;
      }
    }

    st = cursor;
    boolean switchStateReg;

    //在这里，表示是严格的类型调用，但是当前并没有找到相应的类型信息，因此需要继续进行查找

    //尝试从当前上下文类中找到相应的属性或方法
    Member member = ctx != null ? getFieldOrAccessor(ctx, property) : null;

    //支持特殊的.class 属性
    if (MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS) {
      if ("class".equals(property)) {
        return Class.class;
      }
    }

    //当前成员为字段
    if (member instanceof Field) {
      //处理严格泛型调用，则尝试解析泛型信息,即字段声明为 T 类型时，需要从泛型中解析出相应的实际类型
      if (pCtx.isStrictTypeEnforcement()) {
        Field f = ((Field) member);

        //当前字段具有泛型信息
        if (f.getGenericType() != null) {
          //泛型为类似 List<T> 这种类型，则需要记录相应的参数类型
          if (f.getGenericType() instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            pCtx.setLastTypeParameters(pt.getActualTypeArguments());

            //这里的gpt即实际的泛型信息，如List<T> 如果在类中定义为List<String> 就变成了String类型(class类型)，而如果为List<Z> 就变成了Z,而Z又是一种type
            Type[] gpt = pt.getActualTypeArguments();
            //这里的classArgs指申明的泛型信息
            Type[] classArgs = ((Class) pt.getRawType()).getTypeParameters();

            if (gpt.length > 0 && paramTypes == null) paramTypes = new HashMap<String, Type>();
            //设置起相应的泛型信息
            for (int i = 0; i < gpt.length; i++) {
              paramTypes.put(classArgs[i].toString(), gpt[i]);
            }
          }
          //泛型为简单的 T 类型,则尝试从之前解析的泛型中拿到此属性,如X<T>中的T,很有可能此属性T就是没有被解析的，如果没有被解析，则忽略此字段
          else if (f.getGenericType() instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) f.getGenericType();
            Type paramType = paramTypes.remove(tv.getName());
            if (paramType != null && paramType instanceof Class) {
              return (Class) paramType;
            }
          }
        }

        return f.getType();
      }
      //正常情况下，直接返回类字段类型即可
      else {
        return ((Field) member).getType();
      }
    }

    //不是字段，则一定是方法，则尝试从方法中提取相应的返回类型
    if (member != null) {
      return getReturnType(ctx, (Method) member);
    }

    //这里和之前的first逻辑重复，即尝试从解析上下文中获取之前引入的属性信息
    if (pCtx != null && first && pCtx.hasImport(property)) {
      Class<?> importedClass = pCtx.getImport(property);
      if (importedClass != null) return pCtx.getImport(property);
    }

    //这里表示当前上下文中为集合或者是map，并且相应的最近的泛型参数是有信息的，那么尝试从最近的泛型参数类型中获取相应的类型信息
    //集合则从0下标参数类中获取 如果是map，则从相应的值类型中获取泛型信息
    if (pCtx != null && pCtx.getLastTypeParameters() != null && pCtx.getLastTypeParameters().length != 0
        && ((Collection.class.isAssignableFrom(ctx) && !(switchStateReg = false))
        || (Map.class.isAssignableFrom(ctx) && (switchStateReg = true)))) {
      Type parm = pCtx.getLastTypeParameters()[switchStateReg ? 1 : 0];
      pCtx.setLastTypeParameters(null);

      if (parm instanceof ParameterizedType) {
        return Object.class;
      }
      else {
        return (Class) parm;
      }
    }

    //支持特殊的数组类
    if (pCtx != null && "length".equals(property) && ctx.isArray()) {
      return Integer.class;
    }

    //接下来尝试从静态的属性中找到相应的类型信息
    Object tryStaticMethodRef = tryStaticAccess();

    if (tryStaticMethodRef != null) {
      fqcn = true;
      resolvedExternally = false;
      if (tryStaticMethodRef instanceof Class) {
        classLiteral = !(MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS &&
            new String(expr, end - 6, 6).equals(".class"));
        return classLiteral ? (Class) tryStaticMethodRef : Class.class;
      }
      else if (tryStaticMethodRef instanceof Field) {
        try {
          return ((Field) tryStaticMethodRef).get(null).getClass();
        }
        catch (Exception e) {
          throw new CompileException("in verifier: ", expr, start, e);
        }
      }
      else {
        try {
          return ((Method) tryStaticMethodRef).getReturnType();
        }
        catch (Exception e) {
          throw new CompileException("in verifier: ", expr, start, e);
        }
      }
    }

    //尝试认为此属性为一个内部类
    if (ctx != null) {
      try {
        return findClass(variableFactory, ctx.getName() + "$" + property, pCtx);
      }
      catch (ClassNotFoundException cnfe) {
        // fall through.
      }
    }

    //如果运行伪方法调用，则认为此属性实际上是一个方法名
    if (pCtx != null && pCtx.getParserConfiguration() != null ? pCtx.getParserConfiguration().isAllowNakedMethCall() : MVEL.COMPILER_OPT_ALLOW_NAKED_METH_CALL) {
      Class cls = getMethod(ctx, property);
      if (cls != Object.class) {
        return cls;
      }
    }

    if (pCtx.isStrictTypeEnforcement()) {
      throw new CompileException("unqualified type in strict mode for: " + property, expr, tkStart);
    }

    return Object.class;
  }

  /** 从当前上下文类中以及相应的方法中提取相应的返回类型 */
  private Class getReturnType(Class context, Method m) {
    Class declaringClass = m.getDeclaringClass();
    //如果当前上下文中即是相应方法的声明类，则直接返回此方法的泛型返回信息,即因为没有子类重写泛型信息
    if (context == declaringClass) {
      return returnGenericType(m);
    }
    //当前类为相应的方法声明类的子类,因此根据不同的情况进行处理
    Type returnType = m.getGenericReturnType();
    //处理泛型声明的情况,如返回 T类型，但是子类已经确定了T类型的实际类型，因此这里可以更具体判定出T类型是什么具体的类型
    //如 class X1 extends X<String> 而原X中的方法声明为 T类型，那么在X1类中，实际的返回类型就是 String
    if (returnType instanceof TypeVariable) {
      //根据相应的泛型名进行定向查询
      String typeName = ((TypeVariable) returnType).getName();
      Type superType = context.getGenericSuperclass();
      Class superClass = context.getSuperclass();
      while (superClass != null && superClass != declaringClass) {
        superType = superClass.getGenericSuperclass();
        superClass = superClass.getSuperclass();
      }

      //父类为null，理论上不存在
      if (superClass == null) {
        return returnGenericType(m);
      }
      //只有父类的签名为参数化类型，才有相应的参数信息，因此这里进行判定
      if (superType instanceof ParameterizedType) {
        TypeVariable[] typeParams = superClass.getTypeParameters();
        int typePos = -1;
        for (int i = 0; i < typeParams.length; i++) {
          if (typeParams[i].getName().equals(typeName)) {
            typePos = i;
            break;
          }
        }
        //这里表示父类的泛型签名与当前方法签名并不相同，即表示实际上此方法的签名信息并不是由相应的父类产生的，可能直接为方法本身的泛型信息，如<T> T的这种
        if (typePos < 0) {
          return returnGenericType(m);
        }
        //这里找到相应的泛型对应信息，则尝试直接从相应的类型信息中进行提取
        Type actualType = ((ParameterizedType) superType).getActualTypeArguments()[typePos];
        return actualType instanceof Class ? (Class) actualType : returnGenericType(m);
      }
    }

    //此方法的返回类型为泛型参数类型，直接使用
    return returnGenericType(m);
  }

  /** 尝试记录最新的泛型参数信息的泛型信息,主要记录相应的类型,建立想定义名和实际泛型之间的关系 */
  private void recordParametricReturnedType(Type parametricReturnType) {
    //push return type parameters onto parser context, only if this is a parametric type
    if (parametricReturnType instanceof ParameterizedType) {
      pCtx.setLastTypeParameters(((ParameterizedType) parametricReturnType).getActualTypeArguments());
      ParameterizedType pt = (ParameterizedType) parametricReturnType;

      Type[] gpt = pt.getActualTypeArguments();
      Type[] classArgs = ((Class) pt.getRawType()).getTypeParameters();

      if (gpt.length > 0 && paramTypes == null) paramTypes = new HashMap<String, Type>();
      for (int i = 0; i < gpt.length; i++) {
        paramTypes.put(classArgs[i].toString(), gpt[i]);
      }
    }
  }

  /** 返回指定方法的返回类型,并尝试处理其的泛型返回信息 */
  private Class<?> returnGenericType(Method m) {
    Type parametricReturnType = m.getGenericReturnType();
    recordParametricReturnedType(parametricReturnType);
    String returnTypeArg = parametricReturnType.toString();

    //push return type parameters onto parser context, only if this is a parametric type
    if (parametricReturnType instanceof ParameterizedType) {
      pCtx.setLastTypeParameters(((ParameterizedType) parametricReturnType).getActualTypeArguments());
    }

    //这里表示之前可能已经有解析相应的返回类型信息,那么就直接使用之前的解析信息，如之前解析为 List<X> 这次就直接返回List<X>类型
    if (paramTypes != null && paramTypes.containsKey(returnTypeArg)) {
      /**
       * If the paramTypes Map contains the known type, return that type.
       */
      return (Class) paramTypes.get(returnTypeArg);
    }

    //直接返回本身的返回类型
    return m.getReturnType();
  }

  /**
   * 处理访问集合或map时的属性的类型，即list里面的值类型或者是map的value的值类型信息
   * Process collection property
   *
   * @param ctx      - the ingress type
   * @param property - the property component
   * @return known egress type
   */
  private Class getCollectionProperty(Class ctx, String property) {
    //如果属性是首次调用，则重新处理相应的上下文类型，因此ctl可能本身就为null
    // 这里property即为 abc[中的abc，因此在以下的处理中，才在first时尝试从解析上下文中找到相应的类型
    if (first) {
      //属性为入参类型，则ctx为入参的类型
      if (pCtx.hasVarOrInput(property)) {
        ctx = getSubComponentType(pCtx.getVarOrInputType(property));
      }
      //属性为引用类型，则ctx为引入的类型
      else if (pCtx.hasImport(property)) {
        resolvedExternally = false;
        ctx = getSubComponentType(pCtx.getImport(property));
      }
      //默认为object类型
      else {
        ctx = Object.class;
      }
    }

    //如果是严格类型限制，则尝试进行严格类型处理
    if (pCtx.isStrictTypeEnforcement()) {
      //property为空，即表示直接为 [xx 访问，不为空是则表示是 abc[ 方法，因此先找到abc的类型
      //如果类型为map，则找到map中的值信息
      if (Map.class.isAssignableFrom(property.length() != 0 ? ctx = getBeanProperty(ctx, property) : ctx)) {
        ctx = (Class) (pCtx.getLastTypeParameters().length != 0 ? pCtx.getLastTypeParameters()[1] : Object.class);
      }
      //集合中的元素类型信息
      else if (Collection.class.isAssignableFrom(ctx)) {
        if (pCtx.getLastTypeParameters().length == 0) {
          ctx = Object.class;
        }
        else {
          Type type = pCtx.getLastTypeParameters()[0];
          if (type instanceof Class) ctx = (Class) type;
          else ctx = (Class) ((ParameterizedType) type).getRawType();
        }
      }
      //数组中的类型信息
      else if (ctx.isArray()) {
        ctx = ctx.getComponentType();
      }
      else if (pCtx.isStrongTyping()) {
        throw new CompileException("unknown collection type: " + ctx + "; property=" + property, expr, start);
      }
    }
    //非严格类型，默认为object类型
    else {
      ctx = Object.class;
    }

    ++cursor;

    skipWhitespace();

    int start = cursor;

    if (scanTo(']')) {
      addFatalError("unterminated [ in token");
    }

    //对整个[]里面的属性进行验证，以保证相应的表达式是正确的
    MVEL.analysisCompile(new String(expr, start, cursor - start), pCtx);

    ++cursor;

    return ctx;
  }


  /**
   * 从当前上下文中获取相应的方法的返回类型信息
   * Process method
   *
   * @param ctx  - the ingress type
   * @param name - the property component
   * @return known egress type.
   */
  private Class getMethod(Class ctx, String name) {
    int st = cursor;

    /**
     * Check to see if this is the first element in the statement.
     */
    if (first) {
      first = false;
      //当前是首节点，并且调用到这里，就表示当前是一个方法调用
      methodCall = true;

      /**
       * It's the first element in the statement, therefore we check to see if there is a static import of a
       * native Java method or an MVEL function.
       * 先尝试从解析上下文中查找相应的定义，如果有，则表示是提前引用导入的
       */
      if (pCtx.hasImport(name)) {
        Method m = pCtx.getStaticImport(name).getMethod();

        //因为解析中有此相应的引用，因此相应的上下文类和方法类强制从解析中读取，而忽略相应的参数信息
        /**
         * Replace the method parameters.
         */
        ctx = m.getDeclaringClass();
        name = m.getName();
      }
      else {
        //这里看一下是否是针对函数的定义和引用
        Function f = pCtx.getFunction(name);
        if (f != null && f.getEgressType() != null) {
          resolvedExternally = false;
          f.checkArgumentCount(
              parseParameterList(
                  (((cursor = balancedCapture(expr, cursor, end, '(')) - st) > 1 ?
                      ParseTools.subset(expr, st + 1, cursor - st - 1) : new char[0]), 0, -1).size());

          return f.getEgressType();
        }
        //有this,在first时强制使用this所对应的类型来进行处理
        else if (pCtx.hasVarOrInput("this")) {
          if (pCtx.isStrictTypeEnforcement()) {
            recordTypeParmsForProperty("this");
          }
          ctx = pCtx.getVarOrInputType("this");
          resolvedExternally = false;
        }
      }
    }

    //处理参数信息，以便后续通过参数个数以及相应的类型来找到正确的方法,以处理方法重载的问题
    /**
     * Get the arguments for the method.
     */
    String tk;

    if (cursor < end && expr[cursor] == '(' && ((cursor = balancedCapture(expr, cursor, end, '(')) - st) > 1) {
      tk = new String(expr, st + 1, cursor - st - 1);
    }
    else {
      tk = "";
    }

    cursor++;

    /**
     * Parse out the arguments list.
     */
    Class[] args;
    //分割参数信息
    List<char[]> subtokens = parseParameterList(tk.toCharArray(), 0, -1);

    //无参方法
    if (subtokens.size() == 0) {
      args = new Class[0];
      subtokens = Collections.emptyList();
    }
    //有参方法，那么尝试继续解析相应的参数类型
    else {
      //   ParserContext subCtx = pCtx.createSubcontext();
      args = new Class[subtokens.size()];

      /**
       *  Subcompile all the arguments to determine their known types.
       */
      //  ExpressionCompiler compiler;

      List<ErrorDetail> errors = pCtx.getErrorList().isEmpty() ?
          pCtx.getErrorList() : new ArrayList<ErrorDetail>(pCtx.getErrorList());

      //挨个解析参数，如果在解析过程中有严重错误，则停止解析工作，按照正常的流程，最终会解析好相应的参数类型信息
      CompileException rethrow = null;
      for (int i = 0; i < subtokens.size(); i++) {
        try {
          args[i] = MVEL.analyze(subtokens.get(i), pCtx);

          if ("null".equals(String.valueOf(subtokens.get(i)))) {
            args[i] = NullType.class;
          }

        }
        catch (CompileException e) {
          rethrow = ErrorUtil.rewriteIfNeeded(e, expr, this.st);
        }

        if (errors.size() < pCtx.getErrorList().size()) {
          for (ErrorDetail detail : pCtx.getErrorList()) {
            if (!errors.contains(detail)) {
              detail.setExpr(expr);
              detail.setCursor(new String(expr).substring(this.st).indexOf(new String(subtokens.get(i))) + this.st);
              detail.setColumn(0);
              detail.setLineNumber(0);
              detail.calcRowAndColumn();
            }
          }
        }

        //有解析参数过程中有异常发生，则直接throws
        if (rethrow != null) {
          throw rethrow;
        }
      }
    }

    /**
     * If the target object is an instance of java.lang.Class itself then do not
     * adjust the Class scope target.
     */

    Method m;

    /**
     * If we have not cached the method then we need to go ahead and try to resolve it.
     */
    //尝试从公共方法以及私有方法中找到相应的方法
    //首先找公共方法
    if ((m = getBestCandidate(args, name, ctx, ctx.getMethods(), pCtx.isStrongTyping())) == null) {
      //再查找仅有方法
      if ((m = getBestCandidate(args, name, ctx, ctx.getDeclaredMethods(), pCtx.isStrongTyping())) == null) {
        StringAppender errorBuild = new StringAppender();
        for (int i = 0; i < args.length; i++) {
          errorBuild.append(args[i] != null ? args[i].getName() : null);
          if (i < args.length - 1) errorBuild.append(", ");
        }

        //支持数组类的特殊属性
        if (("size".equals(name) || "length".equals(name)) && args.length == 0 && ctx.isArray()) {
          return Integer.class;
        }

        //如果是强类型，则直接报错
        if (pCtx.isStrictTypeEnforcement()) {
          throw new CompileException("unable to resolve method using strict-mode: "
              + ctx.getName() + "." + name + "(" + errorBuild.toString() + ")", expr, tkStart);
        }

        //非强类型处理，则返回通用类型
        return Object.class;
      }
    }

    //到这里，表示已经找到方法了，那么尝试进一步处理相应的泛型信息
    /**
     * If we're in strict mode, we look for generic type information.
     */
    //这里的方法签名上是有泛型信息的
    if (pCtx.isStrictTypeEnforcement() && m.getGenericReturnType() != null) {
      Map<String, Class> typeArgs = new HashMap<String, Class>();

      //先通过泛型参数上的定义，快速地找到相应的泛型信息定义，然后通过相应的map映射快速地得到相应的返回类型

      Type[] gpt = m.getGenericParameterTypes();
      Class z;
      ParameterizedType pt;

      for (int i = 0; i < gpt.length; i++) {
        if (gpt[i] instanceof ParameterizedType) {
          pt = (ParameterizedType) gpt[i];
          if ((z = pCtx.getImport(new String(subtokens.get(i)))) != null) {
            /**
             * We record the value of the type parameter to our typeArgs Map.
             */
            if (pt.getRawType().equals(Class.class)) {
              /**
               * If this is an instance of Class, we deal with the special parameterization case.
               */
              typeArgs.put(pt.getActualTypeArguments()[0].toString(), z);
            }
            else {
              typeArgs.put(gpt[i].toString(), z);
            }
          }
        }
      }

      if (pCtx.isStrictTypeEnforcement() && ctx.getTypeParameters().length != 0 && pCtx.getLastTypeParameters() !=
          null && pCtx.getLastTypeParameters().length == ctx.getTypeParameters().length) {

        TypeVariable[] typeVariables = ctx.getTypeParameters();
        for (int i = 0; i < typeVariables.length; i++) {
          Type typeArg = pCtx.getLastTypeParameters()[i];
          typeArgs.put(typeVariables[i].getName(), typeArg instanceof Class ? (Class) pCtx.getLastTypeParameters()[i] : Object.class);
        }
      }

      //这里尝试通过相应的最近处理参数映射以及刚从泛型参数中解析到的映射中查找相应的返回类型
      /**
       * Get the return type argument
       */
      Type parametricReturnType = m.getGenericReturnType();
      String returnTypeArg = parametricReturnType.toString();

      //push return type parameters onto parser context, only if this is a parametric type
      if (parametricReturnType instanceof ParameterizedType) {
        pCtx.setLastTypeParameters(((ParameterizedType) parametricReturnType).getActualTypeArguments());
      }

      if (paramTypes != null && paramTypes.containsKey(returnTypeArg)) {
        /**
         * If the paramTypes Map contains the known type, return that type.
         */
        return (Class) paramTypes.get(returnTypeArg);
      }
      else if (typeArgs.containsKey(returnTypeArg)) {
        /**
         * If the generic type was declared as part of the method, it will be in this
         * Map.
         */
        return typeArgs.get(returnTypeArg);
      }
    }

    //如果是强泛型类型处理，则不允许访问相应的私有方法以及package方法
    if (!Modifier.isPublic(m.getModifiers()) && pCtx.isStrictTypeEnforcement()) {
      StringAppender errorBuild = new StringAppender();
      for (int i = 0; i < args.length; i++) {
        errorBuild.append(args[i] != null ? args[i].getName() : null);
        if (i < args.length - 1) errorBuild.append(", ");
      }

      String scope = Modifier.toString(m.getModifiers());
      if (scope.trim().equals("")) scope = "<package local>";

      addFatalError("the referenced method is not accessible: "
          + ctx.getName() + "." + name + "(" + errorBuild.toString() + ")"
          + " (scope: " + scope + "; required: public", this.tkStart);
    }

    //默认情况下，仍采用默认的方法从上下文类和方法中找到返回类型
    return getReturnType(ctx, m);
  }

  /** 处理with中的属性类型 */
  private Class getWithProperty(Class ctx) {
    String root = new String(expr, 0, cursor - 1).trim();

    int start = cursor + 1;
    cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '{', pCtx);

    new WithAccessor(pCtx, root, expr, start, cursor++ - start, ctx);

    return ctx;
  }

  /** 返回当前解析中是否需要外部处理 */
  public boolean isResolvedExternally() {
    return resolvedExternally;
  }

  public boolean isClassLiteral() {
    return classLiteral;
  }

  public boolean isDeepProperty() {
    return deepProperty;
  }

  /** 表示当前解析属性是否是输入属性，而不是方法调用 */
  public boolean isInput() {
    return resolvedExternally && !methodCall;
  }

  /** 返回当前返回是否是方法调用 */
  public boolean isMethodCall() {
    return methodCall;
  }

  public boolean isFqcn() {
    return fqcn;
  }

  /** 获取当前的解析上下文 */
  public Class getCtx() {
    return ctx;
  }

  public void setCtx(Class ctx) {
    this.ctx = ctx;
  }
}
