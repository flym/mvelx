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

import org.mvel2.ast.LineLabel;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.integration.VariableResolverFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 一个具体的调试代码行上下文 */
public class DebuggerContext {
  /** 之前注册的按调试文件分类的代码调试行注册,即先注册断点,再进行调试 */
  private Map<String, Set<Integer>> breakpoints;
  /** 当前使用的调试器 */
  private Debugger debugger;
  /** 当前调试器运行的状态 */
  private int debuggerState = 0;

  public DebuggerContext() {
    breakpoints = new HashMap<String, Set<Integer>>();
  }

  public Map<String, Set<Integer>> getBreakpoints() {
    return breakpoints;
  }

  public void setBreakpoints(Map<String, Set<Integer>> breakpoints) {
    this.breakpoints = breakpoints;
  }

  public Debugger getDebugger() {
    return debugger;
  }

  /** 设置相应的调试器 */
  public void setDebugger(Debugger debugger) {
    this.debugger = debugger;
  }

  public int getDebuggerState() {
    return debuggerState;
  }

  public void setDebuggerState(int debuggerState) {
    this.debuggerState = debuggerState;
  }

  // utility methods

  /** 对指定的源文件的某一行进行注册,以表示在该行进行调试 */
  public void registerBreakpoint(String sourceFile, int lineNumber) {
    if (!breakpoints.containsKey(sourceFile)) breakpoints.put(sourceFile, new HashSet<Integer>());
    breakpoints.get(sourceFile).add(lineNumber);
  }

  /** 移除指定源文件的某一行的调试点,即移除调试断点 */
  public void removeBreakpoint(String sourceFile, int lineNumber) {
    if (!breakpoints.containsKey(sourceFile)) return;
    breakpoints.get(sourceFile).remove(lineNumber);
  }

  /** 清除所有的调试断点 */
  public void clearAllBreakpoints() {
    breakpoints.clear();
  }

  /** 当前是否有已注册的断点 */
  public boolean hasBreakpoints() {
    return breakpoints.size() != 0;
  }

  /** 在指定注册行中是否存在断点 */
  public boolean hasBreakpoint(LineLabel label) {
    return breakpoints.containsKey(label.getSourceFile()) && breakpoints.get(label.getSourceFile()).
        contains(label.getLineNumber());
  }

  public boolean hasBreakpoint(String sourceFile, int lineNumber) {
    return breakpoints.containsKey(sourceFile) && breakpoints.get(sourceFile).contains(lineNumber);
  }

  public boolean hasDebugger() {
    return debugger != null;
  }

  /**
   * 进行实际的调试工作,在当前状态为单步递进或者是当前行有断点标记时执行
   * 即如果当前行有断点,那么执行断点调试.或者在上一个断点之后的执行为单步调试,那么到下一步时也会进行断点调试
   */
  public int checkBreak(LineLabel label, VariableResolverFactory factory, CompiledExpression expression) {
    if (debuggerState == Debugger.STEP || hasBreakpoint(label)) {
      if (debugger == null) throw new RuntimeException("no debugger registered to handle breakpoint");
      return debuggerState = debugger.onBreak(new Frame(label, factory));

    }
    return 0;
  }

}
