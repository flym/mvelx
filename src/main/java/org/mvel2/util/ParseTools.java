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

package org.mvel2.util;

import org.mvel2.CompileException;
import org.mvel2.DataTypes;
import org.mvel2.MVEL;
import org.mvel2.Operator;
import org.mvel2.OptimizationFailure;
import org.mvel2.ParserContext;
import org.mvel2.ast.ASTNode;
import org.mvel2.compiler.AbstractParser;
import org.mvel2.compiler.BlankLiteral;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExecutableAccessor;
import org.mvel2.compiler.ExecutableAccessorSafe;
import org.mvel2.compiler.ExecutableLiteral;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ClassImportResolverFactory;
import org.mvel2.math.MathProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static java.lang.Class.forName;
import static java.lang.Double.parseDouble;
import static java.lang.String.valueOf;
import static java.lang.System.arraycopy;
import static java.lang.Thread.currentThread;
import static java.nio.ByteBuffer.allocateDirect;
import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataTypes.*;
import static org.mvel2.MVEL.getDebuggingOutputFileName;
import static org.mvel2.compiler.AbstractParser.LITERALS;
import static org.mvel2.integration.ResolverTools.appendFactory;


/** 整个编译的工具类,可直接用于各项表达式编译 */
@SuppressWarnings({"ManualArrayCopy"})
public class ParseTools {
  public static final Object[] EMPTY_OBJ_ARR = new Object[0];
  public static final Class[] EMPTY_CLS_ARR = new Class[0];

  /** 处理构造函数或方法的参数信息 */
  public static List<char[]> parseMethodOrConstructor(char[] parm) {
    int start = -1;
    for (int i = 0; i < parm.length; i++) {
      if (parm[i] == '(') {
        start = ++i;
        break;
      }
    }
    if (start != -1) {
      return parseParameterList(parm, --start + 1, balancedCapture(parm, start, '(') - start - 1);
    }

    return Collections.emptyList();
  }


  /** 从一个字符串中解析出参数的定义信息,即通过,号进行分隔解析处理,用于自定义函数时进行参数解析 */
  public static String[] parseParameterDefList(char[] parm, int offset, int length) {
    List<String> list = new LinkedList<String>();

    if (length == -1)
      length = parm.length;

    int start = offset;
    int i = offset;
    int end = i + length;
    String s;

    for (; i < end; i++) {
      switch (parm[i]) {
        //---------------------------- 各种需要表示单个语句块 start ------------------------------//
        case '(':
        case '[':
        case '{':
          i = balancedCapture(parm, i, parm[i]);
          continue;

        case '\'':
          i = captureStringLiteral('\'', parm, i, parm.length);
          continue;

        case '"':
          i = captureStringLiteral('"', parm, i, parm.length);
          continue;

          //---------------------------- 各种需要表示单个语句块 end ------------------------------//

          //这里表示碰到一个, 即一个定义结束了,则将这个值加入到参数列表当中
        case ',':
          if (i > start) {
            //在处理字符串参数时要求最前面不能是空格,如 __abc这种,则移除最前面的空格位
            while (isWhitespace(parm[start]))
              start++;

            checkNameSafety(s = new String(parm, start, i - start));

            list.add(s);
          }

          //以下这个while无用
          while (isWhitespace(parm[i]))
            i++;

          start = i + 1;
          continue;

          //碰到不是以上的字符的,则要求必须是定义字段a-zA-Z0-9,或者是空格
        default:
          if (!isWhitespace(parm[i]) && !isIdentifierPart(parm[i])) {
            throw new CompileException("expected parameter", parm, start);
          }
      }
    }

    //追加最末尾的参数信息
    // 以下为什么使用if else未看明白,可能是之前的bug,看这里看不出来走else的情况
    if (start < (length + offset) && i > start) {
      if ((s = createStringTrimmed(parm, start, i - start)).length() > 0) {
        checkNameSafety(s);
        list.add(s);
      }
    }
    else if (list.size() == 0) {
      if ((s = createStringTrimmed(parm, start, length)).length() > 0) {
        checkNameSafety(s);
        list.add(s);
      }
    }

    return list.toArray(new String[list.size()]);
  }


  /** 将整个字符串处理成相应的参数列表,就是按照 逗号将数据进行分组处理 */
  public static List<char[]> parseParameterList(char[] parm, int offset, int length) {
    List<char[]> list = new ArrayList<char[]>();

    if (length == -1)
      length = parm.length;

    int start = offset;
    int i = offset;
    int end = i + length;

    for (; i < end; i++) {
      switch (parm[i]) {
        case '(':
        case '[':
        case '{':
          i = balancedCapture(parm, i, parm[i]);
          continue;

        case '\'':
          i = captureStringLiteral('\'', parm, i, parm.length);
          continue;

        case '"':
          i = captureStringLiteral('"', parm, i, parm.length);
          continue;

        case ',':
          if (i > start) {
            while (isWhitespace(parm[start]))
              start++;

            list.add(subsetTrimmed(parm, start, i - start));
          }

          while (isWhitespace(parm[i]))
            i++;

          start = i + 1;
      }
    }

    if (start < (length + offset) && i > start) {
      char[] s = subsetTrimmed(parm, start, i - start);
      if (s.length > 0)
        list.add(s);
    }
    else if (list.size() == 0) {
      char[] s = subsetTrimmed(parm, start, length);
      if (s.length > 0)
        list.add(s);
    }

    return list;
  }

  /** 在指定的方法中获取最匹配的方法 */
  public static Method getBestCandidate(Object[] arguments, String method, Class decl, Method[] methods, boolean requireExact) {
    Class[] targetParms = new Class[arguments.length];
    for (int i = 0; i != arguments.length; i++) {
      targetParms[i] = arguments[i] != null ? arguments[i].getClass() : null;
    }
    return getBestCandidate(targetParms, method, decl, methods, requireExact);
  }

  /** 在指定的方法中获取最匹配的方法 */
  public static Method getBestCandidate(Class[] arguments, String method, Class decl, Method[] methods, boolean requireExact) {
    return getBestCandidate(arguments, method, decl, methods, requireExact, false);
  }

  /**
   * 在指定的方法中获取最匹配的方法
   *
   * @param classTarget 是否是静态方法
   */
  public static Method getBestCandidate(Class[] arguments, String method, Class decl, Method[] methods, boolean requireExact, boolean classTarget) {

    if (methods.length == 0) {
      return null;
    }

    Class<?>[] parmTypes;
    Method bestCandidate = null;
    int bestScore = -1;
    //是否已经重试过
    boolean retry = false;

    do {
      for (Method meth : methods) {
        //静态方法判定
        if (classTarget && !Modifier.isStatic(meth.getModifiers())) continue;

        if (method.equals(meth.getName())) {
          parmTypes = meth.getParameterTypes();
          if (parmTypes.length == 0 && arguments.length == 0) {
            if (bestCandidate == null || isMoreSpecialized(meth, bestCandidate)) {
              bestCandidate = meth;
            }
            continue;
          }

          boolean isVarArgs = meth.isVarArgs();
          if (parmTypes.length != arguments.length && !isVarArgs) {
            continue;
          }

          //这里采用评分算法计算当前方法是否得分更高,得分最高的为最匹配方法
          int score = getMethodScore(arguments, requireExact, parmTypes, isVarArgs);
          if (score != 0) {
            if (score > bestScore) {
              bestCandidate = meth;
              bestScore = score;
            }
            else if (score == bestScore) {
              if (isMoreSpecialized(meth, bestCandidate) && !isVarArgs) {
                bestCandidate = meth;
              }
            }
          }
        }
      }

      if (bestCandidate != null) {
        break;
      }

      //没找到，尝试将object的方法一并加上进行处理
      if (!retry && decl.isInterface()) {
        Method[] objMethods = Object.class.getMethods();
        Method[] nMethods = new Method[methods.length + objMethods.length];
        for (int i = 0; i < methods.length; i++) {
          nMethods[i] = methods[i];
        }

        for (int i = 0; i < objMethods.length; i++) {
          nMethods[i + methods.length] = objMethods[i];
        }
        methods = nMethods;

        retry = true;
      }
      else {
        break;
      }
    }
    while (true);

    return bestCandidate;
  }

  /** 判定一个方法是否比另一个方法更合适 判定依据即是返回类型更加细化，声明的参数类型也更细化 */
  private static boolean isMoreSpecialized(Method newCandidate, Method oldCandidate) {
    return oldCandidate.getReturnType().isAssignableFrom(newCandidate.getReturnType()) &&
        oldCandidate.getDeclaringClass().isAssignableFrom(newCandidate.getDeclaringClass());
  }

