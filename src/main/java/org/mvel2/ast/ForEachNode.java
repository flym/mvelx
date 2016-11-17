/**
 * MVEL 2.0
 * Copyright (C) 2007 The Codehaus
 * Mike Brock, Dhanji Prasanna, John Graham, Mark Proctor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.DataConversion;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.DefaultLocalVariableResolverFactory;
import org.mvel2.integration.impl.ItemResolverFactory;
import org.mvel2.util.ParseTools;

import java.lang.reflect.Array;

import static org.mvel2.util.ParseTools.*;

/**
 * 描述在java5中for(i:col)这种表达式
 * @author Christopher Brock
 */
public class ForEachNode extends BlockNode {
  /** 描述具体这个对象的字符串形式，即for(abc:efg)中的abc */
  protected String item;
  /** 对象的类型 */
  protected Class itemType;

  /** :后面的表达式节点 */
  protected ExecutableStatement condition;

  /** 集合迭代 */
  private static final int ITERABLE = 0;
  /** 数组迭代 */
  private static final int ARRAY = 1;
  /** 字符串迭代 */
  private static final int CHARSEQUENCE = 2;
  /** 数字处理，表示从 A..B的数字处理,即for(x : 3) x的值分别为 1..3,即1 2 3 */
  private static final int INTEGER = 3;

  /** 当前循环对象的类型 4选1(上面的) */
  private int type = -1;

  public ForEachNode(char[] expr, int start, int offset, int blockStart, int blockOffset, int fields, ParserContext pCtx) {
    super(pCtx);

    handleCond(this.expr = expr, this.start = start, this.offset = offset, this.fields = fields, pCtx);
    this.blockStart = blockStart;
    this.blockOffset = blockOffset;

    //编译期进行编译
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      //为相应的变量设置相应的类型,以保证后续能正常访问到
      if (pCtx.isStrictTypeEnforcement() && itemType != null) {
        pCtx = pCtx.createSubcontext();
        pCtx.addInput(item, itemType);
      }

      //为循环体创建新上下文
      pCtx.pushVariableScope();
      //当前变量在子上下文中可见,即在循环体中可使用到此变量
      pCtx.makeVisible(item);

      this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockOffset, pCtx);

