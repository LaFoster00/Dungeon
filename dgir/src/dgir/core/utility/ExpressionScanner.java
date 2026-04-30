package dgir.core.utility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared scanner for expression strings that respect quoted regions and bracket nesting ({@code
 * </>} and {@code (/)}).
 */
public final class ExpressionScanner {
  @FunctionalInterface
  public interface ScannerCharCallback {
    boolean invoke(int i, char c, int depth);
  }

  @FunctionalInterface
  public interface ScannerQuoteCallback {
    boolean invoke(int i, boolean opening, int depth);
  }

  private ExpressionScanner() {}

  /**
   * Scan the given text starting from {@code startIndex}, invoking the provided callbacks for each
   * character, quote, open bracket, and close bracket. The scanner tracks whether it is currently
   * inside a quoted region and the current nesting depth of brackets, and passes this information
   * to the callbacks. The scan terminates early if any callback returns {@code true}.
   *
   * @param text the text to scan.
   * @param startIndex the index to start scanning from.
   * @param onChar callback invoked for each character outside of quotes, with the character index,
   *     the character itself, and the current bracket depth.
   * @param onQuote callback invoked for each quote character, with the character index, a boolean
   *     indicating whether it's an opening quote, and the current bracket depth.
   * @param onOpen callback invoked for each open bracket character, with the character index, the
   *     character itself, and the new bracket depth after the open.
   * @param onClose callback invoked for each close bracket character, with the character index, the
   *     character itself, and the new bracket depth after the close.
   * @throws IllegalArgumentException if {@code startIndex} is out of bounds.
   */
  public static void scan(
      @NotNull String text,
      int startIndex,
      @Nullable ScannerCharCallback onChar,
      @Nullable ScannerQuoteCallback onQuote,
      @Nullable ScannerCharCallback onOpen,
      @Nullable ScannerCharCallback onClose) {
    int depth = 0;
    boolean inQuotes = false;

    for (int i = startIndex; i < text.length(); i++) {
      char c = text.charAt(i);

      if (inQuotes) {
        if (c == '"' && !isPrecededByOddBackslashes(text, i)) {
          inQuotes = false;
          if (onQuote != null && onQuote.invoke(i, false, depth)) return;
        }
        continue;
      }

      if (c == '"') {
        inQuotes = true;
        if (onQuote != null && onQuote.invoke(i, true, depth)) return;
        continue;
      }

      if (c == '<' || c == '(') {
        depth++;
        if (onOpen != null && onOpen.invoke(i, c, depth)) return;
        continue;
      }

      if (c == '>' || c == ')') {
        if (depth > 0) depth--;
        if (onClose != null && onClose.invoke(i, c, depth)) return;
        continue;
      }

      if (onChar != null && onChar.invoke(i, c, depth)) return;
    }
  }

  /** Counts backslashes immediately before {@code index}; returns true if the count is odd. */
  private static boolean isPrecededByOddBackslashes(@NotNull String text, int index) {
    int count = 0;
    int j = index - 1;
    while (j >= 0 && text.charAt(j) == '\\') {
      count++;
      j--;
    }
    return (count % 2) != 0;
  }
}
