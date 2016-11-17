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

import org.mvel2.ast.Function;
import org.mvel2.ast.LineLabel;
import org.mvel2.ast.Proto;
import org.mvel2.compiler.AbstractParser;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.Parser;
import org.mvel2.integration.Interceptor;
import org.mvel2.util.LineMapper;
import org.mvel2.util.MethodStub;
import org.mvel2.util.ReflectionUtil;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;

/**
 * 描述一个在编译过程中所使用的解析上下文，相应的在编译过程中所使用的一些临时变量，定义等都存储在上下文中
 * 由于在编译中存储级联关系，因为上下文也存在parent关系，即内层会引用外层的信息，以达到一个scope的语义概念
 * 上下文可以在进行编译的过程中进行引入，否则将采用默认的上下文
 * The <tt>ParserContext</tt> is the main environment object used for sharing state throughout the entire
 * parser/compileShared process.<br/><br/>
 * The <tt>ParserContext</tt> is used to configure the parser/compiler.  For example:
 * <pre><code>
 * ParserContext parserContext = new ParserContext();
 * parserContext.setStrongTyping(true); // turn on strong typing.
 * <p/>
 * Serializable comp = MVEL.compileExpression("foo.bar", parserContext);
 * </code</pre>
 */
public class ParserContext implements Serializable {
  /** 基于指定的文件进行编译时相应的原文件引用 */
  private String sourceFile;

  /** 基于多行时相应的文件行数 */
  private int lineCount = 1;
  /** 设置基于多行,当前位置所距行首的距离 */
  private int lineOffset;

  /** 父上下文,即在解析过程中,也存在递归解析的过程 */
  private ParserContext parent;
  /** 相应的解析配置 */
  private ParserConfiguration parserConfiguration;

  /** 无用属性 */
  private Object evaluationContext;

  /**
   * 存放当前有顺序的输入变量信息,顺序保证变量不会随机分布，之后可以根据此顺序查找具体值
   * 因为这里专指输入参数,因此在最终执行时,相应的变量工厂也会以相同的顺序存储相应的变量解析器
   * 同时此顺序也与在具体执行的作用域的变量顺序相一致
   */
  private ArrayList<String> indexedInputs;
  /** 存放当前有顺序的临时变量信息,顺序保证当前变量不会随机分布,之后可以根据此顺序查找具体值 */
  private ArrayList<String> indexedLocals;
  /**
   * 变量作用域,描述每个变量是否在指定的变量域中,此变量作用域为编译期作用域
   * 此字段的作用在于可见性,即描述指定的变量在哪一些是可见的,即在哪一层中之前有出现过
   */
  private ArrayList<Set<String>> variableVisibility;

  /** 变量声明以及变量类型定义 */
  private HashMap<String, Class> variables;
  /** 表示需要接收的参数入参信息以及入参类型信息(这些信息需要从外部传入或者是外部必须存在 */
  private Map<String, Class> inputs;

  /** 每个变量的类型中类型参数信息,即用于处理<T>等泛型信息 */
  private transient HashMap<String, Map<String, Type>> typeParameters;
  /** 临时存储,用于描述如泛型信息的参数类型,比如参数名的多个类型信息,如Map<K,V>中的类型信息 */
  private transient Type[] lastTypeParameters;
  /** 描述全局函数信息 */
  private HashMap<String, Function> globalFunctions;

  /** 描述在编译过程中的错误信息 */
  private transient List<ErrorDetail> errorList;

  /** 表达式对应表达式行的一些映射(调试使用) */
  private transient Map<String, LineMapper.LineLookup> sourceLineLookups;
  /** 描述已经在编译过程中处理过的行(调试使用) */
  private transient Map<String, Set<Integer>> visitedLines;

  /** 最新的代码行(调试使用) */
  private LineLabel lastLineLabel;

  /** 最底层解析器,实际上没有被使用 */
  @Deprecated
  private transient Parser rootParser;
  /** 缓存的编译表达式 */
  private transient Map<String, CompiledExpression> compiledExpressionCache;
  /** 针对指定的编译表达式，缓存相应的返回类型 */
  private transient Map<String, Class> returnTypeCache;

  /** 当前是否是一个函数上下文,即正在一个解析函数的过程当中 */
  private boolean functionContext = false;
  /** 无用字段 */
  @Deprecated
  private boolean compiled = false;
  /** 是否是严格类型调用的，即相应的泛型也严格处理 */
  private boolean strictTypeEnforcement = false;
  /** 是否是强类型处理 */
  private boolean strongTyping = false;
  /** 设置当前的优化状态，表示正在进行优化 */
  private boolean optimizationMode = false;