  private static int getMethodScore(Class[] arguments, boolean requireExact, Class<?>[] parmTypes, boolean varArgs) {
    int score = 0;
    for (int i = 0; i != arguments.length; i++) {
      Class<?> actualParamType;
      if (varArgs && i >= parmTypes.length - 1)
        actualParamType = parmTypes[parmTypes.length - 1].getComponentType();
      else
        actualParamType = parmTypes[i];

      if (arguments[i] == null) {
        if (!actualParamType.isPrimitive()) {
          score += 6;
        }
        else {
          score = 0;
          break;
        }
      }
      else if (actualParamType == arguments[i]) {
        score += 7;
      }
      else if (actualParamType.isPrimitive() && boxPrimitive(actualParamType) == arguments[i]) {
        score += 6;
      }
      else if (arguments[i].isPrimitive() && unboxPrimitive(arguments[i]) == actualParamType) {
        score += 6;
      }
      else if (actualParamType.isAssignableFrom(arguments[i])) {
        score += 5;
      }
      else if (isNumericallyCoercible(arguments[i], actualParamType)) {
        score += 4;
      }
      else if (boxPrimitive(actualParamType).isAssignableFrom(boxPrimitive(arguments[i]))
          && Object.class != arguments[i]) {
        score += 3 + scoreInterface(actualParamType, arguments[i]);
      }
      else if (!requireExact && canConvert(actualParamType, arguments[i])) {
        if (actualParamType.isArray() && arguments[i].isArray()) score += 1;
        else if (actualParamType == char.class && arguments[i] == String.class) score += 1;

        score += 1;
      }
      else if (actualParamType == Object.class || arguments[i] == NullType.class) {
        score += 1;
      }
      else {
        score = 0;
        break;
      }
    }
    if (score == 0 && varArgs && parmTypes.length - 1 == arguments.length) {
      score += 3;
    }
    return score;
  }

  public static int scoreInterface(Class<?> parm, Class<?> arg) {
    if (parm.isInterface()) {
      Class[] iface = arg.getInterfaces();
      if (iface != null) {
        for (Class c : iface) {
          if (c == parm) return 1;
          else if (parm.isAssignableFrom(c)) return scoreInterface(parm, arg.getSuperclass());
        }
      }
    }
    return 0;
  }

  /** 获取在相应类上具备指定名字，参数类型以及返回类型的方法 */
  public static Method getExactMatch(String name, Class[] args, Class returnType, Class cls) {
    outer:
    for (Method meth : cls.getMethods()) {
      if (name.equals(meth.getName()) && returnType == meth.getReturnType()) {
        Class[] parameterTypes = meth.getParameterTypes();
        if (parameterTypes.length != args.length) continue;

        for (int i = 0; i < parameterTypes.length; i++) {
          if (parameterTypes[i] != args[i]) continue outer;
        }
        return meth;
      }
    }
    return null;
  }

  /** 从指定方法的定义上获取更宽化的定义方法 */
  public static Method getWidenedTarget(Method method) {
    return getWidenedTarget(method.getDeclaringClass(), method);
  }

  /** 从指定类上查找指定的宽化方法定义(即从指定类的接口或者父类，不处理当前类) */
  public static Method getWidenedTarget(Class cls, Method method) {
    //静态方法不存在继承问题,因此只能直接返回
    if (Modifier.isStatic(method.getModifiers())) {
      return method;
    }

    Method m = method, best = method;
    Class[] args = method.getParameterTypes();
    String name = method.getName();
    Class rt = m.getReturnType();

    //先尝试从接口上进行查找
    Class currentCls = cls;
    while (currentCls != null) {
      for (Class iface : currentCls.getInterfaces()) {
        if ((m = getExactMatch(name, args, rt, iface)) != null) {
          best = m;
        }
      }
      currentCls = currentCls.getSuperclass();
    }

    //必须保证与当前的参数方法不一样
    if (best != method) return best;

    //再尝试从父类中进行查找
    for (currentCls = cls; currentCls != null; currentCls = currentCls.getSuperclass()) {
      if ((m = getExactMatch(name, args, rt, currentCls)) != null) {
        best = m;
      }
    }
    return best;
  }

  private static final Map<Constructor, WeakReference<Class[]>> CONSTRUCTOR_PARMS_CACHE
      = Collections.synchronizedMap(new WeakHashMap<Constructor, WeakReference<Class[]>>(10));

  private static Class[] getConstructors(Constructor cns) {
    WeakReference<Class[]> ref = CONSTRUCTOR_PARMS_CACHE.get(cns);
    Class[] parms;
    if (ref != null && (parms = ref.get()) != null) {
      return parms;
    }
    else {
      CONSTRUCTOR_PARMS_CACHE.put(cns, new WeakReference<Class[]>(parms = cns.getParameterTypes()));
      return parms;
    }
  }

  /** 获取指定类的一个最匹配的构造函数 */
  public static Constructor getBestConstructorCandidate(Object[] args, Class cls, boolean requireExact) {
    Class[] arguments = new Class[args.length];

    for (int i = 0; i != args.length; i++) {
      if (args[i] != null) {
        arguments[i] = args[i].getClass();
      }
    }

    return getBestConstructorCandidate(arguments, cls, requireExact);
  }

  public static Constructor getBestConstructorCandidate(Class[] arguments, Class cls, boolean requireExact) {
    Class[] parmTypes;
    Constructor bestCandidate = null;
    int bestScore = 0;

    for (Constructor construct : getConstructors(cls)) {
      boolean isVarArgs = construct.isVarArgs();
      if ((parmTypes = getConstructors(construct)).length != arguments.length && !construct.isVarArgs()) {
        continue;
      }
      else if (arguments.length == 0 && parmTypes.length == 0) {
        return construct;
      }

      int score = getMethodScore(arguments, requireExact, parmTypes, isVarArgs);
      if (score != 0 && score > bestScore) {
        bestCandidate = construct;
        bestScore = score;
      }
    }

    return bestCandidate;
  }


  /** 对加载过的类进行的一个全局缓存,优化查找速度 */
  private static final Map<ClassLoader, Map<String, WeakReference<Class>>> CLASS_RESOLVER_CACHE
      = Collections.synchronizedMap(new WeakHashMap<ClassLoader, Map<String, WeakReference<Class>>>(1, 1.0f));
  /** 对加载过的构建函数进行一个全局缓存,优化查找速度 */
  private static final Map<Class, WeakReference<Constructor[]>> CLASS_CONSTRUCTOR_CACHE
      = Collections.synchronizedMap(new WeakHashMap<Class, WeakReference<Constructor[]>>(10));


  /** 进行类加载,使用编译上下文中的类加载器 */
  public static Class createClass(String className, ParserContext pCtx) throws ClassNotFoundException {
    ClassLoader classLoader = pCtx != null ? pCtx.getClassLoader() : currentThread().getContextClassLoader();

    Map<String, WeakReference<Class>> cache = CLASS_RESOLVER_CACHE.get(classLoader);

    if (cache == null) {
      CLASS_RESOLVER_CACHE.put(classLoader, cache = Collections.synchronizedMap(new WeakHashMap<String, WeakReference<Class>>(10)));
    }

    WeakReference<Class> ref;
    Class cls;

    //如果缓存中存在,则直接使用缓存
    if ((ref = cache.get(className)) != null && (cls = ref.get()) != null) {
      return cls;
    }
    else {
      //使用上下文中的加载器或者是线程类加载器进行加载
      try {
        cls = Class.forName(className, true, classLoader);
      }
      catch (ClassNotFoundException e) {
        /**
         * Now try the system classloader.
         */
        if (classLoader != Thread.currentThread().getContextClassLoader()) {
          cls = forName(className, true, Thread.currentThread().getContextClassLoader());
        }
        else {
          throw e;
        }
      }

      cache.put(className, new WeakReference<Class>(cls));
      return cls;
    }
  }


  public static Constructor[] getConstructors(Class cls) {
    WeakReference<Constructor[]> ref = CLASS_CONSTRUCTOR_CACHE.get(cls);
    Constructor[] cns;

    if (ref != null && (cns = ref.get()) != null) {
      return cns;
    }
    else {
      CLASS_CONSTRUCTOR_CACHE.put(cls, new WeakReference<Constructor[]>(cns = cls.getConstructors()));
      return cns;
    }
  }


