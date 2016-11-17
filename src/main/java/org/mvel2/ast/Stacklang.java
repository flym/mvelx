package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.Operator;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.ExecutionStack;
import org.mvel2.util.ParseTools;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于描述堆栈指令集的节点信息
 *
 * @author Mike Brock <cbrock@redhat.com>
 */
public class Stacklang extends BlockNode {
  /** 指令列表,由原字符串使用;隔开的多个指令 */
  List<Instruction> instructionList;
  /** 当前解析上下文 */
  ParserContext pCtx;

  public Stacklang(char[] expr, int blockStart, int blockOffset, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.blockStart = blockStart;
    this.blockOffset = blockOffset;
    this.fields = fields | ASTNode.STACKLANG;

    //每个指令集以分号进行分隔
    String[] instructions = new String(expr, blockStart, blockOffset).split(";");

    //编译相应的指令集
    instructionList = new ArrayList<Instruction>(instructions.length);
    for (String s : instructions) {
      instructionList.add(parseInstruction(s.trim()));
    }

    this.pCtx = pCtx;
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //整个执行栈,即是以解释模式运行的,因此直接从之前的解释栈中返回即可
    ExecutionStack stk = new ExecutionStack();
    stk.push(getReducedValue(stk, thisValue, factory));
    //如果栈中有多个值，就直接使用后缀表达式操作对栈进行操作
    if (stk.isReduceable()) {
      while (true) {
        stk.op();
        //还可以进行处理，则进行交换，以将相应的操作符进行交换，以执行处理
        //这里执行交换的意思在于需要将栈中的中缀转换为后缀
        if (stk.isReduceable()) {
          stk.xswap();
        }
        else {
          break;
        }
      }
    }
    return stk.peek();
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    ExecutionStack stack = (ExecutionStack) ctx;

    for (int i1 = 0, instructionListSize = instructionList.size(); i1 < instructionListSize; i1++) {
      Instruction instruction = instructionList.get(i1);

      System.out.println(stack.toString() + " >> " + instruction.opcode + ":" + instruction.expr);


      switch (instruction.opcode) {
        //store即存储值语义 不会弹出栈值
        case Operator.STORE:
          if (instruction.cache == null) {
            instruction.cache = factory.createVariable(instruction.expr, stack.peek());
          }
          else {
            ((VariableResolver) instruction.cache).setValue(stack.peek());
          }
          break;
        //加载值语义,入栈
        case Operator.LOAD:
          if (instruction.cache == null) {
            instruction.cache = factory.getVariableResolver(instruction.expr);
          }
          stack.push(((VariableResolver) instruction.cache).getValue());
          break;
        //获取字段语义 弹出class,入栈字段
        case Operator.GETFIELD:
          try {
            if (stack.isEmpty() || !(stack.peek() instanceof Class)) {
              throw new CompileException("getfield without class", expr, blockStart);
            }

            Field field;
            if (instruction.cache == null) {
              instruction.cache = field = ((Class) stack.pop()).getField(instruction.expr);
            }
            else {
              stack.discard();
              field = (Field) instruction.cache;
            }

            stack.push(field.get(stack.pop()));
          }
          catch (Exception e) {
            throw new CompileException("field access error", expr, blockStart, e);
          }
          break;
        //存储字段,弹出class和相应的值,设置值之后,重新入栈
        case Operator.STOREFIELD:
          try {
            if (stack.isEmpty() || !(stack.peek() instanceof Class)) {
              throw new CompileException("storefield without class", expr, blockStart);
            }

            Class cls = (Class) stack.pop();
            Object val = stack.pop();
            cls.getField(instruction.expr).set(stack.pop(), val);
            stack.push(val);
          }
          catch (Exception e) {
            throw new CompileException("field access error", expr, blockStart, e);
          }
          break;

        //压入类型信息
        case Operator.LDTYPE:
          try {
            if (instruction.cache == null) {
              instruction.cache = ParseTools.createClass(instruction.expr, pCtx);
            }
            stack.push(instruction.cache);
          }
          catch (ClassNotFoundException e) {
            throw new CompileException("error", expr, blockStart, e);
          }
          break;

        //方法 调用 出栈参数列表+类,压入相应的处理值
        case Operator.INVOKE:
          Object[] parms;
          ExecutionStack call = new ExecutionStack();
          while (!stack.isEmpty() && !(stack.peek() instanceof Class)) {
            call.push(stack.pop());
          }
          if (stack.isEmpty()) {
            throw new CompileException("invoke without class", expr, blockStart);
          }

          parms = new Object[call.size()];
          for (int i = 0; !call.isEmpty(); i++) parms[i] = call.pop();

          if ("<init>".equals(instruction.expr)) {
            Constructor c;
            if (instruction.cache == null) {
              instruction.cache = c = ParseTools.getBestConstructorCandidate(parms, (Class) stack.pop(), false);
            }
            else {
              c = (Constructor) instruction.cache;
            }

            try {
              stack.push(c.newInstance(parms));
            }
            catch (Exception e) {
              throw new CompileException("instantiation error", expr, blockStart, e);
            }
          }
          else {
            Method m;
            if (instruction.cache == null) {
              Class cls = (Class) stack.pop();

              instruction.cache = m = ParseTools.getBestCandidate(parms, instruction.expr, cls,
                  cls.getDeclaredMethods(), false);
            }
            else {
              stack.discard();
              m = (Method) instruction.cache;
            }

            try {
              stack.push(m.invoke(stack.isEmpty() ? null : stack.pop(), parms));
            }
            catch (Exception e) {
              throw new CompileException("invokation error", expr, blockStart, e);
            }
          }
          break;
        //压入执行值
        case Operator.PUSH:
          if (instruction.cache == null) {
            instruction.cache = MVEL.eval(instruction.expr, ctx, factory);
          }
          stack.push(instruction.cache);
          break;
        //弹出值
        case Operator.POP:
          stack.pop();
          break;
        //复制顶层值
        case Operator.DUP:
          stack.dup();
          break;
        //标记指令,用于跳转时处理
        case Operator.LABEL:
          break;
        //带条件跳转
        case Operator.JUMPIF:
          if (!stack.popBoolean()) continue;

          //指令 无条件跳转
        case Operator.JUMP:
          //这里直接跳转到之前记标记的位置
          if (instruction.cache != null) {
            i1 = (Integer) instruction.cache;
          }
          else {
            //找到相应之前的标记指令,并找到相应的位置,直接跳转到相应的位置
            for (int i2 = 0; i2 < instructionList.size(); i2++) {
              Instruction ins = instructionList.get(i2);
              if (ins.opcode == Operator.LABEL &&
                  instruction.expr.equals(ins.expr)) {
                instruction.cache = i1 = i2;
                break;
              }
            }
          }
          break;
        //相等指令
        case Operator.EQUAL:
          stack.push(stack.pop().equals(stack.pop()));
          break;
        //不相等指令
        case Operator.NEQUAL:
          stack.push(!stack.pop().equals(stack.pop()));
          break;
        //栈顶操作
        case Operator.REDUCE:
          stack.op();
          break;
        //操作数交换指令
        case Operator.XSWAP:
          stack.xswap2();
          break;
        //交换值
        case Operator.SWAP:
          Object o = stack.pop();
          Object o2 = stack.pop();
          stack.push(o);
          stack.push(o2);
          break;

      }
    }

    return stack.pop();
  }

