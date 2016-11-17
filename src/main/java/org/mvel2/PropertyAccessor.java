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
package org.mvel2;

import org.mvel2.ast.*;
import org.mvel2.integration.GlobalListenerFactory;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.util.ErrorUtil;
import org.mvel2.util.MethodStub;
import org.mvel2.util.ParseTools;
import org.mvel2.util.StringAppender;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Thread.currentThread;
import static java.lang.reflect.Array.getLength;
import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;
import static org.mvel2.MVEL.eval;
import static org.mvel2.ast.TypeDescriptor.getClassReference;
import static org.mvel2.compiler.AbstractParser.LITERALS;
import static org.mvel2.integration.GlobalListenerFactory.notifySetListeners;
import static org.mvel2.integration.PropertyHandlerFactory.*;
import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.PropertyTools.getFieldOrAccessor;
import static org.mvel2.util.PropertyTools.getFieldOrWriteAccessor;
import static org.mvel2.util.ReflectionUtil.toNonPrimitiveType;
import static org.mvel2.util.Varargs.normalizeArgsForVarArgs;
import static org.mvel2.util.Varargs.paramTypeVarArgsSafe;


@SuppressWarnings({"unchecked"})
/**
 * The property accessor class is used for extracting properties from objects instances.
 * 使用解析模式来访问相应的对象属性信息，包括字段，方法，以及数组信息引用等
 * 因为它的工作模式是解释运行的，因此
 */
public class PropertyAccessor {
  /** 从属性的哪个下标位置开始处理 */
  private int start = 0;
  /** 逐步解析时按访问递增的下标值 */
  private int cursor = 0;
  /** 上一次处理完毕之后一个有效的下标位置(除去各种解析符号) */
  private int st;

  /** 要处理的属性信息 */
  private char[] property;
  /** 要处理的属性的长度信息 */
  private int length;
  /** 要处理的属性的截止位置 */
  private int end;

  /** 相应的this对象引用 */
  private Object thisReference;
  /** 当前的对象上下文 */
  private Object ctx;
  /** 在解析过程中随着解析的进行，相应的当前对象信息 */
  private Object curr;
  /** 解析过程中的当前对象的类型 */
  private Class currType = null;

  /** 是否处于最开始的解析位置，以支持特定的语法(如this) */
  private boolean first = true;
  /** 是否支持 .? 的处理方式，即支持不能解析时返回null */
  private boolean nullHandle = false;

  /** 变量作用域解析工厂 */
  private VariableResolverFactory variableFactory;
  /** 相应的解析上下文 */
  private ParserContext pCtx;

  //  private static final int DONE = -1;
  /** 普通的属性访问 */
  private static final int NORM = 0;
  /** 方法调用 */
  private static final int METH = 1;
  /** 集合信息或数组访问 */
  private static final int COL = 2;
  /** 使用with语法访问(不作处理) */
  private static final int WITH = 3;

  /** 特定标识，表示空参数信息 */
  private static final Object[] EMPTYARG = new Object[0];

  /** 用于描述读取指定类的属性信息的缓存，相应的二级key采用的是属性字符串的hashcode值 */
  private static final Map<Class, WeakHashMap<Integer, WeakReference<Member>>> READ_PROPERTY_RESOLVER_CACHE;
  /** 用于描述写入相应的类的属性信息的缓存，相应的二级key采用属性字符串hashcode */
  private static final Map<Class, WeakHashMap<Integer, WeakReference<Member>>> WRITE_PROPERTY_RESOLVER_CACHE;
  /** 方法调用缓存，二级key使用方法名和脚本中原始的参数信息 */
  private static final Map<Class, WeakHashMap<Integer, WeakReference<Object[]>>> METHOD_RESOLVER_CACHE;
  /** 方法的参数类型缓存，缓存相应的方法的参数类型信息 */
  private static final Map<Member, WeakReference<Class[]>> METHOD_PARMTYPES_CACHE;

  static {
    READ_PROPERTY_RESOLVER_CACHE = Collections.synchronizedMap(new WeakHashMap<Class, WeakHashMap<Integer, WeakReference<Member>>>(10));
    WRITE_PROPERTY_RESOLVER_CACHE = Collections.synchronizedMap(new WeakHashMap<Class, WeakHashMap<Integer, WeakReference<Member>>>(10));
    METHOD_RESOLVER_CACHE = Collections.synchronizedMap(new WeakHashMap<Class, WeakHashMap<Integer, WeakReference<Object[]>>>(10));
    METHOD_PARMTYPES_CACHE = Collections.synchronizedMap(new WeakHashMap<Member, WeakReference<Class[]>>(10));
  }

  public PropertyAccessor(String property, Object ctx) {
    this.length = end = (this.property = property.toCharArray()).length;
    this.ctx = ctx;
    this.variableFactory = new ImmutableDefaultFactory();
  }

  public PropertyAccessor(char[] property, Object ctx, VariableResolverFactory resolver, Object thisReference, ParserContext pCtx) {
    this.length = end = (this.property = property).length;
    this.ctx = ctx;
    this.variableFactory = resolver;
    this.thisReference = thisReference;
    this.pCtx = pCtx;
  }

  public PropertyAccessor(char[] property, int start, int offset, Object ctx, VariableResolverFactory resolver, Object thisReference, ParserContext pCtx) {
    this.property = property;
    this.cursor = this.st = this.start = start;
    this.length = offset;
    this.end = start + offset;
    this.ctx = ctx;
    this.variableFactory = resolver;
    this.thisReference = thisReference;
    this.pCtx = pCtx;
  }

  /** 通过单次调用通过对整个属性进行解析并使用指定的上下文进行解析 */
  public static Object get(String property, Object ctx) {
    return new PropertyAccessor(property, ctx).get();
  }

  /** 静态方法，通过单次调用相应的解释语句来获取相应的处理结果 */
  public static Object get(char[] property, int offset, int end, Object ctx, VariableResolverFactory resolver, Object thisReferece, ParserContext pCtx) {
    return new PropertyAccessor(property, offset, end, ctx, resolver, thisReferece, pCtx).get();
  }

