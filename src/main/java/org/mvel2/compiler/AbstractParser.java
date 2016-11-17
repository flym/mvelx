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

import org.mvel2.CompileException;
import org.mvel2.ErrorDetail;
import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.ast.ASTNode;
import org.mvel2.ast.AssertNode;
import org.mvel2.ast.AssignmentNode;
import org.mvel2.ast.BooleanNode;
import org.mvel2.ast.DeclProtoVarNode;
import org.mvel2.ast.DeclTypedVarNode;
import org.mvel2.ast.DeepAssignmentNode;
import org.mvel2.ast.DoNode;
import org.mvel2.ast.DoUntilNode;
import org.mvel2.ast.EndOfStatement;
import org.mvel2.ast.Fold;
import org.mvel2.ast.ForEachNode;
import org.mvel2.ast.ForNode;
import org.mvel2.ast.Function;
import org.mvel2.ast.IfNode;
import org.mvel2.ast.ImportNode;
import org.mvel2.ast.IndexedAssignmentNode;
import org.mvel2.ast.IndexedDeclTypedVarNode;
import org.mvel2.ast.IndexedOperativeAssign;
import org.mvel2.ast.IndexedPostFixDecNode;
import org.mvel2.ast.IndexedPostFixIncNode;
import org.mvel2.ast.IndexedPreFixDecNode;
import org.mvel2.ast.IndexedPreFixIncNode;
import org.mvel2.ast.InlineCollectionNode;
import org.mvel2.ast.InterceptorWrapper;
import org.mvel2.ast.Invert;
import org.mvel2.ast.IsDef;
import org.mvel2.ast.LineLabel;
import org.mvel2.ast.LiteralDeepPropertyNode;
import org.mvel2.ast.LiteralNode;
import org.mvel2.ast.Negation;
import org.mvel2.ast.NewObjectNode;
import org.mvel2.ast.NewObjectPrototype;
import org.mvel2.ast.NewPrototypeNode;
import org.mvel2.ast.OperativeAssign;
import org.mvel2.ast.OperatorNode;
import org.mvel2.ast.PostFixDecNode;
import org.mvel2.ast.PostFixIncNode;
import org.mvel2.ast.PreFixDecNode;
import org.mvel2.ast.PreFixIncNode;
import org.mvel2.ast.Proto;
import org.mvel2.ast.ProtoVarNode;
import org.mvel2.ast.RedundantCodeException;
import org.mvel2.ast.RegExMatch;
import org.mvel2.ast.ReturnNode;
import org.mvel2.ast.Sign;
import org.mvel2.ast.Stacklang;
import org.mvel2.ast.StaticImportNode;
import org.mvel2.ast.Substatement;
import org.mvel2.ast.ThisWithNode;
import org.mvel2.ast.TypeCast;
import org.mvel2.ast.TypeDescriptor;
import org.mvel2.ast.TypedVarNode;
import org.mvel2.ast.Union;
import org.mvel2.ast.UntilNode;
import org.mvel2.ast.WhileNode;
import org.mvel2.ast.WithNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.ErrorUtil;
import org.mvel2.util.ExecutionStack;
import org.mvel2.util.FunctionParser;
import org.mvel2.util.PropertyTools;
import org.mvel2.util.ProtoParser;

import java.io.Serializable;
import java.util.HashMap;
import java.util.WeakHashMap;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Double.parseDouble;
import static java.lang.Thread.currentThread;
import static org.mvel2.Operator.*;
import static org.mvel2.ast.TypeDescriptor.getClassReference;
import static org.mvel2.util.ArrayTools.findFirst;
import static org.mvel2.util.ParseTools.*;
import static org.mvel2.util.PropertyTools.isEmpty;
import static org.mvel2.util.Soundex.soundex;

/**
 * 核心解析器，用于解析相应的表达式，即词法分析器，将相应的表达式分块转换为节点
 * This is the core parser that the subparsers extend.
 *
 * @author Christopher Brock
 */
public class AbstractParser implements Parser, Serializable {
  /** 当前正在编译的字符串 */
  protected char[] expr;
  /** 当前编译游标 */
  protected int cursor;

  /** 当前字符串起始处理点(跳过空格) */
  protected int start;
  /** 当前字符串长度(可能跳过末尾空格) */
  protected int length;
  /** 当前处理字符串的结束位置 */
  protected int end;
  /** 当前处理节点的起始位置(临时存储) */
  protected int st;

  /** 表示当前正在作的操作 */
  protected int fields;

  /** 表示操作溢出了,或者表示相应的操作不能够再继续进行 */
  protected static final int OP_OVERFLOW = -2;
  /** 表示操作需要提前终止,如and || 这种 */
  protected static final int OP_TERMINATE = -1;
  /** 栈操作，表示当前操作桢已经处理完毕，可以理解为当前的一个 表达式已经计算完毕，如 a > b ? c :d 这样的一个子表达式 */
  protected static final int OP_RESET_FRAME = 0;
  /** 栈操作，表示需要继续运行 */
  protected static final int OP_CONTINUE = 1;

  /** 提前处理 = 操作 (而不是单独成项),即a=3,可以为a=3 1个赋值节点,也可以是a op 3 3个节点,这里的greedy即前者 */
  protected boolean greedy = true;
  /** 最新节点是否是属性,引用变量等变量信息 */
  protected boolean lastWasIdentifier = false;
  /** 最新节点是否是代码行 */
  protected boolean lastWasLineLabel = false;
  /** 最新节点是否是注释 */
  protected boolean lastWasComment = false;
  /** 当前是否是编译模式，在编译模式下某些操作不能马上进行执行(如变量处理等) */
  protected boolean compileMode = false;

  /** 表示解析的表达式是否是常量表达式 */
  protected int literalOnly = -1;

  /** 最新处理行的行头的下标值 */
  protected int lastLineStart = 0;
  /** 当前正在处理的代码行(以\n作标识区分) */
  protected int line = 0;

  /** 当前处理解析的最新的节点信息 */
  protected ASTNode lastNode;

  /** 使用一个弱引用来维护从原始表达式到去掉前后空格的表达式的映射，以减少处理空格的时间 */
  private static final WeakHashMap<String, char[]> EX_PRECACHE = new WeakHashMap<String, char[]>(15);

  /** 各种常量集合,如true,false等 */
  public static HashMap<String, Object> LITERALS;
  /** 各种内部系统类集合,如String,System等 */
  public static HashMap<String, Object> CLASS_LITERALS;
  /** 各种操作符，集合，如+,-等 */
  public static HashMap<String, Integer> OPERATORS;

  /** 编译期执行栈 */
  protected ExecutionStack stk;
  /**
   * 用于描述一个分段的解析表达式，表示一个正常的执行节点
   * 在整个编译期，此栈可能会在编译期add多个节点，同时会在相应的nextToken中进行还原出来
   */
  protected ExecutionStack splitAccumulator = new ExecutionStack();

  /** 当前解析上下文 */
  protected ParserContext pCtx;
  /**
   * 临时栈,用于辅助stack进行处理,仅用于一些高优化级运算数，不便于在stack中处理的,主要用于处理优先级操作问题
   * 正常的情况下辅助器仅用于保存最近的操作数和操作符
   */
  protected ExecutionStack dStack;
  /** 解释模式下所使用的当前上下文 */
  protected Object ctx;
  /** 当前所使用的变量工厂 */
  protected VariableResolverFactory variableFactory;

  /** 判定是否是调试模式 */
  protected boolean debugSymbols = false;

  static {
    setupParser();
  }

  protected AbstractParser() {
    pCtx = new ParserContext();
  }

  protected AbstractParser(ParserContext pCtx) {
    this.pCtx = pCtx != null ? pCtx : new ParserContext();
  }

  /**
   * 初始化相应的常量信息，类常量 以及操作常量信息
   * This method is internally called by the static initializer for AbstractParser in order to setup the parser.
   * The static initialization populates the operator and literal tables for the parser.  In some situations, like
   * OSGi, it may be necessary to utilize this manually.
   */
  public static void setupParser() {
    if (LITERALS == null || LITERALS.isEmpty()) {
      LITERALS = new HashMap<String, Object>();
      CLASS_LITERALS = new HashMap<String, Object>();
      OPERATORS = new HashMap<String, Integer>();

      /**
       * Add System and all the class wrappers from the JCL.
       */
      CLASS_LITERALS.put("System", System.class);
      CLASS_LITERALS.put("String", String.class);
      CLASS_LITERALS.put("CharSequence", CharSequence.class);

      CLASS_LITERALS.put("Integer", Integer.class);
      CLASS_LITERALS.put("int", int.class);

      CLASS_LITERALS.put("Long", Long.class);
      CLASS_LITERALS.put("long", long.class);

      CLASS_LITERALS.put("Boolean", Boolean.class);
      CLASS_LITERALS.put("boolean", boolean.class);

      CLASS_LITERALS.put("Short", Short.class);
      CLASS_LITERALS.put("short", short.class);

      CLASS_LITERALS.put("Character", Character.class);
      CLASS_LITERALS.put("char", char.class);

      CLASS_LITERALS.put("Double", Double.class);
      CLASS_LITERALS.put("double", double.class);

      CLASS_LITERALS.put("Float", Float.class);
      CLASS_LITERALS.put("float", float.class);

      CLASS_LITERALS.put("Byte", Byte.class);
      CLASS_LITERALS.put("byte", byte.class);

      CLASS_LITERALS.put("Math", Math.class);
      CLASS_LITERALS.put("Void", Void.class);
      CLASS_LITERALS.put("Object", Object.class);
      CLASS_LITERALS.put("Number", Number.class);

      //java.lang包下的各项常量
      CLASS_LITERALS.put("Class", Class.class);
      CLASS_LITERALS.put("ClassLoader", ClassLoader.class);
      CLASS_LITERALS.put("Runtime", Runtime.class);
      CLASS_LITERALS.put("Thread", Thread.class);
      CLASS_LITERALS.put("Compiler", Compiler.class);
      CLASS_LITERALS.put("StringBuffer", StringBuffer.class);
      CLASS_LITERALS.put("ThreadLocal", ThreadLocal.class);
      CLASS_LITERALS.put("SecurityManager", SecurityManager.class);
      CLASS_LITERALS.put("StrictMath", StrictMath.class);

      CLASS_LITERALS.put("Exception", Exception.class);

      CLASS_LITERALS.put("Array", java.lang.reflect.Array.class);

      CLASS_LITERALS.put("StringBuilder", StringBuilder.class);

      // Setup LITERALS
      LITERALS.putAll(CLASS_LITERALS);
      LITERALS.put("true", TRUE);
      LITERALS.put("false", FALSE);

      LITERALS.put("null", null);
      LITERALS.put("nil", null);

      LITERALS.put("empty", BlankLiteral.INSTANCE);

      setLanguageLevel(Boolean.getBoolean("mvel.future.lang.support") ? 6 : 5);
    }
  }

  /** 实际上就是获取下一个节点，即跳过调试节点 */
  protected ASTNode nextTokenSkipSymbols() {
    ASTNode n = nextToken();
    //如果是调试节点，则跳过
    if (n != null && n.getFields() == -1) n = nextToken();
    return n;
  }

