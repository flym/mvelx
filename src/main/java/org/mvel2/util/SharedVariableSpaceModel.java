package org.mvel2.util;

import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.IndexVariableResolver;
import org.mvel2.integration.impl.IndexedVariableResolverFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

/**
 * 此类由VariableSpaceCompiler引用,但对方类也没实际使用
 * @author Mike Brock .
 */
@Deprecated
public class SharedVariableSpaceModel extends VariableSpaceModel {
  private VariableResolver[] cachedGlobalResolvers;

  public SharedVariableSpaceModel(String[] allVars, Object[] vals) {
    this.allVars = allVars;

    cachedGlobalResolvers = new VariableResolver[vals.length];
    for (int i = 0; i < vals.length; i++) {
      cachedGlobalResolvers[i] = new IndexVariableResolver(i, vals);
    }
  }

  public VariableResolverFactory createFactory() {
    VariableResolver[] resolvers = new VariableResolver[allVars.length];
    for (int i = 0; i < resolvers.length; i++) {
      if (i >= cachedGlobalResolvers.length) {
        resolvers[i] = new SimpleValueResolver(null);
      }
      else {
        resolvers[i] = cachedGlobalResolvers[i];
      }
    }

    return new IndexedVariableResolverFactory(allVars, resolvers);
  }
}