  /** 表示在过程中是否有严重的错误发生 */
  private boolean fatalError = false;
  /** 无用字段 */
  @Deprecated
  private boolean retainParserState = false;
  /** 是否有调试标识 */
  private boolean debugSymbols = false;
  /** 语法块标识,用于临时进行标识,无特殊作用 */
  private boolean blockSymbols = false;
  /** 无用字段 */
  @Deprecated
  private boolean executableCodeReached = false;
  /**
   * 是否在解析过程中允许按下标进行变量存储和分配,即开启变量工厂的下标处理模式
   * 或者是认为在处理过程中,是否允许添加新的变量信息
   */
  private boolean indexAllocation = false;
  /** 判断在处理中，是否使用了新的变量,或者是相应的变量有作更新操作处理,即从当前上下文中有获取变量的动作 */
  protected boolean variablesEscape = false;

  public ParserContext() {
    parserConfiguration = new ParserConfiguration();
  }

  public ParserContext(boolean debugSymbols) {
    this();
    this.debugSymbols = debugSymbols;
  }

  @Deprecated
  public ParserContext(Parser rootParser) {
    this();
    this.rootParser = rootParser;
  }

  /** 通过一个外界的解析配置信息初始化上下文 */
  public ParserContext(ParserConfiguration parserConfiguration) {
    this.parserConfiguration = parserConfiguration;
  }

  /** 此方法实际无效 */
  @Deprecated
  public ParserContext(ParserConfiguration parserConfiguration, Object evaluationContext) {
    this(parserConfiguration);
    this.evaluationContext = evaluationContext;
  }

  /** 通过一个已有的解析配置+父类上下文+当前是否为函数上下文构建起新的解析配置对象 */
  public ParserContext(ParserConfiguration parserConfiguration, ParserContext parent, boolean functionContext) {
    this(parserConfiguration);
    this.parent = parent;
    this.functionContext = functionContext;
  }

  /** 使用一个针对对象引入+拦截器构建的解析配置,以及相应的脚本源文构建起解析上下文 */
  public ParserContext(Map<String, Object> imports, Map<String, Interceptor> interceptors, String sourceFile) {
    this.sourceFile = sourceFile;
    this.parserConfiguration = new ParserConfiguration(imports, interceptors);
  }

  /** 构建起一个子上下文,以用于特定的上下文处理,即相应原来的信息进行复制处理,主要用于处理for循环 */
  public ParserContext createSubcontext() {
    ParserContext ctx = new ParserContext(parserConfiguration);
    ctx.sourceFile = sourceFile;
    ctx.parent = this;

    ctx.addInputs(inputs);
    ctx.addVariables(variables);
    ctx.addIndexedInputs(indexedInputs);
    ctx.addTypeParameters(typeParameters);

    ctx.sourceLineLookups = sourceLineLookups;
    ctx.lastLineLabel = lastLineLabel;
    ctx.variableVisibility = variableVisibility;

    ctx.globalFunctions = globalFunctions;
    ctx.lastTypeParameters = lastTypeParameters;
    ctx.errorList = errorList;
    ctx.rootParser = rootParser;
    ctx.lineCount = lineCount;
    ctx.lineOffset = lineOffset;

    ctx.compiled = compiled;
    ctx.strictTypeEnforcement = strictTypeEnforcement;
    ctx.strongTyping = strongTyping;

    ctx.fatalError = fatalError;
    ctx.retainParserState = retainParserState;
    ctx.debugSymbols = debugSymbols;
    ctx.blockSymbols = blockSymbols;
    ctx.executableCodeReached = executableCodeReached;
    ctx.indexAllocation = indexAllocation;

    return ctx;
  }

