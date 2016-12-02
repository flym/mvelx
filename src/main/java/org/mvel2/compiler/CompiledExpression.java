package org.mvel2.compiler;

import lombok.Getter;
import lombok.Setter;
import org.mvel2.ParserConfiguration;
import org.mvel2.ast.ASTNode;
import org.mvel2.ast.TypeCast;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.ClassImportResolverFactory;
import org.mvel2.integration.impl.StackResetResolverFactory;
import org.mvel2.util.ASTLinkedList;

import java.io.Serializable;

import static org.mvel2.MVELRuntime.execute;

/**
 * 用于表示一个编译完成的编译表达式，通过此表达式可以最终进行执行最得到最终的数据
 * 在整个概念上，编译表达式即完成了语法分析和静态编译的环节，可以在多个环境中进行缓存。在每次访问时，可以通过传入不同的参数直接通过MvelRuntime进行执行
 * 在数据存储上，内部通过链接一个节点链来表示整个表达式
 */
public class CompiledExpression implements Serializable, ExecutableStatement {
    /** 当前表达式第一个节点(剩下的信息通过第1个节点来调用) */
    @Getter
    private ASTNode firstNode;

    /** 声明的出参类型 */
    @Getter
    @Setter
    private Class knownEgressType;
    /** 声明的入参类型 */
    @Getter
    @Setter
    private Class knownIngressType;

    @Getter
    private boolean convertableIngressEgress;
    /** 表示相应的解析上下文中是否存在外部导入的注入信息,在使用时根据此标记以创建不同的变量工厂，以支持相应的import或者是类引用处理 */
    @Getter
    private boolean importInjectionRequired = false;
    /** 当前表达式是否仅是常量 */
    @Getter
    private boolean literalOnly;

    /** 相应的解析配置信息 */
    @Getter
    private ParserConfiguration parserConfiguration;

    public CompiledExpression(ASTLinkedList astMap, String sourceName, Class egressType, ParserConfiguration parserConfiguration, boolean literalOnly) {
        this.firstNode = astMap.firstNode();
        this.knownEgressType = astMap.isSingleNode() ? astMap.firstNonSymbol().getEgressType() : egressType;
        this.literalOnly = literalOnly;
        this.parserConfiguration = parserConfiguration;
        this.importInjectionRequired = parserConfiguration.getImports() != null && !parserConfiguration.getImports().isEmpty();
    }

    /** 解析此表达式是否仅有单个节点 */
    public boolean isSingleNode() {
        return firstNode != null && firstNode.nextASTNode == null;
    }

    /** 判定相应的入参和出参是否兼容 */
    public void computeTypeConversionRule() {
        if(knownIngressType != null && knownEgressType != null) {
            convertableIngressEgress = knownIngressType.isAssignableFrom(knownEgressType);
        }
    }

    /** 根据上下文和相应的this引用获取相应的最终计算值 */
    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        return getValue(ctx, variableFactory);
    }

    /** 根据上下文获取相应的最终计算值 */
    public Object getValue(Object staticContext, VariableResolverFactory factory) {
        return getDirectValue(staticContext, factory);
    }

    /** 调用计算程序最终计算出相应的值 */
    public Object getDirectValue(Object staticContext, VariableResolverFactory factory) {
        return execute(false, this, staticContext,
                importInjectionRequired ? new ClassImportResolverFactory(parserConfiguration, factory, true) : new StackResetResolverFactory(factory));
    }

    /** 当前表达式不是整数优化的 */
    public boolean intOptimized() {
        return false;
    }

    /** 不支持设置值操作 */
    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        return null;
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
        while(node != null) {
            appender.append(node.toString()).append(";\n");
            node = node.nextASTNode;
        }
        return appender.toString();
    }
}
