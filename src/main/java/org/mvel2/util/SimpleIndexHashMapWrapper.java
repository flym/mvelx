package org.mvel2.util;

import java.util.*;

/**
 * 一个即记录相应的对象kv映射,又记录相应的下标的map
 * As most use-cases of the VariableResolverFactory's rely on Maps, this is meant to implement a simple wrapper
 * which records index positions for use by the optimizing facilities.
 * <p/>
 * This wrapper also ensures that the Map is only additive.  You cannot remove an element once it's been added.
 * While this may seem like an odd limitation, it is consistent with the language semantics. (ie. it's not possible
 * to delete a variable at runtime once it's been declared).
 *
 * @author Mike Brock
 */
public class SimpleIndexHashMapWrapper<K, V> implements Map<K, V> {
  /** 实际上无用 */
  @Deprecated
  private int indexCounter;
  /** 相应的内部访问器,基于key,value结构 */
  private final Map<K, ValueContainer<K, V>> wrappedMap;
  /** 相应的值访问器,基于下标 */
  private final ArrayList<ValueContainer<K, V>> indexBasedLookup;

  public SimpleIndexHashMapWrapper() {
    this.wrappedMap = new HashMap<K, ValueContainer<K, V>>();
    this.indexBasedLookup = new ArrayList<ValueContainer<K, V>>();
  }

  /**
   * 使用之前的结构 来创建相应的数据结构
   *
   * @param wrapper      之前的结构
   * @param allocateOnly 是否只分配结构,而不设置值,即是否将之前结构中的值copy过来
   */
  public SimpleIndexHashMapWrapper(SimpleIndexHashMapWrapper<K, V> wrapper, boolean allocateOnly) {
    this.indexBasedLookup = new ArrayList<ValueContainer<K, V>>(wrapper.indexBasedLookup.size());
    this.wrappedMap = new HashMap<K, ValueContainer<K, V>>();

    ValueContainer<K, V> vc;
    int index = 0;
    if (allocateOnly) {
      for (ValueContainer<K, V> key : wrapper.indexBasedLookup) {
        //这里的值持有器为null,即不copy相应的值
        vc = new ValueContainer<K, V>(index++, key.getKey(), null);
        indexBasedLookup.add(vc);
        wrappedMap.put(key.getKey(), vc);
      }
    }
    else {
      for (ValueContainer<K, V> key : wrapper.indexBasedLookup) {
        vc = new ValueContainer<K, V>(index++, key.getKey(), key.getValue());
        indexBasedLookup.add(vc);
        wrappedMap.put(key.getKey(), vc);
      }
    }
  }


  /** 使用已知的key值来构建起容器结构 */
  public SimpleIndexHashMapWrapper(K[] keys) {
    this.wrappedMap = new HashMap<K, ValueContainer<K, V>>(keys.length * 2);
    this.indexBasedLookup = new ArrayList<ValueContainer<K, V>>(keys.length);

    initWithKeys(keys);
  }

  public SimpleIndexHashMapWrapper(K[] keys, int initialCapacity, float load) {
    this.wrappedMap = new HashMap<K, ValueContainer<K, V>>(initialCapacity * 2, load);
    this.indexBasedLookup = new ArrayList<ValueContainer<K, V>>(initialCapacity);

    initWithKeys(keys);
  }

  /** 初始化相应的key值 */
  public void initWithKeys(K[] keys) {
    //好将相应的值持有器添加到相应的map和list中
    int index = 0;
    ValueContainer<K, V> vc;
    for (K key : keys) {
      vc = new ValueContainer<K, V>(index++, key, null);
      wrappedMap.put(key, vc);
      indexBasedLookup.add(vc);
    }
  }

  @Deprecated
  public void addKey(K key) {
    ValueContainer<K, V> vc = new ValueContainer<K, V>(indexCounter++, key, null);
    this.indexBasedLookup.add(vc);
    this.wrappedMap.put(key, vc);
  }

  @Deprecated
  public void addKey(K key, V value) {
    ValueContainer<K, V> vc = new ValueContainer<K, V>(indexCounter++, key, value);
    this.indexBasedLookup.add(vc);
    this.wrappedMap.put(key, vc);
  }

  public int size() {
    return wrappedMap.size();
  }

  public boolean isEmpty() {
    return wrappedMap.isEmpty();
  }

  public boolean containsKey(Object key) {
    return wrappedMap.containsKey(key);
  }

  public boolean containsValue(Object value) {
    return wrappedMap.containsValue(value);
  }

  public V get(Object key) {
    return wrappedMap.get(key).getValue();
  }

  /** 根据下标访问相应的值 */
  public V getByIndex(int index) {
    return indexBasedLookup.get(index).getValue();
  }

  /** 与 getByIndex 相同 */
  public K getKeyAtIndex(int index) {
    return indexBasedLookup.get(index).getKey();
  }

  /** 获取相应key的下标 */
  public int indexOf(K key) {
    return wrappedMap.get(key).getIndex();
  }

  /** 修改相应的值 */
  public V put(K key, V value) {
    ValueContainer<K, V> vc = wrappedMap.get(key);
    if (vc == null)
      throw new RuntimeException("cannot add a new entry.  you must allocate a new key with addKey() first.");

    indexBasedLookup.add(vc);//这里应该为有bug,不应该再添加相应的值持有器
    return wrappedMap.put(key, vc).getValue();
  }

  /** 在指定的位置中设置相应的值 */
  public void putAtIndex(int index, V value) {
    ValueContainer<K, V> vc = indexBasedLookup.get(index);
    vc.setValue(value);
  }

  public V remove(Object key) {
    throw new UnsupportedOperationException("cannot remove keys");
  }

  public void putAll(Map<? extends K, ? extends V> m) {
    //   wrappedMap.put
  }

  public void clear() {
    throw new UnsupportedOperationException("cannot clear map");
  }

  public Set<K> keySet() {
    return wrappedMap.keySet();
  }

  public Collection<V> values() {
    throw new UnsupportedOperationException();
  }

  public Set<Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }

  /** 一个相应的值持有器,可以理解为相应的Map.Entry */
  private class ValueContainer<K, V> {
    /** 当前的访问下标 */
    private int index;
    /** key值 */
    private K key;
    /** value值 */
    private V value;

    public ValueContainer(int index, K key, V value) {
      this.index = index;
      this.key = key;
      this.value = value;
    }

    public int getIndex() {
      return index;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    void setKey(K key) {
      this.key = key;
    }

    void setValue(V value) {
      this.value = value;
    }
  }
}
