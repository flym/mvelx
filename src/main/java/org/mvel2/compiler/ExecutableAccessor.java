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

import org.mvel2.ast.ASTNode;
import org.mvel2.ast.TypeCast;
import org.mvel2.integration.VariableResolverFactory;

/**
 * 描述一下通过节点进行处理的执行访问器(其实就是对astNode的封装)
 * 可以使用此类来完成执行单元的相应的编译节点之间的转换,这样就简单的将相应的节点先暂时转换为执行单元.
 * 而具体的节点在处理时,再根据实际的行为重新进行二次优化编译执行
 * 可以理解为node->解析转换为执行单元->再编译转换为访问器,然后进行这样的一个处理.这种工作方式可以避免提前进行编译,即将优化编译延迟来处理
 */
public class ExecutableAccessor implements ExecutableStatement {
  /** 所引用的节点 */
  private ASTNode node;

  /** 入参类型 */
  private Class ingress;
  /** 出参类型 */
  private Class egress;
  /** 当前运行是否需要转换(即入参，出参转型) */
  private boolean convertable;

  public ExecutableAccessor(ASTNode node, Class egress) {
    this.node = node;
    this.egress = egress;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    return node.getReducedValueAccelerated(ctx, elCtx, variableFactory);
  }

  public Object getValue(Object staticContext, VariableResolverFactory factory) {
    return node.getReducedValueAccelerated(staticContext, staticContext, factory);
  }

  public void setKnownIngressType(Class type) {
    this.ingress = type;
  }

  public void setKnownEgressType(Class type) {
    this.egress = type;
  }

  public Class getKnownIngressType() {
    return ingress;
  }

  public Class getKnownEgressType() {
    return egress;
  }

  public boolean isConvertableIngressEgress() {
    return convertable;
  }

  public void computeTypeConversionRule() {
    if (ingress != null && egress != null) {
      convertable = ingress.isAssignableFrom(egress);
    }
  }

  /** 当前还没有进行编译 */
  public boolean intOptimized() {
    return false;
  }

  public ASTNode getNode() {
    return node;
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    return null;
  }

  public boolean isLiteralOnly() {
    return false;
  }

  public boolean isExplicitCast() {
    return node instanceof TypeCast;
  }

  public boolean isEmptyStatement() {
    return node == null;
  }

  @Override
  public String toString() {
    return node.toString();
  }
}


