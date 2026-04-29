package dgir.core.ir;

import dgir.dialect.builtin.BuiltinTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * A descriptor for a type, containing metadata such as its unique identifier, namespace, and
 * validation logic. This is used to register types with the {@link TypeDetails} and to provide
 * information about types at runtime.
 *
 * <h3><strong>Important:</strong> Every type descriptor which uses the allTypes reflection util
 * must implement a public static method returning all descriptors for the default available types
 * (see {@link BuiltinTypes.BuiltinTypeDescriptor.IntegerDescriptor} for an example). This is
 * necessary to ensure that all type descriptors are loaded and registered with the global {@link
 * DGIRContext}. Bellow is the exact syntax for the function.
 *
 * <p>Returns the list of type descriptors that were used to describe all instances of this type.
 * For non parameterized types such as int1, int8, etc. multiple entries will be returned while
 * parameterized types such as ptr<TYPE> will only have one entry describing the base type.
 *
 * <pre>{@code
 * @Contract(pure = true)
 * static @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors();
 * }</pre>
 *
 * </h3>
 */
public interface TypeDescriptor {

  /**
   * Returns the class of the dialect that contributes this type.
   *
   * @return the dialect class, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  Class<? extends Dialect> getDialect();

  /**
   * Get the Java class that is described by this descriptor.
   *
   * @return The Java class of the type, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  Class<? extends Type> getTypeClass();

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
   * Returns a function that checks whether a given value is a valid instance of this type.
   *
   * @return the validator function, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
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
  @NotNull
  Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type>
      getParameterizedIdentFactory();

  /**
   * Initialize all default type instances for the type described by this descriptor. This should be
   * called during dialect registration to ensure that all default instances are created and
   * available for use. For non-parameterized types, this should create the singleton instances
   * (e.g. instance behind BuiltinTypes.IntegerT.INT8()). For parameterized types, this may be a
   * no-op or it may create some commonly used default instances (e.g. a null pointer type). This is
   * necessary to ensure that the default instances are created and available for use before any
   * code tries to access them, but after type registration so that type instances can access their
   * type details without throwing.
   */
  void initDefaultTypeInstances();
}