  /** 构建一个克隆上下文,即对原上下文是直接引用,但在各个变量的使用时增加相应的溢出标识,主要用于判定for无限循环处理 */
  public ParserContext createColoringSubcontext() {
    if (parent == null) {
      throw new RuntimeException("create a subContext first");
    }

    //在添加,获取变量时即认为会使用新的变量信息,那么即认为会存在变量溢出,那么肯定会有数据的变量,即简单地认为不会出现死循环
    ParserContext ctx = new ParserContext(parserConfiguration) {
      @Override
      public void addVariable(String name, Class type) {
        if ((parent.variables != null && parent.variables.containsKey(name))
            || (parent.inputs != null && parent.inputs.containsKey(name))) {
          this.variablesEscape = true;
        }
        super.addVariable(name, type);
      }

      @Override
      public void addVariable(String name, Class type, boolean failIfNewAssignment) {
        if ((parent.variables != null && parent.variables.containsKey(name))
            || (parent.inputs != null && parent.inputs.containsKey(name))) {
          this.variablesEscape = true;
        }
        super.addVariable(name, type, failIfNewAssignment);
      }

      @Override
      public Class getVarOrInputType(String name) {
        if ((parent.variables != null && parent.variables.containsKey(name))
            || (parent.inputs != null && parent.inputs.containsKey(name))) {
          this.variablesEscape = true;
        }

        return super.getVarOrInputType(name);
      }
    };
    ctx.initializeTables();

    ctx.sourceFile = sourceFile;

    ctx.inputs = inputs;
    ctx.variables = variables;
    ctx.indexedInputs = indexedInputs;
    ctx.typeParameters = typeParameters;

    ctx.sourceLineLookups = sourceLineLookups;
    ctx.lastLineLabel = lastLineLabel;
    ctx.variableVisibility = variableVisibility;

    ctx.globalFunctions = globalFunctions;
    ctx.lastTypeParameters = lastTypeParameters;
    ctx.errorList = errorList;
    ctx.rootParser = rootParser;
    ctx.lineCount = lineCount;
    ctx.lineOffset = lineOffset;

    ctx.compiled = compiled;
    ctx.strictTypeEnforcement = strictTypeEnforcement;
    ctx.strongTyping = strongTyping;

    ctx.fatalError = fatalError;
    ctx.retainParserState = retainParserState;
    ctx.debugSymbols = debugSymbols;
    ctx.blockSymbols = blockSymbols;
    ctx.executableCodeReached = executableCodeReached;
    ctx.indexAllocation = indexAllocation;

    return ctx;
  }

  /**
   * 判定是否包括指定名称的变量或者是输入参数
   * Tests whether or not a variable or input exists in the current parser context.
   *
   * @param name The name of the identifier.
   * @return boolean
   */
  public boolean hasVarOrInput(String name) {
    return (variables != null && variables.containsKey(name))
        || (inputs != null && inputs.containsKey(name));
  }

  /**
   * 获取一个变量的类型(从变量及入参中提取),默认值为 object
   * Return the variable or input type froom the current parser context.  Returns <tt>Object.class</tt> if the
   * type cannot be determined.
   *
   * @param name The name of the identifier
   * @return boolean
   */
  public Class getVarOrInputType(String name) {
    if (variables != null && variables.containsKey(name)) {
      return variables.get(name);
    }
    else if (inputs != null && inputs.containsKey(name)) {
      return inputs.get(name);
    }
    return Object.class;
  }

  /** 读取一个变量或输入信息的类型值 */
  public Class getVarOrInputTypeOrNull(String name) {
    if (variables != null && variables.containsKey(name)) {
      return variables.get(name);
    }
    else if (inputs != null && inputs.containsKey(name)) {
      return inputs.get(name);
    }
    return null;
  }

  /**
   * Get total number of lines declared in the current context.
   *
   * @return int of lines
   */
  public int getLineCount() {
    return lineCount;
  }

  /**
   * Set the current number of lines in the current context. (Generally only used by the compiler)
   *
   * @param lineCount The number of lines
   * @return int of lines
   */
  public int setLineCount(int lineCount) {
    return this.lineCount = lineCount;
  }

  /**
   * 增加记录行,主要用于记录解析到了新的行数
   * Increments the current line count by the specified amount
   *
   * @param increment The number of lines to increment
   * @return int of lines
   */
  public int incrementLineCount(int increment) {
    return this.lineCount += increment;
  }

  /**
   * Get the current line offset.  This measures the number of cursor positions back to the beginning of the line.
   *
   * @return int offset
   */
  public int getLineOffset() {
    return lineOffset;
  }

  /**
   * Sets the current line offset. (Generally only used by the compiler)
   *
   * @param lineOffset The offset amount
   */
  public void setLineOffset(int lineOffset) {
    this.lineOffset = lineOffset;
  }

  /**
   * Sets both the current line count and line offset
   *
   * @param lineCount  The line count
   * @param lineOffset The line offset
   */
  public void setLineAndOffset(int lineCount, int lineOffset) {
    //addKnownLine(this.lineCount = lineCount);
    this.lineOffset = lineOffset;
  }

  /**
   * 从解析配置中获取指定import名字的类信息
   * Get an import that has been declared, either in the parsed script or programatically
   *
   * @param name The name identifier for the imported class (ie. "HashMap")
   * @return An instance of <tt>Class</tt> denoting the imported class.
   */
  public Class getImport(String name) {
    return parserConfiguration.getImport(name);
  }

  /**
   * 从解析配置中获取指定import名字的静态引用
   * Get a {@link MethodStub} which wraps a static method import.
   *
   * @param name The name identifier
   * @return An instance of {@link MethodStub}
   */
  public MethodStub getStaticImport(String name) {
    return parserConfiguration.getStaticImport(name);
  }

  /**
   * 从解析配置中获取指定import名字的引用(静态方法句柄或者是指定类)
   * Returns either an instance of <tt>Class</tt> or {@link MethodStub} (whichever matches).
   *
   * @param name The name identifier.
   * @return An instance of <tt>Class</tt> or {@link MethodStub}
   */
  public Object getStaticOrClassImport(String name) {
    return parserConfiguration.getStaticOrClassImport(name);
  }

