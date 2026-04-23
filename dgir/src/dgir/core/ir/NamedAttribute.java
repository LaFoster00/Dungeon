package dgir.core.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dgir.core.serialization.NamedAttributeDeserializer;
import dgir.core.serialization.NamedAttributeSerializer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;
import java.util.Optional;

/** An {@link Attribute} paired with its name, as stored inside an {@link Operation}. */
@JsonSerialize(using = NamedAttributeSerializer.class)
@JsonDeserialize(using = NamedAttributeDeserializer.class)
public final class NamedAttribute {

  // =========================================================================
  // Members
  // =========================================================================

  private final @NotNull String name;
  private @Nullable Attribute attribute;

  // =========================================================================
  // Constructors
  // =========================================================================

  /**
   * Creates a named attribute pair with no attribute value.
   *
   * @param name attribute key.
   */
  public NamedAttribute(@NotNull String name) {
    this.name = name;
    this.attribute = null;
  }

  /**
   * Creates a named attribute pair.
   *
   * @param name attribute key.
   * @param attribute attribute value.
   */
  @JsonCreator
  public NamedAttribute(
      @JsonProperty("name") @NotNull String name,
      @JsonProperty("attribute") @Nullable Attribute attribute) {
    this.name = name;
    this.attribute = attribute;
  }

  // =========================================================================
  // Functions
  // =========================================================================

  /**
   * Returns the attribute key.
   *
   * @return attribute name.
   */
  @Contract(pure = true)
  public @NotNull String getName() {
    return name;
  }

  /**
   * Returns the attribute value, or an empty optional if no attribute was set.
   *
   * @return optional containing the stored attribute, or empty if no attribute value is set for
   *     this named attribute.
   */
  @Contract(pure = true)
  public @NotNull Optional<Attribute> getAttribute() {
    return Optional.ofNullable(attribute);
  }

  /**
   * Returns the attribute value, or throws if no attribute was set.
   *
   * @return stored attribute.
   * @throws NullPointerException if no attribute value is set for this named attribute.
   */
  @Contract(pure = true)
  public @NotNull Attribute getAttributeOrThrow() {
    return Objects.requireNonNull(
        attribute, "Attribute value is null for named attribute: " + name);
  }

  /**
   * Replace the attribute value stored in this named attribute.
   *
   * @param attribute the new attribute value; must not be {@code null}.
   */
  public void setAttribute(@NotNull Attribute attribute) {
    this.attribute = attribute;
  }
}
