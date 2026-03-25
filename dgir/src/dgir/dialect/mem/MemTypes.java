package dgir.dialect.mem;

import dgir.core.DgirCoreUtils;
import dgir.core.Dialect;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDetails;
import dgir.core.ir.TypeUniquer;
import dgir.dialect.builtin.BuiltinTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Sealed marker interface for all types contributed by the {@link MemoryDialect}.
 *
 * <p>Every concrete type must extend {@link MemType} and implement this interface so that {@link
 * Dialect#allTypes(Class)} can discover it automatically via reflection.
 */
public sealed interface MemTypes {
  /**
   * Abstract base class for all types contributed by the {@code mem} dialect.
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and {@link #getValidator()}, and must
   * implement {@link MemTypes} to be enumerated by {@link MemoryDialect}.
   */
  abstract class MemType extends Type {
    /**
     * Returns the namespace prefix used when printing this type.
     *
     * @return the fixed {@code "mem"} namespace.
     */
    @Override
    public @NotNull String getNamespace() {
      return "mem";
    }

    /**
     * Returns the dialect that owns this type.
     *
     * @return the {@link MemoryDialect} class.
     */
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return MemoryDialect.class;
    }
  }

  /** GC-managed fixed or dynamic-width array type in the {@code mem} dialect. */
  final class ArrayT extends MemType implements MemTypes {

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this type.
     *
     * @return the fixed identifier {@code "mem.array"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.array";
    }

    /**
     * Validates array storage against this array type.
     *
     * @return {@code true} if the value is a compatible object array.
     */
    @Override
    public @NotNull Function<Object, Boolean> getValidator() {
      return value -> {
        if (!(value instanceof Object[] objects)) return false;
        if (width != -1 && objects.length != width) return false;
        for (Object object : objects) {
          if (object != null && !elementType.getValidator().apply(object)) return false;
        }
        return true;
      };
    }

    /**
     * Returns the default array type instances for this bundle.
     *
     * @return an empty list because array types are parameterized.
     */
    @Override
    public @NotNull @Unmodifiable List<Type> getDefaultTypeInstances() {
      return List.of();
    }

    /**
     * Returns the parameterized identifier for this array type.
     *
     * @return the element type and optional width.
     */
    @Override
    public @NotNull String getParameterizedIdent() {
      return "mem.array<"
          + elementType.getParameterizedIdent()
          + (width != -1 ? ", " + width : "")
          + ">";
    }

    /**
     * Creates a parser for the parameterized array type syntax.
     *
     * @return a factory that parses the element type and optional width.
     */
    @Override
    public Function<Pair<String, TypeDetails>, Type> getParameterizedStringFactory() {
      return args -> {
        List<String> params = DgirCoreUtils.getParameterStrings(args.getLeft());
        if (params.isEmpty() || params.size() > 2) {
          throw new IllegalArgumentException("Invalid number of parameters for array type");
        }
        Type elementType = TypeDetails.fromParameterizedIdent(params.getFirst());
        OptionalInt width =
            params.size() == 2
                ? OptionalInt.of(Integer.parseInt(params.get(1)))
                : OptionalInt.empty();
        return ArrayT.of(elementType, width);
      };
    }

    private final @NotNull Type elementType;
    private final int width;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Creates the canonical default array type. */
    private ArrayT() {
      elementType = BuiltinTypes.IntegerT.INT32;
      width = -1;
    }

    /**
     * Creates an array type with the given element type and width.
     *
     * @param elementType the array element type.
     * @param width the fixed width, or {@code -1} for dynamic sizing.
     */
    private ArrayT(@NotNull Type elementType, int width) {
      this.elementType = elementType;
      this.width = width;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Returns a canonical array type for the given element type and optional width.
     *
     * @param elementType the array element type.
     * @param width the optional width.
     * @return the canonicalized array type.
     */
    public static @NotNull ArrayT of(@NotNull Type elementType, @NotNull OptionalInt width) {
      return TypeUniquer.uniqueInstance(new ArrayT(elementType, width.orElse(-1)));
    }

    /**
     * Returns a copy of this array type with a new size.
     *
     * @param size the new size.
     * @return a canonicalized array type with the same element type.
     */
    public @NotNull ArrayT withSize(@NotNull OptionalInt size) {
      return ArrayT.of(elementType, size);
    }

    /**
     * Returns a copy of this array type with a new element type.
     *
     * @param elementType the new element type.
     * @return a canonicalized array type with the same width.
     */
    public @NotNull ArrayT withElementType(@NotNull Type elementType) {
      return ArrayT.of(elementType, getWidth());
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the element type of this array.
     *
     * @return the array element type.
     */
    public @NotNull Type getElementType() {
      return elementType;
    }

    /**
     * Returns the fixed width of this array, if present.
     *
     * @return the width or an empty optional for dynamically sized arrays.
     */
    public @NotNull OptionalInt getWidth() {
      return width == -1 ? OptionalInt.empty() : OptionalInt.of(width);
    }
  }
}