  /**
   * 往解析配置中添加一个新的包或者是静态方法引用
   * Adds a package import to a parse session.
   *
   * @param packageName A fully qualified package (eg. <tt>java.util.concurrent</tt>).
   */
  public void addPackageImport(String packageName) {
    parserConfiguration.addPackageImport(packageName);
  }

  /**
   * 判定解析配置中是否存在指定名字的引用
   * Tests to see if the specified import exists.
   *
   * @param name A name identifier
   * @return boolean
   */
  public boolean hasImport(String name) {
    return parserConfiguration.hasImport(name);
  }

  /** 是否存在相应的引用,并且此引用为一个原型引用 */
  public boolean hasProtoImport(String name) {
    if (parserConfiguration.getImports() == null) return false;
    Object o = parserConfiguration.getImports().get(name);
    return o != null && o instanceof Proto;
  }

  public Proto getProtoImport(String name) {
    return (Proto) parserConfiguration.getImports().get(name);
  }

  /**
   * 往解析配置中添加一个类,使用类的简名simpleName作为引用名,后续可以直接进行类引用
   * Adds an import for the specified <tt>Class</tt>.
   *
   * @param cls The instance of the <tt>Class</tt> which represents the imported class.
   */
  public void addImport(Class cls) {
    addImport(cls.getSimpleName(), cls);
  }

  /** 往解析配置中添加一个原型引用 */
  public void addImport(Proto proto) {
    parserConfiguration.addImport(proto.getName(), proto);

  }

  /**
   * 使用指定的引用名往解析配置中添加一个引用类
   * 这里的引用名不一定非得是类名,也可以是别名.比如通过x 引用 Txyz这种情况
   * Adds an import for a specified <tt>Class</tt> using an alias.  For example:
   * <pre><code>
   * parserContext.addImport("sys", System.class);
   * </code></pre>
   * ... doing this would allow an MVEL script to be written as such:
   * <pre><code>
   * sys.currentTimeMillis();
   * </code></pre>
   *
   * @param name The alias to use
   * @param cls  The instance of the <tt>Class</tt> which represents the imported class.
   */
  public void addImport(String name, Class cls) {
    parserConfiguration.addImport(name, cls);
    //      addInput(name, cls);
  }

  /**
   * 通过一个指定的引用名+相应的方法(静态方法)往解析配置中添加相应的引用
   * Adds an import for a specified <tt>Method</tt> representing a static method import using an alias. For example:
   * <pre><code>
   * parserContext.addImport("time", MVEL.getStaticMethod(System.class, "currentTimeMillis", new Class[0]));
   * </code></pre>
   * ... doing this allows the <tt>System.currentTimeMillis()</tt> method to be executed in a script simply by writing
   * <tt>time()</tt>.
   *
   * @param name   The alias to use
   * @param method The instance of <tt>Method</tt> which represents the static import.
   */
  public void addImport(String name, Method method) {
    addImport(name, new MethodStub(method));
    //   addInput(name, MethodStub.class);
  }

  /**
   * 使用指定的引用名字往解析配置中添加相应的方法句柄
   * Adds a static import for the specified {@link MethodStub} with an alias.
   *
   * @param name   The alias to use
   * @param method The instance of <tt>Method</tt> which represents the static import.
   * @see #addImport(String, org.mvel2.util.MethodStub)
   */
  public void addImport(String name, MethodStub method) {
    parserConfiguration.addImport(name, method);
  }

  /**
   * 初始化各项变量表
   * Initializes internal Maps.  Called by the compiler.
   */
  public void initializeTables() {
    if (variables == null) variables = new LinkedHashMap<String, Class>();
    if (inputs == null) inputs = new LinkedHashMap<String, Class>();

    //开启当前作用域，并将相应的变量和输入信息加到当前作用域当中
    if (variableVisibility == null) {
      initVariableVisibility();
      pushVariableScope();

      Set<String> scope = getVariableScope();

      scope.addAll(variables.keySet());
      scope.addAll(inputs.keySet());

      if (parserConfiguration.getImports() != null)
        scope.addAll(parserConfiguration.getImports().keySet());

      //如果当前输入类型包括this属性，则加入相应的静态字段以及属性或方法名
      if (inputs.containsKey("this")) {
        Class<?> ctxType = inputs.get("this");

        //加入静态字段
        for (Field field : ctxType.getFields()) {
          if ((field.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) != 0) {
            scope.add(field.getName());
          }
        }

        for (Method m : ctxType.getMethods()) {
          if ((m.getModifiers() & Modifier.PUBLIC) != 0) {
            //加入属性名,包括首字母大写，或小写的
            if (m.getName().startsWith("get")
                || (m.getName().startsWith("is")
                && (m.getReturnType().equals(boolean.class) || m.getReturnType().equals(Boolean.class)))) {
              String propertyName = ReflectionUtil.getPropertyFromAccessor(m.getName());
              scope.add(propertyName);
              propertyName = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
              scope.add(propertyName);
            }
            else {
              //加入方法名
              scope.add(m.getName());
            }
          }
        }
      }
    }
  }

