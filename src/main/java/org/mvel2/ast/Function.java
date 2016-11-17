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
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.compiler.ExpressionCompiler;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.DefaultLocalVariableResolverFactory;
import org.mvel2.integration.impl.FunctionVariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.mvel2.integration.impl.StackDemarcResolverFactory;

import java.util.Map;

import static org.mvel2.util.ParseTools.parseParameterDefList;
import static org.mvel2.util.ParseTools.subCompileExpression;


/** 描述函数定义的节点,在执行时,即创建一个 */
@SuppressWarnings({"unchecked"})
public class Function extends ASTNode implements Safe {
  /** 函数名 */
  protected String name;
  /** 相应的执行代码块(即函数内部实现代码) */
  protected ExecutableStatement compiledBlock;

  /** 参数定义信息 */
  protected String[] parameters;
  /** 参数个数 */
  protected int parmNum;
  /** 是否处于编译模式中 */
  protected boolean compiledMode = false;
  /** 是否是单列的，可以理解为是否是静态方法 */
  protected boolean singleton;

  /**
   * 构建出函数构建对象
   *
   * @param start       条件起始位置
   * @param offset      条件长度位
   * @param blockStart  执行语句起始位
   * @param blockOffset 执行语句长度位
   */
  public Function(String name,
                  char[] expr,
                  int start,
                  int offset,
                  int blockStart,
                  int blockOffset,
                  int fields,
                  ParserContext pCtx) {

    super(pCtx);
    if ((this.name = name) == null || name.length() == 0) {
      this.name = null;
    }
    this.expr = expr;

    //解析参数
    parmNum = (this.parameters = parseParameterDefList(expr, start, offset)).length;

    //pCtx.declareFunction(this);

    //解析函数执行体，因此重新建立一个解析上下文
    ParserContext ctx = new ParserContext(pCtx.getParserConfiguration(), pCtx, true);

    //单独声明函数,即声明为全局静态,否则认为是在一个函数体内再声明函数,那么就不是一个全局的,可以理解为就是一个普通的闭包函数
    if (!pCtx.isFunctionContext()) {
      singleton = true;
      pCtx.declareFunction(this);
    }
    else {
      ctx.declareFunction(this);
    }

    /**
     * 显示的加入变量当中,以避免当外面有一个同名的参数时存在参数覆盖的问题
     * To prevent the function parameters from being counted as
     * external inputs, we must add them explicitly here.
     */
    for (String s : this.parameters) {
      ctx.addVariable(s, Object.class);
      ctx.addIndexedInput(s);
    }

    /**
     * 检测代码块的表达式是否合法,即进行验证式编译
     * Compile the expression so we can determine the input-output delta.
     */
    ctx.setIndexAllocation(false);
    ExpressionCompiler compiler = new ExpressionCompiler(expr, blockStart, blockOffset, ctx);
    compiler.setVerifyOnly(true);
    compiler.compile();

    ctx.setIndexAllocation(true);

    /**
     * 这里认为在父上下文中的变量信息均已经存在,因此当前上下文中认为这里都是外部已输入的
     * Add globals as inputs
     */
    if (pCtx.getVariables() != null) {
      for (Map.Entry<String, Class> e : pCtx.getVariables().entrySet()) {
        ctx.getVariables().remove(e.getKey());
        ctx.addInput(e.getKey(), e.getValue());
      }

      ctx.processTables();
    }

    //定义顺序入参
    ctx.addIndexedInputs(ctx.getVariables().keySet());
    ctx.getVariables().clear();

    //编译相应的执行块
    this.compiledBlock = (ExecutableStatement) subCompileExpression(expr, blockStart, blockOffset, ctx);

    this.parameters = new String[ctx.getIndexedInputs().size()];

    int i = 0;
    for (String s : ctx.getIndexedInputs()) {
      this.parameters[i++] = s;
    }

    compiledMode = (fields & COMPILE_IMMEDIATE) != 0;

    this.egressType = this.compiledBlock.getKnownEgressType();

    //最后把自己也添加进变量，以支持递归
    pCtx.addVariable(name, Function.class);
  }

  /** 函数定义执行，即产生一个函数实体对象 */
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //执行即创建起整个函数,如果有name就加入到变量作用域中,同时返回其处理,以方便在后续处理
    PrototypalFunctionInstance instance = new PrototypalFunctionInstance(this, new MapVariableResolverFactory());
    if (name != null) {
      if (!factory.isIndexedFactory() && factory.isResolveable(name))
        throw new CompileException("duplicate function: " + name, expr, start);

      factory.createVariable(name, instance);
    }
    return instance;
  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    PrototypalFunctionInstance instance = new PrototypalFunctionInstance(this, new MapVariableResolverFactory());
    if (name != null) {
      if (!factory.isIndexedFactory() && factory.isResolveable(name))
        throw new CompileException("duplicate function: " + name, expr, start);
      factory.createVariable(name, instance);
    }
    return instance;
  }

  /** 执行真正的调用过程,即在已经产生了一个函数实例之后，再进行函数调用 */
  public Object call(Object ctx, Object thisValue, VariableResolverFactory factory, Object[] parms) {
    if (parms != null && parms.length != 0) {
      // detect tail recursion
      //这里处理递归化调用,则当前函数递归调用当前函数,那么相应的工厂就是之前在当前函数内创建好地变量工厂
      //同时这里需要判定相应的函数定义必须为当前函数才是递归调用,否则就不被支持
      if (factory instanceof FunctionVariableResolverFactory
          && ((FunctionVariableResolverFactory) factory).getIndexedVariableResolvers().length == parms.length) {
        FunctionVariableResolverFactory fvrf = (FunctionVariableResolverFactory) factory;
        //这里判定函数对象是同一个
        if (fvrf.getFunction().equals(this)) {
          VariableResolver[] swapVR = fvrf.getIndexedVariableResolvers();
          //先替换参数,在调用完之后,再替换回来,即递归调用内的参数值不会影响到外面的值
          fvrf.updateParameters(parms);
          try {
            return compiledBlock.getValue(ctx, thisValue, fvrf);
          }
          finally {
            fvrf.setIndexedVariableResolvers(swapVR);
          }
        }
      }
      //正常的调用,有参数信息,就使用函数变量工厂来表示相应的作用域
      return compiledBlock.getValue(thisValue,
          new StackDemarcResolverFactory(new FunctionVariableResolverFactory(this, factory, parameters, parms)));
    }
    //因为没有参数信息,则直接使用一个默认的变量工厂即可,因为不需要对参数作处理,函数内部的值都是由新的作用域来处理
    else if (compiledMode) {
      return compiledBlock.getValue(thisValue,
          new StackDemarcResolverFactory(new DefaultLocalVariableResolverFactory(factory, parameters)));
    }
    else {
      return compiledBlock.getValue(thisValue,
          new StackDemarcResolverFactory(new DefaultLocalVariableResolverFactory(factory, parameters)));
    }

  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String[] getParameters() {
    return parameters;
  }

  public boolean hasParameters() {
    return this.parameters != null && this.parameters.length != 0;
  }

  public void checkArgumentCount(int passing) {
    if (passing != parmNum) {
      throw new CompileException("bad number of arguments in function call: "
          + passing + " (expected: " + (parmNum == 0 ? "none" : parmNum) + ")", expr, start);
    }
  }

  /** 获取相应的执行代码块 */
  public ExecutableStatement getCompiledBlock() {
    return compiledBlock;
  }

  public String toString() {
    return "FunctionDef:" + (name == null ? "Anonymous" : name);
  }
}

