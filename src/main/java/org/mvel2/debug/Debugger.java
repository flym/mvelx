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

package org.mvel2.debug;

/** 表示一个具体的调试器接口,调试器可以在执行的执行帧时探测到相应的数据信息 */
public interface Debugger {
  /** 调试状态 继续运行 */
  public static int CONTINUE = 0;
  /** 调试状态 单步运行 */
  public static int STEP = 1;
  /** 调试状态 跳出(目前与单步运行相同) */
  public static int STEP_OVER = STEP;


  /**
   * 对一个特定的执行帧进行调试,即处理指定的栈帧,并返回相应的调试状态
   * When a breakpoint is recached,
   *
   * @param frame
   * @return continuation command
   */
  public int onBreak(Frame frame);
}