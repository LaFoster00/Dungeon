package dgir.dialect.builtin;

import dgir.core.ir.*;
import dgir.core.serialization.IntegerAttributeDeserializer;
import dgir.core.serialization.IntegerAttributeSerializer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Optional;

import static dgir.dialect.builtin.BuiltinTypes.FloatT;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.func.FuncOps.CallOp;
import static dgir.dialect.func.FuncOps.FuncOp;

/** Marker interface for builtin attributes shared across dialects. */
public sealed interface BuiltinAttrs {
  sealed interface BuiltinAttrDescriptor extends AttributeDescriptor {
    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return BuiltinDialect.class;
    }

    final class IntegerAttributeDescriptor implements BuiltinAttrDescriptor {
      public static AttributeDescriptor defaultInstance() {
        return new IntegerAttributeDescriptor();
      }

      @Override
      public @NotNull Class<? extends Attribute> getAttributeClass() {
        return IntegerAttribute.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "integerAttr";
      }
    }

    final class FloatAttributeDescriptor implements BuiltinAttrDescriptor {
      public static AttributeDescriptor defaultInstance() {
        return new FloatAttributeDescriptor();
      }

      @Override
      public @NotNull Class<? extends Attribute> getAttributeClass() {
        return FloatAttribute.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "floatAttr";
      }
    }

    final class SymbolRefAttributeDescriptor implements BuiltinAttrDescriptor {
      public static AttributeDescriptor defaultInstance() {
        return new SymbolRefAttributeDescriptor();
      }

      @Override
      public @NotNull Class<? extends Attribute> getAttributeClass() {
        return SymbolRefAttribute.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "symbolRefAttr";
      }
    }

    final class TypeAttributeDescriptor implements BuiltinAttrDescriptor {
      public static AttributeDescriptor defaultInstance() {
        return new TypeAttributeDescriptor();
      }

      @Override
      public @NotNull Class<? extends Attribute> getAttributeClass() {
        return TypeAttribute.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "typeAttr";
      }
    }
  }

  /**
   * Attribute that carries an integer value together with its {@link IntegerT} type.
   *
   * <p>Ident: {@code integerAttr}. The stored value is always the narrowest Java numeric type that
   * matches the integer width — e.g. {@link Integer} for {@link IntegerT#INT32}.
   */
  @JsonSerialize(using = IntegerAttributeSerializer.class)
  @JsonDeserialize(using = IntegerAttributeDeserializer.class)
  final class IntegerAttribute extends TypedAttribute implements BuiltinAttrs {
    // =========================================================================
    // Members
    // =========================================================================

    /** The integer value stored by this attribute. */
    private @NotNull Number value;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a default integer attribute (value {@code null}, type {@link IntegerT#INT64}). */
    public IntegerAttribute() {
      super(IntegerT.INT64());
      value = 0L;
    }

    /**
     * Create an integer attribute with the given value and the default type {@link IntegerT#INT64}.
     *
     * @param value the integer value.
     */
    public IntegerAttribute(long value) {
      super(IntegerT.INT64());
      this.value = ((IntegerT) getType()).convertToValidNumber(value);
    }

    /**
     * Create an integer attribute with an explicit value and type
     *
     * @param value the integer value; will be converted to the correct Java type via {@link
     *     IntegerT#convertToValidNumber(long)}.
     * @param type the integer type that determines the bit-width.
     */
    public IntegerAttribute(long value, IntegerT type) {
      super(type);
      this.value = ((IntegerT) getType()).convertToValidNumber(value);
    }

    // =========================================================================
    // Functions
    // =========================================================================

    @Contract(pure = true)
    @Override
    public @NotNull Number getStorage() {
      return getValue();
    }

    /**
     * Returns the integer value held by this attribute.
     *
     * @return the numeric value.
     */
    @Contract(pure = true)
    public @NotNull Number getValue() {
      return value;
    }

    /**
     * Sets the integer value of this attribute. The provided value will be converted to the correct
     * Java type based on the attribute's {@link IntegerT} type.
     *
     * @param value the new integer value.
     */
    public void setValue(long value) {
      this.value = ((IntegerT) getType()).convertToValidNumber(value);
    }

    public void setValue(boolean value) {
      this.value = ((IntegerT) getType()).convertToValidNumber(value ? 1L : 0L);
    }
  }

  /** Attribute that carries a floating-point value together with its {@link FloatT} type. */
  final class FloatAttribute extends TypedAttribute implements BuiltinAttrs {
    private @NotNull Number value;

    public FloatAttribute() {
      super(FloatT.FLOAT64());
      value = 0.0;
    }

    public FloatAttribute(@NotNull Number value) {
      super(FloatT.FLOAT64());
      this.value = ((FloatT) getType()).convertToValidNumber(value);
    }

    public FloatAttribute(@NotNull Number value, @NotNull FloatT type) {
      super(type);
      this.value = ((FloatT) getType()).convertToValidNumber(value);
    }

    @Contract(pure = true)
    @Override
    public @NotNull Number getStorage() {
      return value;
    }

    @Contract(pure = true)
    public @NotNull Number getValue() {
      return value;
    }

    public void setValue(@NotNull Number value) {
      this.value = ((FloatT) getType()).convertToValidNumber(value);
    }
  }

  /**
   * Attribute that holds a reference to a symbol by its string name.
   *
   * <p>Ident: {@code symbolRefAttr}. Used by operations such as {@link CallOp} to record the name
   * of a callee function without hard-linking the IR nodes together.
   */
  final class SymbolRefAttribute extends Attribute implements BuiltinAttrs {
    // =========================================================================
    // Members
    // =========================================================================

    /** The referenced symbol name. */
    private @NotNull String value;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a default symbol reference attribute with an empty symbol name. */
    public SymbolRefAttribute() {
      value = "";
    }

    /**
     * Create a symbol reference attribute pointing to the given name.
     *
     * @param value the symbol name to reference.
     */
    public SymbolRefAttribute(@NotNull String value) {
      this.value = value;
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the referenced symbol name.
     *
     * @return the symbol name.
     */
    @Contract(pure = true)
    @Override
    public @NotNull String getStorage() {
      return value;
    }

    /**
     * Returns the referenced symbol name.
     *
     * @return the symbol name.
     */
    @Contract(pure = true)
    public @NotNull String getValue() {
      return value;
    }

    public void setValue(@NotNull String value) {
      this.value = value;
    }
  }

  /**
   * Attribute that wraps a {@link Type} instance as an IR attribute.
   *
   * <p>Ident: {@code typeAttr}. Used by operations such as {@link FuncOp} to embed the full
   * function type into the operation's attribute dictionary.
   */
  final class TypeAttribute extends Attribute implements BuiltinAttrs {
    // =========================================================================
    // Members
    // =========================================================================

    /** The wrapped type. */
    private @NotNull Type type;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a default type attribute initialized to {@link IntegerT#INT64()}. */
    public TypeAttribute() {
      type = IntegerT.INT64();
    }

    /**
     * Create a type attribute wrapping the given type.
     *
     * @param type the type to wrap.
     */
    public TypeAttribute(@NotNull Type type) {
      this.type = type;
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the wrapped type.
     *
     * @return the wrapped type.
     */
    @Contract(pure = true)
    @Override
    public @NotNull Type getStorage() {
      return type;
    }

    /**
     * Returns the wrapped type as an {@link Optional}.
     *
     * @return an optional containing the wrapped type, or empty if unset.
     */
    @Contract(pure = true)
    public @NotNull Type getType() {
      return type;
    }

    /** Sets the wrapped type. */
    public void setType(@NotNull Type type) {
      this.type = type;
    }
  }
}