  /** 通过使用相应的上下文，变量解析工厂，当前对象引用，解析上下文对属性进行解析，并获取相应的值 */
  public static Object get(String property, Object ctx, VariableResolverFactory resolver, Object thisReference, ParserContext pCtx) {
    return new PropertyAccessor(property.toCharArray(), ctx, resolver, thisReference, pCtx).get();
  }

  /** 使用指定的上下文通过对指定的属性进行解析，并设置相应的值 */
  public static void set(Object ctx, String property, Object value) {
    new PropertyAccessor(property, ctx).set(value);
  }

  /** 使用指定的上下文,指定的变量解析工厂，解析上下文通过对指定的属性进行解析，并设置相应的值 */
  public static void set(Object ctx, VariableResolverFactory resolver, String property, Object value, ParserContext pCtx) {
    new PropertyAccessor(property.toCharArray(), ctx, resolver, null, pCtx).set(value);
  }

  /** 获取相应的解释运行结果 */
  private Object get() {
    //逐步推进
    curr = ctx;

    try {
      //根据是否支持属性访问和null处理重写来使用不同的处理逻辑
      if (!MVEL.COMPILER_OPT_ALLOW_OVERRIDE_ALL_PROPHANDLING) {
        //正常的访问方式
        return getNormal();
      }
      else {
        //支持重写的访问方式
        return getAllowOverride();
      }
    }
    catch (InvocationTargetException e) {
      throw new PropertyAccessException("could not access property", property, cursor, e, pCtx);
    }
    catch (IllegalAccessException e) {
      throw new PropertyAccessException("could not access property", property, cursor, e, pCtx);
    }
    catch (IndexOutOfBoundsException e) {
      if (cursor >= length) cursor = length - 1;

      throw new PropertyAccessException("array or collections index out of bounds in property: "
          + new String(property, cursor, length), property, cursor, e, pCtx);
    }
    catch (CompileException e) {
      throw ErrorUtil.rewriteIfNeeded(e, property, st);
    }
    catch (NullPointerException e) {
      throw new PropertyAccessException("null pointer exception in property: " + new String(property), property, cursor, e, pCtx);
    }
    catch (Exception e) {
      throw new PropertyAccessException("unknown exception in expression: " + new String(property), property, cursor, e, pCtx);
    }
  }

  /** 使用正常的解析方式进行属性访问 */
  private Object getNormal() throws Exception {
    while (cursor < end) {
      switch (nextToken()) {
        //属性访问，即表示获取对象的属性信息
        case NORM:
          curr = getBeanProperty(curr, capture());
          break;
        //方法调用
        case METH:
          curr = getMethod(curr, capture());
          break;
        //集合属性访问
        case COL:
          curr = getCollectionProperty(curr, capture());
          break;
        //with语法支持
        case WITH:
          curr = getWithProperty(curr);
          break;
      }

      if (nullHandle) {
        if (curr == null) {
          return null;
        }
        else {
          nullHandle = false;
        }
      }

      first = false;
    }
    return curr;
  }

  /** 按照支持重写方法的方式进行解析并处理 */
  private Object getAllowOverride() throws Exception {
    while (cursor < end) {
      switch (nextToken()) {
        //正常的属性处理
        case NORM:
          if ((curr = getBeanPropertyAO(curr, capture())) == null && hasNullPropertyHandler()) {
            //如果有null处理器，则使用其处理
            curr = getNullPropertyHandler().getProperty(capture(), ctx, variableFactory);
          }
          break;
        //方法调用
        case METH:
          if ((curr = getMethod(curr, capture())) == null && hasNullMethodHandler()) {
            curr = getNullMethodHandler().getProperty(capture(), ctx, variableFactory);
          }
          break;
        //集合处理
        case COL:
          curr = getCollectionPropertyAO(curr, capture());
          break;
        //with语法处理
        case WITH:
          curr = getWithProperty(curr);
          break;
      }

      //如果返回的值为null,并且允许null处理，那么直接返回null，否则就认为已没有null处理。
      // 并且如果当前并没有解析完，就表示再继续解析就会出现相应的NPE异常，因此后面则直接报相应的错误信息
      if (nullHandle) {
        if (curr == null) {
          return null;
        }
        else {
          nullHandle = false;
        }
      }
      else {
        if (curr == null && cursor < end) throw new NullPointerException();
      }

      first = false;
    }
    return curr;
  }

