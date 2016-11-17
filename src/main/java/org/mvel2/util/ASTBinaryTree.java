package org.mvel2.util;

import static org.mvel2.Operator.*;
import org.mvel2.ast.ASTNode;
import org.mvel2.ast.EndOfStatement;
import org.mvel2.ast.OperatorNode;

/**
 * 一个工具类,用于描述一个具有执行优化级的语法树,通过相应的语法树,最终可以表示出此树最终的返回类型以及其它信息等
 * 当前主要用于返回相应的节点处理类型
 * 节点的优先级, 中间小于两边
 * 此语法树专门用于处理操作符的语法树,即中间为操作符,然后两边为具体的操作数
 * 备注:实际上此类没有太多的用处
 */
public class ASTBinaryTree {
  /** 中间节点 */
    private ASTNode root;
  /** 左边节点 */
    private ASTBinaryTree left;
  /** 右边节点 */
    private ASTBinaryTree right;

  /** 通过中间节点初始构建起语法树 */
    public ASTBinaryTree(ASTNode node) {
        this.root = node;
    }

    /** 在当前树中追加新的节点,并返回追加后的语法树,因此有可能当前root节点会被改掉 */
    public ASTBinaryTree append(ASTNode node) {
      //比较优先级,将低优先级的放到中间,即尽可能操作节点在中间
        if (comparePrecedence(root, node) >= 0) {
            ASTBinaryTree tree = new ASTBinaryTree(node);
            tree.left = this;
            return tree;
        } else {
          //这里对left判断,即不能直接添加2个非操作符节点,或者是2个操作符节点,一定是1个操作符+一个非操作符节点
            if (left == null) throw new RuntimeException("Missing left node");
            if (right == null) {
                right = new ASTBinaryTree(node);
            } else {
                right = right.append(node);
            }
            return this;
        }
    }

    /** 根据是否强类型返回相应的执行类型 */
    public Class<?> getReturnType(boolean strongTyping) {
      //root不是操作符,则认为只有当前节点,因此直接返回该声明类型即可
        if (!(root instanceof OperatorNode)) return root.getEgressType();
        if (left == null || right == null) throw new RuntimeException("Malformed expression");
        Class<?> leftType = left.getReturnType(strongTyping);
        Class<?> rightType = right.getReturnType(strongTyping);
      //root为操作符,根据操作符来进行判断
        switch (((OperatorNode)root).getOperator()) {
          //boolean型操作
            case CONTAINS:
            case SOUNDEX:
            case INSTANCEOF:
            case SIMILARITY:
            case REGEX:
                return Boolean.class;
            //+操作,先判断是否是字符串拼接,其它情况进行宽化处理,认为返回double类型
            case ADD:
                if (leftType.equals(String.class) || rightType.equals(String.class)) return String.class;
            case SUB:
            case MULT:
            case DIV:
                if (strongTyping && !CompatibilityStrategy.areEqualityCompatible(leftType, rightType))
                    throw new RuntimeException("Associative operation requires compatible types. Found " + leftType + " and " + rightType);
                return Double.class;
            // ?: 表达式,即3维表达式,随便返回一个即可
            case TERNARY_ELSE:
                if (strongTyping && !CompatibilityStrategy.areEqualityCompatible(leftType, rightType))
                    throw new RuntimeException("Associative operation requires compatible types. Found " + leftType + " and " + rightType);
                return leftType;
            // == 判断,返回boolean
            case EQUAL:
            case NEQUAL:
                if (strongTyping && !CompatibilityStrategy.areEqualityCompatible(leftType, rightType))
                    throw new RuntimeException("Comparison operation requires compatible types. Found " + leftType + " and " + rightType);
                return Boolean.class;
            // <= < >= > 返回boolean
            case LTHAN:
            case LETHAN:
            case GTHAN:
            case GETHAN:
                if (strongTyping && !CompatibilityStrategy.areComparisonCompatible(leftType, rightType))
                    throw new RuntimeException("Comparison operation requires compatible types. Found " + leftType + " and " + rightType);
                return Boolean.class;
            // and || 操作,返回boolean
            case AND:
            case OR:
                if (strongTyping) {
                    if (leftType != Boolean.class && leftType != Boolean.TYPE)
                        throw new RuntimeException("Left side of logical operation is not of type boolean. Found " + leftType);
                    if (rightType != Boolean.class && rightType != Boolean.TYPE)
                        throw new RuntimeException("Right side of logical operation is not of type boolean. Found " + rightType);
                }
                return Boolean.class;
            case TERNARY:
                if (strongTyping && leftType != Boolean.class && leftType != Boolean.TYPE)
                    throw new RuntimeException("Condition of ternary operator is not of type boolean. Found " + leftType);
                return rightType;
        }
        // TODO: should throw new RuntimeException("Unknown operator");
        // it doesn't because I am afraid I am not covering all the OperatorNode types
        return root.getEgressType();
    }

    /** 比较两个节点的优先级,低优先级的会认为是root节点 */
    private int comparePrecedence(ASTNode node1, ASTNode node2) {
      //两个都不是操作节点,认为均相等
        if (!(node1 instanceof OperatorNode) && !(node2 instanceof OperatorNode)) return 0;
      //两个都是操作节点,则按照操作节点的优先级来处理
        if (node1 instanceof OperatorNode && node2 instanceof OperatorNode) {
            return PTABLE[((OperatorNode)node1).getOperator()] - PTABLE[((OperatorNode)node2).getOperator()];
        }
        //哪个为操作节点,哪个的优先级就低
        return node1 instanceof OperatorNode ? -1 : 1;
    }

    /** 通过一个节点链构建出相应的操作符语法树 */
    public static ASTBinaryTree buildTree(ASTIterator input) {
        ASTIterator iter = new ASTLinkedList(input.firstNode());
        ASTBinaryTree tree = new ASTBinaryTree(iter.nextNode());
        while (iter.hasMoreNodes()) {
            ASTNode node = iter.nextNode();
          //这里表示碰到了;对于表达式 a > b;c > d 在此操作中a>b并不会影响到最终的返回结果,因此这里直接丢弃之前的构建,从c > b开始进行处理
            if (node instanceof EndOfStatement) {
                if (iter.hasMoreNodes()) tree = new ASTBinaryTree(iter.nextNode());
            } else {
                tree = tree.append(node);
            }
        }
        return tree;
    }
}