  /** 捕获构建函数参数以及剩下的数据信息,即将构建的参数内容以及后续的内容拆分开 */
  public static String[] captureContructorAndResidual(char[] cs, int start, int offset) {
    int depth = 0;
    int end = start + offset;
    boolean inQuotes = false;
    for (int i = start; i < end; i++) {
      switch (cs[i]) {
        case '"':
          inQuotes = !inQuotes;
          break;
        case '(':
          depth++;
          break;
        case ')':
          if (!inQuotes) {
            if (1 == depth--) {
              return new String[]{createStringTrimmed(cs, start, ++i - start), createStringTrimmed(cs, i, end - i)};
            }
          }
      }
    }
    return new String[]{new String(cs, start, offset)};
  }


  /** 如果类型为基本类型,返回其包装类型,其它不变 */
  public static Class<?> boxPrimitive(Class cls) {
    if (cls == int.class || cls == Integer.class) {
      return Integer.class;
    }
    else if (cls == int[].class || cls == Integer[].class) {
      return Integer[].class;
    }
    else if (cls == char.class || cls == Character.class) {
      return Character.class;
    }
    else if (cls == char[].class || cls == Character[].class) {
      return Character[].class;
    }
    else if (cls == long.class || cls == Long.class) {
      return Long.class;
    }
    else if (cls == long[].class || cls == Long[].class) {
      return Long[].class;
    }
    else if (cls == short.class || cls == Short.class) {
      return Short.class;
    }
    else if (cls == short[].class || cls == Short[].class) {
      return Short[].class;
    }
    else if (cls == double.class || cls == Double.class) {
      return Double.class;
    }
    else if (cls == double[].class || cls == Double[].class) {
      return Double[].class;
    }
    else if (cls == float.class || cls == Float.class) {
      return Float.class;
    }
    else if (cls == float[].class || cls == Float[].class) {
      return Float[].class;
    }
    else if (cls == boolean.class || cls == Boolean.class) {
      return Boolean.class;
    }
    else if (cls == boolean[].class || cls == Boolean[].class) {
      return Boolean[].class;
    }
    else if (cls == byte.class || cls == Byte.class) {
      return Byte.class;
    }
    else if (cls == byte[].class || cls == Byte[].class) {
      return Byte[].class;
    }

    return cls;
  }

  public static Class unboxPrimitive(Class cls) {
    if (cls == Integer.class || cls == int.class) {
      return int.class;
    }
    else if (cls == Integer[].class || cls == int[].class) {
      return int[].class;
    }
    else if (cls == Long.class || cls == long.class) {
      return long.class;
    }
    else if (cls == Long[].class || cls == long[].class) {
      return long[].class;
    }
    else if (cls == Character.class || cls == char.class) {
      return char.class;
    }
    else if (cls == Character[].class || cls == char[].class) {
      return char[].class;
    }
    else if (cls == Short.class || cls == short.class) {
      return short.class;
    }
    else if (cls == Short[].class || cls == short[].class) {
      return short[].class;
    }
    else if (cls == Double.class || cls == double.class) {
      return double.class;
    }
    else if (cls == Double[].class || cls == double[].class) {
      return double[].class;
    }
    else if (cls == Float.class || cls == float.class) {
      return float.class;
    }
    else if (cls == Float[].class || cls == float[].class) {
      return float[].class;
    }
    else if (cls == Boolean.class || cls == boolean.class) {
      return boolean.class;
    }
    else if (cls == Boolean[].class || cls == boolean[].class) {
      return boolean[].class;
    }
    else if (cls == Byte.class || cls == byte.class) {
      return byte.class;
    }
    else if (cls == Byte[].class || cls == byte[].class) {
      return byte[].class;
    }


    return cls;
  }

  /** 对2个值进行contains判定,以返回相应的处理值,即first对象是否包含second对象 */
  public static boolean containsCheck(Object compareTo, Object compareTest) {
    //null处理
    if (compareTo == null)
      return false;
      //字符串
    else if (compareTo instanceof String)
      return ((String) compareTo).contains(valueOf(compareTest));
      //集合
    else if (compareTo instanceof Collection)
      return ((Collection) compareTo).contains(compareTest);
      //map
    else if (compareTo instanceof Map)
      return ((Map) compareTo).containsKey(compareTest);
      //数组,分为基本类型和非基本类型
    else if (compareTo.getClass().isArray()) {
      if (compareTo.getClass().getComponentType().isPrimitive())
        return containsCheckOnPrimitveArray(compareTo, compareTest);
      for (Object o : ((Object[]) compareTo)) {
        if (compareTest == null && o == null)
          return true;
        if ((Boolean) MathProcessor.doOperations(o, Operator.EQUAL, compareTest))
          return true;
      }
    }
    //其它情况直接返回false,如 x.contains(null)这种
    return false;
  }

  /** 在基本类型数组中判定相应的值是否满足contains的处理 */
  private static boolean containsCheckOnPrimitveArray(Object primitiveArray, Object compareTest) {
    Class<?> primitiveType = primitiveArray.getClass().getComponentType();
    if (primitiveType == boolean.class)
      return compareTest instanceof Boolean && containsCheckOnBooleanArray((boolean[]) primitiveArray, (Boolean) compareTest);
    if (primitiveType == int.class)
      return compareTest instanceof Integer && containsCheckOnIntArray((int[]) primitiveArray, (Integer) compareTest);
    if (primitiveType == long.class)
      return compareTest instanceof Long && containsCheckOnLongArray((long[]) primitiveArray, (Long) compareTest);
    if (primitiveType == double.class)
      return compareTest instanceof Double && containsCheckOnDoubleArray((double[]) primitiveArray, (Double) compareTest);
    if (primitiveType == float.class)
      return compareTest instanceof Float && containsCheckOnFloatArray((float[]) primitiveArray, (Float) compareTest);
    if (primitiveType == char.class)
      return compareTest instanceof Character && containsCheckOnCharArray((char[]) primitiveArray, (Character) compareTest);
    if (primitiveType == short.class)
      return compareTest instanceof Short && containsCheckOnShortArray((short[]) primitiveArray, (Short) compareTest);
    if (primitiveType == byte.class)
      return compareTest instanceof Byte && containsCheckOnByteArray((byte[]) primitiveArray, (Byte) compareTest);

    //类型不兼容则直接返回false
    return false;
  }

  /** boolean数组判定是否contains */
  private static boolean containsCheckOnBooleanArray(boolean[] array, Boolean compareTest) {
    boolean test = compareTest;
    for (boolean b : array) if (b == test) return true;
    return false;
  }

  /** int数组判定是否contains */
  private static boolean containsCheckOnIntArray(int[] array, Integer compareTest) {
    int test = compareTest;
    for (int i : array) if (i == test) return true;
    return false;
  }

  /** long数组判定是否contains */
  private static boolean containsCheckOnLongArray(long[] array, Long compareTest) {
    long test = compareTest;
    for (long l : array) if (l == test) return true;
    return false;
  }

  /** double数组判定是否contains */
  private static boolean containsCheckOnDoubleArray(double[] array, Double compareTest) {
    double test = compareTest;
    for (double d : array) if (d == test) return true;
    return false;
  }

  /** float数组判定是否contains */
  private static boolean containsCheckOnFloatArray(float[] array, Float compareTest) {
    float test = compareTest;
    for (float f : array) if (f == test) return true;
    return false;
  }

  /** char数组判定是否contains */
  private static boolean containsCheckOnCharArray(char[] array, Character compareTest) {
    char test = compareTest;
    for (char c : array) if (c == test) return true;
    return false;
  }

  /** short数组判定是否contains */
  private static boolean containsCheckOnShortArray(short[] array, Short compareTest) {
    short test = compareTest;
    for (short s : array) if (s == test) return true;
    return false;
  }

  /** byte数组判定是否contains */
  private static boolean containsCheckOnByteArray(byte[] array, Byte compareTest) {
    byte test = compareTest;
    for (byte b : array) if (b == test) return true;
    return false;
  }

