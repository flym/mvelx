package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.GlobalListenerFactory;
import org.mvel2.integration.VariableResolverFactory;


/**
 * 描述一下可以在调用时进行监听通知的访问器
 * 在具体使用时,即在实现时,先通过使用此访问器进行占位,然后将真实地访问器作为next来处理,因此可以在真实的访问器执行前进行相应的拦截处理
 */
public class Notify implements AccessorNode {
  /** 当前处理的属性名 */
  private String name;
  private AccessorNode nextNode;

  public Notify(String name) {
    this.name = name;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory vrf) {
    //调用前进行get调用通知
    GlobalListenerFactory.notifyGetListeners(ctx, name, vrf);
    return nextNode.getValue(ctx, elCtx, vrf);
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //调用前进行set调用通知
    GlobalListenerFactory.notifySetListeners(ctx, name, variableFactory, value);
    return nextNode.setValue(ctx, elCtx, variableFactory, value);
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode nextNode) {
    return this.nextNode = nextNode;
  }

  /** 声明类型未知,为Object */
  public Class getKnownEgressType() {
    return Object.class;
  }
}