  /**
   * 添加一个变量信息
   *
   * @param failIfNewAssignment 如果变量已存在,则抛出相应的异常
   */
  public void addVariable(String name, Class type, boolean failIfNewAssignment) {
    initializeTables();
    if (variables.containsKey(name) && failIfNewAssignment)
      throw new RuntimeException("statically-typed variable already defined in scope: " + name);

    if (type == null) type = Object.class;

    variables.put(name, type);
    makeVisible(name);
  }

  /** 添加相应的变量信息,如果之前存在,则不再添加 */
  public void addVariable(String name, Class type) {
    initializeTables();
    if (variables.containsKey(name) || inputs.containsKey(name)) return;
    if (type == null) type = Object.class;
    variables.put(name, type);
    makeVisible(name);
  }

  /** 批量添加多个变量信息 */
  public void addVariables(Map<String, Class> variables) {
    if (variables == null) return;
    initializeTables();
    for (Map.Entry<String, Class> entry : variables.entrySet()) {
      addVariable(entry.getKey(), entry.getValue());
    }
  }

  /** 添加入参定义及类型 */
  public void addInput(String name, Class type) {
    if (inputs == null) inputs = new LinkedHashMap<String, Class>();
    if (inputs.containsKey(name) || (variables != null && variables.containsKey(name))) return;
    if (type == null) type = Object.class;

    inputs.put(name, type);
  }

  /** 添加入参定义及类型,以及类型的泛型参数类型信息 */
  public void addInput(String name, Class type, Class[] typeParameters) {
    if (type == null) type = Object.class;
    addInput(name, type);

    if (this.typeParameters == null) {
      this.typeParameters = new LinkedHashMap<String, Map<String, Type>>();
    }
    if (this.typeParameters.get(name) == null) {
      this.typeParameters.put(name, new LinkedHashMap<String, Type>());
    }

    Map<String, Type> t = this.typeParameters.get(name);

    //要求声明的类型长度与当前类的泛型长度应该是一样的
    if (typeParameters.length != type.getTypeParameters().length) {
      throw new RuntimeException("wrong number of type parameters for: " + type.getName());
    }

    TypeVariable[] tvs = type.getTypeParameters();

    for (int i = 0; i < typeParameters.length; i++) {
      t.put(tvs[i].getName(), typeParameters[i]);
    }
  }

  /** 批量添加一组入参信息 */
  public void addInputs(Map<String, Class> inputs) {
    if (inputs == null) return;
    for (Map.Entry<String, Class> entry : inputs.entrySet()) {
      addInput(entry.getKey(), entry.getValue());
    }
  }

  /** 从入参中移除相应的变量信息,即认为这些信息不需要通过入参进行传递(因为变量中本身就有) */
  public void processTables() {
    for (String name : variables.keySet()) {
      inputs.remove(name);
    }
  }

  public Map<String, Class> getInputs() {
    return inputs;
  }

  public void setInputs(Map<String, Class> inputs) {
    this.inputs = inputs;
  }

  public List<ErrorDetail> getErrorList() {
    return errorList == null ? Collections.<ErrorDetail>emptyList() : errorList;
  }

  public void setErrorList(List<ErrorDetail> errorList) {
    this.errorList = errorList;
  }

  /** 添加一个错误信息,如果错误信息是严重的,则置相应的标记 */
  public void addError(ErrorDetail errorDetail) {
    if (errorList == null) errorList = new ArrayList<ErrorDetail>();
    else {
      for (ErrorDetail detail : errorList) {
        if (detail.getMessage().equals(errorDetail.getMessage())
            && detail.getColumn() == errorDetail.getColumn()
            && detail.getLineNumber() == errorDetail.getLineNumber()) {
          return;
        }
      }
    }

    if (errorDetail.isCritical()) fatalError = true;
    errorList.add(errorDetail);
  }

  /** 是否发生了严重错误 */
  public boolean isFatalError() {
    return fatalError;
  }

  public void setFatalError(boolean fatalError) {
    this.fatalError = fatalError;
  }

  public boolean isStrictTypeEnforcement() {
    return strictTypeEnforcement;
  }

  /**
   * Enables strict type enforcement -
   *
   * @param strictTypeEnforcement -
   */
  public void setStrictTypeEnforcement(boolean strictTypeEnforcement) {
    this.strictTypeEnforcement = strictTypeEnforcement;
  }

