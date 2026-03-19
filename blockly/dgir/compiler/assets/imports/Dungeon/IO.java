package Dungeon;

import Instrinsic;

public class IO {
  /**
   * Prints a string to standard output.
   *
   * @param s The string to print.
   */
  @Intrinsic("Dungeon.IO.print(java.lang.String)")
  public static native void print(String s) {
    System.out.print(s);
  }

  /**
   * Prints a string to standard output and adds a newline character.
   *
   * @param s The string to print.
   */
  @Intrinsic("Dungeon.IO.println(java.lang.String)")
  public static native void println(String s);

  /**
   * Prints a formatted string to standard output using the specified format string and arguments.
   *
   * @param format The format string, which may contain format specifiers that are replaced by the
   *     arguments.
   * @param args The arguments to be inserted into the format string.
   */
  @Intrinsic("Dungeon.IO.printf(java.lang.String, java.lang.Object...)")
  public static native void printf(String format, Object... args);

  /**
   * Reads a boolean value from standard input. The input should be "true" or "false" (case
   * insensitive).
   *
   * @return true if the input is "true", false otherwise.
   */
  @Intrinsic("Dungeon.IO.nextBoolean()")
  public static native boolean nextBoolean();

  /**
   * Reads a byte value from standard input.
   *
   * @return byte value
   */
  @Intrinsic("Dungeon.IO.nextByte()")
  public static native byte nextByte();

  /**
   * Reads a short value from standard input.
   *
   * @return short value
   */
  @Intrinsic("Dungeon.IO.nextShort()")
  public static native short nextShort();

  /**
   * Reads an integer value from standard input.
   * @return integer value
   */
  @Intrinsic("Dungeon.IO.nextInt()")
  public static native int nextInt();

  /**
   * Reads a long value from standard input.
   * @return long value
   */
  @Intrinsic("Dungeon.IO.nextLong()")
  public static native long nextLong();

  /**
   * Reads a float value from standard input.
   * @return float value
   */
  @Intrinsic("Dungeon.IO.nextFloat()")
  public static native float nextFloat();

  /**
   * Reads a double value from standard input.
   * @return double value
   */
  @Intrinsic("Dungeon.IO.nextDouble()")
  public static native double nextDouble();

  /**
   * Reads a line of text from standard input.
   * @return line of text
   */
  @Intrinsic("Dungeon.IO.nextLine()")
  public static native String nextLine();
}
