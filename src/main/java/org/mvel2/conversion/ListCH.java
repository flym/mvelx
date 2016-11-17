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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** 将数组，集合或可迭代的对象转换为List */
public class ListCH implements ConversionHandler {
  /** 进行转换，为避免修改原数据，这里采用的为复制式转换 */
  public Object convertFrom(Object in) {
    Class type = in.getClass();
    List newList = new ArrayList();
    //数组，直接asList即可
    if (type.isArray()) {
      newList.addAll(Arrays.asList(((Object[]) in)));
    }
    else if (Collection.class.isAssignableFrom(type)) {
      newList.addAll((Collection) in);
    }
    else if (Iterable.class.isAssignableFrom(type)) {
      for (Object o : (Iterable) in) {
        newList.add(o);
      }
    }

    return newList;
  }

  /** 支持数组,集合以及可迭代对象转集合 */
  public boolean canConvertFrom(Class cls) {
    return cls.isArray() || Collection.class.isAssignableFrom(cls) || Iterable.class.isAssignableFrom(cls);
  }
}