  /** 对相应的属性访问表达式进行设置值操作 */
  private void set(Object value) {
    curr = ctx;

    try {
      //临时暂存结尾下标，待处理再还原回来
      int oLength = end;

      //重新定位到要处理的属性的下标位置
      end = findAbsoluteLast(property);

      //按照最新的end，将前面的数据值信息获取出来，如 a.b.c 则表示先要获取a.b属性，然后再设置c属性的值
      if ((curr = get()) == null)
        throw new PropertyAccessException("cannot bind to null context: " + new String(property, cursor, length), property, cursor, pCtx);

      end = oLength;

      //数组，集合说
      if (nextToken() == COL) {
        //跳过 [符号
        int _start = ++cursor;

        whiteSpaceSkip();

        if (cursor == length || scanTo(']'))
          throw new PropertyAccessException("unterminated '['", property, cursor, pCtx);

        //获取这里实际的属性值
        String ex = new String(property, _start, cursor - _start);

        //按照没有重写的方法执行,即先解析后面的数据，然后按照不同的类型进行不同的处理
        if (!MVEL.COMPILER_OPT_ALLOW_OVERRIDE_ALL_PROPHANDLING) {
          //map处理
          if (curr instanceof Map) {
            //noinspection unchecked
            ((Map) curr).put(eval(ex, this.ctx, this.variableFactory), value);
          }
          //list处理
          else if (curr instanceof List) {
            //noinspection unchecked
            ((List) curr).set(eval(ex, this.ctx, this.variableFactory, Integer.class), value);
          }
          //如果当前类有相应的属性处理器，则使用自己的处理器来处理
          else if (hasPropertyHandler(curr.getClass())) {
            getPropertyHandler(curr.getClass()).setProperty(ex, ctx, variableFactory, value);
          }
          //数组
          else if (curr.getClass().isArray()) {
            Array.set(curr, eval(ex, this.ctx, this.variableFactory, Integer.class), convert(value, getBaseComponentType(curr.getClass())));
          }
          else {
            throw new PropertyAccessException("cannot bind to collection property: "
                + new String(property) + ": not a recognized collection type: " + ctx.getClass(),
                property, cursor, pCtx);
          }

          return;
        }
        //按照支持重写的方式处理
        else {
          notifySetListeners(ctx, ex, variableFactory, value);

          //map处理
          if (curr instanceof Map) {
            //noinspection unchecked
            if (hasPropertyHandler(Map.class))
              getPropertyHandler(Map.class).setProperty(ex, curr, variableFactory, value);
            else
              ((Map) curr).put(eval(ex, this.ctx, this.variableFactory), value);
          }
          //list处理
          else if (curr instanceof List) {
            //noinspection unchecked
            if (hasPropertyHandler(List.class))
              getPropertyHandler(List.class).setProperty(ex, curr, variableFactory, value);
            else
              ((List) curr).set(eval(ex, this.ctx, this.variableFactory, Integer.class), value);
          }
          //支持数组
          else if (curr.getClass().isArray()) {
            if (hasPropertyHandler(Array.class))
              getPropertyHandler(Array.class).setProperty(ex, curr, variableFactory, value);
            else
              Array.set(curr, eval(ex, this.ctx, this.variableFactory, Integer.class), convert(value, getBaseComponentType(curr.getClass())));
          }
          //使用自定义的属性处理器来进行处理
          else if (hasPropertyHandler(curr.getClass())) {
            getPropertyHandler(curr.getClass()).setProperty(ex, curr, variableFactory, value);
          }
          else {
            throw new PropertyAccessException("cannot bind to collection property: " + new String(property)
                + ": not a recognized collection type: " + ctx.getClass(), property, cursor, pCtx);
          }

          return;
        }
      }
      //不是集合，则应该为相应的属性，这里先尝试使用自定义的属性访问来进行处理
      else if (MVEL.COMPILER_OPT_ALLOW_OVERRIDE_ALL_PROPHANDLING && hasPropertyHandler(curr.getClass())) {
        getPropertyHandler(curr.getClass()).setProperty(capture(), curr, variableFactory, value);
        return;
      }

      String tk = capture();

      //使用缓存来获取相应的成员信息，加快处理速度
      Member member = checkWriteCache(curr.getClass(), tk == null ? 0 : tk.hashCode());
      if (member == null) {
        addWriteCache(curr.getClass(), tk != null ? tk.hashCode() : -1,
            (member = value != null ? getFieldOrWriteAccessor(curr.getClass(), tk, value.getClass()) : getFieldOrWriteAccessor(curr.getClass(), tk)));
      }

      //如果成员为一个方法，则认为此方法只带一个参数，并且相应的参数值即为传入的数据信息
      if (member instanceof Method) {
        Method meth = (Method) member;

        Class[] parameterTypes = checkParmTypesCache(meth);

        //处理相应的值类型的转换，即存在可能的类型转换，转换之后再进行相应的方法调用
        if (value != null && !parameterTypes[0].isAssignableFrom(value.getClass())) {
          if (!canConvert(parameterTypes[0], value.getClass())) {
            throw new CompileException("cannot convert type: "
                + value.getClass() + ": to " + meth.getParameterTypes()[0], property, cursor);
          }
          meth.invoke(curr, convert(value, parameterTypes[0]));
        }
        else {
          meth.invoke(curr, value);
        }
      }
      //成员并不为null,不是方法即就是字段信息，因此按字段信息的方式来处理
      else if (member != null) {
        Field fld = (Field) member;

        //根据值类型进行相应的类型转换，然后再进行相应的处理
        if (value != null && !fld.getType().isAssignableFrom(value.getClass())) {
          if (!canConvert(fld.getType(), value.getClass())) {
            throw new CompileException("cannot convert type: "
                + value.getClass() + ": to " + fld.getType(), property, cursor);
          }

          fld.set(curr, convert(value, fld.getType()));
        }
        else {
          fld.set(curr, value);
        }
      }
      //没有成员信息，可能是 map的 a.b 这种形式，则尝试进行map.put处理
      else if (curr instanceof Map) {
        //noinspection unchecked
        ((Map) curr).put(eval(tk, this.ctx, this.variableFactory), value);
      }
      //支持函数实例对象
      else if (curr instanceof FunctionInstance) {
        ((PrototypalFunctionInstance) curr).getResolverFactory().getVariableResolver(tk).setValue(value);
      }
      //其它情况，则直接报异常错误
      else {
        throw new PropertyAccessException("could not access/write property (" + tk + ") in: "
            + (curr == null ? "Unknown" : curr.getClass().getName()), property, cursor, pCtx);
      }
    }
    catch (InvocationTargetException e) {
      throw new PropertyAccessException("could not access property", property, st, e, pCtx);
    }
    catch (IllegalAccessException e) {
      throw new PropertyAccessException("could not access property", property, st, e, pCtx);
    }
  }