  /**
   * Replace escape sequences and return trim required.
   *
   * @param escapeStr -
   * @param pos       -
   * @return -
   */
  public static int handleEscapeSequence(char[] escapeStr, int pos) {
    escapeStr[pos - 1] = 0;

    switch (escapeStr[pos]) {
      case '\\':
        escapeStr[pos] = '\\';
        return 1;
      case 'b':
        escapeStr[pos] = '\b';
        return 1;
      case 'f':
        escapeStr[pos] = '\f';
        return 1;
      case 't':
        escapeStr[pos] = '\t';
        return 1;
      case 'r':
        escapeStr[pos] = '\r';
        return 1;
      case 'n':
        escapeStr[pos] = '\n';
        return 1;
      case '\'':
        escapeStr[pos] = '\'';
        return 1;
      case '"':
        escapeStr[pos] = '\"';
        return 1;
      case 'u':
        //unicode
        int s = pos;
        if (s + 4 > escapeStr.length)
          throw new CompileException("illegal unicode escape sequence", escapeStr, pos);
        else {
          while (++pos - s != 5) {
            if ((escapeStr[pos] > ('0' - 1) && escapeStr[pos] < ('9' + 1)) ||
                (escapeStr[pos] > ('A' - 1) && escapeStr[pos] < ('F' + 1))) {
            }
            else {
              throw new CompileException("illegal unicode escape sequence", escapeStr, pos);
            }
          }

          escapeStr[s - 1] = (char) Integer.decode("0x" + new String(escapeStr, s + 1, 4)).intValue();
          escapeStr[s] = 0;
          escapeStr[s + 1] = 0;
          escapeStr[s + 2] = 0;
          escapeStr[s + 3] = 0;
          escapeStr[s + 4] = 0;

          return 5;
        }

      default:
        //octal
        s = pos;
        while (escapeStr[pos] >= '0' && escapeStr[pos] < '8') {
          if (pos != s && escapeStr[s] > '3') {
            escapeStr[s - 1] = (char) Integer.decode("0" + new String(escapeStr, s, pos - s + 1)).intValue();
            escapeStr[s] = 0;
            escapeStr[s + 1] = 0;
            return 2;
          }
          else if ((pos - s) == 2) {
            escapeStr[s - 1] = (char) Integer.decode("0" + new String(escapeStr, s, pos - s + 1)).intValue();
            escapeStr[s] = 0;
            escapeStr[s + 1] = 0;
            escapeStr[s + 2] = 0;
            return 3;
          }

          if (pos + 1 == escapeStr.length || (escapeStr[pos] < '0' || escapeStr[pos] > '7')) {
            escapeStr[s - 1] = (char) Integer.decode("0" + new String(escapeStr, s, pos - s + 1)).intValue();
            escapeStr[s] = 0;
            return 1;
          }

          pos++;
        }
        throw new CompileException("illegal escape sequence: " + escapeStr[pos], escapeStr, pos);
    }
  }

  /** 将变量名+赋值表达式+相应的算术符解析联合成一个 类似 a + b的表达式 */
  public static char[] createShortFormOperativeAssignment(String name, char[] statement, int start, int offset, int operation) {
    //没有操作符,则默认就是后面的表达式
    if (operation == -1) {
      return statement;
    }

    //反向查找操作符
    char[] stmt;
    char op = 0;
    switch (operation) {
      case Operator.ADD:
        op = '+';
        break;
      case Operator.STR_APPEND:
        op = '#';
        break;
      case Operator.SUB:
        op = '-';
        break;
      case Operator.MULT:
        op = '*';
        break;
      case Operator.MOD:
        op = '%';
        break;
      case Operator.DIV:
        op = '/';
        break;
      case Operator.BW_AND:
        op = '&';
        break;
      case Operator.BW_OR:
        op = '|';
        break;
      case Operator.BW_SHIFT_LEFT:
        op = '\u00AB';
        break;
      case Operator.BW_SHIFT_RIGHT:
        op = '\u00BB';
        break;
      case Operator.BW_USHIFT_RIGHT:
        op = '\u00AC';
        break;
    }

    //将3者联合起来 a + b
    arraycopy(name.toCharArray(), 0, (stmt = new char[name.length() + offset + 1]), 0, name.length());
    stmt[name.length()] = op;
    arraycopy(statement, start, stmt, name.length() + 1, offset);

    return stmt;
  }


  /** 从当前解析类中找到用于解析类名信息的解析器,如果没有，则追加一个 */
  public static ClassImportResolverFactory findClassImportResolverFactory(VariableResolverFactory factory, ParserContext pCtx) {
    if (factory == null) {
      throw new OptimizationFailure("unable to import classes.  no variable resolver factory available.");
    }

    for (VariableResolverFactory v = factory; v != null; v = v.getNextFactory()) {
      if (v instanceof ClassImportResolverFactory) {
        return (ClassImportResolverFactory) v;
      }
    }

    return appendFactory(factory, new ClassImportResolverFactory(null, null, false));
  }

  /** 从变量工厂+当前类型名+编译上下文中查找或者创建出类型信息 */
  public static Class findClass(VariableResolverFactory factory, String name, ParserContext pCtx) throws ClassNotFoundException {
    try {
      //常量类型 fast path
      if (LITERALS.containsKey(name)) {
        return (Class) LITERALS.get(name);
      }
      //变量工厂可以处理的
      else if (factory != null && factory.isResolveable(name)) {
        return (Class) factory.getVariableResolver(name).getValue();
      }
      //上下文中引用的
      else if (pCtx != null && pCtx.hasImport(name)) {
        return pCtx.getImport(name);
      }
      //默认情况下,尝试进行类加载
      else {
        return createClass(name, pCtx);
      }
    }
    catch (ClassNotFoundException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException("class not found: " + name, e);
    }
  }

  public static char[] subsetTrimmed(char[] array, int start, int length) {
    if (length <= 0) {
      return new char[0];
    }

    int end = start + length;
    while (end > 0 && isWhitespace(array[end - 1])) {
      end--;
    }

    while (isWhitespace(array[start]) && start < end) {
      start++;
    }

    length = end - start;

    if (length == 0) {
      return new char[0];
    }

    return subset(array, start, length);
  }

  /** 拿到字符数组的中间一段信息,相当于str.substring */
  public static char[] subset(char[] array, int start, int length) {


    char[] newArray = new char[length];

    for (int i = 0; i < newArray.length; i++) {
      newArray[i] = array[i + start];
    }

    return newArray;
  }

  public static char[] subset(char[] array, int start) {
    char[] newArray = new char[array.length - start];

    for (int i = 0; i < newArray.length; i++) {
      newArray[i] = array[i + start];
    }

    return newArray;
  }

  private static final HashMap<Class, Integer> typeResolveMap = new HashMap<Class, Integer>();

  static {
    Map<Class, Integer> t = typeResolveMap;
    t.put(BigDecimal.class, DataTypes.BIG_DECIMAL);
    t.put(BigInteger.class, DataTypes.BIG_INTEGER);
    t.put(String.class, DataTypes.STRING);

    t.put(int.class, INTEGER);
    t.put(Integer.class, DataTypes.W_INTEGER);

    t.put(short.class, DataTypes.SHORT);
    t.put(Short.class, DataTypes.W_SHORT);

    t.put(float.class, DataTypes.FLOAT);
    t.put(Float.class, DataTypes.W_FLOAT);

    t.put(double.class, DOUBLE);
    t.put(Double.class, DataTypes.W_DOUBLE);

    t.put(long.class, LONG);
    t.put(Long.class, DataTypes.W_LONG);

    t.put(boolean.class, DataTypes.BOOLEAN);
    t.put(Boolean.class, DataTypes.W_BOOLEAN);

    t.put(byte.class, DataTypes.BYTE);
    t.put(Byte.class, DataTypes.W_BYTE);

    t.put(char.class, DataTypes.CHAR);
    t.put(Character.class, DataTypes.W_CHAR);

    t.put(BlankLiteral.class, DataTypes.EMPTY);
  }

  public static int resolveType(Object o) {
    if (o == null) return DataTypes.OBJECT;
    else return __resolveType(o.getClass());
  }

  private static final Map<Class, Integer> typeCodes = new HashMap<Class, Integer>(30, 0.5f);

  static {
    typeCodes.put(Integer.class, DataTypes.W_INTEGER);
    typeCodes.put(Double.class, DataTypes.W_DOUBLE);
    typeCodes.put(Boolean.class, DataTypes.W_BOOLEAN);
    typeCodes.put(String.class, DataTypes.STRING);
    typeCodes.put(Long.class, DataTypes.W_LONG);
    typeCodes.put(Short.class, DataTypes.W_SHORT);
    typeCodes.put(Float.class, DataTypes.W_FLOAT);
    typeCodes.put(Byte.class, DataTypes.W_BYTE);
    typeCodes.put(Character.class, DataTypes.W_CHAR);

    typeCodes.put(BigDecimal.class, DataTypes.BIG_DECIMAL);
    typeCodes.put(BigInteger.class, DataTypes.BIG_INTEGER);

    typeCodes.put(int.class, DataTypes.INTEGER);
    typeCodes.put(double.class, DataTypes.DOUBLE);
    typeCodes.put(boolean.class, DataTypes.BOOLEAN);
    typeCodes.put(long.class, DataTypes.LONG);
    typeCodes.put(short.class, DataTypes.SHORT);
    typeCodes.put(float.class, DataTypes.FLOAT);
    typeCodes.put(byte.class, DataTypes.BYTE);
    typeCodes.put(char.class, DataTypes.CHAR);

    typeCodes.put(BlankLiteral.class, DataTypes.EMPTY);
  }

