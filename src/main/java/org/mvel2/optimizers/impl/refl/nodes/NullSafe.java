package org.mvel2.optimizers.impl.refl.nodes;

import org.mvel2.ParserContext;
import org.mvel2.compiler.Accessor;
import org.mvel2.compiler.AccessorNode;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.OptimizerFactory;

/**
 * 对象安全的访问器,即在具体的处理时，如果当前对象为null，则不再处理剩下的处理
 * <p/>
 * 如?a.b,如果a为null，则整体返回为null，而不是报NPE.这里表示a属性的值可能为null
 */
public class NullSafe implements AccessorNode {
  /** 由当前节点包含的真实访问节点 */
  private AccessorNode nextNode;
  private char[] expr;
  private int start;
  private int offset;
  /** 解析上下文 */
  private ParserContext pCtx;

  public NullSafe(char[] expr, int start, int offset, ParserContext pCtx) {
    this.expr = expr;
    this.start = start;
    this.offset = offset;
    this.pCtx = pCtx;
  }

  public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
    //当前对象为null，跳过
    if (ctx == null) return null;
    //真实调用还未进行编译,则尝试编译,并将相应的调用委托给相应的编译好的访问器
    if (nextNode == null) {
      final Accessor a = OptimizerFactory.getAccessorCompiler(OptimizerFactory.SAFE_REFLECTIVE)
          .optimizeAccessor(pCtx, expr, start, offset, ctx, elCtx, variableFactory, true, ctx.getClass());

      nextNode = new AccessorNode() {
        public AccessorNode getNextNode() {
          return null;
        }

        public AccessorNode setNextNode(AccessorNode accessorNode) {
          return null;
        }

        public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
          return a.getValue(ctx, elCtx, variableFactory);
        }

        public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
          return a.setValue(ctx, elCtx, variableFactory, value);
        }

        public Class getKnownEgressType() {
          return a.getKnownEgressType();
        }
      };


    }
    //   else {
    return nextNode.getValue(ctx, elCtx, variableFactory);
    //    }
  }

  public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
    //当前对象为null，跳过
    if (ctx == null) return null;
    return nextNode.setValue(ctx, elCtx, variableFactory, value);
  }

  public AccessorNode getNextNode() {
    return nextNode;
  }

  public AccessorNode setNextNode(AccessorNode accessorNode) {
    return this.nextNode = accessorNode;
  }

  /** 类型未知,为Object类型 */
  public Class getKnownEgressType() {
    return Object.class;
  }
}
