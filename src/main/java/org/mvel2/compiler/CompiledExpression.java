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

import org.mvel2.ParserConfiguration;
import org.mvel2.ast.ASTNode;
import org.mvel2.ast.TypeCast;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ClassImportResolverFactory;
import org.mvel2.integration.impl.StackResetResolverFactory;
import org.mvel2.optimizers.AccessorOptimizer;
import org.mvel2.optimizers.OptimizerFactory;
import org.mvel2.util.ASTLinkedList;

import java.io.Serializable;

import static org.mvel2.MVELRuntime.execute;
import static org.mvel2.optimizers.OptimizerFactory.setThreadAccessorOptimizer;

/**
 * 用于表示一个编译完成的编译表达式，通过此表达式可以最终进行执行最得到最终的数据
 * 在整个概念上，编译表达式即完成了语法分析和静态编译的环节，可以在多个环境中进行缓存。在每次访问时，可以通过传入不同的参数直接通过MvelRuntime进行执行
 * 在数据存储上，内部通过链接一个节点链来表示整个表达式
 */
public class CompiledExpression implements Serializable, ExecutableStatement {
  /** 当前表达式第一个节点(剩下的信息通过第1个节点来调用) */
  private ASTNode firstNode;

  /** 声明的出参类型 */
  private Class knownEgressType;
  /** 声明的入参类型 */
  private Class knownIngressType;

  private boolean convertableIngressEgress;
  /** 表示当前表达式是否已经经过优化处理,未优化则必须经过优化才能处理(即设置访问优化器) */
  private boolean optimized = false;
  /** 表示相应的解析上下文中是否存在外部导入的注入信息,在使用时根据此标记以创建不同的变量工厂，以支持相应的import或者是类引用处理 */
  private boolean importInjectionRequired = false;
  /** 当前表达式是否仅是常量 */
  private boolean literalOnly;

  /** 在当前执行过程中使用的优化器(或者是当前表达式使用的优化器) */
  private Class<? extends AccessorOptimizer> accessorOptimizer;

  /** 表达式所对应的源文件 */
  private String sourceName;

  /** 相应的解析配置信息 */
  private ParserConfiguration parserConfiguration;

  public CompiledExpression(ASTLinkedList astMap, String sourceName, Class egressType, ParserConfiguration parserConfiguration, boolean literalOnly) {
    this.firstNode = astMap.firstNode();
    this.sourceName = sourceName;
    this.knownEgressType = astMap.isSingleNode() ? astMap.firstNonSymbol().getEgressType() : egressType;
    this.literalOnly = literalOnly;
    this.parserConfiguration = parserConfiguration;
    this.importInjectionRequired = parserConfiguration.getImports() != null && !parserConfiguration.getImports().isEmpty();
  }

  /** 获取相应的第一个节点 */
  public ASTNode getFirstNode() {
    return firstNode;
  }

  /** 解析此表达式是否仅有单个节点 */
  public boolean isSingleNode() {
    return firstNode != null && firstNode.nextASTNode == null;
  }

  public Class getKnownEgressType() {
    return knownEgressType;
  }

  public void setKnownEgressType(Class knownEgressType) {
    this.knownEgressType = knownEgressType;
  }

  public Class getKnownIngressType() {
    return knownIngressType;
  }

  public void setKnownIngressType(Class knownIngressType) {
    this.knownIngressType = knownIngressType;
  }

  public boolean isConvertableIngressEgress() {
    return convertableIngressEgress;
  }

  /** 判定相应的入参和出参是否兼容 */
  public void computeTypeConversionRule() {
    if (knownIngressType != null && knownEgressType != null) {
      convertableIngressEgress = knownIngressType.isAssignableFrom(knownEgressType);
    }
  }

  /** 根据上下文和相应的this引用获取相应的最终计算值 */
  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    if (!optimized) {
      setupOptimizers();
      try {
        return getValue(ctx, variableFactory);
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
    return getValue(ctx, variableFactory);
  }

  /** 根据上下文获取相应的最终计算值 */
  public Object getValue(Object staticContext, VariableResolverFactory factory) {
    if (!optimized) {
      setupOptimizers();
      try {
        return getValue(staticContext, factory);
      }
      finally {
        OptimizerFactory.clearThreadAccessorOptimizer();
      }
    }
    return getDirectValue(staticContext, factory);
  }

  /** 调用计算程序最终计算出相应的值 */
  public Object getDirectValue(Object staticContext, VariableResolverFactory factory) {
    return execute(false, this, staticContext,
        importInjectionRequired ? new ClassImportResolverFactory(parserConfiguration, factory, true) : new StackResetResolverFactory(factory));
  }

  private void setupOptimizers() {
    if (accessorOptimizer != null) setThreadAccessorOptimizer(accessorOptimizer);
    optimized = true;
  }

  public boolean isOptimized() {
    return optimized;
  }

  public Class<? extends AccessorOptimizer> getAccessorOptimizer() {
    return accessorOptimizer;
  }

  public String getSourceName() {
    return sourceName;
  }

  /** 当前表达式不是整数优化的 */
  public boolean intOptimized() {
    return false;
  }

  public ParserConfiguration getParserConfiguration() {
    return parserConfiguration;
  }

  public boolean isImportInjectionRequired() {
    return importInjectionRequired;
  }

  /** 不支持设置值操作 */
  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    return null;
  }

  public boolean isLiteralOnly() {
    return literalOnly;
  }

  public boolean isEmptyStatement() {
    return firstNode == null;
  }

  public boolean isExplicitCast() {
    return firstNode != null && firstNode instanceof TypeCast;
  }

  public String toString() {
    StringBuilder appender = new StringBuilder();
    ASTNode node = firstNode;
    while (node != null) {
      appender.append(node.toString()).append(";\n");
      node = node.nextASTNode;
    }
    return appender.toString();
  }
}
