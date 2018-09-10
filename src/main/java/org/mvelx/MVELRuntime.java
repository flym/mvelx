package org.mvelx;

import org.mvelx.ast.ASTNode;
import org.mvelx.compiler.CompiledExpression;
import org.mvelx.integration.VariableResolverFactory;
import org.mvelx.optimizers.OptimizerFactory;
import org.mvelx.util.ExecutionStack;

import static org.mvelx.Operator.*;
import static org.mvelx.util.PropertyTools.isEmpty;

/**
 * 用于主要运行已经编译过的表达式的处理，完成主要的运算逻辑处理
 * This class contains the runtime for running compiled MVEL expressions.
 */
@SuppressWarnings({"CaughtExceptionImmediatelyRethrown"})
public class MVELRuntime {
    /**
     * 执行相应的编译运行表达式
     * Main interpreter.
     *
     * @param debugger        Run in debug mode
     * @param expression      The compiled expression object
     * @param ctx             The root context object
     * @param variableFactory The variable factory to be injected
     * @return The resultant value
     * @see MVEL
     */
    public static Object execute(boolean debugger, final CompiledExpression expression, final Object ctx,
                                 VariableResolverFactory variableFactory) {

        Object v1, v2;
        //保存当前临时的执行栈
        ExecutionStack stk = new ExecutionStack();

        ASTNode tk = expression.getFirstNode();
        Integer operator;

        //本身就没有可执行节点，则直接返回null
        if(tk == null) return null;

        try{
            do{
                //这里表示当前节点为调试节点，因此尝试设置相应的调试上下文，fields 为 1 为调试节点
                if(tk.fields == -1) {
                    continue;
                }
                //当前操作栈为空的，则压入当前值，以进行处理
                else if(stk.isEmpty()) {
                    stk.push(tk.getReducedValueAccelerated(ctx, ctx, variableFactory));
                }

                //如果标识已结束了则直接返回数据信息
                if(variableFactory.tiltFlag()) {
                    return stk.pop();
                }

                switch(operator = tk.getOperator()) {
                    //操作符return 在前一步即可以处理了
                    case RETURN:
                        variableFactory.setTiltFlag(true);
                        return stk.pop();

                    //默认情况下，大部分的节点均返回NOOP操作符
                    case NOOP:
                        continue;

                        //? 操作符，如果当前结果为false，则表示要获取 ? :冒号之后的值，则当前执行流程直接跳转到:后面去.并且清空操作数栈
                    case TERNARY:
                        if(!stk.popBoolean()) {
                            //noinspection StatementWithEmptyBody
                            while(tk.nextASTNode != null && !(tk = tk.nextASTNode).isOperator(TERNARY_ELSE)) ;
                        }
                        stk.clear();
                        continue;

                        //因为这里到达 :，表示对于?已经执行完:之前的表达式，因此直接返回即可
                    case TERNARY_ELSE:
                        return stk.pop();

                    case END_OF_STMT:
            /*
             * 这里如果还有下一个节点，表示还有进一步的操作，那么就把当前执行栈给清掉，表示不再需要之前的信息
             * If the program doesn't end here then we wipe anything off the stack that remains.
             * Althought it may seem like intuitive stack optimizations could be leveraged by
             * leaving hanging values on the stack,  trust me it's not a good idea.
             */
                        if(tk.nextASTNode != null) {
                            stk.clear();
                        }

                        continue;
                }

                //这里继续圧入下一个节点值
                //到这里这里的tk.nextASTNode肯定不为null，因为如果为null,则在上一个switch中已经处理掉，这里只要是支持一些当前还未支持到的处理
                stk.push(tk.nextASTNode.getReducedValueAccelerated(ctx, ctx, variableFactory), operator);

                try{
                    //这里保证当前栈中只有一个操作数，因为之前的操作数均没有用处
                    while(stk.isReduceable()) {
                        if((Integer) stk.peek() == CHOR) {
                            stk.pop();
                            v1 = stk.pop();
                            v2 = stk.pop();
                            if(!isEmpty(v2) || !isEmpty(v1)) {
                                stk.clear();
                                stk.push(!isEmpty(v2) ? v2 : v1);
                            } else stk.push(null);
                        } else {
                            stk.op();
                        }
                    }
                } catch(ClassCastException e) {
                    throw new CompileException("syntax error or incomptable types", new char[0], 0, e);
                } catch(CompileException e) {
                    throw e;
                } catch(Exception e) {
                    throw new CompileException("failed to compileShared sub expression", new char[0], 0, e);
                }
            }
            //这里继续循环的前提在于下一个节点必须不为null
            while((tk = tk.nextASTNode) != null);

            //最终到达结尾，直接返回最后一个表达式的值
            return stk.peek();
        } catch(NullPointerException e) {
            if(tk != null && tk.isOperator() && tk.nextASTNode != null) {
                throw new CompileException("incomplete statement: "
                        + tk.getName() + " (possible use of reserved keyword as identifier: " + tk.getName() + ")", tk.getExpr(), tk.getStart());
            } else {
                throw e;
            }
        } finally {
            OptimizerFactory.clearThreadAccessorOptimizer();
        }
    }
}