  public boolean isStrongTyping() {
    return strongTyping;
  }

  /**
   * Enables strong type enforcement.
   *
   * @param strongTyping -
   */
  public void setStrongTyping(boolean strongTyping) {
    if (this.strongTyping = strongTyping) {
      // implies strict-type enforcement too
      this.strictTypeEnforcement = true;
    }
  }

  @Deprecated
  public boolean isRetainParserState() {
    return retainParserState;
  }

  @Deprecated
  public void setRetainParserState(boolean retainParserState) {
    this.retainParserState = retainParserState;
  }

  @Deprecated
  public Parser getRootParser() {
    return rootParser;
  }

  @Deprecated
  public void setRootParser(Parser rootParser) {
    this.rootParser = rootParser;
  }

  public String getSourceFile() {
    return sourceFile;
  }

  public void setSourceFile(String sourceFile) {
    if (sourceFile != null)
      this.sourceFile = sourceFile;
  }

  public Map<String, Interceptor> getInterceptors() {
    return this.parserConfiguration.getInterceptors();
  }

  public void setInterceptors(Map<String, Interceptor> interceptors) {
    this.parserConfiguration.setInterceptors(interceptors);
  }

  public Map<String, Object> getImports() {
    return this.parserConfiguration.getImports();
  }

  public void setImports(Map<String, Object> imports) {
    if (imports == null) return;

    Object val;
    for (Map.Entry<String, Object> entry : imports.entrySet()) {
      if ((val = entry.getValue()) instanceof Class) {
        addImport(entry.getKey(), (Class) val);
      }
      else if (val instanceof Method) {
        addImport(entry.getKey(), (Method) val);
      }
      else if (val instanceof MethodStub) {
        addImport(entry.getKey(), (MethodStub) val);
      }
      else {
        throw new RuntimeException("invalid element in imports map: " + entry.getKey() + " (" + val + ")");
      }
    }
  }

  private void initVariableVisibility() {
    if (variableVisibility == null) {
      variableVisibility = new ArrayList<Set<String>>();
    }
  }

  /** 用于创建一个新的变量作用域 */
  public void pushVariableScope() {
    initVariableVisibility();
    variableVisibility.add(new HashSet<String>());
  }

  /** 用于将刚才的变量作用域出栈，以表示不再使用此作用域 */
  public void popVariableScope() {
    if (variableVisibility != null && !variableVisibility.isEmpty()) {
      variableVisibility.remove(variableVisibility.size() - 1);
      setLastTypeParameters(null);
    }
  }

  /** 在当前变量作用域中添加新的变量 */
  public void makeVisible(String var) {
    if (variableVisibility == null || variableVisibility.isEmpty()) {
      throw new RuntimeException("no context");
    }
    getVariableScope().add(var);
  }

  public Set<String> getVariableScope() {
    if (variableVisibility == null || variableVisibility.isEmpty()) {
      throw new RuntimeException("no context");
    }

    return variableVisibility.get(variableVisibility.size() - 1);
  }

  /** 判定指定的变量曾经出现过,即之前添加过 */
  public boolean isVariableVisible(String var) {
    if (variableVisibility == null || variableVisibility.isEmpty()) {
      return false;
    }

    if (AbstractParser.LITERALS.containsKey(var) || hasImport(var)) {
      return true;
    }

    int pos = variableVisibility.size() - 1;

    do {
      if (variableVisibility.get(pos).contains(var)) {
        return true;
      }
    }
    while (pos-- != 0);

    return false;
  }

  public HashMap<String, Class> getVariables() {
    return variables;
  }

  public void setVariables(HashMap<String, Class> variables) {
    this.variables = variables;
  }

  @Deprecated
  public boolean isCompiled() {
    return compiled;
  }

  @Deprecated
  public void setCompiled(boolean compiled) {
    this.compiled = compiled;
  }

  public boolean isDebugSymbols() {
    return debugSymbols;
  }

  public void setDebugSymbols(boolean debugSymbols) {
    this.debugSymbols = debugSymbols;
  }

  public boolean isLineMapped(String sourceName) {
    return sourceLineLookups != null && sourceLineLookups.containsKey(sourceName);
  }

  /** 在整个字符串中将每一行进行初始化并进行标识好，即多少行，起始点，结束点等 */
  public void initLineMapping(String sourceName, char[] expr) {
    if (sourceLineLookups == null) {
      sourceLineLookups = new HashMap<String, LineMapper.LineLookup>();
    }
    sourceLineLookups.put(sourceName, new LineMapper(expr).map());
  }

  /** 针对一个下标值拿到此下标所对应的行数 */
  public int getLineFor(String sourceName, int cursor) {
    return (sourceLineLookups != null
        && sourceLineLookups.containsKey(sourceName)) ?
        sourceLineLookups.get(sourceName).getLineFromCursor(cursor) : -1;
  }

