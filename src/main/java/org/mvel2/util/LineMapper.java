package org.mvel2.util;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * 行映射处理
 *
 * @author Mike Brock <cbrock@redhat.com>
 */
public class LineMapper {
  /** 整个表达式 */
  private char[] expr;

  /** 每一行的具体映射 */
  private ArrayList<Node> lineMapping;
  /** 有哪些行 */
  private Set<Integer> lines;

  /** 使用表达式进行构建 */
  public LineMapper(char[] expr) {
    this.expr = expr;
  }

  /** 进行正式的映射操作 */
  public LineLookup map() {
    lineMapping = new ArrayList<Node>();
    lines = new TreeSet<Integer>();

    int cursor = 0;
    int start = 0;
    int line = 1;

    //进行解析,以回车进行处理点
    for (; cursor < expr.length; cursor++) {
      switch (expr[cursor]) {
        //碰到回车,即记录当前行,同时加入相应的位置信息
        case '\n':
          lines.add(line);
          lineMapping.add(new Node(start, cursor, line++));
          start = cursor + 1;
          break;
      }
    }

    //添加最后一行
    if (cursor > start) {
      lines.add(line);
      lineMapping.add(new Node(start, cursor, line));
    }

    //返回一个自实现的行查找器
    return new LineLookup() {
      /** 通过行遍列找出相应的行位置 */
      public int getLineFromCursor(int cursor) {
        for (Node n : lineMapping) {
          if (n.isInRange(cursor)) {
            return n.getLine();
          }
        }
        return -1;
      }

      public boolean hasLine(int line) {
        return lines.contains(line);
      }
    };
  }

  public static interface LineLookup {
    public int getLineFromCursor(int cursor);

    public boolean hasLine(int line);
  }

  /** 描述每一行的位置信息 */
  private static class Node implements Comparable<Node> {
    /** 起始点(在整个文件中) */
    private int cursorStart;
    /** 结束点(在整个文件中) */
    private int cursorEnd;

    /** 当前第几行 */
    private int line;


    private Node(int cursorStart, int cursorEnd, int line) {
      this.cursorStart = cursorStart;
      this.cursorEnd = cursorEnd;
      this.line = line;
    }

    public int getLine() {
      return line;
    }

    /** 判断指定的下标是否在当前位置中,即当前位置满足下标点的要求 */
    public boolean isInRange(int cursor) {
      return cursor >= cursorStart && cursor <= cursorEnd;
    }

    public int compareTo(Node node) {
      if (node.cursorStart >= cursorEnd) {
        return 1;
      }
      else if (node.cursorEnd < cursorStart) {
        return -1;
      }
      else {
        return 0;
      }
    }
  }
}