  /** 描述所支持的指令集 */
  private static class Instruction {
    int opcode;
    String expr;
    Object cache;
  }

  /** 解析指令集，对于不能解析的指令实际上会忽略掉 */
  private static Instruction parseInstruction(String s) {
    //每个指令集中间以空格进行分隔
    int split = s.indexOf(' ');

    Instruction instruction = new Instruction();

    String keyword = split == -1 ? s : s.substring(0, split);

    //进行进行指令代码赋值,如果没有匹配上,在最终处理时将会在case中忽略掉
    if (opcodes.containsKey(keyword)) {
      instruction.opcode = opcodes.get(keyword);
    }

    //指令集后面的表达式,即判定是否是单指令集,否则就认为后面的是其它参数信息
    //noinspection StringEquality
    if (keyword != s) {
      instruction.expr = s.substring(split + 1);
    }

    return instruction;
  }

  static final Map<String, Integer> opcodes = new HashMap<String, Integer>();

  static {
    opcodes.put("push", Operator.PUSH);
    opcodes.put("pop", Operator.POP);
    opcodes.put("load", Operator.LOAD);
    opcodes.put("ldtype", Operator.LDTYPE);
    opcodes.put("invoke", Operator.INVOKE);
    opcodes.put("store", Operator.STORE);
    opcodes.put("getfield", Operator.GETFIELD);
    opcodes.put("storefield", Operator.STOREFIELD);
    opcodes.put("dup", Operator.DUP);
    opcodes.put("jump", Operator.JUMP);
    opcodes.put("jumpif", Operator.JUMPIF);
    opcodes.put("label", Operator.LABEL);
    opcodes.put("eq", Operator.EQUAL);
    opcodes.put("ne", Operator.NEQUAL);
    opcodes.put("reduce", Operator.REDUCE);
    opcodes.put("xswap", Operator.XSWAP);
    opcodes.put("swap", Operator.SWAP);
  }
}
