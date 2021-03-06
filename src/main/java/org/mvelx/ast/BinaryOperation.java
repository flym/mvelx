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

package org.mvelx.ast;

import org.mvelx.CompileException;
import org.mvelx.Operator;
import org.mvelx.ParserContext;
import org.mvelx.ScriptRuntimeException;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.util.CompatibilityStrategy;
import org.mvelx.util.NullType;
import org.mvelx.util.ParseTools;

import static org.mvelx.DataConversion.canConvert;
import static org.mvelx.DataConversion.convert;
import static org.mvelx.math.MathProcessor.doOperations;
import static org.mvelx.util.CompilerTools.getReturnTypeFromOp;
import static org.mvelx.util.ParseTools.boxPrimitive;

/** 这里描述一个二元运算操作 */
public class BinaryOperation extends BooleanNode {
    /** 运算符 */
    private final int operation;
    /** 左边类型(内部表示形式,见DataTypes) */
    private int lType = -1;
    /** 右边类型(内部表示形式,见DataTypes) */
    private int rType = -1;

    public BinaryOperation(int operation, ParserContext ctx) {
        super(ctx);
        this.operation = operation;
    }

    public BinaryOperation(int operation, ASTNode left, ASTNode right, ParserContext ctx) {
        super(ctx);
        this.operation = operation;
        //要求左右的节点都需要存在
        if((this.left = left) == null) {
            throw new ScriptRuntimeException("not a statement");
        }
        if((this.right = right) == null) {
            throw new ScriptRuntimeException("not a statement");
        }

        //    if (ctx.isStrongTyping()) {
        switch(operation) {
            case Operator.ADD:
        /*
         * 处理可能为字符串相加的情况,则设置相应的声明类型为字符串
         * In the special case of Strings, the return type may leftward propogate.
         */
                if(left.getEgressType() == String.class || right.getEgressType() == String.class) {
                    egressType = String.class;
                    lType = ParseTools.__resolveType(left.egressType);
                    rType = ParseTools.__resolveType(right.egressType);

                    return;
                }

            default:
                //通过操作符,类型推断出当前的返回类型
                egressType = getReturnTypeFromOp(operation, this.left.egressType, this.right.egressType);
                if(!ctx.isStrongTyping()) break;

                //处理可能的左右类型转换
                //这里即左边类型和右边类型不能类型上兼容,即int和long
                if(!left.getEgressType().isAssignableFrom(right.getEgressType()) && !right.getEgressType().isAssignableFrom(left.getEgressType())) {
                    //如果右边是常量,并且可以和左边进行转换,则根据算数符来决定常量转换为哪个类型
                    //同时因为是常量,因此可以直接运算出相应的结果,即可以马上计算
                    if(right.isLiteral() && canConvert(left.getEgressType(), right.getEgressType())) {
                        //如果是四则运算,则以结果为准,否则则以左边结点为准.比如 a < b这种
                        Class targetType = isAritmeticOperation(operation) ? egressType : left.getEgressType();
                        this.right = new LiteralNode(convert(right.getReducedValueAccelerated(null, null, null), targetType), pCtx);
                    } else if(!(areCompatible(left.getEgressType(), right.getEgressType()) ||
                            ((operation == Operator.EQUAL || operation == Operator.NEQUAL) &&
                                    CompatibilityStrategy.areEqualityCompatible(left.getEgressType(), right.getEgressType())))) {
                        //即在算术上不兼容,同时也不是== 或 != 这种,则报错

                        throw new CompileException("incompatible types in statement: " + right.getEgressType()
                                + " (compared from: " + left.getEgressType() + ")",
                                left.getExpr() != null ? left.getExpr() : right.getExpr(),
                                left.getExpr() != null ? left.getStart() : right.getStart());
                    }
                }
        }


        // }

        //都是常量,则可以认为都可以直接拿到相应的类型了
        if(this.left.isLiteral() && this.right.isLiteral()) {
            if(this.left.egressType == this.right.egressType) {
                lType = rType = ParseTools.__resolveType(left.egressType);
            } else {
                lType = ParseTools.__resolveType(this.left.egressType);
                rType = ParseTools.__resolveType(this.right.egressType);
            }
        }
    }

    /** 是否是算术操作，即基本的 +-* */
    private boolean isAritmeticOperation(int operation) {
        return operation <= Operator.POWER;
    }

    /** 判定2个类型在计算上是否是兼容的,即都是数字类型 */
    private boolean areCompatible(Class<?> leftClass, Class<?> rightClass) {
        return leftClass.equals(NullType.class) || rightClass.equals(NullType.class) ||
                (Number.class.isAssignableFrom(rightClass) && Number.class.isAssignableFrom(leftClass)) ||
                ((rightClass.isPrimitive() || leftClass.isPrimitive()) &&
                        canConvert(boxPrimitive(leftClass), boxPrimitive(rightClass)));
    }

    public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
        //由数学处理器来完成相应的计算
        return doOperations(lType, left.getReducedValueAccelerated(ctx, thisValue, factory), operation, rType,
                right.getReducedValueAccelerated(ctx, thisValue, factory));
    }

    public int getOperation() {
        return operation;
    }

    /** 替换掉最右边的节点 如 a + b - c 增加一个 * d时，就把c替换为(c*d) */
    public void setRightMost(ASTNode right) {
        BinaryOperation n = this;
        while(n.right != null && n.right instanceof BinaryOperation) {
            n = (BinaryOperation) n.right;
        }
        n.right = right;

        if(n == this) {
            if((rType = ParseTools.__resolveType(n.right.getEgressType())) == 0) rType = -1;
        }
    }

    /** 获取最右边的节点,如果右边仍是二元操作，继续靠右 */
    public ASTNode getRightMost() {
        BinaryOperation n = this;
        while(n.right != null && n.right instanceof BinaryOperation) {
            n = (BinaryOperation) n.right;
        }
        return n.right;
    }

    /** 当前节点不是常量 */
    @Override
    public boolean isLiteral() {
        return false;
    }

    public String toString() {
        return "(" + left + " " + (operation) + " " + right + ")";
    }
}
