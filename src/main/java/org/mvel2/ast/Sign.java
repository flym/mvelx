package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.util.ParseTools;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.mvel2.util.ParseTools.boxPrimitive;

/** 表示一个取负操作节点 */
public class Sign extends ASTNode {
  /** 表示对不同的操作数取负的取负器 */
  private Signer signer;
  /** -号后面的表达式节点 */
  private ExecutableStatement stmt;

  public Sign(char[] expr, int start, int end, int fields, ParserContext pCtx) {
    super(pCtx);
    this.expr = expr;
    this.start = start + 1;
    this.offset = end - 1;
    this.fields = fields;

    //编译模式下初始化相应的表达式以及负数处理值
    if ((fields & COMPILE_IMMEDIATE) != 0) {
      stmt = (ExecutableStatement) ParseTools.subCompileExpression(expr, this.start, this.offset, pCtx);

      egressType = stmt.getKnownEgressType();

      //如果类型已知,则尝试根据类型来进行不同的取负处理
      if (egressType != null && egressType != Object.class) {
        initSigner(egressType);
      }
    }
  }

  /** 返回相应的表达式内容 */
  public ExecutableStatement getStatement() {
    return stmt;
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //逻辑即 读取相应的结果,再进行取负即可
    return sign(stmt.getValue(ctx, thisValue, factory));
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    //相应的结果采用解释模式来运行
    return sign(MVEL.eval(expr, start, offset, thisValue, factory));
  }

  /** 执行取负操作,即根据相应的取负器来进行处理 */
  private Object sign(Object o) {
    if (o == null) return null;
    //如果之前没有初始化取负器,则尝试根据声明类型,甚至当前的结果来进行初始化
    if (signer == null) {
      //声明类型不能确定,则将声明类型认为即当前的结果类型
      if (egressType == null || egressType == Object.class) egressType = o.getClass();
      initSigner(egressType);
    }
    return signer.sign(o);
  }

  /** 初始化取负器,即根据不同的类型进行判断,最终找到不同的数字类型,再进行处理 */
  private void initSigner(Class type) {
    if (Integer.class.isAssignableFrom(type = boxPrimitive(type))) signer = new IntegerSigner();
    else if (Double.class.isAssignableFrom(type)) signer = new DoubleSigner();
    else if (Long.class.isAssignableFrom(type)) signer = new LongSigner();
    else if (Float.class.isAssignableFrom(type)) signer = new FloatSigner();
    else if (Short.class.isAssignableFrom(type)) signer = new ShortSigner();
    else if (BigInteger.class.isAssignableFrom(type)) signer = new BigIntSigner();
    else if (BigDecimal.class.isAssignableFrom(type)) signer = new BigDecSigner();
    else {
      throw new CompileException("illegal use of '-': cannot be applied to: " + type.getName(), expr, start);
    }

  }


  /** 取 -接口,根据不同的类型进行处理 */
  private interface Signer extends Serializable {
    /** 返回一个数字的 取负形式(即-x的结果) */
    public Object sign(Object o);
  }

  /** 整数取负 */
  private class IntegerSigner implements Signer {
    public Object sign(Object o) {
      return -((Integer) o);
    }
  }

  /** short值取负 */
  private class ShortSigner implements Signer {
    public Object sign(Object o) {
      return -((Short) o);
    }
  }

  /** 长整数取负 */
  private class LongSigner implements Signer {
    public Object sign(Object o) {
      return -((Long) o);
    }
  }

  /** double类型取负 */
  private class DoubleSigner implements Signer {
    public Object sign(Object o) {
      return -((Double) o);
    }
  }

  /** float类型取负 */
  private class FloatSigner implements Signer {
    public Object sign(Object o) {
      return -((Float) o);
    }
  }

  /** bigint取负 */
  private class BigIntSigner implements Signer {
    public Object sign(Object o) {
      //这里使用long来进行模拟,会丢失数字精度以及小数位,是有问题的
      //应该采用BigInteger.negate
      return new BigInteger(String.valueOf(-(((BigInteger) o).longValue())));
    }
  }

  /** bigDecimal取负 */
  private class BigDecSigner implements Signer {
    public Object sign(Object o) {
      //这里使用double来进行模拟,会丢失数字精度以及小数位,是有问题的
      //应该采用BigDecimal.negate
      return new BigDecimal(-((BigDecimal) o).doubleValue());
    }
  }


  /** 当前节点不是变量 */
  @Override
  public boolean isIdentifier() {
    return false;
  }
}