  /** 获取下一个有效节点的类型信息 */
  private int nextToken() {
    //先尝试根据第1个有效字符进行判定
    switch (property[st = cursor]) {
      //碰到[,即表示数组或集合访问
      case '[':
        return COL;

      //特殊的with语法
      case '{':
        if (property[cursor - 1] == '.') {
          return WITH;
        }
        break;

      //碰到. 可能是 .? 也可能是 .{ 也可能是其它
      case '.':
        // ++cursor;
        //跳过空白
        while (cursor < end && isWhitespace(property[cursor])) cursor++;

        if ((st + 1) != end) {
          switch (property[cursor = ++st]) {
            // .? 语法，即支持 空处理
            case '?':
              cursor = ++st;
              nullHandle = true;
              break;
            // .{ 语法，with处理
            case '{':
              return WITH;
          }

        }

        //直接 ？，也是一种特殊的 空处理，即以当前对象作为上下文，获取可能的属性信息
      case '?':
        if (cursor == start) {
          cursor = ++st;
          nullHandle = true;
        }
    }

    //第1个字符不是特征字符，即表示可能是属性，因此继续往后查找，以找到一个连续的调用，如 a.b 或 a.b[2] 这种的调用
    //这里找到第1个有效的. 调用，以继续处理后续的访问
    do {
      while (cursor < end && isWhitespace(property[cursor])) cursor++;

      if (cursor < end && property[cursor] == '.') {
        cursor++;
      }
      else {
        break;
      }
    }
    while (true);

    st = cursor;

    //跳过后续的属性值，即字段或者是方法名
    //noinspection StatementWithEmptyBody
    while (++cursor < end && isJavaIdentifierPart(property[cursor])) ;

    //接下来根据相应的字符判定是否是哪种调用
    if (cursor < end) {
      while (isWhitespace(property[cursor])) cursor++;
      switch (property[cursor]) {
        //数组访问
        case '[':
          return COL;
        //方法调用
        case '(':
          return METH;
        //不是，那就认为是正常的属性访问
        default:
          return 0;
      }
    }

    //默认也是属性访问，即 NORM
    return 0;
  }

  /** 捕获当前访问到的处理块 */
  private String capture() {
    return new String(property, st, trimLeft(cursor) - st);
  }

  /** 从当前下标进行回退，退到一个不是空格的字符为止,作用在于类似获取一个字符串再调用trim一样 */
  protected int trimLeft(int pos) {
    while (pos > 0 && isWhitespace(property[pos - 1])) pos--;
    return pos;
  }

  /** 无用方法，清除临时缓存 */
  @Deprecated
  public static void clearPropertyResolverCache() {
    READ_PROPERTY_RESOLVER_CACHE.clear();
    WRITE_PROPERTY_RESOLVER_CACHE.clear();
    METHOD_RESOLVER_CACHE.clear();
  }

  /** 无用方法，报告相应的缓存长度信息 */
  @Deprecated
  public static void reportCacheSizes() {
    System.out.println("read property cache: " + READ_PROPERTY_RESOLVER_CACHE.size());
    for (Class cls : READ_PROPERTY_RESOLVER_CACHE.keySet()) {
      System.out.println(" [" + cls.getName() + "]: " + READ_PROPERTY_RESOLVER_CACHE.get(cls).size() + " entries.");
    }
    System.out.println("write property cache: " + WRITE_PROPERTY_RESOLVER_CACHE.size());
    for (Class cls : WRITE_PROPERTY_RESOLVER_CACHE.keySet()) {
      System.out.println(" [" + cls.getName() + "]: " + WRITE_PROPERTY_RESOLVER_CACHE.get(cls).size() + " entries.");
    }
    System.out.println("method cache: " + METHOD_RESOLVER_CACHE.size());
    for (Class cls : METHOD_RESOLVER_CACHE.keySet()) {
      System.out.println(" [" + cls.getName() + "]: " + METHOD_RESOLVER_CACHE.get(cls).size() + " entries.");
    }
  }

  /** 在相应的读缓存中添加相应的类的成员信息 */
  private static void addReadCache(Class cls, Integer property, Member member) {
    synchronized (READ_PROPERTY_RESOLVER_CACHE) {
      WeakHashMap<Integer, WeakReference<Member>> nestedMap = READ_PROPERTY_RESOLVER_CACHE.get(cls);

      if (nestedMap == null) {
        READ_PROPERTY_RESOLVER_CACHE.put(cls, nestedMap = new WeakHashMap<Integer, WeakReference<Member>>());
      }

      nestedMap.put(property, new WeakReference<Member>(member));
    }
  }

  /** 尝试从读类缓存中获取相应的成员信息 */
  private static Member checkReadCache(Class cls, Integer property) {
    WeakHashMap<Integer, WeakReference<Member>> map = READ_PROPERTY_RESOLVER_CACHE.get(cls);
    if (map != null) {
      WeakReference<Member> member = map.get(property);
      if (member != null) return member.get();
    }
    return null;
  }

  /** 添加相应的写入类属性的缓存信息 */
  private static void addWriteCache(Class cls, Integer property, Member member) {
    synchronized (WRITE_PROPERTY_RESOLVER_CACHE) {
      WeakHashMap<Integer, WeakReference<Member>> map = WRITE_PROPERTY_RESOLVER_CACHE.get(cls);
      if (map == null) {
        WRITE_PROPERTY_RESOLVER_CACHE.put(cls, map = new WeakHashMap<Integer, WeakReference<Member>>());
      }
      map.put(property, new WeakReference<Member>(member));
    }
  }

  /** 检查从写属性缓存中获取相应的成员信息 */
  private static Member checkWriteCache(Class cls, Integer property) {
    Map<Integer, WeakReference<Member>> map = WRITE_PROPERTY_RESOLVER_CACHE.get(cls);
    if (map != null) {
      WeakReference<Member> member = map.get(property);
      if (member != null) return member.get();
    }
    return null;
  }

  /** 检查从方法的参数类型信息缓存中获取相应的类型信息值 */
  public static Class[] checkParmTypesCache(Method member) {
    WeakReference<Class[]> pt = METHOD_PARMTYPES_CACHE.get(member);
    Class[] ret;
    if (pt == null || (ret = pt.get()) == null) {
      //noinspection UnusedAssignment
      METHOD_PARMTYPES_CACHE.put(member, pt = new WeakReference<Class[]>(ret = member.getParameterTypes()));
    }
    return ret;
  }


  /** 添加相应的方法缓存信息 */
  private static void addMethodCache(Class cls, Integer property, Method member) {
    synchronized (METHOD_RESOLVER_CACHE) {
      WeakHashMap<Integer, WeakReference<Object[]>> map = METHOD_RESOLVER_CACHE.get(cls);
      if (map == null) {
        METHOD_RESOLVER_CACHE.put(cls, map = new WeakHashMap<Integer, WeakReference<Object[]>>());
      }
      map.put(property, new WeakReference<Object[]>(new Object[]{member, member.getParameterTypes()}));
    }
  }

