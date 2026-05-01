package dgir.dialect.mem;

import dgir.core.ir.*;
import dgir.core.utility.DgirCoreUtils;
import dgir.dialect.builtin.BuiltinTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Sealed marker interface for all types contributed by the {@link MemoryDialect}. */
public sealed interface MemTypes {
  /** Abstract base class for all type-descriptors contributed by the {@link MemoryDialect}. */
  sealed interface MemTypeDescriptor extends TypeDescriptor {
    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return MemoryDialect.class;
    }

    final class ArrayDescriptor implements MemTypeDescriptor {
      @Contract(pure = true)
      public static @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(new ArrayDescriptor());
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return ArrayT.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "mem.array";
      }

      @Override
      public @NotNull Function<Object, Boolean> getValidator() {
        // Concrete validation depends on the element type and optional width.
        return value -> true;
      }

      @Override
      public void initDefaultTypeInstances() {}

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type>
          getParameterizedIdentFactory() {
        return args -> {
          AtomicReference<Type> elementType = new AtomicReference<>();
          AtomicReference<OptionalInt> width = new AtomicReference<>();
          Type.consumeParametricIdent(
              args.getLeft(),
              parameters -> {
                if (parameters.isEmpty() || parameters.size() > 2) {
                  return Optional.of(
                      "Invalid number of parameters for array type: expected 1 or 2, got "
                          + parameters.size());
                }
                if (!(parameters.getFirst() instanceof Type type))
                  return Optional.of(
                      "Invalid parameter type for array element type: expected a type, got "
                          + parameters.getFirst().getClass().getSimpleName());
                elementType.set(type);
                if (parameters.size() == 2 && !(parameters.get(1) instanceof Integer))
                  return Optional.of(
                      "Invalid parameter type for array width: expected an integer, got "
                          + parameters.get(1).getClass().getSimpleName());
                width.set(
                    parameters.size() == 2
                        ? OptionalInt.of((Integer) parameters.get(1))
                        : OptionalInt.empty());
                return Optional.empty();
              });

          return ArrayT.of(elementType.get(), width.get());
        };
      }
    }
  }

  /** GC-managed fixed or dynamic-width array type in the {@code mem} dialect. */
  final class ArrayT extends Type implements MemTypes {

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the parameterized identifier for this array type.
     *
     * @return the element type and optional width.
     */
    @Override
    public @NotNull String getParameterizedIdent() {
      return Type.buildParameterizedIdent(
          getDetails(),
          DgirCoreUtils.listOf(getElementType(), getWidth().isPresent() ? width : null));
    }

    // =========================================================================
    // Members
    // =========================================================================

    private final @NotNull Type elementType;
    private final int width;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Creates the canonical default array type. */
    private ArrayT() {
      super("mem.array");
      elementType = BuiltinTypes.IntegerT.INT32();
      width = -1;
    }

    /**
     * Creates an array type with the given element type and width.
     *
     * @param elementType the array element type.
     * @param width the fixed width, or {@code -1} for dynamic sizing.
     */
    private ArrayT(@NotNull Type elementType, int width) {
      super("mem.array");
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
