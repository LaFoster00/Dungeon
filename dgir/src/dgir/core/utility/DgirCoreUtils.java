package dgir.core.utility;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Miscellaneous utility helpers used throughout the DGIR. All inner classes have private
 * constructors — they are purely namespace containers.
 */
public class DgirCoreUtils {
  /**
   * Create an unmodifiable list containing the given elements. Similar to List.of() but allows
   * {@code null} elements.
   *
   * @param elements the elements to include in the list, may be {@code null} or contain {@code
   *     null} values.
   * @return an unmodifiable list containing the given elements, never {@code null}.
   * @param <T> the element type.
   * @see List#of(Object...)
   */
  @SafeVarargs
  public static <T> @UnmodifiableView @NotNull List<@Nullable T> listOf(@Nullable T... elements) {
    return Collections.unmodifiableList(Arrays.asList(elements));
  }

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
    String indentStr = String.join("", Collections.nCopies(indent, "\t"));
    for (String line : text.lines().toList()) {
      builder.append(indentStr).append(line).append("\n");
    }
    return builder.toString();
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
