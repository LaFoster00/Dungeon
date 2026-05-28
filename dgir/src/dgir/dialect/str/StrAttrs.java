package dgir.dialect.str;

import dgir.core.ir.Dialect;
import dgir.core.ir.Attribute;
import dgir.core.ir.AttributeDescriptor;
import dgir.core.ir.TypedAttribute;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** Marker interface for attributes contributed by the {@link StrDialect}. */
public sealed interface StrAttrs {
  sealed interface StrAttrDescriptor extends AttributeDescriptor {
    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return StrDialect.class;
    }

    final class StringAttributeDescriptor implements StrAttrDescriptor {
      public static AttributeDescriptor defaultInstance() {
        return new StringAttributeDescriptor();
      }

      @Override
      public @NotNull Class<? extends Attribute> getAttributeClass() {
        return StringAttribute.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "str.attr";
      }
    }
  }

  /**
   * Attribute that carries a Java {@link String} value.
   *
   * <p>Ident: {@code stringAttr}. The stored value is a plain Java {@code String}.
   */
  final class StringAttribute extends TypedAttribute implements StrAttrs {
    // =========================================================================
    // Members
    // =========================================================================

    /** The string value stored by this attribute. */
    private @NotNull String value;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a default string attribute with an empty value. */
    public StringAttribute() {
      super(StrTypes.StringT.INSTANCE());
      value = "";
    }

    /**
     * Create a string attribute with the given value.
     *
     * @param value the string value to store.
     */
    public StringAttribute(@NotNull String value) {
      super(StrTypes.StringT.INSTANCE());
      this.value = value;
    }

    // =========================================================================
    // Functions
    // =========================================================================

    @Contract(pure = true)
    @Override
    public @NotNull Object getStorage() {
      return value;
    }

    /**
     * Returns the string value held by this attribute.
     *
     * @return the string value.
     */
    @Contract(pure = true)
    public @NotNull String getValue() {
      return value;
    }

    /**
     * Sets the string value of this attribute.
     *
     * @param value the new string value.
     */
    public void setValue(@NotNull String value) {
      this.value = value;
    }
  }
}