  /** 指定行是否已标识过 */
  public boolean isVisitedLine(String sourceName, int lineNumber) {
    return visitedLines != null
        && visitedLines.containsKey(sourceName)
        && visitedLines.get(sourceName).contains(lineNumber);
  }

  /** 设置此行已经处理过了 */
  public void visitLine(String sourceName, int lineNumber) {
    if (visitedLines == null) {
      visitedLines = new HashMap<String, Set<Integer>>();
    }

    if (!visitedLines.containsKey(sourceName)) {
      visitedLines.put(sourceName, new TreeSet<Integer>());
    }

    visitedLines.get(sourceName).add(lineNumber);
  }

  public LineLabel getLastLineLabel() {
    return lastLineLabel;
  }

  public LineLabel setLastLineLabel(LineLabel lastLineLabel) {
    return this.lastLineLabel = lastLineLabel;
  }

  public boolean hasImports() {
    return parserConfiguration.hasImports();
  }

  /** 声明函数定义 */
  public void declareFunction(Function function) {
    if (globalFunctions == null) globalFunctions = new LinkedHashMap<String, Function>();
    globalFunctions.put(function.getName(), function);
  }

  /** 在全局函数中获取指定名字的函数定义 */
  public Function getFunction(String name) {
    return globalFunctions == null ? null : globalFunctions.get(name);
  }

  public Map getFunctions() {
    return globalFunctions == null ? Collections.emptyMap() : globalFunctions;
  }

  public boolean hasFunction(String name) {
    return globalFunctions != null && globalFunctions.containsKey(name);
  }

  /** 是否具有全局函数 */
  public boolean hasFunction() {
    return globalFunctions != null && globalFunctions.size() != 0;
  }

  /** 为指定引用名字的类添加相应的泛型类型信息 */
  public void addTypeParameters(String name, Class type) {
    if (typeParameters == null) typeParameters = new HashMap<String, Map<String, Type>>();

    Map<String, Type> newPkg = new HashMap<String, Type>();

    for (Type t : type.getTypeParameters()) {
      newPkg.put(t.toString(), Object.class);
    }

    typeParameters.put(name, newPkg);
  }

  /** 批量添加泛型类型信息 */
  public void addTypeParameters(Map<String, Map<String, Type>> typeParameters) {
    if (typeParameters == null) return;
    if (this.typeParameters == null) typeParameters = new HashMap<String, Map<String, Type>>();

    Map iMap;
    for (Map.Entry<String, Map<String, Type>> e : typeParameters.entrySet()) {
      iMap = new HashMap<String, Class>();
      for (Map.Entry<String, Type> ie : e.getValue().entrySet()) {
        iMap.put(ie.getKey(), ie.getValue());
      }
      typeParameters.put(e.getKey(), iMap);
    }

  }

  public Map<String, Type> getTypeParameters(String name) {
    if (typeParameters == null) return null;
    return typeParameters.get(name);
  }

  /** 获取指定变量名之前在泛型类型中注册过的泛型类型信息,因为泛型类型定义为数组,因为返回相应的数组信息 */
  public Type[] getTypeParametersAsArray(String name) {
    Class c = (variables != null && variables.containsKey(name)) ? variables.get(name) : inputs.get(name);
    if (c == null) return null;

    Type[] tp = c.getTypeParameters();
    Type[] types = new Type[tp.length];

    Map<String, Type> typeVars = getTypeParameters(name);
    if (typeVars == null) {
      return null;
    }

    for (int i = 0; i < tp.length; i++) {
      types[i] = typeVars.get(tp[i].toString());
    }

    return types;
  }

  public boolean isBlockSymbols() {
    return blockSymbols;
  }

  public void setBlockSymbols(boolean blockSymbols) {
    this.blockSymbols = blockSymbols;
  }

  /** 在解析过程中是否存在变量溢出(用于判定无限循环) */
  public boolean isVariablesEscape() {
    return variablesEscape;
  }

  @Deprecated
  public boolean isExecutableCodeReached() {
    return executableCodeReached;
  }

  @Deprecated
  public void setExecutableCodeReached(boolean executableCodeReached) {
    this.executableCodeReached = executableCodeReached;
  }

  /** 声明当前正在进行优化 */
  public void optimizationNotify() {
    this.optimizationMode = true;
  }

  /** 当前是否正在优化阶段 */
  public boolean isOptimizerNotified() {
    return optimizationMode;
  }

  private void initIndexedVariables() {
    if (indexedInputs == null) indexedInputs = new ArrayList<String>();
    if (indexedLocals == null) indexedLocals = new ArrayList<String>();
  }