  private static Object[] checkMethodCache(Class cls, Integer property) {
    Map<Integer, WeakReference<Object[]>> map = METHOD_RESOLVER_CACHE.get(cls);
    if (map != null) {
      WeakReference<Object[]> ref = map.get(property);
      if (ref != null) return ref.get();
    }
    return null;
  }

  /** 使用支持类型重写的处理方式来获取属性信息 */
  private Object getBeanPropertyAO(Object ctx, String property)
      throws IllegalAccessException, InvocationTargetException {
    if (ctx != null && hasPropertyHandler(ctx.getClass()))
      return getPropertyHandler(ctx.getClass()).getProperty(property, ctx, variableFactory);

    GlobalListenerFactory.notifyGetListeners(ctx, property, variableFactory);

    return getBeanProperty(ctx, property);
  }

  /** 获取相应的上下文的属性值信息 */
  private Object getBeanProperty(Object ctx, String property)
      throws IllegalAccessException, InvocationTargetException {

    //如果是首次解析访问，支持first访问,以及各种常量，变量工厂等访问，即认为首次的取值范围以后续的取值更大
    if (first) {
      //特殊的this变量，即支持在 语句首访问
      if ("this".equals(property)) {
        return this.ctx;
      }
      //常量值
      else if (LITERALS.containsKey(property)) {
        return LITERALS.get(property);
      }
      //解析变量工厂中存在此值,如参数变量工厂等
      else if (variableFactory != null && variableFactory.isResolveable(property)) {
        return variableFactory.getVariableResolver(property).getValue();
      }
    }

    //有上下文，则从上下文中获取数据
    if (ctx != null) {
      //获取当前的对象信息
      Class<?> cls;
      if (ctx instanceof Class) {
        //直接访问.class 信息
        if (MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS
            && "class".equals(property)) {
          return ctx;
        }

        cls = (Class<?>) ctx;
      }
      else {
        cls = ctx.getClass();
      }

      //采用get or create的方式来获取相应的成员信息，仍可能是null的
      Member member = checkReadCache(cls, property.hashCode());

      if (member == null) {
        addReadCache(cls, property.hashCode(), member = getFieldOrAccessor(cls, property));
      }

      //如果是方法，则尝试使用空参数调用，即类似于 a.b()的方式
      if (member instanceof Method) {
        try {
          return ((Method) member).invoke(ctx, EMPTYARG);
        }
        catch (IllegalAccessException e) {
          //出现这个异常，就表示访问权限不够，则修改权限之后再访问
          synchronized (member) {
            try {
              ((Method) member).setAccessible(true);
              return ((Method) member).invoke(ctx, EMPTYARG);
            }
            finally {
              ((Method) member).setAccessible(false);
            }
          }
        }
        catch (IllegalArgumentException e) {
          //如果报这个异常，则可能是参数信息不正确 或者是其它异常
          if (member.getDeclaringClass().equals(ctx)) {
            try {
              Class c = Class.forName(member.getDeclaringClass().getName() + "$" + property);

              throw new CompileException("name collision between innerclass: " + c.getCanonicalName()
                  + "; and bean accessor: " + property + " (" + member.toString() + ")", this.property, this.st);
            }
            catch (ClassNotFoundException e2) {
              //fallthru
            }
          }
          throw e;
        }
      }
      //成员是有值的，不是方法，那么肯定就是字段
      else if (member != null) {
        //转换基本类型为非基本的，以方便后续使用
        currType = toNonPrimitiveType(((Field) member).getType());
        return ((Field) member).get(ctx);
      }
      //无此成员，即表示没有这个属性 如果是map,并且相应的属性前面有个?，则表示可以从map中获取这个属性，如果没有，就返回null
      else if (ctx instanceof Map && (((Map) ctx).containsKey(property) || nullHandle)) {
        //原型调用
        if (ctx instanceof Proto.ProtoInstance) {
          return ((Proto.ProtoInstance) ctx).get(property).call(null, thisReference, variableFactory, EMPTY_OBJ_ARR);
        }
        //直接使用map数据调用
        return ((Map) ctx).get(property);
      }
      //支持数组类型的length属性调用
      else if ("length".equals(property) && ctx.getClass().isArray()) {
        return getLength(ctx);
      }
      //支持直接方法当前上下文类中方法，即上下文是一个类，则支持直接访问相应的方法信息 如 A.abc
      else if (ctx instanceof Class) {
        Class c = (Class) ctx;
        for (Method m : c.getMethods()) {
          if (property.equals(m.getName())) {
            //伪方法调用
            if (pCtx != null && pCtx.getParserConfiguration() != null ? pCtx.getParserConfiguration().isAllowNakedMethCall() : MVEL.COMPILER_OPT_ALLOW_NAKED_METH_CALL) {
              return m.invoke(ctx, EMPTY_OBJ_ARR);
            }
            return m;
          }
        }

        //当前上下文是类，则也可能是一个内部的类信息, 这里尝试查找一个内部类信息
        try {
          return findClass(variableFactory, c.getName() + "$" + property, pCtx);
        }
        catch (ClassNotFoundException cnfe) {
          // fall through.
        }
      }
      //使用特别的属性处理器来处理该类
      else if (hasPropertyHandler(cls)) {
        return getPropertyHandler(cls).getProperty(property, ctx, variableFactory);
      }
      //函数实例调用
      else if (ctx instanceof FunctionInstance) {
        return ((PrototypalFunctionInstance) ctx).getResolverFactory().getVariableResolver(property).getValue();
      }
    }

    //没有上下文，则尝试直接使用此属性获取一个静态的数据
    Object tryStatic = tryStaticAccess();

    //静态资源分3种，类，方法，字段，如果是字段，则返回字段的值信息
    if (tryStatic != null) {
      if (tryStatic instanceof Class || tryStatic instanceof Method) return tryStatic;
      else {
        return ((Field) tryStatic).get(null);
      }
    }
    //如果支持伪方法调用，则使用方法调用
    else if (pCtx != null && pCtx.getParserConfiguration() != null ? pCtx.getParserConfiguration().isAllowNakedMethCall() : MVEL.COMPILER_OPT_ALLOW_NAKED_METH_CALL) {
      return getMethod(ctx, property);
    }

    //均没有得到相应的值，则直接报错

    if (ctx == null) {
      throw new PropertyAccessException("unresolvable property or identifier: " + property, this.property, st, pCtx);
    }
    else {
      throw new PropertyAccessException("could not access: " + property + "; in class: " + ctx.getClass().getName(), this.property, st, pCtx);
    }
  }

