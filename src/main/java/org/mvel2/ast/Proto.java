package org.mvel2.ast;

import org.mvel2.CompileException;
import org.mvel2.ParserContext;
import org.mvel2.UnresolveablePropertyException;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.mvel2.integration.impl.SimpleValueResolver;
import org.mvel2.util.CallableProxy;
import org.mvel2.util.SimpleIndexHashMapWrapper;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;

/**
 * 用于描述一个原型的结构,原型具有相应name以及各个内部的属性,以用于各项数据的处理
 * 原型在处理上与function不同,function仅用于声明相应的调用函数,proto可以理解为是一个新的class
 * 注:在当前的版本中proto并不被支持,因此可以认为function及proto这块的支持均不完善
 */
public class Proto extends ASTNode {
  /** 原型的name */
  private String name;
  /** 原型内的各个属性,可以是普通属性,也可以是函数function */
  private Map<String, Receiver> receivers;
  /** 原型块的起始位置 */
  private int cursorStart;
  /** 原型块的结束位置 */
  private int cursorEnd;

  public Proto(String name, ParserContext pCtx) {
    super(pCtx);
    this.name = name;
    this.receivers = new SimpleIndexHashMapWrapper<String, Receiver>();
  }

  /** 声明函数调用处理器 */
  public Receiver declareReceiver(String name, Function function) {
    Receiver r = new Receiver(null, ReceiverType.FUNCTION, function);
    receivers.put(name, r);
    return r;
  }

  /** 声明属性调用处理器,并且赋予相应的初始化值 */
  public Receiver declareReceiver(String name, Class type, ExecutableStatement initCode) {
    Receiver r = new Receiver(null, ReceiverType.PROPERTY, initCode);
    receivers.put(name, r);
    return r;
  }

  /**
   * 使用指定的属性名,类型以及相应的初始化表达式进行处理器声明
   * 这里的initCode只是声明,并不会直接被调用,只有在运行期才会处理
   */
  public Receiver declareReceiver(String name, ReceiverType type, ExecutableStatement initCode) {
    Receiver r = new Receiver(null, type, initCode);
    receivers.put(name, r);
    return r;
  }

  /** 创建一个相应的实例信息 */
  public ProtoInstance newInstance(Object ctx, Object thisCtx, VariableResolverFactory factory) {
    return new ProtoInstance(this, ctx, thisCtx, factory);
  }