  /**
   * 获取下一个节点
   * Retrieve the next token in the expression.
   *
   * @return -
   */
  protected ASTNode nextToken() {
    try {
      /**
       * 这里先尝试从相应的处理栈中获取，但如果拿出来的节点是一个end节点，并且当前也已经到达处理末尾了
       * 则直接返回null，即lastNode没有什么意义
       * If the cursor is at the end of the expression, we have nothing more to do:
       * return null.
       */
      if (!splitAccumulator.isEmpty()) {
        lastNode = (ASTNode) splitAccumulator.pop();
        if (cursor >= end && lastNode instanceof EndOfStatement) {
          return nextToken();
        }
        else {
          return lastNode;
        }
      }
      //已经到末尾，则直接返回null
      else if (cursor >= end) {
        return null;
      }

      int brace, idx;
      int tmpStart;

      String name;
      /**
       * Because of parser recursion for sub-expression parsing, we sometimes need to remain
       * certain field states.  We do not reset for assignments, boolean mode, list creation or
       * a capture only mode.
       */

      boolean capture = false, union = false;

      //当前正在编译阶段，因此从上下文中获取调试标识，以便进行处理
      if ((fields & ASTNode.COMPILE_IMMEDIATE) != 0) {
        debugSymbols = pCtx.isDebugSymbols();
      }

      //如果在调试模式下，则每次调用nextToken，都将一个代码行节点加入到处理当中，以描述当前的处理状态
      //同时,按照处理逻辑,这里的处理逻辑会将第1个节点返回给调用,即此调试节点会以第1个节点加入到节点处理链中,即astLinkedList中
      if (debugSymbols) {
        if (!lastWasLineLabel) {
          if (pCtx.getSourceFile() == null) {
            throw new CompileException("unable to produce debugging symbols: source name must be provided.", expr, st);
          }

          if (!pCtx.isLineMapped(pCtx.getSourceFile())) {
            pCtx.initLineMapping(pCtx.getSourceFile(), expr);
          }

          skipWhitespace();

          if (cursor >= end) {
            return null;
          }

          int line = pCtx.getLineFor(pCtx.getSourceFile(), cursor);

          //当前行是否已经处理过，即在调试中是否已经认为此行处理了，仅在未处理时，才会返回此调试行
          if (!pCtx.isVisitedLine(pCtx.getSourceFile(), pCtx.setLineCount(line)) && !pCtx.isBlockSymbols()) {
            lastWasLineLabel = true;
            pCtx.visitLine(pCtx.getSourceFile(), line);

            return lastNode = pCtx.setLastLineLabel(new LineLabel(pCtx.getSourceFile(), line, pCtx));
          }
        }
        else {
          //因为刚才处理过代码行，因此将相应的标识置false,表示要处理正常的节点了
          lastWasComment = lastWasLineLabel = false;
        }
      }

      /**
       * Skip any whitespace currently under the starting point.
       */
      skipWhitespace();

      //上面部分忽略，以下才是重点

      /**
       * From here to the end of the method is the core MVEL parsing code.  Fiddling around here is asking for
       * trouble unless you really know what you're doing.
       */

      st = cursor;

      //主循环
      Mainloop:
      //主要用于支持，像 ,这种处理
      while (cursor != end) {
        //先尝试捕获,变量，数字等,即找到第一个操作数 或者是 操作关键字
        if (isIdentifierPart(expr[cursor])) {
          capture = true;
          cursor++;

          while (cursor != end && isIdentifierPart(expr[cursor])) cursor++;
        }

        /**
         * If the current character under the cursor is a valid
         * part of an identifier, we keep capturing.
         */

        //因为第一个操作关键字已经找到，则根据不同的类型看怎么进行处理
        if (capture) {
          String t;//当前处理的操作符
          //首先看其是否是语法关键字,判断是否指定的操作关键字 以根据不同的关键字作相应的处理
          if (OPERATORS.containsKey(t = new String(expr, st, cursor - st)) && !Character.isDigit(expr[st])) {
            switch (OPERATORS.get(t)) {
              //new 关键字处理，表示是一个新建对象操作
              //处理的逻辑，包括Date(),Date(1),Date{}，以及Date[2][3]这种情况
              case NEW:
                //new 关键字后面必须接一个有效的 变量
                if (!isIdentifierPart(expr[st = cursor = trimRight(cursor)])) {
                  throw new CompileException("unexpected character (expected identifier): "
                      + expr[cursor], expr, st);
                }

                /**
                 * 找到new后面的连续的操作数，如果后面有带xx[这种，则尝试捕获多维数组块
                 * 即这里会出现两种情况 new Abc 或者是 new Abc[xxx] 以及new Abc[xxx][yyy]这种
                 * Capture the beginning part of the token.
                 */
                do {
                  captureToNextTokenJunction();
                  skipWhitespace();
                }
                while (cursor < end && expr[cursor] == '[');//这里的while循环处理 多维数组

                /**
                 * 这里实际上就是跳到下一个空格前结束，会把整个 new Abc(123).ef xyz中，跳到xyz之前的数据
                 * 同时，这里判定最后一个有效符不能为]，即不能是类似[]这种，对于[]这种，只会有一种情况，就是后面
                 * 接{，这种会在后面继续处理，因此这里需要排除这种处理，即针对普通的new Abc，将整个表达式一直延伸
                 * 到语句结束
                 * If it's not a dimentioned array, continue capturing if necessary.
                 */
                if (cursor < end && !lastNonWhite(']')) captureToEOT();

                //因为当前是new 关键字，因此开始进行类型声明
                //虽然传递了整个语句，但是在typeDesc中会自动提取相应的类型
                TypeDescriptor descr = new TypeDescriptor(expr, st, trimLeft(cursor) - st, fields);

                //如果是函数,则使用函数式的处理方式
                if (pCtx.getFunctions().containsKey(descr.getClassName())) {
                  return lastNode = new NewObjectPrototype(pCtx, pCtx.getFunction(descr.getClassName()));
                }

                //如果是相应的原型引用,则使用新建原型节点来描述
                if (pCtx.hasProtoImport(descr.getClassName())) {
                  return lastNode = new NewPrototypeNode(descr, pCtx);
                }

                //默认情况下,创建正常的新建对象节点
                lastNode = new NewObjectNode(descr, fields, pCtx);

                //---------------------------- 专门处理新建数组的逻辑 start ------------------------------//
                skipWhitespace();
                //这里表示，如果是数组 ，后面允许追加{这种，如[]{2,3,4}这种情况
                if (cursor != end && expr[cursor] == '{') {
                  //无size数组判定
                  if (!((NewObjectNode) lastNode).getTypeDescr().isUndimensionedArray()) {
                    throw new CompileException(
                        "conflicting syntax: dimensioned array with initializer block",
                        expr, st);
                  }

                  st = cursor;
                  Class egressType = lastNode.getEgressType();

                  //前面解析类型没有取到，这里再次尝试一下
                  //这里的逻辑本应该不会放在这里,估计是临时修正
                  if (egressType == null) {
                    try {
                      egressType = getClassReference(pCtx, descr);
                    }
                    catch (ClassNotFoundException e) {
                      throw new CompileException("could not instantiate class", expr, st, e);
                    }
                  }


                  //跳过整个{块结束
                  cursor = balancedCaptureWithLineAccounting(expr, st, end, expr[cursor], pCtx) + 1;
                  //如果是new int[]{1,23}.length，则使用联合节点，否则使用默认的内联节点
                  if (tokenContinues()) {
                    //将主节点(即集合)与后续的属性访问链接起来
                    lastNode = new InlineCollectionNode(expr, st, cursor - st, fields,
                        egressType, pCtx);
                    st = cursor;
                    captureToEOT();
                    return lastNode = new Union(expr, st + 1, cursor, fields, lastNode, pCtx);
                  }
                  else {
                    return lastNode = new InlineCollectionNode(expr, st, cursor - st, fields,
                        egressType, pCtx);
                  }
                }
                //集合对象,但后面没有{1,2}这种即时声明,即必须在内部有长度信息
                else if (((NewObjectNode) lastNode).getTypeDescr().isUndimensionedArray()) {
                  throw new CompileException("array initializer expected", expr, st);
                }
                st = cursor;

                //---------------------------- 专门处理新建数组的逻辑 end ------------------------------//

                return lastNode;

              //断言类
              case ASSERT:
                st = cursor = trimRight(cursor);
                captureToEOS();
                return lastNode = new AssertNode(expr, st, cursor-- - st, fields, pCtx);

              //返回类
              case RETURN:
                st = cursor = trimRight(cursor);
                captureToEOS();
                return lastNode = new ReturnNode(expr, st, cursor - st, fields, pCtx);

              //if条件
              case IF:
                return captureCodeBlock(ASTNode.BLOCK_IF);

              //不会直接支持else
              case ELSE:
                throw new CompileException("else without if", expr, st);

                //for each 循环处理
              case FOREACH:
                return captureCodeBlock(ASTNode.BLOCK_FOREACH);

              //while循环
              case WHILE:
                return captureCodeBlock(ASTNode.BLOCK_WHILE);

              //until循环
              case UNTIL:
                return captureCodeBlock(ASTNode.BLOCK_UNTIL);

              //for循环
              case FOR:
                return captureCodeBlock(ASTNode.BLOCK_FOR);

              //with 处理
              case WITH:
                return captureCodeBlock(ASTNode.BLOCK_WITH);

              //do while循环
              case DO:
                return captureCodeBlock(ASTNode.BLOCK_DO);

              //stack指令集
              case STACKLANG:
                return captureCodeBlock(STACKLANG);

              case PROTO:
                return captureCodeBlock(PROTO);

              //ifDef命令
              case ISDEF:
                st = cursor = trimRight(cursor);
                captureToNextTokenJunction();
                return lastNode = new IsDef(expr, st, cursor - st, pCtx);

              //解析import节点,即引入特定信息
              case IMPORT:
                st = cursor = trimRight(cursor);
                captureToEOS();
                ImportNode importNode = new ImportNode(expr, st, cursor - st, pCtx);

                //因为在这里已经引入了,因此提前加入到解析上下文中,以方便在后面的解析中会直接使用到
                //否则在后面的解析过程中可能就会出现解析冲突(如同类函数等)

                if (importNode.isPackageImport()) {
                  pCtx.addPackageImport(importNode.getPackageImport());
                }
                else {
                  pCtx.addImport(importNode.getImportClass().getSimpleName(), importNode.getImportClass());
                }
                return lastNode = importNode;

              //解析import static节点 引用单个方法
              case IMPORT_STATIC:
                st = cursor = trimRight(cursor);
                captureToEOS();
                StaticImportNode staticImportNode = new StaticImportNode(expr, st, trimLeft(cursor) - st, pCtx);
                //提前加入到上下文中
                pCtx.addImport(staticImportNode.getMethod().getName(), staticImportNode.getMethod());
                return lastNode = staticImportNode;

              //解析函数节点
              case FUNCTION:
                lastNode = captureCodeBlock(FUNCTION);
                st = cursor + 1;
                return lastNode;

              //解析自定义var 声明
              case UNTYPED_VAR:
                int end;
                st = cursor + 1;

                while (true) {
                  captureToEOT();
                  end = cursor;
                  skipWhitespace();

                  if (cursor != end && expr[cursor] == '=') {
                    //这里保证不会出现 var = 的情况
                    if (end == (cursor = st))
                      throw new CompileException("illegal use of reserved word: var", expr, st);

                    //有=号,即表示是一个正常的变量赋值操作,跳出整个操作循环,由后面的赋值处理程序来进行处理(=)
                    //在这个处理过程中,可以认为这里的var没有什么作用
                    //进一步认为,在mvel中,a=1这种赋值操作单元中,var是可选的
                    continue Mainloop;
                  }
                  else {
                    //没有=,则认为是声明一个变量定义
                    name = new String(expr, st, end - st);
                    //变量之前是有定义的(解析时定义),因此认为是重复定义,因此这里即直接引用
                    //并且是按照指定的顺序进行定义,因此这里需要在执行时也要进入到相应的变量下标中
                    if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      splitAccumulator.add(lastNode = new IndexedDeclTypedVarNode(idx, st, end - st, Object.class, pCtx));
                    }
                    //新定义变量,标识一下即可
                    else {
                      splitAccumulator.add(lastNode = new DeclTypedVarNode(name, expr, st, end - st, Object.class,
                          fields, pCtx));
                    }
                  }

                  if (cursor == this.end || expr[cursor] != ',') break;
                    //如果后面存在,则表示要继续进行变量定义,因此这里继续这个循环,否则就跳出此循环
                  else {
                    cursor++;
                    skipWhitespace();
                    st = cursor;
                  }
                }

                //因为可能会定义多个变量,因为这里将最后一个弹出来,即从右到左
                return (ASTNode) splitAccumulator.pop();

              //contains操作
              case CONTAINS:
                lastWasIdentifier = false;
                return lastNode = new OperatorNode(Operator.CONTAINS, expr, st, pCtx);

            }
          }

          skipWhitespace();

          /**
           * 这里处理 abc(xx) 这种情况，在这里abc，表示一个函数调用或属性访问，
           * 但这里并不了解应该如何处理，那么这里跳过整个()，继续处理
           * 因为下面的逻辑只处理一个属性的方式，这里因为可能是一个单独的方法调用。
           * 因此这里单独处理，以避免与下面设计冲突
           * 同时,这里会在最后处理一个propertyNode(即方法调用)
           * If we *were* capturing a token, and we just hit a non-identifier
           * character, we stop and figure out what to do.
           */
          if (cursor != end && expr[cursor] == '(') {
            cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '(', pCtx) + 1;
          }

          /**
           * 进入到这里，即表示已经拿到一个 属性信息或值信息,接下来根据后面的符号以进行进一步处理
           * 以级联处理如 a++,a+=1 这种运算信息，对于像 a + 1这种，则不由此循环处理
           * 这下面的主要处理即处理 属性运算符或者是 ?= 的情况，即形成propertyNode或者是assignNode
           *
           * 对于不能处理,都将跳出captureLoop,以解析为属性操作,并保留现场,在下一次操作中解析为操作符节点
           * If we encounter any of the following cases, we are still dealing with
           * a contiguous token.
           */
          CaptureLoop:
          while (cursor != end) {
            switch (expr[cursor]) {
              //.操作符，表示还会有进一步的处理，因此这里定义为联合操作,即会有多次属性访问这种
              case '.':
                union = true;
                cursor++;
                skipWhitespace();

                continue;

                // ? 操作符，有2种情况，一种是支持 nullSafe的属性访问，另一种是 3元操作符，因此这里分开进行处理
              case '?':
                //a.?b这种访问，表示安全的属性访问，这里进行支持，即如果b值是null(或在map中不存在)，则不会报NPE，而是提前返回
                if (lookToLast() == '.' || cursor == start) {
                  union = true;
                  cursor++;
                  continue;
                }
                else {
                  //三元运算符处理,退出循环,先处理当前捕获组,然后在单独地处理?,就会将?解析为一个操作符了
                  break CaptureLoop;
                }

                // + 操作符，有多种处理 ++, += 以及单独的 + 操作等，分开处理
              case '+':
                switch (lookAhead()) {
                  //++ 操作
                  case '+':
                    name = new String(subArray(st, trimLeft(cursor)));
                    //处理本地变量 及其它变量
                    if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      lastNode = new IndexedPostFixIncNode(idx, pCtx);
                    }
                    else {
                      lastNode = new PostFixIncNode(name, pCtx);
                    }

                    cursor += 2;

                    expectEOS();

                    return lastNode;

                  //+= 操作
                  case '=':
                    name = createStringTrimmed(expr, st, cursor - st);
                    st = cursor += 2;

                    captureToEOS();

                    if (union) {//之前有.号,表示深度属性赋值处理
                      //属性赋值操作
                      return lastNode = new DeepAssignmentNode(expr, st = trimRight(st), trimLeft(cursor) - st, fields,
                          ADD, name, pCtx);
                    }
                    else if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      //本地变量或入参操作（按下标处理)
                      return lastNode = new IndexedAssignmentNode(expr, st, cursor - st, fields,
                          ADD, name, idx, pCtx);
                    }
                    else {
                      //正常变量操作(按属性名)
                      return lastNode = new OperativeAssign(name, expr, st = trimRight(st), trimLeft(cursor) - st,
                          ADD, fields, pCtx);
                    }
                }

                //这里表示特殊的 10E+2这种写法，表示这里是一个数字信息，因此需要继续主循环,
                // 以继续捕获后面的+X中的X信息，并且重新在这里终止循环
                if (isDigit(lookAhead()) &&
                    cursor > 1 && (expr[cursor - 1] == 'E' || expr[cursor - 1] == 'e')
                    && isDigit(expr[cursor - 2])) {
                  cursor++;
                  //     capture = true;
                  continue Mainloop;
                }
                //正常的 加法操作
                break CaptureLoop;

              //- 操作符 处理 -- 以及 -= 处理流程与+相同
              case '-':
                switch (lookAhead()) {
                  // --操作符
                  case '-':
                    name = new String(subArray(st, trimLeft(cursor)));
                    if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      lastNode = new IndexedPostFixDecNode(idx, pCtx);
                    }
                    else {
                      lastNode = new PostFixDecNode(name, pCtx);
                    }
                    cursor += 2;

                    expectEOS();

                    return lastNode;

                  // -= 操作符
                  case '=':
                    name = new String(expr, st, trimLeft(cursor) - st);
                    st = cursor += 2;

                    captureToEOS();

                    if (union) {
                      return lastNode = new DeepAssignmentNode(expr, st, cursor - st, fields,
                          SUB, t, pCtx);
                    }
                    else if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                          SUB, idx, fields, pCtx);
                    }
                    else {
                      return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                          SUB, fields, pCtx);
                    }
                }

                if (isDigit(lookAhead()) &&
                    cursor > 1 && (expr[cursor - 1] == 'E' || expr[cursor - 1] == 'e')
                    && isDigit(expr[cursor - 2])) {
                  cursor++;
                  capture = true;
                  continue Mainloop;
                }

                //正常操作符
                break CaptureLoop;

              /**
               * 这些符号表示单向操作，不会与其它符号结合，因此直接中止符号处理
               * Exit immediately for any of these cases.
               */
              case '!':
              case ',':
              case '"':
              case '\'':
              case ';':
              case ':':
                break CaptureLoop;

              //处理需要与 ?=(如%=) 配合的情况
              case '\u00AB': // special compact code for recursive parses
              case '\u00BB':
              case '\u00AC':
              case '&':
              case '^':
              case '|':
              case '*':
              case '/':
              case '%':
                char op = expr[cursor];
                if (lookAhead() == '=') {
                  name = new String(expr, st, trimLeft(cursor) - st);

                  st = cursor += 2;
                  captureToEOS();

                  if (union) {
                    return lastNode = new DeepAssignmentNode(expr, st, cursor - st, fields,
                        opLookup(op), t, pCtx);
                  }
                  else if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                    return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                        opLookup(op), idx, fields, pCtx);
                  }
                  else {
                    return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                        opLookup(op), fields, pCtx);
                  }
                }
                break CaptureLoop;

              //处理 <<=
              case '<':
                if ((lookAhead() == '<' && lookAhead(2) == '=')) {
                  name = new String(expr, st, trimLeft(cursor) - st);

                  st = cursor += 3;
                  captureToEOS();

                  if (union) {
                    return lastNode = new DeepAssignmentNode(expr, st, cursor - st, fields,
                        BW_SHIFT_LEFT, t, pCtx);
                  }
                  else if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                    return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                        BW_SHIFT_LEFT, idx, fields, pCtx);
                  }
                  else {
                    return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                        BW_SHIFT_LEFT, fields, pCtx);
                  }
                }
                break CaptureLoop;

              //处理 >>= 或者是 >>>=
              case '>':
                if (lookAhead() == '>') {
                  if (lookAhead(2) == '=') {
                    name = new String(expr, st, trimLeft(cursor) - st);

                    st = cursor += 3;
                    captureToEOS();

                    if (union) {
                      return lastNode = new DeepAssignmentNode(expr, st, cursor - st, fields,
                          BW_SHIFT_RIGHT, t, pCtx);
                    }
                    else if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                          BW_SHIFT_RIGHT, idx, fields, pCtx);
                    }
                    else {
                      return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                          BW_SHIFT_RIGHT, fields, pCtx);
                    }
                  }
                  else if ((lookAhead(2) == '>' && lookAhead(3) == '=')) {
                    name = new String(expr, st, trimLeft(cursor) - st);

                    st = cursor += 4;
                    captureToEOS();

                    if (union) {
                      return lastNode = new DeepAssignmentNode(expr, st, cursor - st, fields,
                          BW_USHIFT_RIGHT, t, pCtx);
                    }
                    else if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                      return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                          BW_USHIFT_RIGHT, idx, fields, pCtx);
                    }
                    else {
                      return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                          BW_USHIFT_RIGHT, fields, pCtx);
                    }
                  }
                }
                break CaptureLoop;

              //跳过([{ 因为这些不会形成单独的处理语句
              case '(':
                cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '(', pCtx) + 1;
                continue;

              case '[':
                cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '[', pCtx) + 1;
                continue;

              case '{':
                if (!union) break CaptureLoop;
                cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '{', pCtx) + 1;
                continue;

                //处理 ~= 支持正则表达式处理
              case '~':
                if (lookAhead() == '=') {
                  // tmp = subArray(start, trimLeft(cursor));
                  tmpStart = st;
                  int tmpOffset = cursor - st;
                  st = cursor += 2;

                  captureToEOT();

                  return lastNode = new RegExMatch(expr, tmpStart, tmpOffset, fields, st, cursor - st, pCtx);
                }
                break CaptureLoop;

              //处理 正常的赋值操作 或者是 == 操作
              case '=':
                if (lookAhead() == '+') {
                  name = new String(expr, st, trimLeft(cursor) - st);

                  st = cursor += 2;

                  if (!isNextIdentifierOrLiteral()) {
                    throw new CompileException("unexpected symbol '" + expr[cursor] + "'", expr, st);
                  }

                  captureToEOS();

                  if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                    return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                        ADD, idx, fields, pCtx);
                  }
                  else {
                    return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                        ADD, fields, pCtx);
                  }
                }
                else if (lookAhead() == '-') {
                  //这里把 =- 和 -=弄成一样的了,可以认为就是 -=
                  name = new String(expr, st, trimLeft(cursor) - st);

                  st = cursor += 2;

                  if (!isNextIdentifierOrLiteral()) {
                    throw new CompileException("unexpected symbol '" + expr[cursor] + "'", expr, st);
                  }

                  captureToEOS();

                  if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                    return lastNode = new IndexedOperativeAssign(expr, st, cursor - st,
                        SUB, idx, fields, pCtx);
                  }
                  else {
                    return lastNode = new OperativeAssign(name, expr, st, cursor - st,
                        SUB, fields, pCtx);
                  }
                }
                //处理正常的赋值操作 提前赋值,而不是单独成项
                if (greedy && lookAhead() != '=') {
                  cursor++;

                  if (union) {
                    captureToEOS();

                    return lastNode = new DeepAssignmentNode(expr, st, cursor - st,
                        fields | ASTNode.ASSIGN, pCtx);
                  }
                  else if (lastWasIdentifier) {
                    //这里表示处理如 a.b = 4的这种情况，即前一个节点为一个声明节点
                    //这种情况就是属性赋值处理
                    return procTypedNode(false);
                  }
                  else if (pCtx != null && ((idx = pCtx.variableIndexOf(t)) != -1
                      && (pCtx.isIndexAllocation()))) {
                    captureToEOS();

                    IndexedAssignmentNode ian = new IndexedAssignmentNode(expr, st = trimRight(st),
                        trimLeft(cursor) - st,
                        ASTNode.ASSIGN, idx, pCtx);

                    if (idx == -1) {
                      pCtx.addIndexedInput(t = ian.getVarName());
                      ian.setRegister(pCtx.variableIndexOf(t));
                    }
                    return lastNode = ian;
                  }
                  else {
                    captureToEOS();

                    return lastNode = new AssignmentNode(expr, st, cursor - st,
                        fields | ASTNode.ASSIGN, pCtx);
                  }
                }

                //非greedy下的赋值处理
                break CaptureLoop;

              default:
                if (cursor != end) {
                  if (isIdentifierPart(expr[cursor])) {
                    //处理是否要进行级联处理的情况，如a.b.c这种，否则就跳过
                    //因为前面已经属性,如果这里并不是.级联操作,那肯定是其它情况,否则就是级联下的属性访问等
                    if (!union) {
                      break CaptureLoop;
                    }
                    cursor++;
                    //因为这里拿到的是定义符,则直接贪婪式处理
                    while (cursor != end && isIdentifierPart(expr[cursor])) cursor++;
                  }
                  //其它操作符,并且后面继续接定义符,并由后面处理,如空格等
                  else if ((cursor + 1) != end && isIdentifierPart(expr[cursor + 1])) {
                    break CaptureLoop;
                  }
                  else {
                    cursor++;
                  }
                }
                else {
                  break CaptureLoop;
                }
            }
          }

          /**
           * Produce the token.
           */
          trimWhitespace();

          //因为已经捕获取操作属性,因此这里认为是属性操作符节点,则创建出相应的属性信息
          return createPropertyToken(st, cursor);
        }
        else {
          //没有捕获字符串，表示后面为一些操作符，因此进入操作符处理
          switch (expr[cursor]) {
            case '.': {
              cursor++;
              //如果.后接数字，表示碰到了小数点，那么表示一个普通的浮点数
              //因为是数字,则交由上面的属性处理来完成,即创建出propertyToken信息
              if (isDigit(expr[cursor])) {
                capture = true;
                continue;
              }
              expectNextChar_IW('{');

              //处理with节点 .{ 则表示with
              return lastNode = new ThisWithNode(expr, st, cursor - st - 1
                  , cursor + 1,
                  (cursor = balancedCaptureWithLineAccounting(expr,
                      cursor, end, '{', pCtx) + 1) - 3, fields, pCtx);
            }

            //表示引用拦截器 拦截器需要提前通过parseContext进行注入
            case '@': {
              st++;
              captureToEOT();

              if (pCtx == null || (pCtx.getInterceptors() == null || !pCtx.getInterceptors().
                  containsKey(name = new String(expr, st, cursor - st)))) {
                throw new CompileException("reference to undefined interceptor: "
                    + new String(expr, st, cursor - st), expr, st);
              }

              return lastNode = new InterceptorWrapper(pCtx.getInterceptors().get(name), nextToken(), pCtx);
            }

            //普通赋值
            case '=':
              return createOperator(expr, st, (cursor += 2));

            //处理-号
            case '-':
              //支持--x操作，即前置操作
              if (lookAhead() == '-') {
                cursor += 2;
                skipWhitespace();
                st = cursor;
                captureIdentifier();

                name = new String(subArray(st, cursor));
                if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                  return lastNode = new IndexedPreFixDecNode(idx, pCtx);
                }
                else {
                  return lastNode = new PreFixDecNode(name, pCtx);
                }
              }
              else if ((cursor == start || (lastNode != null &&
                  (lastNode instanceof BooleanNode || lastNode.isOperator())))
                  && !isDigit(lookAhead())) {

                //支持取-操作，即前一个节点是一个操作符，
                // 如 a+ -x这种，这里即表示对后面的表达式取反操作
                cursor += 1;
                captureToEOT();
                return new Sign(expr, st, cursor - st, fields, pCtx);
              }
              else if ((cursor != start &&
                  (lastNode != null && !(lastNode instanceof BooleanNode || lastNode.isOperator())))
                  || !isDigit(lookAhead())) {

                //标准的 减法 操作节点
                return createOperator(expr, st, cursor++ + 1);
              }
              else if ((cursor - 1) != start || (!isDigit(expr[cursor - 1])) && isDigit(lookAhead())) {
                //这里表示一个正常的 负数，因此往前处理
                // 由 createPropertyToken 来完成相应节点的创建操作
                cursor++;
                break;
              }
              else {
                throw new CompileException("not a statement", expr, st);
              }

            case '+':
              if (lookAhead() == '+') {
                // ++ 前置操作节点
                cursor += 2;
                skipWhitespace();
                st = cursor;
                captureIdentifier();

                name = new String(subArray(st, cursor));
                if (pCtx != null && (idx = pCtx.variableIndexOf(name)) != -1) {
                  return lastNode = new IndexedPreFixIncNode(idx, pCtx);
                }
                else {
                  return lastNode = new PreFixIncNode(name, pCtx);
                }
              }
              //由-号对照代码可以看出，这里不支持+x的写法，因此下一步直接就是相应的操作节点
              return createOperator(expr, st, cursor++ + 1);

            //乘法操作
            case '*':
              //这里支持 * 法以及 ** 操作，后者表示对自己执行多少次*操作
              if (lookAhead() == '*') {
                cursor++;
              }
              return createOperator(expr, st, cursor++ + 1);

            //表达式终止操作,当前操作节点进入一个终止段
            case ';':
              cursor++;
              lastWasIdentifier = false;
              return lastNode = new EndOfStatement(pCtx);

            case '?':
              if (cursor == start) {
                //这里表示在属性访问中 .?操作符，因为没有前置操作，默认即以ctx对象作为root进行访问，即表示取当前对象的?属性信息
                cursor++;
                continue;
              }


              //其它运算符
            case '#'://此操作符当前未被支持，会报错
            case '/':
            case ':':
            case '^':
            case '%': {
              return createOperator(expr, st, cursor++ + 1);
            }

            case '(': {
              cursor++;

              //是否全是定义字符,为类型转换作处理
              boolean singleToken = true;

              skipWhitespace();
              for (brace = 1; cursor != end && brace != 0; cursor++) {
                switch (expr[cursor]) {
                  case '(':
                    brace++;
                    break;
                  case ')':
                    brace--;
                    break;
                  case '\'':
                    cursor = captureStringLiteral('\'', expr, cursor, end);
                    break;
                  case '"':
                    cursor = captureStringLiteral('"', expr, cursor, end);
                    break;
                  //支持 in Fold表达式
                  case 'i':
                    if (brace == 1 && isWhitespace(lookBehind()) && lookAhead() == 'n' && isWhitespace(lookAhead(2))) {

                      for (int level = brace; cursor != end; cursor++) {
                        switch (expr[cursor]) {
                          case '(':
                            brace++;
                            break;
                          case ')':
                            if (--brace < level) {
                              cursor++;
                              if (tokenContinues()) {
                                lastNode = new Fold(expr, trimRight(st + 1),
                                    cursor - st - 2, fields, pCtx);
                                if (expr[st = cursor] == '.') st++;
                                captureToEOT();
                                return lastNode = new Union(expr, st = trimRight(st),
                                    cursor - st, fields, lastNode, pCtx);
                              }
                              else {
                                return lastNode = new Fold(expr, trimRight(st + 1),
                                    cursor - st - 2, fields, pCtx);
                              }
                            }
                            break;
                          case '\'':
                            cursor = captureStringLiteral('\'', expr, cursor, end);
                            break;
                          case '"':
                            cursor = captureStringLiteral('\"', expr, cursor, end);
                            break;
                        }
                      }

                      throw new CompileException("unterminated projection; closing parathesis required",
                          expr, st);
                    }
                    break;

                  default:
                    /**
                     * 这里即检测这是不是一个类型转换操作，如(Abc) x这种处理，存在类型转换的惟一可能就是括号内
                     * 只是由字母表示的定义信息，因此只要不满足这一条件，那就不是类型转换
                     * Check to see if we should disqualify this current token as a potential
                     * type-cast candidate.
                     */

                    if (expr[cursor] != '.') {
                      switch (expr[cursor]) {
                        case '[':
                        case ']':
                          break;

                        default:
                          if (!(isIdentifierPart(expr[cursor]) || expr[cursor] == '.')) {
                            singleToken = false;
                          }
                      }
                    }
                }
              }

              //因为以 (开始，也必须以 )结束，这里作一次校验操作
              if (brace != 0) {
                throw new CompileException("unbalanced braces in expression: (" + brace + "):",
                    expr, st);
              }

              tmpStart = -1;
              //这里先尝试一下是否是类型匹配操作，因为并不一定，因此这里有一个try操作
              if (singleToken) {
                int _st;
                TypeDescriptor tDescr = new TypeDescriptor(expr, _st = trimRight(st + 1),
                    trimLeft(cursor - 1) - _st, fields);

                Class cls;
                try {
                  if (tDescr.isClass() && (cls = getClassReference(pCtx, tDescr)) != null) {

                    //已经成功处理了类型信息，但是后面不一定是正确的表达式，因此这里要再作一次确认
                    //如 (X) axxx 或者是 (X) (abc) 这种操作均认为是正确的表达式
                    //如 (X) -xx 这种，则不认为是有效的
                    // lookahead to check if it could be a real cast
                    boolean isCast = false;
                    for (int i = cursor; i < expr.length; i++) {
                      if (expr[i] == ' ' || expr[i] == '\t') continue;
                      isCast = isIdentifierPart(expr[i]) || expr[i] == '\'' || expr[i] == '"' || expr[i] == '(';
                      break;
                    }

                    //认为是类型转换,则返回类型转换节点
                    if (isCast) {
                      st = cursor;

                      captureToEOT();
                      //   captureToEOS();

                      return lastNode = new TypeCast(expr, st, cursor - st,
                          cls, fields, pCtx);
                    }
                  }
                }
                catch (ClassNotFoundException e) {
                  // fallthrough
                }

              }

              //由idea可知这里肯定不会进这个if
              if (tmpStart != -1) {
                return handleUnion(handleSubstatement(new Substatement(expr, tmpStart, cursor - tmpStart, fields, pCtx)));
              }
              else {
                //处理单独的(类型节点)，并且与可能存在的后续调用 联合起来
                return handleUnion(
                    handleSubstatement(
                        new Substatement(expr, st = trimRight(st + 1),
                            trimLeft(cursor - 1) - st, fields, pCtx)));
              }
            }

            case '}':
            case ']':
            case ')': {
              throw new CompileException("unbalanced braces", expr, st);
            }

            //处理 > >> >>> >= >>= 操作
            case '>': {
              switch (expr[cursor + 1]) {
                case '>':
                  if (expr[cursor += 2] == '>') cursor++;
                  return createOperator(expr, st, cursor);
                case '=':
                  return createOperator(expr, st, cursor += 2);
                default:
                  return createOperator(expr, st, ++cursor);
              }
            }

            //处理 < << <<< <= < 操作
            case '<': {
              if (expr[++cursor] == '<') {
                if (expr[++cursor] == '<') cursor++;
                return createOperator(expr, st, cursor);
              }
              else if (expr[cursor] == '=') {
                return createOperator(expr, st, ++cursor);
              }
              else {
                return createOperator(expr, st, cursor);
              }
            }

            //处理字符串节点
            case '\'':
            case '"':
              lastNode = new LiteralNode(handleStringEscapes(subset(expr, st + 1,
                  (cursor = captureStringLiteral(expr[cursor], expr, cursor, end)) - st - 1))
                  , String.class, pCtx);

              cursor++;

              //处理字符串的联合操作
              if (tokenContinues()) {
                return lastNode = handleUnion(lastNode);
              }

              return lastNode;

            //处理 & 及 && 节点
            case '&': {
              if (expr[cursor++ + 1] == '&') {
                return createOperator(expr, st, ++cursor);
              }
              else {
                return createOperator(expr, st, cursor);
              }
            }

            //处理 | 和 || 节点
            case '|': {
              if (expr[cursor++ + 1] == '|') {
                return createOperator(expr, st, ++cursor);
              }
              else {
                return createOperator(expr, st, cursor);
              }
            }

            //处理 ~ ~= 操作
            case '~':
              if ((cursor++ - 1 != 0 || !isIdentifierPart(lookBehind()))
                  && isDigit(expr[cursor])) {
                //这里表示是独立的 ~操作 ，因为后面为数字
                st = cursor;
                captureToEOT();
                return lastNode = new Invert(expr, st, cursor - st, fields, pCtx);
              }
              else if (expr[cursor] == '(') {
                //后面接(，表示一个执行块，也是取~操作
                st = cursor--;
                captureToEOT();
                return lastNode = new Invert(expr, st, cursor - st, fields, pCtx);
              }
              else {
                //正常的~= 操作 表示一个正则表达式操作
                if (expr[cursor] == '=') cursor++;
                return createOperator(expr, st, cursor);
              }

              //处理 取反操作 或者是 !=操作
            case '!': {
              ++cursor;
              if (isNextIdentifier()) {
                //这里表示后面接一个字符串，那么需要判断后面必须是一个正常非操作符节点 即如 !xx ! isdef xx这种操作
                if (lastNode != null && !lastNode.isOperator()) {
                  throw new CompileException("unexpected operator '!'", expr, st);
                }

                st = cursor;
                captureToEOT();
                if ("new".equals(name = new String(expr, st, cursor - st))
                    || "isdef".equals(name)) {
                  //支持 ! new true 或者是 ! isdef x
                  captureToEOT();
                  return lastNode = new Negation(expr, st, cursor - st, fields, pCtx);
                }
                else {
                  //正常的!x调用
                  return lastNode = new Negation(expr, st, cursor - st, fields, pCtx);
                }
              }
              else if (expr[cursor] == '(') {
                //支持 ! (xxx) 这种操作
                st = cursor--;
                captureToEOT();
                return lastNode = new Negation(expr, st, cursor - st, fields, pCtx);
              }
              else if (expr[cursor] == '!') {
                // 两个 !! 操作，正常忽略
                // just ignore a double negation
                ++cursor;
                return nextToken();
              }
              else if (expr[cursor] != '=')
                throw new CompileException("unexpected operator '!'", expr, st, null);
              else {
                // != 操作
                return createOperator(expr, st, ++cursor);
              }
            }

            //表示一个单独的数组或集合信息
            case '[':
            case '{':
              cursor = balancedCaptureWithLineAccounting(expr, cursor, end, expr[cursor], pCtx) + 1;
              if (tokenContinues()) {
                //这里判断是否是[1,2].length这种操作，如果是，则联合起来,这里要求之间不能有空格如[1] [2] .length
                // 标准java是正确的，但这里不允许
                lastNode = new InlineCollectionNode(expr, st, cursor - st, fields, pCtx);
                st = cursor;
                captureToEOT();
                if (expr[st] == '.') st++;

                return lastNode = new Union(expr, st, cursor - st, fields, lastNode, pCtx);
              }
              else {
                //单独的集合操作
                return lastNode = new InlineCollectionNode(expr, st, cursor - st, fields, pCtx);
              }

            default:
              cursor++;
          }
        }
      }

      if (st == cursor)
        return null;
      else
        return createPropertyToken(st, cursor);
    }
    catch (RedundantCodeException e) {
      return nextToken();
    }
    catch (NumberFormatException e) {
      throw new CompileException("badly formatted number: " + e.getMessage(), expr, st, e);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new CompileException("unexpected end of statement", expr, cursor, e);
    }
    catch (ArrayIndexOutOfBoundsException e) {
      throw new CompileException("unexpected end of statement", expr, cursor, e);
    }
    catch (CompileException e) {
      throw ErrorUtil.rewriteIfNeeded(e, expr, cursor);
    }
  }

  /** 如果子节点是一个纯字符串,则尝试直接进行解析,并转换为相应的常量节点 */
  public ASTNode handleSubstatement(Substatement stmt) {
    if (stmt.getStatement() != null && stmt.getStatement().isLiteralOnly()) {
      return new LiteralNode(stmt.getStatement().getValue(null, null, null), pCtx);
    }
    else {
      return stmt;
    }
  }

  /**
   * 用于处理联合操作，如果一个节点，后接.或者是[
   * 则表示此节点还需要进行下一次调用,则这里将相应的调用联合起来
   * Handle a union between a closed statement and a residual property chain.
   *
   * @param node an ast node
   * @return ASTNode
   */
  protected ASTNode handleUnion(ASTNode node) {
    if (cursor != end) {
      skipWhitespace();
      int union = -1;
      if (cursor < end) {
        switch (expr[cursor]) {
          case '.':
            union = cursor + 1;
            break;
          case '[':
            union = cursor;
        }
      }

      if (union != -1) {
        captureToEOT();
        return lastNode = new Union(expr, union, cursor - union, fields, node, pCtx);
      }

    }
    return lastNode = node;
  }

  /**
   * 创建一个相应的操作节点，仅用于描述当前操作信息
   * Create an operator node.
   *
   * @param expr  an char[] containing the expression
   * @param start the start offet for the token
   * @param end   the end offset for the token
   * @return ASTNode
   */
  private ASTNode createOperator(final char[] expr, final int start, final int end) {
    lastWasIdentifier = false;
    return lastNode = new OperatorNode(OPERATORS.get(new String(expr, start, end - start)), expr, start, pCtx);
  }

  /**
   * 用于提取从start开始，到结束(不包括)之间的字符串
   * Create a copy of an array based on a sub-range.  Works faster than System.arrayCopy() for arrays shorter than
   * 1000 elements in most cases, so the parser uses this internally.
   *
   * @param start the start offset
   * @param end   the end offset
   * @return an array
   */
  private char[] subArray(final int start, final int end) {
    if (start >= end) return new char[0];

    char[] newA = new char[end - start];
    for (int i = 0; i != newA.length; i++) {
      newA[i] = expr[i + start];
    }

    return newA;
  }

  /**
   * 根据当前区间范围创建节点信息,可以是属性节点或者是纯字符串常量节点
   * Generate a property token
   *
   * @param st  the start offset
   * @param end the end offset
   * @return an ast node
   */
  private ASTNode createPropertyToken(int st, int end) {
    String tmp;

    if (isPropertyOnly(expr, st, end)) {
      if (pCtx != null && pCtx.hasImports()) {
        int find;

        //解析可能存在的是一个引用的类全名，也可能是引用的普通类名
        if ((find = findFirst('.', st, end - st, expr)) != -1) {
          //这段代码不可能出现，因为上面已经判定这里的expr不能存在.号(由isPropertyOnly决定)
          String iStr = new String(expr, st, find - st);
          if (pCtx.hasImport(iStr)) {
            lastWasIdentifier = true;
            return lastNode = new LiteralDeepPropertyNode(expr, find + 1, end - find - 1, fields,
                pCtx.getImport(iStr), pCtx);
          }
        }
        else {
          if (pCtx.hasImport(tmp = new String(expr, st, cursor - st))) {
            lastWasIdentifier = true;
            return lastNode = new LiteralNode(pCtx.getStaticOrClassImport(tmp), pCtx);
          }
        }
      }

      if (LITERALS.containsKey(tmp = new String(expr, st, end - st))) {
        //常量节点
        lastWasIdentifier = true;
        return lastNode = new LiteralNode(LITERALS.get(tmp), pCtx);
      }
      else if (OPERATORS.containsKey(tmp)) {
        //操作节点
        lastWasIdentifier = false;
        return lastNode = new OperatorNode(OPERATORS.get(tmp), expr, st, pCtx);
      }
      else if (lastWasIdentifier) {
        return procTypedNode(true);
      }
    }

    if (pCtx != null && pCtx.hasImports() && isArrayType(expr, st, end)) {
      //这里也不可能进入，因此已经判断这里必须为一个属性，如果是[，则在前面的nextToken处即会被拦截处理
      if (pCtx.hasImport(new String(expr, st, cursor - st - 2))) {
        lastWasIdentifier = true;
        TypeDescriptor typeDescriptor = new TypeDescriptor(expr, st, cursor - st, fields);

        try {
          return lastNode = new LiteralNode(typeDescriptor.getClassReference(pCtx), pCtx);
        }
        catch (ClassNotFoundException e) {
          throw new CompileException("could not resolve class: " + typeDescriptor.getClassName(), expr, st);
        }
      }
    }

    lastWasIdentifier = true;

    //默认情况下，认为这是一个默认的属性节点(即通用的astNode，最终由属性访问器来进行访问)
    return lastNode = new ASTNode(expr, trimRight(st), trimLeft(end) - st, fields, pCtx);
  }

  /**
   * 处理当前有类型声明的节点,即当前节点还需要一些额外处理
   * Process the current typed node
   *
   * @param decl node is a declaration or not
   *             是否是仅仅声明.如果是false,则表示当前还需要继续进行赋值操作,即对当前节点进行赋值处理
   * @return and ast node
   */
  private ASTNode procTypedNode(boolean decl) {
    while (true) {
      //上一个字点为字符串，那么认为此字符串应该表示一个类型信息，先更换为类型节点信息，以方便下面使用
      if (lastNode.getLiteralValue() instanceof String) {
        char[] tmp = ((String) lastNode.getLiteralValue()).toCharArray();
        TypeDescriptor tDescr = new TypeDescriptor(tmp, 0, tmp.length, 0);

        try {
          lastNode.setLiteralValue(getClassReference(pCtx, tDescr));
          lastNode.discard();
        }
        catch (Exception e) {
          // fall through;
        }
      }

      //上一个节点为类型节点
      if (lastNode.isLiteral() && lastNode.getLiteralValue() instanceof Class) {
        //因为使用新的类型声明节点替换了此节点，因此当前节点被废除
        lastNode.discard();

        captureToEOS();

        if (decl) {
          //仅仅是声明节点
          splitAccumulator.add(new DeclTypedVarNode(new String(expr, st, cursor - st), expr, st, cursor - st,
              (Class) lastNode.getLiteralValue(), fields | ASTNode.ASSIGN, pCtx));
        }
        else {
          captureToEOS();
          splitAccumulator.add(new TypedVarNode(expr, st, cursor - st - 1, fields | ASTNode.ASSIGN, (Class)
              lastNode.getLiteralValue(), pCtx));
        }
      }
      //原型节点
      else if (lastNode instanceof Proto) {
        captureToEOS();
        if (decl) {
          splitAccumulator.add(new DeclProtoVarNode(new String(expr, st, cursor - st),
              (Proto) lastNode, fields | ASTNode.ASSIGN, pCtx));
        }
        else {
          splitAccumulator.add(new ProtoVarNode(expr, st, cursor - st, fields | ASTNode.ASSIGN, (Proto)
              lastNode, pCtx));
        }
      }

      // this redundant looking code is needed to work with the interpreter and MVELSH properly.
      //这里因为不是编译阶段，可能是解释运行阶段，因此尝试从栈中找到上一次的类型信息
      else if ((fields & ASTNode.COMPILE_IMMEDIATE) == 0) {
        if (stk.peek() instanceof Class) {
          captureToEOS();
          if (decl) {
            splitAccumulator.add(new DeclTypedVarNode(new String(expr, st, cursor - st), expr, st, cursor - st,
                (Class) stk.pop(), fields | ASTNode.ASSIGN, pCtx));
          }
          else {
            splitAccumulator.add(new TypedVarNode(expr, st, cursor - st,
                fields | ASTNode.ASSIGN, (Class) stk.pop(), pCtx));
          }
        }
        else if (stk.peek() instanceof Proto) {
          captureToEOS();
          if (decl) {
            splitAccumulator.add(new DeclProtoVarNode(new String(expr, st, cursor - st),
                (Proto) stk.pop(), fields | ASTNode.ASSIGN, pCtx));
          }
          else {
            splitAccumulator.add(new ProtoVarNode(expr, st, cursor - st, fields | ASTNode.ASSIGN, (Proto)
                stk.pop(), pCtx));
          }
        }
        else {
          throw new CompileException("unknown class or illegal statement: " + lastNode.getLiteralValue(), expr, cursor);
        }
      }
      else {
        throw new CompileException("unknown class or illegal statement: " + lastNode.getLiteralValue(), expr, cursor);
      }

      skipWhitespace();
      //如果存在 逗号，表示是多个声明，如 var a = 3,b =2的这种
      if (cursor < end && expr[cursor] == ',') {
        st = ++cursor;
        splitAccumulator.add(new EndOfStatement(pCtx));
      }
      else {
        return (ASTNode) splitAccumulator.pop();
      }
    }
  }

  /**
   * 生成一个带条件的语句块，即类似执行if while foreach这种语句块
   * 相应的参数信息包括条件区间,执行块区间,这些信息足以创建出相应的节点信息
   * Generate a code block token.
   *
   * @param condStart  the start offset for the condition
   * @param condEnd    the end offset for the condition
   * @param blockStart the start offset for the block
   * @param blockEnd   the end offset for the block
   * @param type       the type of block
   * @return and ast node
   */
  private ASTNode createBlockToken(final int condStart,
                                   final int condEnd, final int blockStart, final int blockEnd, int type) {
    lastWasIdentifier = false;
    cursor++;

    //这里如果没有显示的结束符，则强制添加一个结束操作
    if (isStatementNotManuallyTerminated()) {
      splitAccumulator.add(new EndOfStatement(pCtx));
    }

    int condOffset = condEnd - condStart;
    int blockOffset = blockEnd - blockStart;

    if (blockOffset < 0) blockOffset = 0;

    switch (type) {
      //if节点
      case ASTNode.BLOCK_IF:
        return new IfNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
      //for节点
      case ASTNode.BLOCK_FOR:
        //这里在for中，以支持for(abc:efg)这种格式，即转换为foreach表达式
        for (int i = condStart; i < condEnd; i++) {
          if (expr[i] == ';')
            return new ForNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
          else if (expr[i] == ':')
            break;
        }
        //for(a:b)节点
      case ASTNode.BLOCK_FOREACH:
        return new ForEachNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
      //while循环
      case ASTNode.BLOCK_WHILE:
        return new WhileNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
      //until 与 while相同
      case ASTNode.BLOCK_UNTIL:
        return new UntilNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
      //do while循环节点
      case ASTNode.BLOCK_DO:
        return new DoNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
      //tod until节点
      case ASTNode.BLOCK_DO_UNTIL:
        return new DoUntilNode(expr, condStart, condOffset, blockStart, blockOffset, pCtx);
      default:
        return new WithNode(expr, condStart, condOffset, blockStart, blockOffset, fields, pCtx);
    }
  }

  /**
   * 捕获一个语法块,即获取一个类似使用 {} 或者 单独的空格的处理单元
   * 一个语法块，可以认为是 {}里面的语句，里面的语句又使用递归的执行单元来描述
   * Capture a code block by type.
   *
   * @param type the block type
   * @return an ast node
   */
  private ASTNode captureCodeBlock(int type) {
    //相应处理的节点内需要有条件表达式,即类似 if(xxx),for(yyy)这种内部语句
    //这里的cond不一样必须是单个条件,也可能表示for中的多个块
    boolean cond = true;

    //第一个节点,在if处理中使用第一个if来表示整个逻辑
    ASTNode first = null;
    //用于在if else中进行上下联接
    ASTNode tk = null;

    switch (type) {
      //单元处理if else,因此有多个嵌套
      case ASTNode.BLOCK_IF: {
        do {
          //这里判定上一个节点非空(即不是起始节点),那么后续节点需要看其是否是if语句
          //主要即判定其是否是else if
          //那么就需要里面带条件
          if (tk != null) {
            captureToNextTokenJunction();
            skipWhitespace();
            cond = expr[cursor] != '{' && expr[cursor] == 'i' && expr[++cursor] == 'f'
                && expr[cursor = incNextNonBlank()] == '(';
          }

          //这里使用循环处理，保证第1个为if,或者是else if中的if
          //后面的为else if或者是else，如果是else节点，则表示整个语句结束
          if (((IfNode) (tk = _captureBlock(tk, expr, cond, type))).getElseBlock() != null) {
            cursor++;
            return first;
          }

          if (first == null) first = tk;

          //表达式并没有以;结束,表示后续还可能有else if
          //这里的强行下标+1,会在ifthenelse判定中-1
          //这里下标往前一位,主要是让判定提前结束,即如果是; 提前返回即可
          if (cursor != end && expr[cursor] != ';') {
            cursor++;
          }
        }
        while (ifThenElseBlockContinues());

        return first;
      }

      //do 循环，无需要条件
      case ASTNode.BLOCK_DO:
        skipWhitespace();
        return _captureBlock(null, expr, false, type);

      //默认情况，with以及foreach等,for while等
      default: // either BLOCK_WITH or BLOCK_FOREACH
        captureToNextTokenJunction();
        skipWhitespace();
        return _captureBlock(null, expr, true, type);
    }
  }

  /**
   * 准备捕获块节点的内容信息,并针对相应的信息进行解析和处理
   *
   * @param cond 是否要处理条件信息，即后面需要带条件信息
   * @param node 前一个节点,即要处理的节点的前一个节点信息,用于处理需要与前一个节点交互的情况,如else if
   */
  private ASTNode _captureBlock(ASTNode node, final char[] expr, boolean cond, int type) {
    skipWhitespace();
    int startCond = 0;
    int endCond = 0;

    int blockStart;
    int blockEnd;

    String name;

    /**
     * Functions are a special case we handle differently from the rest of block parsing
     */
    switch (type) {
      //解析自定义函数信息
      case FUNCTION: {
        int st = cursor;

        captureToNextTokenJunction();

        if (cursor == end) {
          throw new CompileException("unexpected end of statement", expr, st);
        }

        /**
         * 判定此函数不是是已知关键字或操作符
         * Check to see if the name is legal.
         */
        if (isReservedWord(name = createStringTrimmed(expr, st, cursor - st))
            || isNotValidNameorLabel(name))
          throw new CompileException("illegal function name or use of reserved word", expr, cursor);

        //进行函数解析
        FunctionParser parser = new FunctionParser(name, cursor, end - cursor, expr, fields, pCtx, splitAccumulator);
        Function function = parser.parse();
        cursor = parser.getCursor();

        return lastNode = function;
      }
      //原型解析，不作翻译
      case PROTO: {
        if (ProtoParser.isUnresolvedWaiting()) {
          ProtoParser.checkForPossibleUnresolvedViolations(expr, cursor, pCtx);
        }

        int st = cursor;
        captureToNextTokenJunction();

        if (isReservedWord(name = createStringTrimmed(expr, st, cursor - st))
            || isNotValidNameorLabel(name))
          throw new CompileException("illegal prototype name or use of reserved word", expr, cursor);

        if (expr[cursor = nextNonBlank()] != '{') {
          throw new CompileException("expected '{' but found: " + expr[cursor], expr, cursor);
        }

        cursor = balancedCaptureWithLineAccounting(expr, st = cursor + 1, end, '{', pCtx);

        ProtoParser parser = new ProtoParser(expr, st, cursor, name, pCtx, fields, splitAccumulator);
        Proto proto = parser.parse();

        pCtx.addImport(proto);

        proto.setCursorPosition(st, cursor);
        cursor = parser.getCursor();

        ProtoParser.notifyForLateResolution(proto);

        return lastNode = proto;
      }
      //描述指令集节点，没有条件集
      case STACKLANG: {
        if (expr[cursor = nextNonBlank()] != '{') {
          throw new CompileException("expected '{' but found: " + expr[cursor], expr, cursor);
        }
        int st;
        cursor = balancedCaptureWithLineAccounting(expr, st = cursor + 1, end, '{', pCtx);

        Stacklang stacklang = new Stacklang(expr, st, cursor - st, fields, pCtx);
        cursor++;

        return lastNode = stacklang;

      }
      default:
        //存在条件处理,因此需要使用(来捕获相应的参数信息
        if (cond) {
          //要求条件表达式前面必须要有小括号，这样以方便处理
          if (expr[cursor] != '(') {
            throw new CompileException("expected '(' but encountered: " + expr[cursor], expr, cursor);
          }

          /**
           * This block is an: IF, FOREACH or WHILE node.
           */

          //捕获到条件组了，即使用( 来捕获到)中间的内容
          endCond = cursor = balancedCaptureWithLineAccounting(expr, startCond = cursor, end, '(', pCtx);

          startCond++;
          cursor++;
        }
    }

    skipWhitespace();

    //这里处理语句块的2种情况，即带 {的以及不带{，不带{的情况,即单条语句
    if (cursor >= end) {
      throw new CompileException("unexpected end of statement", expr, end);
    }
    //带 {,语句块
    else if (expr[cursor] == '{') {
      blockEnd = cursor = balancedCaptureWithLineAccounting(expr, blockStart = cursor, end, '{', pCtx);
    }
    //不带,半条语句
    else {
      blockStart = cursor - 1;
      captureToEOSorEOL();
      blockEnd = cursor + 1;
    }

    if (type == ASTNode.BLOCK_IF) {
      //这里根据前一个节点进行处理，以创建if else 或者是 else if节点
      IfNode ifNode = (IfNode) node;

      if (node != null) {
        if (!cond) {
          //前一个节点存在，因为没有条件表达式，即肯定 是else语句
          return ifNode.setElseBlock(expr, st = trimRight(blockStart + 1), trimLeft(blockEnd) - st, pCtx);
        }
        else {
          //前一个节点存在，有条件表达式，即肯定 是else if语句
          return ifNode.setElseIf((IfNode) createBlockToken(startCond, endCond, trimRight(blockStart + 1),
              trimLeft(blockEnd), type));
        }
      }
      else {
        //这里前一个节点没有，那么即默认的if节点
        return createBlockToken(startCond, endCond, blockStart + 1, blockEnd, type);
      }
    }
    else if (type == ASTNode.BLOCK_DO) {
      //特殊处理do循环，因为do循环后面必须带while或者until条件
      cursor++;
      skipWhitespace();
      st = cursor;
      captureToNextTokenJunction();

      if ("while".equals(name = new String(expr, st, cursor - st))) {
        skipWhitespace();
        startCond = cursor + 1;
        endCond = cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '(', pCtx);//while条件
        return createBlockToken(startCond, endCond, trimRight(blockStart + 1), trimLeft(blockEnd), type);
      }
      else if ("until".equals(name)) {
        skipWhitespace();
        startCond = cursor + 1;
        endCond = cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '(', pCtx);//until条件
        return createBlockToken(startCond, endCond, trimRight(blockStart + 1), trimLeft(blockEnd),
            ASTNode.BLOCK_DO_UNTIL);
      }
      else {
        throw new CompileException("expected 'while' or 'until' but encountered: " + name, expr, cursor);
      }
    }
    //剩下的就按照通用解析规则来处理,如for循环等
    // DON"T REMOVE THIS COMMENT!
    // else if (isFlag(ASTNode.BLOCK_FOREACH) || isFlag(ASTNode.BLOCK_WITH)) {
    else {
      return createBlockToken(startCond, endCond, trimRight(blockStart + 1), trimLeft(blockEnd), type);
    }
  }


  /**
   * 判断当前下一个节点是否是 else 节点(包括else if
   * Checking from the current cursor position, check to see if the if-then-else block continues.
   *
   * @return boolean value
   */
  protected boolean ifThenElseBlockContinues() {
    if ((cursor + 4) < end) {
      if (expr[cursor] != ';') cursor--;
      skipWhitespace();

      return expr[cursor] == 'e' && expr[cursor + 1] == 'l' && expr[cursor + 2] == 's' && expr[cursor + 3] == 'e'
          && (isWhitespace(expr[cursor + 4]) || expr[cursor + 4] == '{');
    }
    return false;
  }

  /**
   * 检查是否持续定义，如new int[2].length 这种情况，或者new int[]{2,3}[0]这种情况
   * Checking from the current cursor position, check to see if we're inside a contiguous identifier.
   *
   * @return -
   */
  protected boolean tokenContinues() {
    if (cursor == end) return false;
    else if (expr[cursor] == '.' || expr[cursor] == '[') return true;
    else if (isWhitespace(expr[cursor])) {
      int markCurrent = cursor;
      skipWhitespace();
      if (cursor != end && (expr[cursor] == '.' || expr[cursor] == '[')) return true;
      cursor = markCurrent;
    }
    return false;
  }

  /**
   * 这里表示期望当前表达式已经结束了，如果未能正常结束，则表示是不正确的处理方式。
   * 如 a++ 后面应该不会再跟其它 =表达式了
   * The parser should find a statement ending condition when this is called, otherwise everything should blow up.
   */
  protected void expectEOS() {
    skipWhitespace();
    if (cursor != end && expr[cursor] != ';') {
      switch (expr[cursor]) {
        case '&':
          if (lookAhead() == '&') return;
          else break;
        case '|':
          if (lookAhead() == '|') return;
          else break;
        case '!':
          if (lookAhead() == '=') return;
          else break;

        case '<':
        case '>':
          return;

        case '=': {
          switch (lookAhead()) {
            case '=':
            case '+':
            case '-':
            case '*':
              return;
          }
          break;
        }

        case '+':
        case '-':
        case '/':
        case '*':
          if (lookAhead() == '=') return;
          else break;
      }

      throw new CompileException("expected end of statement but encountered: "
          + (cursor == end ? "<end of stream>" : expr[cursor]), expr, cursor);
    }
  }

  /**
   * 判断接下来的字符是否是有效变量
   * 在处理中跳过连续的空白，因为空白本身也无特殊处理
   * Checks to see if the next part of the statement is an identifier part.
   *
   * @return boolean true if next part is identifier part.
   */
  protected boolean isNextIdentifier() {
    while (cursor != end && isWhitespace(expr[cursor])) cursor++;
    return cursor != end && isIdentifierPart(expr[cursor]);
  }

  /**
   * 匹配以当前表达式结束，与eot的区别在于,eos中可以存在多个节点，如 return 3 * 4，而 eot则到 *结束
   * Capture from the current cursor position, to the end of the statement.
   */
  protected void captureToEOS() {
    while (cursor != end) {
      switch (expr[cursor]) {
        case '(':
        case '[':
        case '{':
          if ((cursor = balancedCaptureWithLineAccounting(expr, cursor, end, expr[cursor], pCtx)) >= end)
            return;
          break;

        case '"':
        case '\'':
          cursor = captureStringLiteral(expr[cursor], expr, cursor, end);
          break;

        case ',':
        case ';':
        case '}':
          return;
      }
      cursor++;
    }
  }

  /**
   * 捕获一个单元逻辑执行块的处理语句
   * 与eos不同,eos忽略换行,这里的换行表示当前语句需要结束了,因此要单独处理
   * From the current cursor position, capture to the end of statement, or the end of line, whichever comes first.
   */
  protected void captureToEOSorEOL() {
    while (cursor != end && (expr[cursor] != '\n' && expr[cursor] != '\r' && expr[cursor] != ';')) {
      cursor++;
    }
  }

  /**
   * 期望接下来能够捕获一个变量信息
   * Capture to the end of the current identifier under the cursor.
   */
  protected void captureIdentifier() {
    boolean captured = false;
    if (cursor == end) throw new CompileException("unexpected end of statement: EOF", expr, cursor);
    while (cursor != end) {
      switch (expr[cursor]) {
        case ';':
          return;

        default: {
          if (!isIdentifierPart(expr[cursor])) {
            if (captured) return;
            throw new CompileException("unexpected symbol (was expecting an identifier): " + expr[cursor],
                expr, cursor);
          }
          else {
            captured = true;
          }
        }
      }
      cursor++;
    }
  }

  /**
   * 匹配到当前节点结束,这里的节点结束，即在语义中可以表示一整个调用语句的
   * 如下列的列表
   * a[xx][yyy]
   * ab(xxx)
   * ab{xxxx}
   * abc.xyz.xxx
   * 如果碰到 操作符这种，如 +-* / 这种，则中止
   * From the current cursor position, capture to the end of the current token.
   */
  protected void captureToEOT() {
    skipWhitespace();
    do {
      switch (expr[cursor]) {
        //如果是([{，则表示调用块,被认为整个语句的一部分
        case '(':
        case '[':
        case '{':
          if ((cursor = balancedCaptureWithLineAccounting(expr, cursor, end, expr[cursor], pCtx)) == -1) {
            throw new CompileException("unbalanced braces", expr, cursor);
          }
          break;

        //碰到操作符，提前返回
        case '*':
        case '/':
        case '+':
        case '%':
        case ',':
        case '=':
        case '&':
        case '|':
        case ';':
          return;

        case '.':
          skipWhitespace();
          break;

        //字符串块
        case '\'':
          cursor = captureStringLiteral('\'', expr, cursor, end);
          break;
        case '"':
          cursor = captureStringLiteral('"', expr, cursor, end);
          break;

        default:
          //默认情况下，其它字符，如果是正常的 abc，则继续认为是调用语句的一部分

          //以下专门处理空格后存在.的这种情况,即认为 ___.和普通的.都是一样的,即abc . 和 abc.一样
          if (isWhitespace(expr[cursor])) {
            skipWhitespace();

            //这里处理类似 abc   .的这种情况
            if (cursor < end && expr[cursor] == '.') {
              if (cursor != end) cursor++;
              skipWhitespace();
              break;
            }
            else {
              //没有碰到 abc . 则跳回去，只处理abc
              //即认为是语句间的分隔，后面视为不同的语句
              trimWhitespace();
              return;
            }
          }
      }
    }
    while (++cursor < end);
  }


  /** 往回跳，找到第一个非空白的字符,并且判定找到的字符是否是指定的字符 */
  protected boolean lastNonWhite(char c) {
    int i = cursor - 1;
    while (isWhitespace(expr[i])) i--;
    return c == expr[i];
  }

  /**
   * 往回走,如果是空格或者是; 继续往回走,返回可以停止的位置
   * From the specified cursor position, trim out any whitespace between the current position and the end of the
   * last non-whitespace character.
   *
   * @param pos - current position
   * @return new position.
   */
  protected int trimLeft(int pos) {
    if (pos > end) pos = end;
    while (pos > 0 && pos >= st && (isWhitespace(expr[pos - 1]) || expr[pos - 1] == ';')) pos--;
    return pos;
  }

  /**
   * 即跳过空格的意思,往右走,到第一个满足条件下标为止
   * From the specified cursor position, trim out any whitespace between the current position and beginning of the
   * first non-whitespace character.
   *
   * @param pos -
   * @return -
   */
  protected int trimRight(int pos) {
    while (pos != end && isWhitespace(expr[pos])) pos++;
    return pos;
  }

  /**
   * 跳过空白以及换行符，注释等
   * 在处理中，同时针对//以及/*这种注解，将原表达式中的信息替换掉，以避免下次再处理同样的信息
   * If the cursor is currently pointing to whitespace, move the cursor forward to the first non-whitespace
   * character, but account for carriage returns in the script (updates parser field: line).
   */
  protected void skipWhitespace() {
    Skip:
    while (cursor != end) {
      switch (expr[cursor]) {
        //忽略换行
        case '\n':
          line++;
          lastLineStart = cursor;
        case '\r':
          cursor++;
          continue;
          //忽略 // 行注释
        case '/':
          if (cursor + 1 != end) {
            switch (expr[cursor + 1]) {
              case '/':

                expr[cursor++] = ' ';
                while (cursor != end && expr[cursor] != '\n') {
                  expr[cursor++] = ' ';
                }
                if (cursor != end) {
                  cursor++;
                }

                line++;
                lastLineStart = cursor;

                continue;

              case '*':
                int len = end - 1;
                int st = cursor;
                cursor++;

                while (cursor != len && !(expr[cursor] == '*' && expr[cursor + 1] == '/')) {
                  cursor++;
                }
                if (cursor != len) {
                  cursor += 2;
                }

                for (int i = st; i < cursor; i++) {
                  expr[i] = ' ';
                }

                continue;

              default:
                break Skip;

            }
          }
        default:
          //忽略 20以下不可打印字符
          if (!isWhitespace(expr[cursor])) break Skip;

      }
      cursor++;
    }
  }

  /**
   * 找到下一个可以隔离代码块的语句,包括[xxx]以及正常的连续性操作数
   * 即查找到第一个可以进行条件式处理的下标位
   * From the current cursor position, capture to the end of the next token junction.
   */
  protected void captureToNextTokenJunction() {
    while (cursor != end) {
      switch (expr[cursor]) {
        case '{':
        case '(':
          return;
        case '/':
          if (expr[cursor + 1] == '*') return;
        case '[':
          cursor = balancedCaptureWithLineAccounting(expr, cursor, end, '[', pCtx) + 1;
          continue;
        default:
          if (isWhitespace(expr[cursor])) {
            return;
          }
          cursor++;
      }
    }
  }

  /**
   * 回退到上一个不是空格的字符
   * From the current cursor position, trim backward over any whitespace to the first non-whitespace character.
   */
  protected void trimWhitespace() {
    while (cursor != 0 && isWhitespace(expr[cursor - 1])) cursor--;
  }

  /**
   * 重新设置相应的表达式，并且在处理时去掉前后空白字符
   * Set and finesse the expression, trimming an leading or proceeding whitespace.
   *
   * @param expression the expression
   */
  protected void setExpression(String expression) {
    if (expression != null && expression.length() != 0) {
      synchronized (EX_PRECACHE) {
        if ((this.expr = EX_PRECACHE.get(expression)) == null) {
          end = length = (this.expr = expression.toCharArray()).length;

          // trim any whitespace.
          while (start < length && isWhitespace(expr[start])) start++;

          while (length != 0 && isWhitespace(this.expr[length - 1])) length--;

          char[] e = new char[length];

          for (int i = 0; i != e.length; i++)
            e[i] = expr[i];

          EX_PRECACHE.put(expression, e);
        }
        else {
          end = length = this.expr.length;
        }
      }
    }
  }

  /**
   * 重新设置相应的表达式信息
   * Set and finesse the expression, trimming an leading or proceeding whitespace.
   *
   * @param expression the expression
   */
  protected void setExpression(char[] expression) {
    end = length = (this.expr = expression).length;
    while (start < length && isWhitespace(expr[start])) start++;
    while (length != 0 && isWhitespace(this.expr[length - 1])) length--;
  }

  /**
   * 往回找，最后一个不是空白的字符
   * Return the previous non-whitespace character.
   *
   * @return -
   */
  protected char lookToLast() {
    if (cursor == start) return 0;
    int temp = cursor;
    for (; ; ) {
      if (temp == start || !isWhitespace(expr[--temp])) break;
    }
    return expr[temp];
  }

  /**
   * 读取当前下标前一个字符
   * Return the last character (delta -1 of cursor position).
   *
   * @return -
   */
  protected char lookBehind() {
    if (cursor == start) return 0;
    else return expr[cursor - 1];
  }

  /**
   * 读取当前下标下一个字符
   * Return the next character (delta 1 of cursor position).
   *
   * @return -
   */
  protected char lookAhead() {
    if (cursor + 1 != end) {
      return expr[cursor + 1];
    }
    else {
      return 0;
    }
  }

  /**
   * 返回从当前下标往前指定位移处的字符，如果超出限度，则返回0,主要用于在某些场景中提前探测一些操作数据
   * 如a+= 这种,或者是 >>> 这种
   * Return the character, forward of the currrent cursor position based on the specified range delta.
   *
   * @param range -
   * @return -
   */
  protected char lookAhead(int range) {
    if ((cursor + range) >= end) return 0;
    else {
      return expr[cursor + range];
    }
  }

  /**
   * 判断接下来碰到的字符肯定是变量名或者是一个字，字符串
   * 即认为在二元操作的右边或者是赋值后应该是一个有效的数据，而不是继续为 操作符
   * Returns true if the next is an identifier or literal.
   *
   * @return true of false
   */
  protected boolean isNextIdentifierOrLiteral() {
    int tmp = cursor;
    if (tmp == end) return false;
    else {
      while (tmp != end && isWhitespace(expr[tmp])) tmp++;
      if (tmp == end) return false;
      char n = expr[tmp];
      return isIdentifierPart(n) || isDigit(n) || n == '\'' || n == '"';
    }
  }

  /**
   * 移动当前下标，并且返回下一个有效下标处(下标仅+1,并不继续移动)
   * Increment one cursor position, and move cursor to next non-blank part.
   *
   * @return cursor position
   */
  public int incNextNonBlank() {
    cursor++;
    return nextNonBlank();
  }

  /**
   * 下标移到下一个非空的字符处
   * Move to next cursor position from current cursor position.
   *
   * @return cursor position
   */
  public int nextNonBlank() {
    if ((cursor + 1) >= end) {
      throw new CompileException("unexpected end of statement", expr, st);
    }
    int i = cursor;
    while (i != end && isWhitespace(expr[i])) i++;
    return i;
  }

  /**
   * 期望下一个操作符,如果不是期望的操作符,则报相应的异常处理
   * Expect the next specified character or fail
   *
   * @param c character
   */
  public void expectNextChar_IW(char c) {
    nextNonBlank();
    if (cursor == end) throw new CompileException("unexpected end of statement", expr, st);
    if (expr[cursor] != c)
      throw new CompileException("unexpected character ('" + expr[cursor] + "'); was expecting: " + c, expr, st);
  }

  /**
   * NOTE: This method assumes that the current position of the cursor is at the end of a logical statement, to
   * begin with.
   * <p/>
   * Determines whether or not the logical statement is manually terminated with a statement separator (';').
   * 判断一个语句是否是非正常中断的，比如 c = a + b，后面并没有一个有效的分号,但仍认为是需要中断的
   *
   * @return -
   */
  protected boolean isStatementNotManuallyTerminated() {
    if (cursor >= end) return false;
    int c = cursor;
    while (c != end && isWhitespace(expr[c])) c++;
    return !(c != end && expr[c] == ';');
  }

  protected static final int SET = 0;
  protected static final int REMOVE = 1;
  protected static final int GET = 2;
  protected static final int GET_OR_CREATE = 3;

  /** 在当前解析上下文中添加相应的错误信息，错误下标从起始点算起 */
  protected void addFatalError(String message) {
    pCtx.addError(new ErrorDetail(expr, st, true, message));
  }

  /** 在当前解析上下文添加严重错误，错误信息从指定下标开始 */
  protected void addFatalError(String message, int start) {
    pCtx.addError(new ErrorDetail(expr, start, true, message));
  }

  public static final int LEVEL_5_CONTROL_FLOW = 5;
  public static final int LEVEL_4_ASSIGNMENT = 4;
  public static final int LEVEL_3_ITERATION = 3;
  public static final int LEVEL_2_MULTI_STATEMENT = 2;
  public static final int LEVEL_1_BASIC_LANG = 1;
  public static final int LEVEL_0_PROPERTY_ONLY = 0;

  public static void setLanguageLevel(int level) {
    OPERATORS.clear();
    OPERATORS.putAll(loadLanguageFeaturesByLevel(level));
  }

  /** 加载操作符常量表 */
  public static HashMap<String, Integer> loadLanguageFeaturesByLevel(int languageLevel) {
    HashMap<String, Integer> operatorsTable = new HashMap<String, Integer>();
    switch (languageLevel) {
      case 6:  // prototype definition
        operatorsTable.put("proto", PROTO);

      case 5:  // control flow operations
        operatorsTable.put("if", IF);
        operatorsTable.put("else", ELSE);
        operatorsTable.put("?", TERNARY);
        operatorsTable.put("switch", SWITCH);
        operatorsTable.put("function", FUNCTION);
        operatorsTable.put("def", FUNCTION);
        operatorsTable.put("stacklang", STACKLANG);


      case 4: // assignment
        operatorsTable.put("=", ASSIGN);
        operatorsTable.put("var", UNTYPED_VAR);
        operatorsTable.put("+=", ASSIGN_ADD);
        operatorsTable.put("-=", ASSIGN_SUB);
        operatorsTable.put("/=", ASSIGN_DIV);
        operatorsTable.put("%=", ASSIGN_MOD);

      case 3: // iteration
        operatorsTable.put("foreach", FOREACH);
        operatorsTable.put("while", WHILE);
        operatorsTable.put("until", UNTIL);
        operatorsTable.put("for", FOR);
        operatorsTable.put("do", DO);

      case 2: // multi-statement
        operatorsTable.put("return", RETURN);
        operatorsTable.put(";", END_OF_STMT);

      case 1: // boolean, math ops, projection, assertion, objection creation, block setters, imports
        operatorsTable.put("+", ADD);
        operatorsTable.put("-", SUB);
        operatorsTable.put("*", MULT);
        operatorsTable.put("**", POWER);
        operatorsTable.put("/", DIV);
        operatorsTable.put("%", MOD);
        operatorsTable.put("==", EQUAL);
        operatorsTable.put("!=", NEQUAL);
        operatorsTable.put(">", GTHAN);
        operatorsTable.put(">=", GETHAN);
        operatorsTable.put("<", LTHAN);
        operatorsTable.put("<=", LETHAN);
        operatorsTable.put("&&", AND);
        operatorsTable.put("and", AND);
        operatorsTable.put("||", OR);
        operatorsTable.put("or", CHOR);
        operatorsTable.put("~=", REGEX);
        operatorsTable.put("instanceof", INSTANCEOF);
        operatorsTable.put("is", INSTANCEOF);
        operatorsTable.put("contains", CONTAINS);
        operatorsTable.put("soundslike", SOUNDEX);
        operatorsTable.put("strsim", SIMILARITY);
        operatorsTable.put("convertable_to", CONVERTABLE_TO);
        operatorsTable.put("isdef", ISDEF);

        operatorsTable.put("#", STR_APPEND);

        operatorsTable.put("&", BW_AND);
        operatorsTable.put("|", BW_OR);
        operatorsTable.put("^", BW_XOR);
        operatorsTable.put("<<", BW_SHIFT_LEFT);
        operatorsTable.put("<<<", BW_USHIFT_LEFT);
        operatorsTable.put(">>", BW_SHIFT_RIGHT);
        operatorsTable.put(">>>", BW_USHIFT_RIGHT);

        operatorsTable.put("new", Operator.NEW);
        operatorsTable.put("in", PROJECTION);

        operatorsTable.put("with", WITH);

        operatorsTable.put("assert", ASSERT);
        operatorsTable.put("import", IMPORT);
        operatorsTable.put("import_static", IMPORT_STATIC);

        operatorsTable.put("++", INC);
        operatorsTable.put("--", DEC);

      case 0: // Property access and inline collections
        operatorsTable.put(":", TERNARY_ELSE);
    }
    return operatorsTable;
  }

  /** 是否是四则运算符 */
  protected static boolean isArithmeticOperator(int operator) {
    return operator != -1 && operator < 6;
  }

  /**
   * 在当前栈上进行操作数递减操作,即针对常量数据进行处理
   * 相应的操作也会继续判定后续的节点,以保证操作数优先级的正确性
   * 在当前处理时,相应的栈中已经有相应的操作数以及当前操作符了,则接下来的操作就是进一步判定可能的优先顺序,以进行处理
   * 最终返回此操作的处理结果
   * Reduce the current operations on the stack.
   *
   * @param operator the operator
   *                 表示当前栈里面最上面的操作符是什么
   * @return a stack control code
   * 返回值 -2,表示碰到了非常量节点
   * 返回值 -1,表示相应的操作应该提前结束
   */
  protected int arithmeticFunctionReduction(int operator) {
    ASTNode tk;
    //用于表示在处理过程中后面的操作符，而operator会在过程中表示为当前操作符,2个操作符用于实现比如 a + b * c 中的2个操作符
    //因为优先级之间的操作仅会涉及到2个操作符，因此不需要更多的变量
    //因为只有 加减  和 乘除 两类操作
    int operator2;

    /**
     * 下一个节点仍是运算符,还可能继续处理
     * If the next token is an operator, we check to see if it has a higher
     * precdence.
     * 这里主要处理优化级问题，以保证高优先级的数据先处理
     */
    if ((tk = nextToken()) != null) {
      //这里栈里的数为 a b +(a + b)，如果碰到 优先级更高的操作符，如 a + b - c，则需要切换为 a + b c *的这种，以支持处理后缀表达式
      //在整个处理中，让stack存储低优先级操作，dstack保留高优先级操作,直到碰到低优先级或同优先级的操作为止,这样可以保证优先级不会出错
      //并且dstack中的优先级都是比stack中高的，因此即使将dstack数据回到stack，也是可以保证这部分运算顺序先执行
      //同时在处理高优先级时，会根据后缀操作处理过程调整相应的栈内数据顺序
      //这里首先判断是否是四则运算，因为四则运算的优先级较高，如果仍比四则运算优先级高的，就直接单独处理了
      if (isArithmeticOperator(operator2 = tk.getOperator()) && PTABLE[operator2] > PTABLE[operator]) {
        //这里将操作符和操作数交换，以将之前表达式后面的操作数提及后面处理
        stk.xswap();
        /**
         * The current arith. operator is of higher precedence the last.
         */

        tk = nextToken();

        /**
         * Check to see if we're compiling or executing interpretively.  If we're compiling, we really
         * need to stop if this is not a literal.
         */
        //如果后面的操作数不是常量，则表示当前不可继续执行操作，因此暂存相应的数据，终止处理
        if (compileMode && !tk.isLiteral()) {


          //这里将操作符放在栈前面，那么在相应的执行表达式expressionCompiler中，就会重新放入前面来
          splitAccumulator.push(tk, new OperatorNode(operator2, expr, st, pCtx));
          return OP_OVERFLOW;
        }

        //因为这里优先级比前面高，加到辅助栈中
        //同时重置当前操作符为后面的这个操作符
        dStack.push(operator = operator2, tk.getReducedValue(ctx, ctx, variableFactory));

        //对后面的连续调用进行处理，直到不再是算术处理
        while (true) {
          // look ahead again
          //这里后面的优先级比当前更高
          if ((tk = nextToken()) != null && (operator2 = tk.getOperator()) != -1
              && operator2 != END_OF_STMT && PTABLE[operator2] > PTABLE[operator]) {
            // if we have back to back operations on the stack, we don't xswap

            //新操作数优先级更高，继续将旧的数据放到栈中
            //这时的顺序为中缀顺序，即放到主栈中的顺序为 a + b，这里的中缀在后续主栈进行deduce时再转换为后缀处理
            if (dStack.isReduceable()) {
              stk.copyx2(dStack);
            }

            /**
             * This operator is of higher precedence, or the same level precedence.  push to the RHS.
             */
            //将新优先级的放入辅助栈，继续支持同样的流程
            dStack.push(operator = operator2, nextToken().getReducedValue(ctx, ctx, variableFactory));

            continue;
          }
          else if (tk != null && operator2 != -1 && operator2 != END_OF_STMT) {
            //优先级相同
            if (PTABLE[operator2] == PTABLE[operator]) {
              //辅助栈中有数据,因为同优先级，因此将相应的操作数和符入到主栈，并且进行计算。减少相应的辅助栈数据
              if (!dStack.isEmpty()) dreduce();
              else {
                //辅助栈没数据,并且这里明确表示主栈中的优先级和当前应该是一致的，因此直接全量性地对主栈进行操作
                //因为这里的dstack为空的情况仅有可能是进入了 下一个else 的情况，仅碰到了低优先级数据，导致会对栈中高优先级的操作进行处理
                //这里采用xswap_op是因为在之前相应的操作符和操作数已经被调整顺序了，而不是正常的后缀形式
                while (stk.isReduceable()) {
                  stk.xswap_op();
                }
              }

              /**
               * This operator is of the same level precedence.  push to the RHS.
               */

              //因为是同优先级，因此直接放到辅助栈中,可以认为与优先级大于左边相同
              dStack.push(operator = operator2, nextToken().getReducedValue(ctx, ctx, variableFactory));

              continue;
            }
            else {
              /**
               * The operator doesn't have higher precedence. Therfore reduce the LHS.
               */
              //当前优先级更低，因此将辅助栈入主栈，并进行计算
              while (dStack.size() > 1) {
                dreduce();
              }

              //接下来的操作，以将主栈中所有比当前优先级高的运算都进行计算，包括优先级高的或者是同优先级
              operator = tk.getOperator();
              // Reduce the lesser or equal precedence operations.
              while (stk.size() != 1 && stk.peek2() instanceof Integer &&
                  ((operator2 = (Integer) stk.peek2()) < PTABLE.length) &&
                  PTABLE[operator2] >= PTABLE[operator]) {
                stk.xswap_op();
              }
            }
          }
          else {
            /**
             * There are no more tokens.
             */

            //进入到这里，就表示没有更多的操作符，可能为end，也可能为return
            //因为如果辅助栈有数据，则copy到主栈，并计算值
            if (dStack.size() > 1) {
              dreduce();
            }

            //这里因为主栈作了处理，如果主栈有更多的数据，那么一定是 a + b这种方式，因此这里将操作符提至后面
            if (stk.isReduceable()) stk.xswap();

            break;
          }

          //进入到这里，表示碰到了操作符节点，但是是低优先级的，因此需要判断是否还需要继续处理
          //因为and or会对相应的运算逻辑产生影响，因此单独进行处理,让调用者进行判断是否继续处理
          if ((tk = nextToken()) != null) {
            switch (operator) {
              case AND: {
                //and，第1个返回false，则不可再往下执行
                if (!(stk.peekBoolean())) return OP_TERMINATE;
                //这里表示还需要继续执行第2个and语句
                else {
                  splitAccumulator.add(tk);
                  return AND;
                }
              }
              //or 第1个返回true,不需要再继续执行，因此提前中断处理
              case OR: {
                if ((stk.peekBoolean())) return OP_TERMINATE;
                else {
                  splitAccumulator.add(tk);
                  return OR;
                }
              }

              //低优先级,直接按中缀加入到主栈中
              default:
                stk.push(operator, tk.getReducedValue(ctx, ctx, variableFactory));
            }
          }
        }
      }
      //这里强制要求相应的操作节点为操作符节点，也可以是如return，;这种节点，其操作符为 -1
      else if (!tk.isOperator()) {
        throw new CompileException("unexpected token: " + tk.getName(), expr, st);
      }
      //表示后面的操作符优先级更低，因此不需要再处理了，这里对当前主数据栈进行递减操作，以表示可以对前面的数据进行处理了
      else {
        //因为这里类似于主栈的顺序没有被修改过，因此直接进行reduce
        reduce();
        splitAccumulator.push(tk);
      }
    }

    // while any values remain on the stack
    // keep XSWAPing and reducing, until there is nothing left.
    //如果栈中有更多操作数和操作符，则进行相应的递减操作
    if (stk.isReduceable()) {
      while (true) {
        reduce();
        //这里因为之前的栈中的顺序为中缀顺序，这里需要转换为后缀处理
        if (stk.isReduceable()) {
          stk.xswap();
        }
        else {
          break;
        }
      }
    }

    return OP_RESET_FRAME;
  }

  /**
   * 将辅助栈中的数据放到数据栈中，并对数据栈进行操作处理
   * 这里的辅助栈中的数据为 + b，即操作数在前，那么放到stk栈时，就会放到最上面,即将操作符放到最上面，这样以方便主栈进行运算操作
   * 这里的处理可以认为当前辅助栈中的优先级都很高，因此要先进行计算，不再需要与后面的操作数再进行判断了
   */
  private void dreduce() {
    stk.copy2(dStack);
    stk.op();
  }

  /**
   * This method is called when we reach the point where we must subEval a trinary operation in the expression.
   * (ie. val1 op val2).  This is not the same as a binary operation, although binary operations would appear
   * to have 3 structures as well.  A binary structure (or also a junction in the expression) compares the
   * current state against 2 downrange structures (usually an op and a val).
   * 对相应的操作栈进行递减操作，即根据最上面的操作符处理操作数，然后将结果重新入栈
   * 在处理时，因为相应的操作符已经提前pop出来，因此相应的操作将操作符作为参数传递
   * 在整个处理过程中，栈中的数据都是常量 ，因此可以直接进行处理。因为这里数据都是在处理过程中加到栈中的
   */
  protected void reduce() {
    Object v1, v2;
    int operator;
    try {
      switch (operator = (Integer) stk.pop()) {
        //二元操作
        case ADD:
        case SUB:
        case DIV:
        case MULT:
        case MOD:
        case EQUAL:
        case NEQUAL:
        case GTHAN:
        case LTHAN:
        case GETHAN:
        case LETHAN:
        case POWER:
          stk.op(operator);
          break;

        //and处理 因为顺序为 a b && 因此要先算a,再算b
        case AND:
          v1 = stk.pop();
          stk.push(((Boolean) stk.pop()) && ((Boolean) v1));
          break;

        //or处理,因为顺序为 a b && 因此要先算a,再算b
        case OR:
          v1 = stk.pop();
          stk.push(((Boolean) stk.pop()) || ((Boolean) v1));
          break;

        //or 处理
        case CHOR:
          v1 = stk.pop();
          if (!isEmpty(v2 = stk.pop()) || !isEmpty(v1)) {
            stk.clear();
            stk.push(!isEmpty(v2) ? v2 : v1);
            return;
          }
          //2个数据都是空的，则直接加一个null到栈中
          else stk.push(null);
          break;

        //正则处理
        case REGEX:
          stk.push(java.util.regex.Pattern.compile(java.lang.String.valueOf(stk.pop()))
              .matcher(java.lang.String.valueOf(stk.pop())).matches());
          break;

        //instance of
        case INSTANCEOF:
          stk.push(((Class) stk.pop()).isInstance(stk.pop()));
          break;

        //转换操作
        case CONVERTABLE_TO:
          stk.push(org.mvel2.DataConversion.canConvert(stk.peek2().getClass(), (Class) stk.pop2()));
          break;

        //contains 处理,处理逻辑与 pop 相似，这里的peek2 和 pop2 能达到两次pop相同的结果
        case CONTAINS:
          stk.push(containsCheck(stk.peek2(), stk.pop2()));
          break;

        case SOUNDEX:
          stk.push(soundex(java.lang.String.valueOf(stk.pop()))
              .equals(soundex(java.lang.String.valueOf(stk.pop()))));
          break;

        case SIMILARITY:
          stk.push(similarity(java.lang.String.valueOf(stk.pop()), java.lang.String.valueOf(stk.pop())));
          break;

        //默认情况下，认为为位移操作，因此进行位移相应处理
        default:
          reduceNumeric(operator);
      }
    }
    catch (ClassCastException e) {
      throw new CompileException("syntax error or incompatable types", expr, st, e);
    }
    catch (ArithmeticException e) {
      throw new CompileException("arithmetic error: " + e.getMessage(), expr, st, e);
    }
    catch (Exception e) {
      throw new CompileException("failed to subEval expression", expr, st, e);
    }
  }

  /** 使用相应的操作符,针对不同的数据类型进行不同的操作 */
  private void reduceNumeric(int operator) {
    Object op1 = stk.peek2();
    Object op2 = stk.pop2();
    if (op1 instanceof Integer) {
      if (op2 instanceof Integer) {
        reduce((Integer) op1, operator, (Integer) op2);
      }
      else {
        reduce((Integer) op1, operator, (Long) op2);
      }
    }
    else {
      if (op2 instanceof Integer) {
        reduce((Long) op1, operator, (Integer) op2);
      }
      else {
        reduce((Long) op1, operator, (Long) op2);
      }
    }
  }

  /** 对2个int数据进行操作 这里为位移操作 */
  private void reduce(int op1, int operator, int op2) {
    switch (operator) {
      case BW_AND:
        stk.push(op1 & op2);
        break;

      case BW_OR:
        stk.push(op1 | op2);
        break;

      case BW_XOR:
        stk.push(op1 ^ op2);
        break;

      case BW_SHIFT_LEFT:
        stk.push(op1 << op2);
        break;

      case BW_USHIFT_LEFT:
        int iv2 = op1;
        if (iv2 < 0) iv2 *= -1;
        stk.push(iv2 << op2);
        break;

      case BW_SHIFT_RIGHT:
        stk.push(op1 >> op2);
        break;

      case BW_USHIFT_RIGHT:
        stk.push(op1 >>> op2);
        break;
    }
  }

  /** 对1个int和1个long类型数据进行操作 这里为位移操作 */
  private void reduce(int op1, int operator, long op2) {
    switch (operator) {
      case BW_AND:
        stk.push(op1 & op2);
        break;

      case BW_OR:
        stk.push(op1 | op2);
        break;

      case BW_XOR:
        stk.push(op1 ^ op2);
        break;

      case BW_SHIFT_LEFT:
        stk.push(op1 << op2);
        break;

      case BW_USHIFT_LEFT:
        int iv2 = op1;
        if (iv2 < 0) iv2 *= -1;
        stk.push(iv2 << op2);
        break;

      case BW_SHIFT_RIGHT:
        stk.push(op1 >> op2);
        break;

      case BW_USHIFT_RIGHT:
        stk.push(op1 >>> op2);
        break;
    }
  }

  /** 对1个long和1个int类型数据进行操作 这里为位移操作 */
  private void reduce(long op1, int operator, int op2) {
    switch (operator) {
      case BW_AND:
        stk.push(op1 & op2);
        break;

      case BW_OR:
        stk.push(op1 | op2);
        break;

      case BW_XOR:
        stk.push(op1 ^ op2);
        break;

      case BW_SHIFT_LEFT:
        stk.push(op1 << op2);
        break;

      case BW_USHIFT_LEFT:
        long iv2 = op1;
        if (iv2 < 0) iv2 *= -1;
        stk.push(iv2 << op2);
        break;

      case BW_SHIFT_RIGHT:
        stk.push(op1 >> op2);
        break;

      case BW_USHIFT_RIGHT:
        stk.push(op1 >>> op2);
        break;
    }
  }

  /** 对2个long类型数据进行操作 这里为位移操作 */
  private void reduce(long op1, int operator, long op2) {
    switch (operator) {
      case BW_AND:
        stk.push(op1 & op2);
        break;

      case BW_OR:
        stk.push(op1 | op2);
        break;

      case BW_XOR:
        stk.push(op1 ^ op2);
        break;

      case BW_SHIFT_LEFT:
        stk.push(op1 << op2);
        break;

      case BW_USHIFT_LEFT:
        long iv2 = op1;
        if (iv2 < 0) iv2 *= -1;
        stk.push(iv2 << op2);
        break;

      case BW_SHIFT_RIGHT:
        stk.push(op1 >> op2);
        break;

      case BW_USHIFT_RIGHT:
        stk.push(op1 >>> op2);
        break;
    }
  }

  /** 获取当前已经编译到的下标位置 */
  public int getCursor() {
    return cursor;
  }

  public char[] getExpression() {
    return expr;
  }

  @Deprecated
  private static int asInt(final Object o) {
    return (Integer) o;
  }
}