  /** 跳过空白字符 */
  private void whiteSpaceSkip() {
    if (cursor < end)
      //noinspection StatementWithEmptyBody
      while (isWhitespace(property[cursor]) && ++cursor < end) ;
  }

  /**
   * 访问字符串, 尝试一直递进,直到查到指定的字符为止
   *
   * @param c - character to scan to.
   * @return - returns true is end of statement is hit, false if the scan scar is countered.
   */
  private boolean scanTo(char c) {
    for (; cursor < end; cursor++) {
      switch (property[cursor]) {
        //特殊处理字符串
        case '\'':
        case '"':
          cursor = captureStringLiteral(property[cursor], property, cursor, end);
        default:
          if (property[cursor] == c) {
            return false;
          }
      }

    }
    return true;
  }

  /** 进行特殊的with语法访问 */
  private Object getWithProperty(Object ctx) {
    int st;

    String nestParm = start == cursor ? null : new String(property, start, cursor - start - 1).trim();

    parseWithExpressions(nestParm, property, st = cursor + 1,
        (cursor = balancedCaptureWithLineAccounting(property, cursor, end,
            '{', pCtx)) - st, ctx, variableFactory);
    cursor++;
    return ctx;
  }

  /**
   * 处理调用[语法进行集合，数组或者是map访问
   * Handle accessing a property embedded in a collections, map, or array
   *
   * @param ctx  -
   * @param prop -
   * @return -
   * @throws Exception -
   */
  private Object getCollectionProperty(Object ctx, String prop) throws Exception {
    //如果本身就有相应的属性信息，就表示是类似 a[ 的这种处理方式，那么先读取到a属性，然后再继续处理相应的[后面的调用
    if (prop.length() != 0) {
      ctx = getBeanProperty(ctx, prop);
      if (ctx == null) {
        throw new NullPointerException("null pointer on indexed access for: " + prop);
      }
    }

    currType = null;

    int _start = ++cursor;

    whiteSpaceSkip();

    //从[ 读到 后面的 ] 符号中
    if (cursor == end || scanTo(']'))
      throw new PropertyAccessException("unterminated '['", property, cursor, pCtx);

    //这里的prop才是真正[ ] 括号里面的属性或数据信息
    prop = new String(property, _start, cursor++ - _start);


    //map调用方式，则使用map.get方法
    if (ctx instanceof Map) {
      return ((Map) ctx).get(eval(prop, ctx, variableFactory));
    }
    //支持按下标访问的集合，使用list.get，里面的数据则认为是一个整数信息
    else if (ctx instanceof List) {
      return ((List) ctx).get((Integer) eval(prop, ctx, variableFactory));
    }
    //如果并不是list，但又是集合，则按照迭代的方式进行访问
    else if (ctx instanceof Collection) {
      int count = (Integer) eval(prop, ctx, variableFactory);
      if (count > ((Collection) ctx).size())
        throw new PropertyAccessException("index [" + count + "] out of bounds on collections", property, cursor, pCtx);

      Iterator iter = ((Collection) ctx).iterator();
      for (int i = 0; i < count; i++) iter.next();
      return iter.next();
    }
    //数据访问
    else if (ctx.getClass().isArray()) {
      return Array.get(ctx, (Integer) eval(prop, ctx, variableFactory));
    }
    //支持特殊的字符串访问方式，如"abc"[2]
    else if (ctx instanceof CharSequence) {
      return ((CharSequence) ctx).charAt((Integer) eval(prop, ctx, variableFactory));
    }
    else {
      try {
        //这里认为相应的信息为一个数组类信息,如 [java.lang.String 这种
        return getClassReference(pCtx, (Class) ctx, new TypeDescriptor(property, start, length, 0));
      }
      catch (Exception e) {
        throw new PropertyAccessException("illegal use of []: unknown type: " + (ctx.getClass().getName()), property, st, e, pCtx);
      }
    }
  }

