package dgir.core.ir;

import dgir.core.Dialect;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A descriptor for a type, containing metadata such as its unique identifier, namespace, and
 * validation logic. This is used to register types with the {@link TypeDetails} and to provide
 * information about types at runtime.
 */
public interface TypeDescriptor {
  /**
   * Get the Java class that is described by this descriptor.
   *
   * @return The Java class of the type, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  Class<? extends Type> getTypeClass();

  /**
   * A supplier that provides a default instance of this type for non-parameterized types. This is
   * optional because parameterized types may not have a natural default instance (e.g. a pointer
   * type without a pointee type). For non-parameterized types, this should be a supplier that
   * always returns the same instance (e.g. a singleton), so that reference equality can be used to
   * compare type instances. For parameterized types, this should be {@code null}.
   *
   * @return A supplier that provides a default instance of this type, or {@code null} if this type
   *     is parameterized.
   */
  @Contract(pure = true)
  @Nullable
  Supplier<Type> getNonParametricInstance();

  /**
   * Get the identifier for this type. This is a unique string that identifies the basic type
   * without any parameters. Example: {@code "i32"} or {@code "func.func"} (instead of {@code
   * func.func<...>}).
   *
   * <p>Syntax:
   *
   * <pre>
   * ident:
   *    namespace '.' name
   * </pre>
   *
   * @return The ident string.
   */
  @Contract(pure = true)
  @NotNull
  String getIdent();

  /**
   * Returns the namespace prefix for this type (e.g. {@code ""} for builtin types or {@code "func"}
   * for the func dialect).
   *
   * @return the namespace string, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  String getNamespace();

  /**
   * Returns the class of the dialect that contributes this type.
   *
   * @return the dialect class, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  Class<? extends Dialect> getDialect();

  /**
   * Returns a function that checks whether a given value is a valid instance of this type.
   *
   * @return the validator function, never {@code null}.
   */
  @Contract(pure = true)
  Function<Object, Boolean> getValidator();

  /**
   * Returns a factory that creates a type from a parameterized identifier. This is used for types
   * that have parameters, such as ptrs or function types. The parameterized identifier is the
   * string representation of the type, including its parameters. For example, for a pointer type,
   * the parameterized identifier could be {@code "ptr<i32>"} or {@code "ptr<ptr<f64>>"}.
   *
   * <p>The factory should parse the parameterized identifier and return the corresponding type
   * instance. For types that do not have parameters, this can simply return a factory that ignores
   * the parameterized identifier and returns the default instance of the type.
   *
   * @return A factory that creates a type from a parameterized identifier.
   */
  @Contract(pure = true)
  default Function<Pair<String, TypeDetails>, Type> getParameterizedStringFactory() {
    return args ->
        Objects.requireNonNull(
                args.getRight().nonParametricInstance(),
                "No parametric string factory supplied for parametric type "
                    + getTypeClass().getName())
            .get();
  }

  /**
   * Returns the list of type descriptors that were used to describe all instances of this type. For
   * non parameterized types such as int1, int8, etc. multiple entries will be returned while
   * parameterized types such as ptr<TYPE> will only have one entry describing the base type.
   */
  @Contract(pure = true)
  @NotNull
  @Unmodifiable
  List<TypeDescriptor> getDescriptors();
}
