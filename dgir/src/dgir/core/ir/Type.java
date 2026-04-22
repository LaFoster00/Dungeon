package dgir.core.ir;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import dgir.core.Dialect;
import dgir.core.serialization.TypeDeserializer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.function.Function;

/**
 * Abstract base class for all types in the IR. Types are contributed by dialects and can be either
 * simple (e.g. {@code int32}) or parameterized (e.g. {@code ptr<int32>} or {@code func<(int32) ->
 * (int32)>}). Each type must have a unique identifier (e.g. "int32" or "func.func") and a validator
 * function that checks whether a given value is a valid instance of that type. Parameterized types
 * must also provide a factory that creates instances of themselves from a parameterized identifier.
 *
 * <p>Types are immutable and should be compared by reference. To make sure a type is truly unique,
 * it should every created instance should be routet through the {@link TypeUniquer}, or be created
 * once and accessed via static fields (e.g. {@code public static final IntegerT INT32 = new
 * IntegerT(32, true);}).
 *
 * <p>In case you do have a parametric type, consider using factories that return unique instances
 * of the type, so that you can still compare by reference. For example, for a pointer type, you
 * could have a factory method like {@code public static Type ptr(Type pointee) { return
 * TypeUniquer.uniqueInstance(new PtrType(pointee)); }}. This way, you can ensure that all instances
 * of the same pointer type are the same object, and you can compare them by reference.
 *
 * <p>Either way make sure that every unique type only has one instance, shared by all uses.
 */
// We have to use the deserializer because we cant use @JsonCreator on static methods and therefore
// can put the logic
// directly in this class.
@JsonDeserialize(using = TypeDeserializer.class)
public abstract class Type {

  // =========================================================================
  // Members
  // =========================================================================

  @JsonIgnore private final @NotNull TypeDetails details;

  // =========================================================================
  // Type Info
  // =========================================================================

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
  public final @NotNull String getIdent() {
    return details.ident();
  }

  /**
   * Get the parameterized ident for this type. Simple types return just the ident; generic types
   * (e.g. {@link Type} parameterized) override this to include parameters.
   *
   * <p>Syntax:
   *
   * <pre>
   * parameterizedType:
   *    ident
   *    | ident '&lt;' typeParameter (',' typeParameter)* '&gt;'
   *    | ident '&lt;' verbatim '&gt;'
   * </pre>
   *
   * @return The parameterized ident string.
   */
  @Contract(pure = true)
  @JsonValue
  public @NotNull String getParameterizedIdent() {
    return getIdent();
  }

  /**
   * Returns the namespace prefix for this type (e.g. {@code ""} for builtin types or {@code "func"}
   * for the func dialect).
   *
   * @return the namespace string, never {@code null}.
   */
  @Contract(pure = true)
  public final @NotNull String getNamespace() {
    return details.namespace();
  }

  /**
   * Returns the class of the dialect that contributes this type.
   *
   * @return the dialect class, never {@code null}.
   */
  @Contract(pure = true)
  public final @NotNull Dialect getDialect() {
    return details.dialect();
  }

  /**
   * Returns a function that checks whether a given value is a valid instance of this type.
   *
   * <p>The validator is stored in {@link TypeDetails} at registration time and used by {@link
   * #validate(Object)} to type-check attribute storage values.
   *
   * @return the validator function, never {@code null}.
   */
  @Contract(pure = true)
  public final Function<Object, Boolean> getValidator() {
    return details.validator();
  }

  // =========================================================================
  // Constructors
  // =========================================================================

  protected Type(String ident) {
    details =
        TypeDetails.get(ident)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Type class " + ident + " is not registered in DGIRContext"));
  }

  // =========================================================================
  // Functions
  // =========================================================================

  /**
   * Returns registration details associated with this type instance.
   *
   * @return the type details.
   */
  @Contract(pure = true)
  public final @NotNull TypeDetails getDetails() {
    return details;
  }

  /**
   * Validates a storage value against this type's validator.
   *
   * @param value the value to validate.
   * @return {@code true} when {@code value} is valid for this type.
   */
  public final boolean validate(Object value) {
    return details.validator().apply(value);
  }

  // =========================================================================
  // Object
  // =========================================================================

  @Override
  public final String toString() {
    return getParameterizedIdent();
  }

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }
}
