package org.mvelx.ast;

import org.mvelx.ParserContext;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.util.CompilerTools;


/** 描述一个instanceof的操作节点,为一个优化节点 */
public class Instance extends ASTNode {
    /** 主对象表达式 */
    private ASTNode stmt;
    /** 类型的表达式 */
    private ASTNode clsStmt;

    public Instance(ASTNode stmt, ASTNode clsStmt, ParserContext pCtx) {
        super(pCtx);
        this.stmt = stmt;
        this.clsStmt = clsStmt;
        //要求后面的clsStmt返回值为class类型
        CompilerTools.expectType(pCtx, clsStmt, Class.class, true);
    }

    public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
        //采用class.isInstance来作判定.因此先计算class值,再计算相应的对象值,最后相调用即可
        return ((Class) clsStmt.getReducedValueAccelerated(ctx, thisValue, factory)).isInstance(stmt.getReducedValueAccelerated(ctx, thisValue, factory));
    }

    /** instanceOf 返回类型为 boolean */
    public Class getEgressType() {
        return Boolean.class;
    }

    /** 当前处理的节点 */
    public ASTNode getStatement() {
        return stmt;
    }

    /** 当前要判断的class节点 */
    public ASTNode getClassStatement() {
        return clsStmt;
    }
}
