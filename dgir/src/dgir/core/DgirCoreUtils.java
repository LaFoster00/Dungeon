package dgir.core;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Miscellaneous utility helpers used throughout the DGIR. All inner classes have private
 * constructors — they are purely namespace containers.
 */
public class DgirCoreUtils {
  /**
   * Indent each line of {@code text} by {@code indent} tab characters. Lines are determined by
   * splitting on {@code \n} (not {@code \r\n}).
   *
   * @param text the text to indent.
   * @param indent the number of tab characters to prepend to each line.
   * @return the indented text.
   */
  public static String indent(String text, int indent) {
    StringBuilder builder = new StringBuilder();
    String indentStr = String.join("", java.util.Collections.nCopies(indent, "\t"));
    for (String line : text.lines().toList()) {
      builder.append(indentStr).append(line).append("\n");
    }
    return builder.toString();
  }

  /**
   * Split {@code text} by the first occurrence of {@code delimiter} that appears at nesting depth
   * 0, where depth is tracked by counting matched pairs of {@code < >} and {@code ( )}.
   *
   * <p>This is the core primitive used by {@link #getParameterStrings(String)}. It can be reused
   * whenever a string must be split on an arbitrary delimiter sequence while respecting bracket
   * nesting — for example splitting {@code "(i32, string) -> (bool)"} on {@code "->"}.
   *
   * <p>If the delimiter does not appear at depth 0, the whole input is returned as a single-element
   * list.
   *
   * <p>Examples:
   *
   * <pre>
   *   splitAtDepthZero("(i32, string) -> (bool)", "->")
   *       → ["(i32, string) ", " (bool)"]          // raw, untrimmed
   *
   *   splitAtDepthZero("i32, string", ",")
   *       → ["i32", " string"]
   *
   *   splitAtDepthZero("func.func&lt;(string) -&gt; (bool)&gt;, i32", ",")
   *       → ["func.func&lt;(string) -&gt; (bool)&gt;", " i32"]
   * </pre>
   *
   * @param text the string to split; must not be {@code null}.
   * @param delimiter the delimiter sequence to split on; must not be {@code null} or empty.
   * @return an unmodifiable list of the parts (in order, not trimmed); never {@code null}.
   */
  @Contract(pure = true)
  public static @NotNull @Unmodifiable List<String> splitAtDepthZero(
      @NotNull String text, @NotNull String delimiter) {
    assert !delimiter.isEmpty() : "delimiter must not be empty";

    List<String> result = new ArrayList<>();
    int depth = 0;
    int start = 0;
    int i = 0;

    while (i < text.length()) {
      char c = text.charAt(i);

      // Track nesting depth
      if (c == '<' || c == '(') {
        depth++;
        i++;
        continue;
      }
      if (c == '>' || c == ')') {
        depth--;
        i++;
        continue;
      }

      // Check for delimiter match at depth 0
      if (depth == 0 && text.startsWith(delimiter, i)) {
        result.add(text.substring(start, i));
        i += delimiter.length();
        start = i;
        continue;
      }

      i++;
    }

    // Add the final segment
    result.add(text.substring(start));

    return Collections.unmodifiableList(result);
  }

  /**
   * Extract the top-level comma-separated parameter strings from a parameterized type ident.
   *
   * <p>The method strips the outermost {@code <…>} wrapper and then splits the inner text by {@code
   * ','} at nesting depth 0 via {@link #splitAtDepthZero(String, String)}. Both angle-bracket pairs
   * ({@code < >}) and parenthesis pairs ({@code ( )}) increment/decrement the depth counter, so
   * nested generic types and parenthesised signatures are never split mid-way. Each resulting
   * segment is trimmed of surrounding whitespace and empty segments are dropped.
   *
   * <p>Examples:
   *
   * <pre>
   *   "func.func&lt;(i32, string) -&gt; (bool)&gt;"
   *       → ["(i32, string) -> (bool)"]
   *
   *   "struct.struct&lt;i32, string&gt;"
   *       → ["i32", "string"]
   *
   *   "func.func&lt;(i32, func.func&lt;(string) -&gt; (bool)&gt;) -&gt; (bool)&gt;"
   *       → ["(i32, func.func&lt;(string) -&gt; (bool)&gt;) -> (bool)"]
   * </pre>
   *
   * @param parameterizedIdent a parameterized ident string that contains exactly one outermost
   *     {@code <…>} wrapper (e.g. {@code "foo<a, b<c>, d>"}).
   * @return an unmodifiable list of trimmed, non-empty parameter strings; never {@code null}.
   */
  @Contract(pure = true)
  public static @NotNull @Unmodifiable List<String> getParameterStrings(
      @NotNull String parameterizedIdent) {
    // Strip the outermost < … >
    String inner =
        parameterizedIdent.substring(
            parameterizedIdent.indexOf('<') + 1, parameterizedIdent.length() - 1);

    // Delegate to the general splitter, then trim and drop empty segments
    return splitAtDepthZero(inner, ",").stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  // =========================================================================
  // Inner: Caller
  // =========================================================================

  public static final @NotNull StackWalker STACK_WALKER =
      StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

  /**
   * Return the {@link Class} that directly called the method which invoked this utility.
   *
   * @return the calling class.
   * @throws IllegalStateException if the caller cannot be determined.
   */
  @Contract(pure = true)
  public static @NotNull Class<?> getCallingClass() {
    Optional<Class<?>> caller =
        STACK_WALKER.walk(
            stream ->
                stream
                    .skip(2) // skip getCallingClass() itself
                    .findFirst()
                    .map(StackFrame::getDeclaringClass));
    return caller.orElseThrow(() -> new IllegalStateException("Unable to determine calling class"));
  }

  @Contract(pure = true)
  public static @NotNull String getCallingMethodName() {
    Optional<String> caller =
        STACK_WALKER.walk(
            stream ->
                stream
                    .skip(3) // skip getCallingMethodName() itself
                    .findFirst()
                    .map(StackFrame::getMethodName));
    return caller.orElseThrow(
        () -> new IllegalStateException("Unable to determine calling method"));
  }

  @Contract(pure = true)
  public static @NotNull String getCallingMethodName(int depth) {
    Optional<String> caller =
        STACK_WALKER.walk(
            stream ->
                stream
                    .skip(depth + 1) // skip getCallingMethodName() itself
                    .findFirst()
                    .map(StackFrame::getMethodName));
    return caller.orElseThrow(
        () -> new IllegalStateException("Unable to determine calling method"));
  }

  /**
   * Adapt an {@link Optional} to an {@link Iterable} with zero or one element.
   *
   * <p>Useful in enhanced for-loops where a method returns an {@code Optional} and you want to
   * iterate over the value if present, or skip the loop body if empty.
   *
   * @param optional the optional value to iterate over.
   * @param <T> the element type.
   * @return an iterable yielding the value if present, or an empty iterable.
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  @Contract(pure = true)
  @NotNull
  public static <T> Iterable<T> iterate(Optional<T> optional) {
    return () -> optional.stream().iterator();
  }
}
