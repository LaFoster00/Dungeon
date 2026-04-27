package dgir.core.ir;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dgir.core.serialization.AttributeTypeIdResolver;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

import java.io.Serializable;

/**
 * Base class for all IR attributes.
 *
 * <p>An {@code Attribute} carries typed metadata attached to operations. Implementations are
 * registered in the DGIR context and resolved through {@link AttributeDetails}. The concrete
 * payload is exposed via {@link #getStorage()} for serialization and diagnostics.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "ident")
@JsonTypeIdResolver(AttributeTypeIdResolver.class)
@JsonPropertyOrder({"ident", "type"})
public abstract class Attribute implements Serializable {
  // =========================================================================
  // Members
  // =========================================================================

  private final @NotNull AttributeDetails details;

  // =========================================================================
  // Attribute Info
  // =========================================================================

  /**
   * Returns the unique ident string for this attribute kind (e.g. {@code "integerAttr"}).
   *
   * @return the ident string, never {@code null}.
   */
  @Contract(pure = true)
  public final @NotNull String getIdent() {
    return details.ident();
  }

  /**
   * Returns the namespace prefix for this attribute kind (e.g. {@code ""} for builtin attributes).
   *
   * @return the namespace string, never {@code null}.
   */
  @Contract(pure = true)
  @JsonIgnore
  public final @NotNull String getNamespace() {
    return details.namespace();
  }

  /**
   * Returns the class of the dialect that contributes this attribute kind.
   *
   * @return the dialect class, never {@code null}.
   */
  @Contract(pure = true)
  @JsonIgnore
  public final @NotNull Dialect getDialect() {
    return details.dialect();
  }

  // =========================================================================
  // Constructors
  // =========================================================================

  protected Attribute() {
    this.details =
        AttributeDetails.get(getClass())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attribute class " + getClass() + " is not registered in DGIRContext"));
  }

  private Attribute(@NotNull String ident) {
    this.details =
        AttributeDetails.get(ident)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attribute class " + ident + " is not registered in DGIRContext"));
  }

  // =========================================================================
  // Functions
  // =========================================================================

  @Contract(pure = true)
  @JsonIgnore
  public @NotNull AttributeDetails getDetails() {
    return details;
  }

  /** Return the raw storage value of this attribute (used for serialization and display). */
  @Contract(pure = true)
  @JsonIgnore
  public abstract @NotNull Object getStorage();

  // =========================================================================
  // Object
  // =========================================================================

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Attribute other
        && this.details.equals(other.details)
        && this.getStorage().equals(other.getStorage());
  }

  @Override
  public int hashCode() {
    return this.details.hashCode() + this.getStorage().hashCode();
  }

  @Override
  public String toString() {
    return getIdent() + "(" + getStorage() + ")";
  }
}
