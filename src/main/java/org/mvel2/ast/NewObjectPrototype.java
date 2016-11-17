package org.mvel2.ast;

import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.HashMap;

/**
 * 用于描述new一个function的效果,即new 一个js原型
 *
 * @author Mike Brock
 */
public class NewObjectPrototype extends ASTNode {
  /** 所对应的函数 */
  private Function function;

  public NewObjectPrototype(ParserContext pCtx, Function function) {
    super(pCtx);
    this.function = function;
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //因为是new一个对象,因此需要一个新的变量工厂,即作用域
    final MapVariableResolverFactory resolverFactory = new MapVariableResolverFactory(new HashMap<String, Object>(), factory);
    //这里实际上已经作了一次函数块调用,但这个调用没什么用处...
    function.getCompiledBlock().getValue(ctx, thisValue, resolverFactory);
    //这里再返回此函数实例
    return new PrototypalFunctionInstance(function, resolverFactory);
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    return getReducedValue(ctx, thisValue, factory);
  }
}
