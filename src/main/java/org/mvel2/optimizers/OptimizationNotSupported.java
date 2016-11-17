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
package org.mvel2.optimizers;

/**
 * 用于描述当前优化器还不能进行支持的异常信息
 * 在使用多重优化器的情况下，可以通过此异常标明当前优化器还不能处理的问题，因而进行优化器切换
 */
public class OptimizationNotSupported extends RuntimeException {

  public OptimizationNotSupported() {
    super();
  }

  public OptimizationNotSupported(String message) {
    super(message);
  }

  public OptimizationNotSupported(String message, Throwable cause) {
    super(message, cause);
  }

  public OptimizationNotSupported(Throwable cause) {
    super(cause);
  }
}