  /** 获取之前的顺序参数列表(如函数参数定义) */
  public ArrayList<String> getIndexedInputs() {
    initIndexedVariables();
    return indexedInputs;
  }

  /** 添加顺序入参信息 */
  public void addIndexedInput(String[] variables) {
    initIndexedVariables();
    for (String s : variables) {
      if (!indexedInputs.contains(s))
        indexedInputs.add(s);
    }
  }

  public void addIndexedLocals(String[] variables) {
    initIndexedVariables();
    for (String s : indexedLocals) {
      if (!indexedLocals.contains(s))
        indexedLocals.add(s);
    }
  }

  /** 没有实际作用 */
  @Deprecated
  public void addIndexedLocals(Collection<String> variables) {
    if (variables == null) return;
    initIndexedVariables();
    for (String s : variables) {
      if (!indexedLocals.contains(s))
        indexedLocals.add(s);
    }
  }

  /** 添加一个顺序入参参数 */
  public void addIndexedInput(String variable) {
    initIndexedVariables();
    if (!indexedInputs.contains(variable)) indexedInputs.add(variable);
  }

  /** 指定添加按顺序的参数变量 */
  public void addIndexedInputs(Collection<String> variables) {
    if (variables == null) return;
    initIndexedVariables();
    for (String s : variables) {
      if (!indexedInputs.contains(s))
        indexedInputs.add(s);
    }
  }

  /** 查找一个变量在当前输入变量以及本地变量中的顺序值 */
  public int variableIndexOf(String name) {
    if (indexedInputs != null) {
      int idx = indexedInputs.indexOf(name);
      if (idx == -1 && indexedLocals != null) {
        idx = indexedLocals.indexOf(name);
        if (idx != -1) {
          idx += indexedInputs.size();
        }
      }
      return idx;
    }

    return -1;
  }

  @Deprecated
  public Object getEvaluationContext() {
    return evaluationContext;
  }

  public boolean hasIndexedInputs() {
    return indexedInputs != null && indexedInputs.size() != 0;
  }

  public boolean isIndexAllocation() {
    return indexAllocation;
  }

  public void setIndexAllocation(boolean indexAllocation) {
    this.indexAllocation = indexAllocation;
  }

  /** 返回当前是否是函数上下文中 */
  public boolean isFunctionContext() {
    return functionContext;
  }

  /** 获取相应的解析配置信息 */
  public ParserConfiguration getParserConfiguration() {
    return parserConfiguration;
  }

  public ClassLoader getClassLoader() {
    return parserConfiguration.getClassLoader();
  }

  /** 获取最近一次的泛型类型信息 */
  public Type[] getLastTypeParameters() {
    return lastTypeParameters;
  }

  public void setLastTypeParameters(Type[] lastTypeParameters) {
    this.lastTypeParameters = lastTypeParameters;
  }

  public boolean isAllowBootstrapBypass() {
    return parserConfiguration.isAllowBootstrapBypass();
  }

  /** 配置解析配置的二次编译标记 */
  public void setAllowBootstrapBypass(boolean allowBootstrapBypass) {
    parserConfiguration.setAllowBootstrapBypass(allowBootstrapBypass);
  }

  /** 获取相应的顺序入参的变量名信息 */
  public String[] getIndexedVarNames() {
    if (indexedInputs == null) return new String[0];

    String[] s = new String[indexedInputs.size()];
    indexedInputs.toArray(s);
    return s;
  }

  public Map<String, CompiledExpression> getCompiledExpressionCache() {
    if (compiledExpressionCache == null) {
      compiledExpressionCache = new HashMap<String, CompiledExpression>();
    }
    return compiledExpressionCache;
  }

  public Map<String, Class> getReturnTypeCache() {
    if (returnTypeCache == null) {
      returnTypeCache = new HashMap<String, Class>();
    }
    return returnTypeCache;
  }

  // Introduce some new Fluent API stuff here.

  public static ParserContext create() {
    return new ParserContext();
  }

  public ParserContext stronglyTyped() {
    setStrongTyping(true);
    return this;
  }

  public ParserContext withInput(String name, Class type) {
    addInput(name, type);
    return this;
  }

  public ParserContext withInputs(Map<String, Class> inputs) {
    setInputs(inputs);
    return this;
  }

  public ParserContext withTypeParameter(String name, Class type) {
    addTypeParameters(name, type);
    return this;
  }

  public ParserContext withTypeParameters(Map<String, Map<String, Type>> typeParameters) {
    addTypeParameters(typeParameters);
    return this;
  }

  public ParserContext withImport(Class clazz) {
    addImport(clazz);
    return this;
  }

  public ParserContext withIndexedVars(String[] varNames) {
    indexedInputs = new ArrayList<String>();
    Collections.addAll(indexedInputs, varNames);

    return this;
  }
}
