package org.mvel2;

/**
 * 用于在编译脚本时预先对脚本进行处理，如处理宏(内容替换)等
 * A preprocessor used for pre-processing any expressions before being parsed/compiled.
 */
public interface PreProcessor {
  /** 处理字符数组，进行翻译 */
  char[] parse(char[] input);

  /** 翻译字符串 */
  String parse(String input);
}
