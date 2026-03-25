package dgir.core.debug;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.NotNull;

/**
 * Source location used for debug metadata.
 *
 * @param file source file path.
 * @param line 1-based line number, or {@code -1} for unknown.
 * @param column 1-based column number, or {@code -1} for unknown.
 */
public record Location(@NotNull String file, int line, int column) {
  /** Sentinel location used when no source position is available. */
  public static final Location UNKNOWN = new Location("<unknown>", -1, -1);

  /**
   * Parses a serialized location in {@code file:line:column} format.
   *
   * @param loc serialized source location.
   * @return parsed {@link Location}.
   */
  @JsonCreator
  public static @NotNull Location fromString(@NotNull String loc) {
    String[] parts = loc.split(":", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid source location format: " + loc);
    }
    String file = parts[0];
    int line = Integer.parseInt(parts[1]);
    int column = Integer.parseInt(parts[2]);
    return new Location(file, line, column);
  }

  @JsonValue
  @Override
  public @NotNull String toString() {
    return file + ":" + line + ":" + column;
  }
}
