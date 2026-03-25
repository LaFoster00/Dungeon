package dgir.dialect.str;

import dgir.core.Dialect;
import dgir.core.ir.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Function;

/**
 * Sealed marker interface for all types contributed by the {@link StrDialect}.
 *
 * <p>Every concrete type must extend {@link StrType} and implement this interface so that {@link
 * Dialect#allTypes(Class)} can discover it automatically via reflection.
 */
public sealed interface StrTypes {
  /**
   * Abstract base class for all types contributed by the {@code str} dialect.
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and {@link #getValidator()}, and must
   * implement {@link StrTypes} to be enumerated by {@link StrDialect}.
   */
  abstract class StrType extends Type {

    /**
     * Returns the namespace prefix used when printing this type.
     *
     * @return the fixed {@code "str"} namespace.
     */
    @Override
    public @NotNull String getNamespace() {
      return "str";
    }

    /**
     * Returns the dialect that owns this type.
     *
     * @return the {@link StrDialect} class.
     */
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return StrDialect.class;
    }
  }

  /**
   * UTF-16 string type in the {@code str} dialect.
   *
   * <p>Ident: {@code string}. Validated values must be Java {@link String} instances.
   *
   * <p>The single pre-built instance is available as {@link #INSTANCE}.
   */
  final class StringT extends StrType implements StrTypes {

    // =========================================================================
    // Static Fields
    // =========================================================================

    /** Singleton instance of the string type. */
    public static final StringT INSTANCE = new StringT();

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this type.
     *
     * @return the fixed identifier {@code "string"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "string";
    }

    /**
     * Validates that a value is a Java string.
     *
     * @return {@code true} if the value is a string.
     */
    @Override
    public Function<Object, Boolean> getValidator() {
      return value -> value instanceof String;
    }

    /**
     * Returns the default string type instances for this bundle.
     *
     * @return a singleton list containing {@link #INSTANCE}.
     */
    @Override
    public @NotNull @Unmodifiable List<Type> getDefaultTypeInstances() {
      return List.of(INSTANCE);
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Creates a new {@code StringT} instance. Prefer {@link #INSTANCE} over this constructor. */
    StringT() {}
  }
}