  @Override
  public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
    factory.createVariable(name, this);
    return this;
  }

  @Override
  public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
    factory.createVariable(name, this);
    return this;
  }

  /** 用于描述原型属性的封装 */
  public class Receiver implements CallableProxy {
    /** 属性的类型 */
    private ReceiverType type;
    /** 实际的属性值表示(function或value) */
    private Object receiver;
    /** 其初始值,也是一个执行块 */
    private ExecutableStatement initValue;
    /** 对实际的原型实例的引用(可能是null),即认为class可以是静态调用,也可以是instance调用 */
    private ProtoInstance instance;

    public Receiver(ProtoInstance protoInstance, ReceiverType type, Object receiver) {
      this.instance = protoInstance;
      this.type = type;
      this.receiver = receiver;
    }

    public Receiver(ProtoInstance protoInstance, ReceiverType type, ExecutableStatement stmt) {
      this.instance = protoInstance;
      this.type = type;
      this.initValue = stmt;
    }

    /** 进行实际处理器的调用 */
    public Object call(Object ctx, Object thisCtx, VariableResolverFactory factory, Object[] parms) {
      switch (type) {
        case FUNCTION:
          return ((Function) receiver).call(ctx, thisCtx, new InvokationContextFactory(factory, instance.instanceStates), parms);
        case PROPERTY:
          return receiver;
        case DEFERRED:
          throw new CompileException("unresolved prototype receiver", expr, start);
      }
      return null;
    }

    /**
     * 初始化一个实例的属性处理器
     * 即认为在定义时,相应的instance是null的,这里采用init以填充一个相对应的实例信息
     */
    public Receiver init(ProtoInstance instance, Object ctx, Object thisCtx, VariableResolverFactory factory) {
      return new Receiver(instance, type,
          type == ReceiverType.PROPERTY && initValue != null ? initValue.getValue(ctx, thisCtx, factory) :
              receiver);
    }

    public void setType(ReceiverType type) {
      this.type = type;
    }

    public void setInitValue(ExecutableStatement initValue) {
      this.initValue = initValue;
    }
  }

  public enum ReceiverType {
    DEFERRED, FUNCTION, PROPERTY
  }

  /** 对于原型的一个实例描述 */
  public class ProtoInstance implements Map<String, Receiver> {
    /** 相应的原型定义描述 */
    private Proto protoType;
    /** 当前所使用的实例scope作用域 */
    private VariableResolverFactory instanceStates;
    /** 内部各个属性相对应的处理器 */
    private SimpleIndexHashMapWrapper<String, Receiver> receivers;

    /** 创建起实例信息,并初始化相应的属性处理器,创建好相应的scope信息 */
    public ProtoInstance(Proto protoType, Object ctx, Object thisCtx, VariableResolverFactory factory) {
      this.protoType = protoType;

      receivers = new SimpleIndexHashMapWrapper<String, Receiver>();
      for (Map.Entry<String, Receiver> entry : protoType.receivers.entrySet()) {
        receivers.put(entry.getKey(), entry.getValue().init(this, ctx, thisCtx, factory));
      }

      instanceStates = new ProtoContextFactory(receivers);
    }

    @SuppressWarnings("unused")
    public Proto getProtoType() {
      return protoType;
    }

    public int size() {
      return receivers.size();
    }

    public boolean isEmpty() {
      return receivers.isEmpty();
    }

    public boolean containsKey(Object key) {
      return receivers.containsKey(key);
    }

    public boolean containsValue(Object value) {
      return receivers.containsValue(value);
    }

    public Receiver get(Object key) {
      return receivers.get(key);
    }

    public Receiver put(String key, Receiver value) {
      return receivers.put(key, value);
    }

    public Receiver remove(Object key) {
      return receivers.remove(key);
    }

    public void putAll(Map m) {
    }

    public void clear() {
    }

    public Set<String> keySet() {
      return receivers.keySet();
    }

    public Collection<Receiver> values() {
      return receivers.values();
    }

    public Set<Entry<String, Receiver>> entrySet() {
      return receivers.entrySet();
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "proto " + name;
  }

  /**
   * 用于描述一个原型的上下文变量工厂,即可认为是一个原型对象内的各项property
   * 参考js对object prototype属性的处理
   */
  public class ProtoContextFactory extends MapVariableResolverFactory {
    /** 使用下标+kv映射的map来进行数据存储 */
    private final SimpleIndexHashMapWrapper<String, VariableResolver> variableResolvers;

    @SuppressWarnings("unchecked")
    public ProtoContextFactory(SimpleIndexHashMapWrapper variables) {
      super(variables);
      variableResolvers = new SimpleIndexHashMapWrapper<String, VariableResolver>(variables, true);
    }

    @Override
    public VariableResolver createVariable(String name, Object value) {
      VariableResolver vr;

      try {
        (vr = getVariableResolver(name)).setValue(value);
        return vr;
      }
      catch (UnresolveablePropertyException e) {
        addResolver(name, vr = new ProtoResolver(variables, name)).setValue(value);
        return vr;
      }
    }

    @Override
    public VariableResolver createVariable(String name, Object value, Class<?> type) {
      VariableResolver vr;
      try {
        vr = getVariableResolver(name);
      }
      catch (UnresolveablePropertyException e) {
        vr = null;
      }

      if (vr != null && vr.getType() != null) {
        throw new CompileException("variable already defined within scope: " + vr.getType() + " " + name, expr, start);
      }
      else {
        addResolver(name, vr = new ProtoResolver(variables, name, type)).setValue(value);
        return vr;
      }
    }

    @Override
    public void setIndexedVariableNames(String[] indexedVariableNames) {
      //
    }

    @Override
    public String[] getIndexedVariableNames() {
      //
      return null;
    }

    @Override
    public VariableResolver createIndexedVariable(int index, String name, Object value, Class<?> type) {
      VariableResolver vr = this.variableResolvers != null ? this.variableResolvers.getByIndex(index) : null;
      if (vr != null && vr.getType() != null) {
        throw new CompileException("variable already defined within scope: " + vr.getType() + " " + name, expr, start);
      }
      else {
        return createIndexedVariable(variableIndexOf(name), name, value);
      }
    }

    @Override
    public VariableResolver createIndexedVariable(int index, String name, Object value) {
      VariableResolver vr = variableResolvers.getByIndex(index);

      if (vr == null) {
        vr = new SimpleValueResolver(value);
        variableResolvers.putAtIndex(index, vr);
      }
      else {
        vr.setValue(value);
      }


      return indexedVariableResolvers[index];
    }

    @Override
    public VariableResolver getIndexedVariableResolver(int index) {
      return variableResolvers.getByIndex(index);
    }

    @Override
    public VariableResolver setIndexedVariableResolver(int index, VariableResolver resolver) {
      variableResolvers.putAtIndex(index, resolver);
      return resolver;
    }

    @Override
    public int variableIndexOf(String name) {
      return variableResolvers.indexOf(name);
    }

    public VariableResolver getVariableResolver(String name) {
      VariableResolver vr = variableResolvers.get(name);
      if (vr != null) {
        return vr;
      }
      else if (variables.containsKey(name)) {
        variableResolvers.put(name, vr = new ProtoResolver(variables, name));
        return vr;
      }
      else if (nextFactory != null) {
        return nextFactory.getVariableResolver(name);
      }

      throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
    }
  }

  /**
   * 使用指定变量名+类型+外部的map存储来进行变量解析的解析器
   * 与mapVarResolver不一样的是，此解析器并没有将相应的值存储到map中，而是存储到map中的value的receiver上
   * 即此是一个定制类，其map中存储的数据为Receiver对象，相应变量的值实际上存储到Receiver对象上的receiver属性中
   */
  public class ProtoResolver implements VariableResolver {
    private String name;
    private Class<?> knownType;
    private Map<String, Object> variableMap;

    public ProtoResolver(Map<String, Object> variableMap, String name) {
      this.variableMap = variableMap;
      this.name = name;
    }

    public ProtoResolver(Map<String, Object> variableMap, String name, Class knownType) {
      this.name = name;
      this.knownType = knownType;
      this.variableMap = variableMap;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setStaticType(Class knownType) {
      this.knownType = knownType;
    }

    public String getName() {
      return name;
    }

    public Class getType() {
      return knownType;
    }

    public void setValue(Object value) {
      if (knownType != null && value != null && value.getClass() != knownType) {
        if (!canConvert(knownType, value.getClass())) {
          throw new CompileException("cannot assign " + value.getClass().getName() + " to type: "
              + knownType.getName(), expr, start);
        }
        try {
          value = convert(value, knownType);
        }
        catch (Exception e) {
          throw new CompileException("cannot convert value of " + value.getClass().getName()
              + " to: " + knownType.getName(), expr, start);
        }
      }

      ((Receiver) variableMap.get(name)).receiver = value;
    }

    public Object getValue() {
      return ((Receiver) variableMap.get(name)).receiver;
    }

    /** 无特殊标记 */
    public int getFlags() {
      return 0;
    }
  }

  /** 设置相应的定义位置 */
  public void setCursorPosition(int start, int end) {
    this.cursorStart = start;
    this.cursorEnd = end;
  }

  public int getCursorStart() {
    return cursorStart;
  }

  public int getCursorEnd() {
    return cursorEnd;
  }
}
