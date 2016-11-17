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

package org.mvel2.conversion;

import org.mvel2.ConversionHandler;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** 数组，集合，迭代式对象转set */
public class SetCH implements ConversionHandler {
  public Object convertFrom(Object in) {
    Class type = in.getClass();
    Set newSet = new LinkedHashSet();
    if (type.isArray()) {
      newSet.addAll(Arrays.asList(((Object[]) in)));
    }
    else if (Collection.class.isAssignableFrom(type)) {
      newSet.addAll((Collection) in);
    }
    else if (Iterable.class.isAssignableFrom(type)) {
      for (Object o : (Iterable) in) {
        newSet.add(o);
      }
    }

    return newSet;
  }

  /** 支持从数组,集合,和迭代对象转set */
  public boolean canConvertFrom(Class cls) {
    return cls.isArray() || Collection.class.isAssignableFrom(cls) || Iterable.class.isAssignableFrom(cls);
  }
}