  /** 获取相应的内部类型信息,以进行类型处理 */
  public static int __resolveType(Class cls) {
    Integer code = typeCodes.get(cls);
    if (code == null) {
      if (cls != null && Collection.class.isAssignableFrom(cls)) {
        return DataTypes.COLLECTION;
      }
      else {
        return DataTypes.OBJECT;
      }
    }
    return code;
  }

  public static boolean isNumericallyCoercible(Class target, Class parm) {
    Class boxedTarget = target.isPrimitive() ? boxPrimitive(target) : target;

    if (boxedTarget != null && Number.class.isAssignableFrom(target)) {
      if ((boxedTarget = parm.isPrimitive() ? boxPrimitive(parm) : parm) != null) {
        return Number.class.isAssignableFrom(boxedTarget);
      }
    }
    return false;
  }

  /** 对相应的大数进行窄化处理,并返回窄化后的值, 包括 double,long和int */
  public static Object narrowType(final BigDecimal result, int returnTarget) {
    if (returnTarget == DataTypes.W_DOUBLE || result.scale() > 0) {
      return result.doubleValue();
    }
    else if (returnTarget == DataTypes.W_LONG || result.longValue() > Integer.MAX_VALUE) {
      return result.longValue();
    }
    else {
      return result.intValue();
    }
  }


  /** 读取指定方法接口或父类中受限类型为公共的方法 */
  public static Method determineActualTargetMethod(Method method) {
    return determineActualTargetMethod(method.getDeclaringClass(), method);
  }

  /** 读取指定方法接口或父类中受限类型为公共的方法(初始类为指定类) */
  private static Method determineActualTargetMethod(Class clazz, Method method) {
    String name = method.getName();

    /**
     * Follow our way up the class heirarchy until we find the physical target method.
     */
    for (Class cls : clazz.getInterfaces()) {
      for (Method meth : cls.getMethods()) {
        if (meth.getParameterTypes().length == 0 && name.equals(meth.getName())) {
          return meth;
        }
      }
    }

    return clazz.getSuperclass() != null ? determineActualTargetMethod(clazz.getSuperclass(), method) : null;
  }

