package dgir.core.ir;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link Attribute} that carries an associated {@link Type}, allowing the stored value to be
 * type-checked at the IR level.
 */
public abstract class TypedAttribute extends Attribute {

  // =========================================================================
  // Members
  // =========================================================================

  private @NotNull Type type;

  // =========================================================================
  // Constructors
  // =========================================================================

  /**
   * Create a typed attribute associated with the given type.
   *
   * @param type the type that governs validation of the stored value.
   */
  protected TypedAttribute(@NotNull Type type) {
    this.type = type;
  }

  // =========================================================================
  // Functions
  // =========================================================================

  /**
   * Returns the type associated with this attribute.
   *
   * @return the type, never {@code null}.
   */
  @Contract(pure = true)
  public @NotNull Type getType() {
    return type;
  }

  /**
   * Sets the type associated with this attribute. This is used by the deserializer to populate the
   * type field after constructing the attribute instance.
   *
   * @param type the type to set, never {@code null}.
   */
  private void setType(@NotNull Type type) {
    this.type = type;
  }

  // =========================================================================
  // Object
  // =========================================================================

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj) && obj instanceof TypedAttribute other && type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + type.hashCode();
  }

  @Override
  public String toString() {
    return getIdent() + "(" + getStorage() + " : " + type + ")";
  }
}
