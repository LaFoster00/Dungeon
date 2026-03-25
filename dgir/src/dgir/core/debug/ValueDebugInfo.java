package dgir.core.debug;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Debug metadata attached to values.
 *
 * @param location source location where the value originates.
 * @param name human-readable debug name.
 */
public record ValueDebugInfo(
    @JsonProperty("loc") @NotNull Location location, @NotNull String name) {
  /** Sentinel debug info used when value origin is unknown. */
  public static final ValueDebugInfo UNKNOWN = new ValueDebugInfo(Location.UNKNOWN, "<unknown>");
}
