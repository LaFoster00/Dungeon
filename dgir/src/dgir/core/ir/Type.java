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
 * Base class for all IR types.
 *
 * <p>Types are contributed by dialects and are either non-parameterized (for example, {@code
 * int32}) or parameterized (for example, {@code ptr<int32>} or {@code func.func<"(int32) ->
 * (int32)">}). Each type has a stable ident and a validator used to check storage values.
 *
 * <p>Type instances are treated as canonical values. Implementations should return shared instances
 * (singletons for non-parameterized types and {@link TypeUniquer}-canonicalized instances for
 * parameterized ones) so identity/reference comparisons are reliable across the IR.
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
   * <pre>{@code
   * ident:
   *    namespace '.' name
   * }</pre>
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
   * <pre>{@code
   * parameterizedType:
   *    ident
   *    | ident '<' typeParam (',' typeParam)* '>'
   *
   * typeParam:
   *    parameterizedType
   *    | quotedString
   *
   * quotedString:
   *    '"' .* '"'
   * }</pre>
   *
   * <p>Custom expressions should use the quoted form so embedded punctuation remains part of the
   * parameter value instead of being parsed structurally.
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
    return getValidator().apply(value);
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