  /** 按照支持重写的方式读取相应的集合属性信息 */
  private Object getCollectionPropertyAO(Object ctx, String prop) throws Exception {
    //相应的prop不为null,这里认为先读取到集合对象本身，即 a.b 时先获取到a的数据值
    if (prop.length() != 0) {
      ctx = getBeanProperty(ctx, prop);
    }

    currType = null;
    if (ctx == null) return null;

    int _start = ++cursor;

    whiteSpaceSkip();

    if (cursor == end || scanTo(']'))
      throw new PropertyAccessException("unterminated '['", property, cursor, pCtx);

    //重新定位相应的属性信息,即实际要访问的属性，即[ ] 中间的部分信息
    prop = new String(property, _start, cursor++ - _start);

    //map 访问
    if (ctx instanceof Map) {
      if (hasPropertyHandler(Map.class))
        return getPropertyHandler(Map.class).getProperty(prop, ctx, variableFactory);
      else
        return ((Map) ctx).get(eval(prop, ctx, variableFactory));
    }
    //list 方法
    else if (ctx instanceof List) {
      if (hasPropertyHandler(List.class))
        return getPropertyHandler(List.class).getProperty(prop, ctx, variableFactory);
      else
        return ((List) ctx).get((Integer) eval(prop, ctx, variableFactory));
    }
    //集合，通过迭代的方式进行访问
    else if (ctx instanceof Collection) {
      if (hasPropertyHandler(Collection.class))
        return getPropertyHandler(Collection.class).getProperty(prop, ctx, variableFactory);
      else {
        int count = (Integer) eval(prop, ctx, variableFactory);
        if (count > ((Collection) ctx).size())
          throw new PropertyAccessException("index [" + count + "] out of bounds on collections",
              property, cursor, pCtx);

        Iterator iter = ((Collection) ctx).iterator();
        for (int i = 0; i < count; i++) iter.next();
        return iter.next();
      }
    }
    //数组处理
    else if (ctx.getClass().isArray()) {
      if (hasPropertyHandler(Array.class))
        return getPropertyHandler(Array.class).getProperty(prop, ctx, variableFactory);

      return Array.get(ctx, (Integer) eval(prop, ctx, variableFactory));
    }
    //支持特殊的字符串访问
    else if (ctx instanceof CharSequence) {
      if (hasPropertyHandler(CharSequence.class))
        return getPropertyHandler(CharSequence.class).getProperty(prop, ctx, variableFactory);
      else
        return ((CharSequence) ctx).charAt((Integer) eval(prop, ctx, variableFactory));
    }
    else {
      try {
        //尝试解析为数组类 引用
        return getClassReference(pCtx, (Class) ctx, new TypeDescriptor(property, start, end - start, 0));
      }
      catch (Exception e) {
        throw new PropertyAccessException("illegal use of []: unknown type: " + (ctx.getClass().getName()), property, st, pCtx);
      }
    }
  }


  /**
   * 进行相应的方法调用
   * Find an appropriate method, execute it, and return it's response.
   *
   * @param ctx  -
   * @param name -
   * @return -
   */
  @SuppressWarnings({"unchecked"})
  private Object getMethod(Object ctx, String name) {
    int _start = cursor;

    //找到整个参数串，是从(开始，到相应的) 结束的字符串组
    String tk = cursor != end
        && property[cursor] == '(' && ((cursor = balancedCapture(property, cursor, '(')) - _start) > 1 ?
        new String(property, _start + 1, cursor - _start - 1) : "";

    cursor++;

    Object[] args;
    //空参数组
    if (tk.length() == 0) {
      args = ParseTools.EMPTY_OBJ_ARR;
    }
    else {
      //分割参数信息，并且每个参数都使用this变量进行解析出来，即先解析出要使用的参数信息
      List<char[]> subtokens = parseParameterList(tk.toCharArray(), 0, -1);
      args = new Object[subtokens.size()];
      for (int i = 0; i < subtokens.size(); i++) {
        args[i] = eval(subtokens.get(i), thisReference, variableFactory);
      }
    }

    //如果是开头调用，则支持从变量工厂中获取相应的引用信息,即直接使用的 abc()这种调用
    if (first && variableFactory != null && variableFactory.isResolveable(name)) {
      Object ptr = variableFactory.getVariableResolver(name).getValue();
      //方法
      if (ptr instanceof Method) {
        ctx = ((Method) ptr).getDeclaringClass();
        name = ((Method) ptr).getName();
      }
      //方法句柄
      else if (ptr instanceof MethodStub) {
        ctx = ((MethodStub) ptr).getClassReference();
        name = ((MethodStub) ptr).getMethodName();
      }
      //函数引用
      else if (ptr instanceof FunctionInstance) {
        ((FunctionInstance) ptr).getFunction().checkArgumentCount(args.length);
        return ((FunctionInstance) ptr).call(null, thisReference, variableFactory, args);
      }
      else {
        throw new OptimizationFailure("attempt to optimize a method call for a reference that does not point to a method: "
            + name + " (reference is type: " + (ctx != null ? ctx.getClass().getName() : null) + ")");
      }

      first = false;
    }

    //不支持没有上下文的直接调用，因此如果没有上下文，那么方法肯定在变量工厂中，而在上面的判断中已经描述出变量工厂中也没有此值
    if (ctx == null) throw new CompileException("no such method or function: " + name, property, cursor);

    /**
     * If the target object is an instance of java.lang.Class itself then do not
     * adjust the Class scope target.
     */
    Class cls = currType != null ? currType : ((ctx instanceof Class ? (Class) ctx : ctx.getClass()));
    currType = null;

    //支持原型实例
    if (cls == Proto.ProtoInstance.class) {
      return ((Proto.ProtoInstance) ctx).get(name).call(null, thisReference, variableFactory, args);
    }

    //使用缓存解析相应的方法
    /**
     * Check to see if we have already cached this method;
     */
    Object[] cache = checkMethodCache(cls, createSignature(name, tk));

    Method m;
    Class[] parameterTypes;

    if (cache != null) {
      m = (Method) cache[0];
      parameterTypes = (Class[]) cache[1];
    }
    else {
      m = null;
      parameterTypes = null;
    }

    //尝试解析相应的方法信息
    /**
     * If we have not cached the method then we need to go ahead and try to resolve it.
     */
    if (m == null) {
      /**
       * Try to find an instance method from the class target.
       */
      if ((m = getBestCandidate(args, name, cls, cls.getMethods(), false)) != null) {
        addMethodCache(cls, createSignature(name, tk), m);
        parameterTypes = m.getParameterTypes();
      }

      if (m == null) {
        /**
         * If we didn't find anything, maybe we're looking for the actual java.lang.Class methods.
         */
        if ((m = getBestCandidate(args, name, cls, cls.getDeclaredMethods(), false)) != null) {
          addMethodCache(cls, createSignature(name, tk), m);
          parameterTypes = m.getParameterTypes();
        }
      }
    }

    //上面的代码从public 方法中没有找到相应的方法，这里尝试从当前类的私有方法列表中查找
    // If we didn't find anything and the declared class is different from the actual one try also with the actual one
    if (m == null && cls != ctx.getClass() && !(ctx instanceof Class)) {
      cls = ctx.getClass();
      if ((m = getBestCandidate(args, name, cls, cls.getDeclaredMethods(), false)) != null) {
        addMethodCache(cls, createSignature(name, tk), m);
        parameterTypes = m.getParameterTypes();
      }
    }

    //支持原型函数对象
    if (ctx instanceof PrototypalFunctionInstance) {
      final VariableResolverFactory funcCtx = ((PrototypalFunctionInstance) ctx).getResolverFactory();
      Object prop = funcCtx.getVariableResolver(name).getValue();
      if (prop instanceof PrototypalFunctionInstance) {
        return ((PrototypalFunctionInstance) prop).call(ctx, thisReference, new InvokationContextFactory(variableFactory, funcCtx), args);
      }
    }

    //还没有找到方法，则尝试支持几个特殊的方法调用
    if (m == null) {
      StringAppender errorBuild = new StringAppender();
      for (int i = 0; i < args.length; i++) {
        errorBuild.append(args[i] != null ? args[i].getClass().getName() : null);
        if (i < args.length - 1) errorBuild.append(", ");
      }

      //支持数组的size()方法调用
      if ("size".equals(name) && args.length == 0 && cls.isArray()) {
        return getLength(ctx);
      }

//      System.out.println("{ " + new String(property) + " }");

      throw new PropertyAccessException("unable to resolve method: "
          + cls.getName() + "." + name + "(" + errorBuild.toString() + ") [arglength=" + args.length + "]"
          , property, st, pCtx);
    }
    else {
      //定位到相应的方法
      //这里将形参转换为实际的参数信息，以处理变长参数
      for (int i = 0; i < args.length; i++) {
        args[i] = convert(args[i], paramTypeVarArgsSafe(parameterTypes, i, m.isVarArgs()));
      }

      /**
       * Invoke the target method and return the response.
       */
      currType = toNonPrimitiveType(m.getReturnType());
      try {
        return m.invoke(ctx, normalizeArgsForVarArgs(parameterTypes, args, m.isVarArgs()));
      }
      catch (IllegalAccessException e) {
        try {
          //调用出错，则尝试使用更宽化的方法调用,即调用了private方法，那么就尝试使用另一个更宽类型的方法来调用，以处理可能存在的多态方法
          addMethodCache(cls, createSignature(name, tk), (m = getWidenedTarget(m)));

          return m.invoke(ctx, args);
        }
        catch (Exception e2) {
          throw new PropertyAccessException("unable to invoke method: " + name, property, cursor, e2, pCtx);
        }
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new PropertyAccessException("unable to invoke method: " + name, property, cursor, e, pCtx);
      }
    }
  }

