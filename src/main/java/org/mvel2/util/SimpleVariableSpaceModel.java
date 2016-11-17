package org.mvel2.util;

import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.IndexVariableResolver;
import org.mvel2.integration.impl.IndexedVariableResolverFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

/**
 * 由VariableSpaceCompiler引用,但对方未实际使用
 * @author Mike Brock .
 */
@Deprecated
public class SimpleVariableSpaceModel extends VariableSpaceModel {
  public SimpleVariableSpaceModel(String[] varNames) {
    this.allVars = varNames;
  }

  public VariableResolverFactory createFactory(Object[] vals) {
    VariableResolver[] resolvers = new VariableResolver[allVars.length];
    for (int i = 0; i < resolvers.length; i++) {
      if (i >= vals.length) {
        resolvers[i] = new SimpleValueResolver(null);
      }
      else {
        resolvers[i] = new IndexVariableResolver(i, vals);
      }
    }

    return new IndexedVariableResolverFactory(allVars, resolvers);
  }
}
