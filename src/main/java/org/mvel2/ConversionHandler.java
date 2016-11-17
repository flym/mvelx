/**
 * MVEL 2.0
 * Copyright (C) 2007  MVFLEX/Valhalla Project and the Codehaus
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

package org.mvel2;

/**
 * 描述一个通用的数据转换处理器，可以将指定类型的数据转换为另一种类型
 * 因为在脚本处理当中，数据类型均为弱类型，不同类型的数据进行处理时，即需要进行转换之后再进行处理
 * 在转换器当中，源类型信息由<code>canConvertFrom</code>来提供
 * 目标类型则由具体的实现类来进行暗示处理
 * The conversion handler interface defines the basic interface for implementing conversion handlers in MVEL.
 *
 * @see org.mvel2.DataConversion
 */
public interface ConversionHandler {
  /**
   * 转换相应的对象至目标对象
   * Converts the passed argument to the type represented by the handler.
   *
   * @param in - the input type
   * @return - the converted type
   */
  public Object convertFrom(Object in);

  /**
   * 当前转换器是否能够转换指定类型的数据
   * This method is used to indicate to the runtime whehter or not the handler knows how to convert
   * from the specified type.
   *
   * @param cls - the source type
   * @return - true if the converter supports converting from the specified type.
   */
  public boolean canConvertFrom(Class cls);
}