  /** 创建出一个名字以及相应的参数值信息共同组成的签名信息 */
  private static int createSignature(String name, String args) {
    return name.hashCode() + args.hashCode();
  }

  private ClassLoader getClassLoader() {
    return pCtx != null ? pCtx.getClassLoader() : currentThread().getContextClassLoader();
  }

  /**
   * 尝试从当前的语句中获取出一个静态的类，方法，字段信息
   * Try static access of the property, and return an instance of the Field, Method of Class if successful.
   *
   * @return - Field, Method or Class instance.
   */
  protected Object tryStaticAccess() {
    int begin = cursor;
    try {
      /**
       * Try to resolve this *smartly* as a static class reference.
       *
       * This starts at the end of the token and starts to step backwards to figure out whether
       * or not this may be a static class reference.  We search for method calls simply by
       * inspecting for ()'s.  The first union area we come to where no brackets are present is our
       * test-point for a class reference.  If we find a class, we pass the reference to the
       * property accessor along  with trailing methods (if any).
       *
       */
      boolean meth = false;
      int last = end;
      //从后往前查找
      for (int i = end - 1; i > start; i--) {
        switch (property[i]) {
          //碰到一个点，则尝试分别从类，方法，字段来处理
          case '.':
            if (!meth) {
              try {
                String test = new String(property, start, (cursor = last) - start);

                //碰到一个.class 属性，则认为前面的就是一个类名,则尝试进行加载之
                if (MVEL.COMPILER_OPT_SUPPORT_JAVA_STYLE_CLASS_LITERALS &&
                    test.endsWith(".class")) test = test.substring(0, test.length() - 6);

                return getClassLoader().loadClass(test);
              }
              catch (ClassNotFoundException e) {
                //分别尝试字段或方法，挨个方法
                Class cls = getClassLoader().loadClass(new String(property, start, i - start));
                String name = new String(property, i + 1, end - i - 1);
                try {
                  return cls.getField(name);
                }
                catch (NoSuchFieldException nfe) {
                  for (Method m : cls.getMethods()) {
                    if (name.equals(m.getName())) return m;
                  }
                  return null;
                }
              }
            }

            meth = false;
            last = i;
            break;

          //如果碰到 },则尝试,往回至 {符号处
          case '}':
            i--;
            for (int d = 1; i > 0 && d != 0; i--) {
              switch (property[i]) {
                case '}':
                  d++;
                  break;
                case '{':
                  d--;
                  break;
                case '"':
                case '\'':
                  char s = property[i];
                  while (i > 0 && (property[i] != s && property[i - 1] != '\\')) i--;
              }
            }
            break;

          //如果找到 ),则尝试回至 ( 符号处
          case ')':
            i--;

            for (int d = 1; i > 0 && d != 0; i--) {
              switch (property[i]) {
                case ')':
                  d++;
                  break;
                case '(':
                  d--;
                  break;
                case '"':
                case '\'':
                  char s = property[i];
                  while (i > 0 && (property[i] != s && property[i - 1] != '\\')) i--;
              }
            }

            meth = true;
            last = i++;
            break;

          //如果是字符串，则回至字符串开头处
          case '\'':
            while (--i > 0) {
              if (property[i] == '\'' && property[i - 1] != '\\') {
                break;
              }
            }
            break;

          case '"':
            while (--i > 0) {
              if (property[i] == '"' && property[i - 1] != '\\') {
                break;
              }
            }
            break;
        }
      }
    }
    catch (Exception cnfe) {
      cursor = begin;
    }

    return null;
  }
}
