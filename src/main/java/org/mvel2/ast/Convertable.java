package org.mvel2.ast;

import org.mvel2.DataConversion;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.CompilerTools;

/** 转换节点 由convertable_to 处理而来,为一个优化节点 */
public class Convertable extends ASTNode {
  /** 处理值 */
  private ASTNode stmt;
  /** 判定的目标类型处理节点 */
  private ASTNode clsStmt;

  public Convertable(ASTNode stmt, ASTNode clsStmt, ParserContext pCtx) {
    super(pCtx);
    this.stmt = stmt;
    this.clsStmt = clsStmt;
    //期望类型能够匹配
    CompilerTools.expectType(pCtx, clsStmt, Class.class, true);
  }

  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //先获取相应的值,再进行相应的转换判断
    Object o = stmt.getReducedValueAccelerated(ctx, thisValue, factory);
    return o != null && DataConversion.canConvert(
        (Class) clsStmt.getReducedValueAccelerated(ctx, thisValue, factory), o.getClass());

  }

  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    try {

      //与编译运行相一致,不过相应的过程转为解释运行
      Object o = stmt.getReducedValue(ctx, thisValue, factory);
      if (o == null) return false;

      Class i = (Class) clsStmt.getReducedValue(ctx, thisValue, factory);
      if (i == null) throw new ClassCastException();


      return DataConversion.canConvert(i, o.getClass());
    }
    catch (ClassCastException e) {
      throw new RuntimeException("not a class reference: " + clsStmt.getName());
    }

  }

  /** 出参类型为boolean,即a instanceof C */
  public Class getEgressType() {
    return Boolean.class;
  }
}