      //编译完,结束临时上下文
      pCtx.popVariableScope();
    }
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //这里面有单独的一个变量,因此创建一个优先级最高的单独解析器并通过封装此解析器来完成后面执行块的处理
    ItemResolverFactory.ItemResolver itemR = new ItemResolverFactory.ItemResolver(item);
    //因为这里要执行方法体,因此需要一个新的作用域,因此在原factory的基础上创建新的解析器作用域
    ItemResolverFactory itemFactory = new ItemResolverFactory(itemR, new DefaultLocalVariableResolverFactory(factory));

    //一次性获取到相应的循环的值(不会多次求值)
    Object iterCond = condition.getValue(ctx, thisValue, factory);

    //如果变量类型还没有确定,则这里根据相应的值类型再进行判定
    if (type == -1) {
      determineIterType(iterCond.getClass());
    }

    Object v;
    switch (type) {
      //数组的执行方式,因为不能判定数组是原始还是包装,因此采用反射+3段式循环来做.即不能直接使用(Object[])来强制转型
      case ARRAY:
        int len = Array.getLength(iterCond);
        for (int i = 0; i < len; i++) {
          itemR.setValue(Array.get(iterCond, i));
          v = compiledBlock.getValue(ctx, thisValue, itemFactory);
          //提前返回
          if (itemFactory.tiltFlag()) return v;
        }
        break;
      //字符串迭代
      case CHARSEQUENCE:
        for (Object o : iterCond.toString().toCharArray()) {
          itemR.setValue(o);
          v = compiledBlock.getValue(ctx, thisValue, itemFactory);
          //提前返回
          if (itemFactory.tiltFlag()) return v;
        }
        break;
      //整数递增式迭代处理
      case INTEGER:
        int max = (Integer) iterCond + 1;
        for (int i = 1; i != max; i++) {
          itemR.setValue(i);
          v = compiledBlock.getValue(ctx, thisValue, itemFactory);
          //提前返回
          if (itemFactory.tiltFlag()) return v;
        }
        break;

      //可迭代对象,采用java 1.5式iterate处理
      case ITERABLE:
        for (Object o : (Iterable) iterCond) {
          itemR.setValue(o);
          v = compiledBlock.getValue(ctx, thisValue, itemFactory);
          //提前返回
          if (itemFactory.tiltFlag()) return v;
        }

        break;
    }

    return null;
  }

  /** 执行逻辑与编译相同,但执行过程采用解释运行 */
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    ItemResolverFactory.ItemResolver itemR = new ItemResolverFactory.ItemResolver(item);
    ItemResolverFactory itemFactory = new ItemResolverFactory(itemR, new DefaultLocalVariableResolverFactory(factory));

    Object iterCond = MVEL.eval(expr, start, offset, thisValue, factory);

    if (itemType != null && itemType.isArray())
      enforceTypeSafety(itemType, getBaseComponentType(iterCond.getClass()));

    this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockOffset, pCtx);

    Object v;
    if (iterCond instanceof Iterable) {
      for (Object o : (Iterable) iterCond) {
        itemR.setValue(o);
        v = compiledBlock.getValue(ctx, thisValue, itemFactory);
        if (itemFactory.tiltFlag()) return v;
      }
    }
    else if (iterCond != null && iterCond.getClass().isArray()) {
      int len = Array.getLength(iterCond);
      for (int i = 0; i < len; i++) {
        itemR.setValue(Array.get(iterCond, i));
        v = compiledBlock.getValue(ctx, thisValue, itemFactory);
        if (itemFactory.tiltFlag()) return v;
      }
    }
    else if (iterCond instanceof CharSequence) {
      for (Object o : iterCond.toString().toCharArray()) {
        itemR.setValue(o);
        v = compiledBlock.getValue(ctx, thisValue, itemFactory);
        if (itemFactory.tiltFlag()) return v;
      }
    }
    else if (iterCond instanceof Integer) {
      int max = (Integer) iterCond + 1;
      for (int i = 1; i != max; i++) {
        itemR.setValue(i);
        v = compiledBlock.getValue(ctx, thisValue, itemFactory);
        if (itemFactory.tiltFlag()) return v;
      }
    }
    else {
      throw new CompileException("non-iterable type: "
          + (iterCond != null ? iterCond.getClass().getName() : "null"), expr, start);
    }

    return null;
  }

  /** 解析整个声明体 */
  private void handleCond(char[] condition, int start, int offset, int fields, ParserContext pCtx) {
    int cursor = start;
    int end = start + offset;
    //查找 :
    while (cursor < end && condition[cursor] != ':') cursor++;

    //没找到,即肯定是有错的
    if (cursor == end || condition[cursor] != ':')
      throw new CompileException("expected : in foreach", condition, cursor);

    //这里判断这个for(a:b)以及for(String a:b)的情况
    //在后面一种中，因为:前面包括2块内容，类型声明以及具体的变量，因此需要分别处理
    int x;
    //这里item最先取值为一个值,即前端部分,但如果里面有空格,则表示此值本身就是有类型声明.则拆分成类型和变量名两部分
    //如果没有空格,则表示本身即变量名
    if ((x = (item = createStringTrimmed(condition, start, cursor - start)).indexOf(' ')) != -1) {
      //这里认为前段部分为类型声明,则进行类型解析
      String tk = new String(condition, start, x).trim();
      try {
        itemType = ParseTools.findClass(null, tk, pCtx);
        //重新设置变量名
        item = new String(condition, start + x, (cursor - start) - x).trim();

      }
      catch (ClassNotFoundException e) {
        throw new CompileException("cannot resolve identifier: " + tk, condition, start);
      }
    }

    // this.start = ++cursor;

    this.start = cursor + 1;
    this.offset = offset - (cursor - start) - 1;

    //编译期,对后面部分进行编译
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      Class egress = (this.condition = (ExecutableStatement) subCompileExpression(expr, this.start, this.offset, pCtx)).getKnownEgressType();

      //这里如果后面的声明类型为数组,则要求这里前面变量的类型必须与数组的数据类型相匹配(至少是可以转换的)
      if (itemType != null && egress.isArray()) {
        //这里对数组保证类型正确
        enforceTypeSafety(itemType, getBaseComponentType(this.condition.getKnownEgressType()));
      }
      else if (pCtx.isStrongTyping()) {
        //对相应的前面变量类型进行限定,以及对如何解析语句进行判定
        determineIterType(egress);
      }
    }
  }

  /** 判定相应的循环类型 */
  private void determineIterType(Class t) {
    if (Iterable.class.isAssignableFrom(t)) {
      type = ITERABLE;
    }
    else if (t.isArray()) {
      type = ARRAY;
    }
    else if (CharSequence.class.isAssignableFrom(t)) {
      type = CHARSEQUENCE;
    }
    else if (Integer.class.isAssignableFrom(t)) {
      type = INTEGER;
    }
    else {
      throw new CompileException("non-iterable type: " + t.getName(), expr, start);
    }
  }

  /** 保证类型安全 */
  private void enforceTypeSafety(Class required, Class actual) {
    if (!required.isAssignableFrom(actual) && !DataConversion.canConvert(actual, required)) {
      throw new CompileException("type mismatch in foreach: expected: "
          + required.getName() + "; but found: " + getBaseComponentType(actual), expr, start);
    }
  }
}
