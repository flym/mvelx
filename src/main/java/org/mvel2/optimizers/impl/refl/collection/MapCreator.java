package org.mvel2.optimizers.impl.refl.collection;

import org.mvel2.compiler.Accessor;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.optimizers.impl.refl.nodes.BaseAccessor;

import java.util.HashMap;
import java.util.Map;

/**
 * map对象创建器,用于内联的直接map创建,如下的表达式即会采用mapCreator来进行处理
 * var a = new java.util.HashMap();a = {'a':2,'b':3};return a;
 *
 * @author Christopher Brock
 */
public class MapCreator extends BaseAccessor implements Accessor {
    /** 相应的key值访问器 */
    private Accessor[] keys;
    /** 相应的value值访问器,其相应的长度与keys保持一致 */
    private Accessor[] vals;
    /** 相应map的长度信息 */
    private int size;

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        //直接依次调用相应的访问器,使用hashMap来进行构建
        Map map = new HashMap(size * 2);
        for(int i = size - 1; i != -1; i--) {
            //noinspection unchecked
            map.put(keys[i].getValue(ctx, elCtx, variableFactory), vals[i].getValue(ctx, elCtx, variableFactory));
        }
        return map;
    }

    /** 根据相应的key值访问器和相应的value访问器来构建相应的访问器 */
    public MapCreator(Accessor[] keys, Accessor[] vals) {
        super(null, null);
        this.size = (this.keys = keys).length;
        this.vals = vals;
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        // not implemented
        return null;
    }

    /** 声明类型为Map */
    public Class getKnownEgressType() {
        return Map.class;
    }

    @Override
    public boolean ctxSensitive() {
        return false;
    }
}