  /** 找到下一个可以作为分隔的点，如空格 ( [ {等，但对于[，需要找到]位置 */
  public static int captureToNextTokenJunction(char[] expr, int cursor, int end, ParserContext pCtx) {
    while (cursor != expr.length) {
      switch (expr[cursor]) {
        case '{':
        case '(':
          return cursor;
        case '[':
          cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '[', pCtx) + 1;
          continue;
        default:
          if (isWhitespace(expr[cursor])) {
            return cursor;
          }
          cursor++;
      }
    }
    return cursor;
  }

  /** 找到下一个非空的字符 */
  public static int nextNonBlank(char[] expr, int cursor) {
    if ((cursor + 1) >= expr.length) {
      throw new CompileException("unexpected end of statement", expr, cursor);
    }
    int i = cursor;
    while (i != expr.length && isWhitespace(expr[i])) i++;
    return i;
  }

  /** 从指定的下标跳过空白字符及注释 */
  public static int skipWhitespace(char[] expr, int cursor) {
    Skip:
    while (cursor != expr.length) {
      switch (expr[cursor]) {
        case '\n':
        case '\r':
          cursor++;
          continue;
        case '/':
          if (cursor + 1 != expr.length) {
            switch (expr[cursor + 1]) {
              case '/':
                expr[cursor++] = ' ';
                while (cursor != expr.length && expr[cursor] != '\n') expr[cursor++] = ' ';
                if (cursor != expr.length) expr[cursor++] = ' ';
                continue;

              case '*':
                int len = expr.length - 1;
                expr[cursor++] = ' ';
                while (cursor != len && !(expr[cursor] == '*' && expr[cursor + 1] == '/')) {
                  expr[cursor++] = ' ';
                }
                if (cursor != len) expr[cursor++] = expr[cursor++] = ' ';
                continue;

              default:
                break Skip;

            }
          }
        default:
          if (!isWhitespace(expr[cursor])) break Skip;

      }
      cursor++;
    }

    return cursor;
  }

  /** 表示从当前下标起，有没有结束符的; 符号 */
  public static boolean isStatementNotManuallyTerminated(char[] expr, int cursor) {
    if (cursor >= expr.length) return false;
    int c = cursor;
    while (c != expr.length && isWhitespace(expr[c])) c++;
    return !(c != expr.length && expr[c] == ';');
  }


  public static int captureToEOS(char[] expr, int cursor, int end, ParserContext pCtx) {
    while (cursor != expr.length) {
      switch (expr[cursor]) {
        case '(':
        case '[':
        case '{':
          if ((cursor = balancedCaptureWithLineAccounting(expr, cursor, end, expr[cursor], pCtx)) >= expr.length)
            return cursor;
          break;

        case '"':
        case '\'':
          cursor = captureStringLiteral(expr[cursor], expr, cursor, expr.length);
          break;

        case ',':
        case ';':
        case '}':
          return cursor;
      }
      cursor++;
    }
    return cursor;
  }


  /**
   * From the specified cursor position, trim out any whitespace between the current position and the end of the
   * last non-whitespace character.
   *
   * @param expr  -
   * @param start -
   * @param pos   - current position
   * @return new position.
   */
  public static int trimLeft(char[] expr, int start, int pos) {
    if (pos > expr.length) pos = expr.length;
    while (pos != 0 && pos >= start && isWhitespace(expr[pos - 1])) pos--;
    return pos;
  }

  /**
   * From the specified cursor position, trim out any whitespace between the current position and beginning of the
   * first non-whitespace character.
   *
   * @param expr -
   * @param pos  -
   * @return -
   */
  public static int trimRight(char[] expr, int pos) {
    while (pos != expr.length && isWhitespace(expr[pos])) pos++;
    return pos;
  }

  public static char[] subArray(char[] expr, final int start, final int end) {
    if (start >= end) return new char[0];

    char[] newA = new char[end - start];
    for (int i = 0; i != newA.length; i++) {
      newA[i] = expr[i + start];
    }

    return newA;
  }


  /**
   * This is an important aspect of the core parser tools.  This method is used throughout the core parser
   * and sub-lexical parsers to capture a balanced capture between opening and terminating tokens such as:
   * <em>( [ { ' " </em>
   * <br>
   * <br>
   * For example: ((foo + bar + (bar - foo)) * 20;<br>
   * <br>
   * <p/>
   * If a balanced capture is performed from position 2, we get "(foo + bar + (bar - foo))" back.<br>
   * If a balanced capture is performed from position 15, we get "(bar - foo)" back.<br>
   * Etc.
   *
   * @param chars -
   * @param start -
   * @param type  -
   * @return -
   */
  public static int balancedCapture(char[] chars, int start, char type) {
    return balancedCapture(chars, start, chars.length, type);
  }

  /** 获取相对应捕获组的中间数据信息,并返回相对应位置的下标 */
  public static int balancedCapture(char[] chars, int start, int end, char type) {
    int depth = 1;
    char term = type;
    switch (type) {
      case '[':
        term = ']';
        break;
      case '{':
        term = '}';
        break;
      case '(':
        term = ')';
        break;
    }

    //如果两边的类型都相同，则认为可能是 类似于 " "这种，那么直接顺序查找即可
    if (type == term) {
      for (start++; start < end; start++) {
        if (chars[start] == type) {
          return start;
        }
      }
    }
    //两边的类型不相同，则是类似 [ ] 这种，但在中间又会碰到类似 [[ 的这种访问，则使用depth来表示实际递进的数据结构深度值
    else {
      for (start++; start < end; start++) {
        //处理注解，会跳过相应的注释信息
        if (start < end && chars[start] == '/') {
          if (start + 1 == end) return start;
          if (chars[start + 1] == '/') {
            start++;
            while (start < end && chars[start] != '\n') start++;
          }
          else if (chars[start + 1] == '*') {
            start += 2;
            SkipComment:
            while (start < end) {
              switch (chars[start]) {
                case '*':
                  if (start + 1 < end && chars[start + 1] == '/') {
                    break SkipComment;
                  }
                case '\r':
                case '\n':

                  break;
              }
              start++;
            }
          }
        }
        //到结尾
        if (start == end) return start;
        //处理字符串
        if (chars[start] == '\'' || chars[start] == '"') {
          start = captureStringLiteral(chars[start], chars, start, end);
        }
        //处理深度，即又碰到一个 [这种
        else if (chars[start] == type) {
          depth++;
        }
        //碰到反向的标记，如果depth为0,即表示已经到达正确的匹配点，这里则可以进行相应的数据返回
        else if (chars[start] == term && --depth == 0) {
          return start;
        }
      }
    }

    switch (type) {
      case '[':
        throw new CompileException("unbalanced braces [ ... ]", chars, start);
      case '{':
        throw new CompileException("unbalanced braces { ... }", chars, start);
      case '(':
        throw new CompileException("unbalanced braces ( ... )", chars, start);
      default:
        throw new CompileException("unterminated string literal", chars, start);
    }
  }

  /**
   * 这里是寻找一个与相应符号相对应的符号结束位置,如 [对应],{->} (->) 其它的则对应自己
   * 一般情况下，此方法只处理如 ( { [ 这种符号
   * 这个方法不能处理 " '符号，处理字符串需要采用 captureStringLiteral
   */
  public static int balancedCaptureWithLineAccounting(char[] chars, int start, int end, char type, ParserContext pCtx) {
    int depth = 1;
    int st = start;
    char term = type;
    switch (type) {
      case '[':
        term = ']';
        break;
      case '{':
        term = '}';
        break;
      case '(':
        term = ')';
        break;
    }

    if (type == term) {
      //这里处理除([{的情况
      for (start++; start != end; start++) {
        if (chars[start] == type) {
          return start;
        }
      }
    }
    else {
      int lines = 0;
      for (start++; start < end; start++) {
        //---------------------------- 处理空格及注释 start ------------------------------//
        if (isWhitespace(chars[start])) {
          switch (chars[start]) {
            case '\r':
              continue;
            case '\n':
              if (pCtx != null) pCtx.setLineOffset((short) start);
              lines++;
          }
        }
        else if (start < end && chars[start] == '/') {
          //到底末尾，忽略
          if (start + 1 == end) return start;
          //行注释
          if (chars[start + 1] == '/') {
            start++;
            while (start < end && chars[start] != '\n') start++;
          }
          // 段注释
          else if (chars[start + 1] == '*') {
            start += 2;
            Skiploop:
            while (start != end) {
              switch (chars[start]) {
                case '*':
                  if (start + 1 < end && chars[start + 1] == '/') {
                    break Skiploop;
                  }
                case '\r':
                case '\n':
                  if (pCtx != null) pCtx.setLineOffset((short) start);
                  lines++;
                  break;
              }
              start++;
            }
          }
        }
        //---------------------------- 处理空格及注释 start ------------------------------//

        if (start == end) return start;
        //特殊处理字符串,因为字符串也是一种类似于闭合结构,在这里直接跳过字符串
        if (chars[start] == '\'' || chars[start] == '"') {
          start = captureStringLiteral(chars[start], chars, start, end);
        }
        //增加递归处理，即 [[ 情况
        else if (chars[start] == type) {
          depth++;
        }
        //惟一退出的情况
        else if (chars[start] == term && --depth == 0) {
          if (pCtx != null) pCtx.incrementLineCount(lines);
          return start;
        }
      }
    }

    //因为没正常返回，最终报错
    switch (type) {
      case '[':
        throw new CompileException("unbalanced braces [ ... ]", chars, st);
      case '{':
        throw new CompileException("unbalanced braces { ... }", chars, st);
      case '(':
        throw new CompileException("unbalanced braces ( ... )", chars, st);
      default:
        throw new CompileException("unterminated string literal", chars, st);
    }
  }

  public static String handleStringEscapes(char[] input) {
    int escapes = 0;
    for (int i = 0; i < input.length; i++) {
      if (input[i] == '\\') {
        escapes += handleEscapeSequence(input, ++i);
      }
    }

    if (escapes == 0) return new String(input);

    char[] processedEscapeString = new char[input.length - escapes];
    int cursor = 0;
    for (char aName : input) {
      if (aName != 0) {
        processedEscapeString[cursor++] = aName;
      }
    }

    return new String(processedEscapeString);
  }

  /** 匹配字符串组 */
  public static int captureStringLiteral(final char type, final char[] expr, int cursor, int end) {
    //查找相对应的字符，并且不再处理像 注释的场景，处理逻辑与 balancedCaptureWithLineAccounting 相一致
    while (++cursor < end && expr[cursor] != type) {
      //如果这里是转义符，则强行跳过，以避免出现 \"的情况，这里是简化处理，因为像\ u zxxx并不完全处理
      if (expr[cursor] == '\\') cursor++;
    }

    if (cursor >= end || expr[cursor] != type) {
      throw new CompileException("unterminated string literal", expr, cursor);
    }

    return cursor;
  }


  public static void parseWithExpressions(String nestParm,
                                          char[] block,
                                          int start,
                                          int offset,
                                          Object ctx,
                                          VariableResolverFactory factory) {
    /**
     *
     * MAINTENANCE NOTE: A COMPILING VERSION OF THIS CODE IS DUPLICATED IN: WithNode
     *
     */
    int _st = start;
    int _end = -1;

    int end = start + offset;

    int oper = -1;
    String parm = "";


    for (int i = start; i < end; i++) {
      switch (block[i]) {
        case '{':
        case '[':
        case '(':
        case '\'':
        case '"':
          i = balancedCapture(block, i, end, block[i]);
          continue;


        case '/':
          if (i < end && block[i + 1] == '/') {
            while (i < end && block[i] != '\n') block[i++] = ' ';
            if (parm == null) _st = i;
          }
          else if (i < end && block[i + 1] == '*') {
            int len = end - 1;
            while (i < len && !(block[i] == '*' && block[i + 1] == '/')) {
              block[i++] = ' ';
            }
            block[i++] = ' ';
            block[i++] = ' ';

            if (parm == null) _st = i;
          }
          else if (i < end && block[i + 1] == '=') {
            oper = Operator.DIV;
          }
          continue;

        case '%':
        case '*':
        case '-':
        case '+':
          if (i + 1 < end && block[i + 1] == '=') {
            oper = opLookup(block[i]);
          }
          continue;

        case '=':
          parm = new String(block, _st, i - _st - (oper != -1 ? 1 : 0)).trim();
          _st = i + 1;
          continue;

        case ',':
          if (_end == -1) _end = i;

          if (parm == null) {
            try {
              if (nestParm == null) {
                MVEL.eval(new String(block, _st, _end - _st), ctx, factory);
              }
              else {
                MVEL.eval(new StringBuilder(nestParm).append('.')
                    .append(block, _st, _end - _st).toString(), ctx, factory);
              }
            }
            catch (CompileException e) {
              e.setCursor(_st + (e.getCursor() - (e.getExpr().length - offset)));
              e.setExpr(block);
              throw e;
            }

            oper = -1;
            _st = ++i;
          }
          else {
            try {
              if (oper != -1) {
                if (nestParm == null) {
                  throw new CompileException("operative assignment not possible here", block, start);
                }

                String rewrittenExpr = new String(
                    createShortFormOperativeAssignment(nestParm + "." + parm, block, _st, _end - _st, oper));

                MVEL.setProperty(ctx, parm, MVEL.eval(rewrittenExpr, ctx, factory));
              }
              else {
                MVEL.setProperty(ctx, parm, MVEL.eval(block, _st, _end - _st, ctx, factory));
              }
            }
            catch (CompileException e) {
              e.setCursor(_st + (e.getCursor() - (e.getExpr().length - offset)));
              e.setExpr(block);
              throw e;
            }

            parm = null;
            oper = -1;
            _st = ++i;
          }

          _end = -1;
          break;
      }
    }

    if (_st != (_end = end)) {
      try {
        if (parm == null || "".equals(parm)) {
          if (nestParm == null) {
            MVEL.eval(new String(block, _st, _end - _st), ctx, factory);
          }
          else {
            MVEL.eval(new StringAppender(nestParm).append('.')
                .append(block, _st, _end - _st).toString(), ctx, factory);
          }
        }
        else {
          if (oper != -1) {
            if (nestParm == null) {
              throw new CompileException("operative assignment not possible here", block, start);
            }

            MVEL.setProperty(ctx, parm,
                MVEL.eval(
                    new String(createShortFormOperativeAssignment(nestParm + "." + parm, block, _st, _end - _st, oper)),
                    ctx, factory
                )
            );
          }
          else {
            MVEL.setProperty(ctx, parm, MVEL.eval(block, _st, end - _st, ctx, factory));
          }
        }
      }
      catch (CompileException e) {
        e.setCursor(_st + (e.getCursor() - (e.getExpr().length - offset)));
        e.setExpr(block);
        throw e;
      }
    }
  }

  /** 处理类型转换，并且判断相应的实际类型 */
  public static Object handleNumericConversion(final char[] val, int start, int offset) {
    //以0开头，但并没有.，即表示是整数
    if (offset != 1 && val[start] == '0' && val[start + 1] != '.') {
      //末尾带标识，通过标记来进行判定
      if (!isDigit(val[start + offset - 1])) {
        switch (val[start + offset - 1]) {
          case 'L':
          case 'l':
            return Long.decode(new String(val, start, offset - 1));
          case 'I':
            return new BigInteger(new String(val, start, offset - 1));
          case 'B':
            return new BigDecimal(new String(val, start, offset - 1));
        }
      }

      return Integer.decode(new String(val, start, offset));
    }
    //末尾带标识，通过标记判断
    else if (!isDigit(val[start + offset - 1])) {
      switch (val[start + offset - 1]) {
        case 'l':
        case 'L':
          return java.lang.Long.parseLong(new String(val, start, offset - 1));
        case '.':
        case 'd':
        case 'D':
          return parseDouble(new String(val, start, offset - 1));
        case 'f':
        case 'F':
          return java.lang.Float.parseFloat(new String(val, start, offset - 1));
        case 'I':
          return new BigInteger(new String(val, start, offset - 1));
        case 'B':
          return new BigDecimal(new String(val, start, offset - 1));
      }
      throw new CompileException("unrecognized numeric literal", val, start);
    }
    else {
      //通过数字测试来进行,即猜一下
      switch (numericTest(val, start, offset)) {
        case DataTypes.FLOAT:
          return java.lang.Float.parseFloat(new String(val, start, offset));
        case INTEGER:
          return java.lang.Integer.parseInt(new String(val, start, offset));
        case LONG:
          return java.lang.Long.parseLong(new String(val, start, offset));
        case DOUBLE:
          return parseDouble(new String(val, start, offset));
        case DataTypes.BIG_DECIMAL:
          return new BigDecimal(val, MathContext.DECIMAL128);
        default:
          return new String(val, start, offset);
      }
    }
  }

  public static boolean isNumeric(Object val) {
    if (val == null) return false;

    Class clz;
    if (val instanceof Class) {
      clz = (Class) val;
    }
    else {
      clz = val.getClass();
    }

    return clz == int.class || clz == long.class || clz == short.class || clz == double.class ||
        clz == float.class || Number.class.isAssignableFrom(clz);
  }

  public static int numericTest(final char[] val, int start, int offset) {
    boolean fp = false;

    char c;
    int i = start;

    if (offset > 1) {
      if (val[start] == '-') i++;
      else if (val[start] == '~') {
        i++;
        if (val[start + 1] == '-') i++;
      }
    }

    int end = start + offset;
    for (; i < end; i++) {
      if (!isDigit(c = val[i])) {
        switch (c) {
          case '.':
            fp = true;
            break;
          case 'e':
          case 'E':
            fp = true;
            if (i++ < end && val[i] == '-') i++;
            break;

          default:
            return -1;
        }
      }
    }

    if (offset != 0) {
      if (fp) {
        return DOUBLE;
      }
      else if (offset > 9) {
        return LONG;
      }
      else {
        return INTEGER;
      }
    }
    return -1;
  }

  /** 判定一个对象是否是数字或者可以转换为数字,这里的转换为实际转换,而不是自定义转换 */
  public static boolean isNumber(Object val) {
    if (val == null) return false;
    if (val instanceof String) return isNumber((String) val);
    if (val instanceof char[]) return isNumber(new String((char[]) val));
    return val instanceof Integer || val instanceof BigDecimal || val instanceof BigInteger
        || val instanceof Float || val instanceof Double || val instanceof Long
        || val instanceof Short || val instanceof Character;
  }

  /** 判定一个字符串是否可以转换为数字 */
  public static boolean isNumber(final String val) {
    int len = val.length();
    char c;
    boolean f = true;
    int i = 0;
    if (len > 1) {
      if (val.charAt(0) == '-') i++;
      else if (val.charAt(0) == '~') {
        i++;
        if (val.charAt(1) == '-') i++;
      }
    }
    for (; i < len; i++) {
      if (!isDigit(c = val.charAt(i))) {
        if (c == '.' && f) {
          f = false;
        }
        else {
          return false;
        }
      }
    }

    return len > 0;
  }

  public static boolean isNumber(char[] val, int start, int offset) {
    char c;
    boolean f = true;
    int i = start;
    int end = start + offset;
    if (offset > 1) {
      switch (val[start]) {
        case '-':
          if (val[start + 1] == '-') i++;
        case '~':
          i++;
      }
    }
    for (; i < end; i++) {
      if (!isDigit(c = val[i])) {
        if (f && c == '.') {
          f = false;
        }
        else if (offset != 1 && i == start + offset - 1) {
          switch (c) {
            case 'l':
            case 'L':
            case 'f':
            case 'F':
            case 'd':
            case 'D':
            case 'I':
            case 'B':
              return true;
            case '.':
              throw new CompileException("invalid number literal: " + new String(val), val, start);
          }
          return false;
        }
        else if (i == start + 1 && c == 'x' && val[start] == '0') {
          for (i++; i < end; i++) {
            if (!isDigit(c = val[i])) {
              if ((c < 'A' || c > 'F') && (c < 'a' || c > 'f')) {
                if (i == offset - 1) {
                  switch (c) {
                    case 'l':
                    case 'L':
                    case 'I':
                    case 'B':
                      return true;
                  }
                }

                return false;
              }
            }
          }
          return offset - 2 > 0;

        }
        else if (i != start && (i + 1) < end && (c == 'E' || c == 'e')) {
          if (val[++i] == '-' || val[i] == '+') i++;
        }
        else {
          if (i != start)
            throw new CompileException("invalid number literal: " + new String(val, start, offset), val, start);
          return false;
        }
      }
    }

    return end > start;
  }

  public static int find(char[] c, int start, int offset, char find) {
    int length = start + offset;
    for (int i = start; i < length; i++) if (c[i] == find) return i;
    return -1;
  }

  /** 从后往前找1个字符的位置信息 */
  public static int findLast(char[] c, int start, int offset, char find) {
    for (int i = start + offset; i >= start; i--) if (c[i] == find) return i;
    return -1;
  }

  /** 匹配一个连接的字符串(即直到空格结束) */
  public static String createStringTrimmed(char[] s) {
    int start = 0, end = s.length;
    while (start != end && s[start] < '\u0020' + 1) start++;
    while (end != start && s[end - 1] < '\u0020' + 1) end--;
    return new String(s, start, end - start);
  }

  /**
   * 匹配一个连接的字符串(即直到空格结束)
   * 准确的说,是在一个连续的字符串中从最前端和最后面进行双向处理,找到中间一段值,可以理解为 trim的简化版
   */
  public static String createStringTrimmed(char[] s, int start, int length) {
    if ((length = start + length) > s.length) return new String(s);
    while (start != length && s[start] < '\u0020' + 1) {
      start++;
    }
    while (length != start && s[length - 1] < '\u0020' + 1) {
      length--;
    }
    return new String(s, start, length - start);
  }

  /** 判断指定的字符串(字符数组)是否以指定的后缀(以字符数组表示)结尾 */
  public static boolean endsWith(char[] c, int start, int offset, char[] test) {
    if (test.length > c.length) return false;

    int tD = test.length - 1;
    int cD = start + offset - 1;

    while (tD >= 0) {
      if (c[cD--] != test[tD--]) return false;
    }

    return true;
  }

  /** 判定当前符号是否是一个有效的操作数，即包括 字母，数字 _ $等 */
  public static boolean isIdentifierPart(final int c) {
    return ((c > 96 && c < 123)//小写字母
        //大写字母 数字
        || (c > 64 && c < 91) || (c > 47 && c < 58) || (c == '_') || (c == '$')
        || Character.isJavaIdentifierPart(c));
  }

  public static boolean isDigit(final int c) {
    return c > ('0' - 1) && c < ('9' + 1);
  }

  public static float similarity(String s1, String s2) {
    if (s1 == null || s2 == null)
      return s1 == null && s2 == null ? 1f : 0f;

    char[] c1 = s1.toCharArray();
    char[] c2 = s2.toCharArray();

    char[] comp;
    char[] against;

    float same = 0;
    float baselength;

    int cur1 = 0;

    if (c1.length > c2.length) {
      baselength = c1.length;
      comp = c1;
      against = c2;
    }
    else {
      baselength = c2.length;
      comp = c2;
      against = c1;
    }

    while (cur1 < comp.length && cur1 < against.length) {
      if (comp[cur1] == against[cur1]) {
        same++;
      }

      cur1++;
    }

    return same / baselength;
  }

  /** 查找到字符串数组针对于set时，其正确的结尾属性,如 a.b,其结尾为a,并且相应的位置为 a.的位置，b则为要处理的属性 */
  public static int findAbsoluteLast(char[] array) {
    int depth = 0;
    for (int i = array.length - 1; i >= 0; i--) {
      if (array[i] == ']') {
        depth++;
      }
      if (array[i] == '[') {
        depth--;
      }

      if (depth == 0 && array[i] == '.' || array[i] == '[') return i;
    }
    return -1;
  }

  /**
   * 返回指定类型的子类型
   * 如果是数组,则返回数组的包装类型,并作迭代判断,否则返回类型本身
   * 最终返回一个不是数组的类型
   */
  public static Class getBaseComponentType(Class cls) {
    while (cls.isArray()) {
      cls = cls.getComponentType();
    }
    return cls;
  }

  /**
   * 返回指定类型的子类型
   * 如果是数组,则返回数组的包装类型,否则返回类型本身
   * 这里与base不同，这里如果是数组，仅降维一层
   */
  public static Class getSubComponentType(Class cls) {
    if (cls.isArray()) {
      cls = cls.getComponentType();
    }
    return cls;
  }

  /** 此字符是否是一个空格或[( 这样的连接2个语句的字符 */
  public static boolean isJunct(char c) {
    switch (c) {
      case '[':
      case '(':
        return true;
      default:
        return isWhitespace(c);
    }
  }

  /** 尝试将当前符号查找为操作符 */
  public static int opLookup(char c) {
    switch (c) {
      case '|':
        return Operator.BW_OR;
      case '&':
        return Operator.BW_AND;
      case '^':
        return Operator.BW_XOR;
      case '*':
        return Operator.MULT;
      case '/':
        return Operator.DIV;
      case '+':
        return Operator.ADD;
      case '%':
        return Operator.MOD;
      case '\u00AB':
        return Operator.BW_SHIFT_LEFT;
      case '\u00BB':
        return Operator.BW_SHIFT_RIGHT;
      case '\u00AC':
        return Operator.BW_USHIFT_RIGHT;
    }
    return -1;
  }

  /**
   * Check if the specified string is a reserved word in the parser.
   *
   * @param name -
   * @return -
   */
  public static boolean isReservedWord(String name) {
    return LITERALS.containsKey(name) || AbstractParser.OPERATORS.containsKey(name);
  }

  /**
   * 检查字符串是否合法(作为字段标识)
   * Check if the specfied string represents a valid name of label.
   *
   * @param name -
   * @return -
   */
  public static boolean isNotValidNameorLabel(String name) {
    for (char c : name.toCharArray()) {
      if (c == '.') return true;
      else if (!isIdentifierPart(c)) return true;
    }
    return false;
  }

  /** 判断当前字符串是否只是字段元素 */
  public static boolean isPropertyOnly(char[] array, int start, int end) {
    for (int i = start; i < end; i++) {
      if (!isIdentifierPart(array[i])) return false;
    }
    return true;
  }

  /** 判断当前字符串是否是数组类型 */
  public static boolean isArrayType(char[] array, int start, int end) {
    return end > start + 2 && isPropertyOnly(array, start, end - 2) && array[end - 2] == '[' && array[end - 1] == ']';
  }

  /** 检查一个字符串作为参数是否合法(即不能是关键字或者首位不能是数字) */
  public static void checkNameSafety(String name) {
    if (isReservedWord(name)) {
      throw new RuntimeException("illegal use of reserved word: " + name);
    }
    else if (isDigit(name.charAt(0))) {
      throw new RuntimeException("not an identifier: " + name);
    }
  }

  public static FileWriter getDebugFileWriter() throws IOException {
    return new FileWriter(new File(getDebuggingOutputFileName()), true);
  }

  public static boolean isPrimitiveWrapper(Class clazz) {
    return clazz == Integer.class || clazz == Boolean.class || clazz == Long.class || clazz == Double.class
        || clazz == Float.class || clazz == Character.class || clazz == Short.class || clazz == Byte.class;
  }

  /** 对指定的字符数组进行一段编译,sub表示此编译是整个外层编译的一部分 */
  public static Serializable subCompileExpression(char[] expression) {
    return _optimizeTree(new ExpressionCompiler(expression)._compile());
  }

  /** 对指定的字符数组+编译上下文进行一段编译,sub即子编译 */
  public static Serializable subCompileExpression(char[] expression, ParserContext ctx) {
    ExpressionCompiler c = new ExpressionCompiler(expression, ctx);
    return _optimizeTree(c._compile());
  }

  /** 对指定区间的字符数组+上下文进行子编译 */
  public static Serializable subCompileExpression(char[] expression, int start, int offset, ParserContext ctx) {
    ExpressionCompiler c = new ExpressionCompiler(expression, start, offset, ctx);
    return _optimizeTree(c._compile());
  }

  /** 对指定的字符串+上下文进行子编译 */
  public static Serializable subCompileExpression(String expression, ParserContext ctx) {
    ExpressionCompiler c = new ExpressionCompiler(expression, ctx);
    return _optimizeTree(c._compile());
  }

  public static Serializable optimizeTree(final CompiledExpression compiled) {
    /**
     * If there is only one token, and it's an identifier, we can optimize this as an accessor expression.
     */
    if (!compiled.isImportInjectionRequired() &&
        compiled.getParserConfiguration().isAllowBootstrapBypass() && compiled.isSingleNode()) {

      return _optimizeTree(compiled);
    }

    return compiled;
  }

  private static Serializable _optimizeTree(final CompiledExpression compiled) {
    /**
     * If there is only one token, and it's an identifier, we can optimize this as an accessor expression.
     */
    if (compiled.isSingleNode()) {
      ASTNode tk = compiled.getFirstNode();

      if (tk.isLiteral() && !tk.isThisVal()) {
        return new ExecutableLiteral(tk.getLiteralValue());
      }
      return tk.canSerializeAccessor() ? new ExecutableAccessorSafe(tk, compiled.getKnownEgressType()) :
          new ExecutableAccessor(tk, compiled.getKnownEgressType());
    }

    return compiled;
  }

  public static boolean isWhitespace(char c) {
    return c < '\u0020' + 1;
  }

  public static String repeatChar(char c, int times) {
    char[] n = new char[times];
    for (int i = 0; i < times; i++) {
      n[i] = c;
    }
    return new String(n);
  }

  /** 使用系统默认编码读取文件，并返回相应的字符信息 */
  public static char[] loadFromFile(File file) throws IOException {
    return loadFromFile(file, null);
  }

  /** 使用指定编码读取文件，并返回相应的字符内容 */
  public static char[] loadFromFile(File file, String encoding) throws IOException {
    if (!file.exists())
      throw new RuntimeException("cannot find file: " + file.getName());

    FileInputStream inStream = null;
    ReadableByteChannel fc = null;
    try {
      fc = (inStream = new FileInputStream(file)).getChannel();
      ByteBuffer buf = allocateDirect(10);

      StringAppender sb = new StringAppender((int) file.length(), encoding);

      int read = 0;
      while (read >= 0) {
        buf.rewind();
        read = fc.read(buf);
        buf.rewind();

        for (; read > 0; read--) {
          sb.append(buf.get());
        }
      }

      //noinspection unchecked
      return sb.toChars();
    }
    catch (FileNotFoundException e) {
      // this can't be thrown, we check for this explicitly.
    }
    finally {
      if (inStream != null) inStream.close();
      if (fc != null) fc.close();
    }

    return null;
  }

  public static char[] readIn(InputStream inStream, String encoding) throws IOException {
    try {
      byte[] buf = new byte[10];

      StringAppender sb = new StringAppender(10, encoding);

      int bytesRead;
      while ((bytesRead = inStream.read(buf)) > 0) {
        for (int i = 0; i < bytesRead; i++) {
          sb.append(buf[i]);
        }
      }

      //noinspection unchecked
      return sb.toChars();
    }
    finally {
      if (inStream != null) inStream.close();
    }
  }

  public static Class forNameWithInner(String className, ClassLoader classLoader) throws ClassNotFoundException {
    try {
      return Class.forName(className, true, classLoader);
    }
    catch (ClassNotFoundException cnfe) {
      return findInnerClass(className, classLoader, cnfe);
    }
  }

  public static Class findInnerClass(String className, ClassLoader classLoader, ClassNotFoundException cnfe) throws ClassNotFoundException {
    for (int lastDotPos = className.lastIndexOf('.'); lastDotPos > 0; lastDotPos = className.lastIndexOf('.')) {
      className = className.substring(0, lastDotPos) + "$" + className.substring(lastDotPos + 1);
      try {
        return Class.forName(className, true, classLoader);
      }
      catch (ClassNotFoundException e) { /* ignore */ }
    }
    throw cnfe;
  }
}
