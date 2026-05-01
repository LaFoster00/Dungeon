package dgir.core.ir;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import dgir.core.serialization.TypeDeserializer;
import dgir.core.utility.ExpressionScanner;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
   *    | integer
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

  // =========================================================================
  // Static Helpers
  // =========================================================================

  /**
   * Build a parameterized ident string from the given type details and parameters. The parameters
   * are converted to strings according to the syntax rules defined in {@link
   * #getParameterizedIdent()}. {@code String} parameters are escaped and stored as custom
   * expressions. {@code null} parameters are allowed and will be skipped in the resulting ident
   * string, but the list itself must not be {@code null}.
   *
   * @param typeDetails the type details of the base type.
   * @param parameters the list of parameters to include in the ident; entries may be {@code null}
   *     but not the list itself. Parameters may be {@code Type}, {@code String}, or {@code Integer}
   *     instances; other types are not supported.
   * @return the constructed parameterized ident string.
   */
  public static @NotNull String buildParameterizedIdent(
      @NotNull TypeDetails typeDetails, @NotNull List<? extends @Nullable Object> parameters) {
    return typeDetails.ident() + "<" + buildParameterList(parameters) + ">";
  }

  /**
   * Build the parameter list portion of a parameterized ident string from the given parameters.
   * Each parameter is converted to a string according to the syntax rules defined in {@link
   * #getParameterizedIdent()}. {@code String} parameters are escaped and stored as custom
   * expressions. {@code null} parameters are allowed and will be skipped in the resulting list, but
   * the list itself must not be {@code null}.
   *
   * @param parameters the list of parameters to include in the ident; entries may be {@code null}
   *     but not the list itself. Parameters may be {@code Type}, {@code String}, or {@code Integer}
   *     instances; other types are not supported.
   * @return the constructed parameter list string (e.g. {@code "int32, func.func<...>,
   *     \"custom\""}).
   */
  public static @NotNull String buildParameterList(
      @NotNull List<? extends @Nullable Object> parameters) {
    List<String> typeParameters =
        parameters.stream()
            .map(
                param ->
                    switch (param) {
                      case Type t -> t.toString();
                      case String s -> quoteCustomExpression(s);
                      case Integer n -> n.toString();
                      case null -> null;
                      default ->
                          throw new IllegalArgumentException(
                              "Unsupported parameter type: " + param.getClass().getName());
                    })
            .filter(Objects::nonNull)
            .toList();
    return String.join(", ", typeParameters);
  }

  /**
   * Create a Type instance from the provided parameterized ident. Works for both simple and
   * generic/complex types (e.g. {@code func.func<...>}).
   *
   * <p>Examples:
   *
   * <pre>{@literal
   *   int32
   *   float64
   *   func.func<"(int32, string) -> (bool)">
   *   func.func<"(func.func<\"(int32) -> (bool)\">) -> ()">
   * }</pre>
   *
   * @param parameterizedIdent The parameterized ident string.
   * @return The created Type instance.
   */
  @Contract(pure = true)
  public static @NotNull Type fromParameterizedIdent(@NotNull String parameterizedIdent) {
    String normalizedIdent = parameterizedIdent.trim();
    if (normalizedIdent.isEmpty()
        || normalizedIdent.charAt(0) == '"'
        || Character.isDigit(normalizedIdent.charAt(0))) {
      throw new IllegalArgumentException(
          "Parameterized ident must not be empty, a custom expression or number: "
              + parameterizedIdent);
    }
    String baseIdent = extractBaseIdent(normalizedIdent);
    return TypeDetails.get(baseIdent)
        .map(
            typeDetails ->
                typeDetails
                    .parameterizedIdentFactory()
                    .apply(Pair.of(parameterizedIdent, typeDetails)))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Cannot create type from parameterized ident with unregistered base type: "
                        + parameterizedIdent));
  }

  /**
   * Extract the base ident from a parameterized ident string. If the input string contains a '<'
   * character, the base ident is the substring before the first '<', trimmed of whitespace. If
   * there is no '<' character, the entire input string is treated as the base ident. The method
   * also validates the generic part of the ident (if present) by calling {@link
   * #extractParameterText(String)}, which will throw an IllegalArgumentException if the generic
   * syntax is malformed. This ensures that the returned base ident is always valid and that any
   * issues with the generic part are caught early.
   *
   * @param normalizedIdent a parameterized ident string that may contain generic parameters (e.g.
   *     {@code "foo<a, b<c>, d>"}); must not be {@code null} or empty
   * @return the base ident string (e.g. {@code "foo"}); never {@code null}.
   * @throws IllegalArgumentException if the generic part of the input string is malformed (e.g.
   *     unbalanced angle brackets, unexpected trailing content, or empty parameter list).
   */
  private static @NotNull String extractBaseIdent(@NotNull String normalizedIdent) {
    int genericStart = normalizedIdent.indexOf('<');
    if (genericStart == -1) {
      return normalizedIdent;
    }
    extractParameterText(normalizedIdent); // Validate the generic part and throw if malformed
    return normalizedIdent.substring(0, genericStart).trim();
  }

  /**
   * Extract the parameter text from a parameterized ident, validating the syntax in the process.
   * The method locates the first '<' character to identify the start of the parameter list, then
   * finds the matching '>' character while correctly handling nested angle brackets and quoted
   * strings. It also checks that there is no trailing content after the closing '>' and that the
   * parameter list is not empty. If any of these conditions are violated, an
   * IllegalArgumentException is thrown with a descriptive message.
   *
   * @param parameterizedIdent a parameterized ident string that contains exactly one outermost
   *     {@code <…>} wrapper (e.g. {@code "foo<a, b<c>, d>"}
   * @return the raw parameter text inside the angle brackets (e.g. {@code "a, b<c>, d"}); never
   *     {@code null}.
   * @throws IllegalArgumentException if the input string is malformed (e.g. missing angle brackets,
   *     unbalanced brackets, unexpected trailing content, or empty parameter list).
   */
  @Contract(pure = true)
  private static @NotNull String extractParameterText(@NotNull String parameterizedIdent) {
    String normalizedIdent = parameterizedIdent.trim();
    if (normalizedIdent.isEmpty()
        || normalizedIdent.charAt(0) == '"'
        || Character.isDigit(normalizedIdent.charAt(0))) {
      throw new IllegalArgumentException(
          "Parameterized ident must not be empty, a custom expression or number: "
              + parameterizedIdent);
    }

    int genericStart = normalizedIdent.indexOf('<');
    if (genericStart == -1) {
      throw new IllegalArgumentException(
          "Malformed parameterized ident (missing parameters): " + normalizedIdent);
    }
    int genericEnd = findMatchingGenericEnd(normalizedIdent, genericStart);
    if (genericEnd != normalizedIdent.length() - 1) {
      throw new IllegalArgumentException(
          "Malformed parameterized ident (unexpected trailing content): " + normalizedIdent);
    }
    String parameterText = normalizedIdent.substring(genericStart + 1, genericEnd);
    if (parameterText.isBlank()) {
      throw new IllegalArgumentException(
          "Malformed parameterized ident (empty parameter list): " + normalizedIdent);
    }
    return parameterText;
  }

  /**
   * Extract the top-level comma-separated parameter strings from a parameterized type ident.
   *
   * <p>The method strips the outermost {@code <…>} wrapper and then splits the inner text by {@code
   * ','} at nesting depth 0 via {@link #splitAtDepth}. Both angle-bracket pairs ({@code < >}) and
   * parenthesis pairs ({@code ( )}) increment/decrement the depth counter, so nested generic types
   * and parenthesised signatures are never split mid-way. Quoted custom expressions are unquoted
   * and unescaped. Each resulting segment is trimmed of surrounding whitespace, and empty segments
   * are rejected.
   *
   * <p>Examples:
   *
   * <pre>
   *   "struct.struct&lt;i32, string&gt;"
   *       → ["i32", "string"]
   * </pre>
   *
   * @param parameterizedIdent a parameterized ident string that contains exactly one outermost
   *     {@code <…>} wrapper (e.g. {@code "foo<a, b<c>, d>"}).
   * @return an unmodifiable list of trimmed, non-empty parameter strings; never {@code null}.
   */
  @Contract(pure = true)
  public static @NotNull @Unmodifiable List<String> extractParameterStrings(
      @NotNull String parameterizedIdent) {
    String normalizedIdent = parameterizedIdent.trim();
    if (normalizedIdent.isEmpty()
        || normalizedIdent.charAt(0) == '"'
        || Character.isDigit(normalizedIdent.charAt(0))) {
      throw new IllegalArgumentException(
          "Parameterized ident must not be empty, a custom expression or number: "
              + parameterizedIdent);
    }

    String inner = extractParameterText(normalizedIdent);
    // Delegate to the general splitter, then trim and reject empty segments
    return splitAtDepth(inner, ",", 0, true).stream()
        .map(
            s -> {
              if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                return unquoteCustomExpression(s);
              }
              return s;
            })
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  /**
   * A consumer interface for validating and using parameters parsed from a parameterized ident
   * string. The {@code consume} method takes a list of parsed parameters (which may be of mixed
   * types such as {@code Type}, {@code String}, and {@code Integer}) and returns an optional error.
   */
  @FunctionalInterface
  public interface ParameterConsumer {
    Optional<String> consume(@NotNull List<? super Object> parameters);
  }

  /**
   * A simple implementation of {@link ParameterConsumer} that expects all parameters to be types.
   *
   * @param target the list to which the parsed Type parameters will be added; must not be {@code
   *     null}.
   */
  public record AllTypes(@NotNull List<Type> target) implements ParameterConsumer {
    public static @NotNull AllTypes of(@NotNull List<Type> target) {
      return new AllTypes(target);
    }

    @Override
    public Optional<String> consume(@NotNull List<? super Object> parameters) {
      for (Object parameter : parameters) {
        if (!(parameter instanceof Type type)) {
          return Optional.of(
              "Invalid parameter type: expected a type, got "
                  + parameter.getClass().getSimpleName());
        }
        target.add(type);
      }
      return Optional.empty();
    }
  }

  /**
   * A simple implementation of {@link ParameterConsumer} that expects all parameters to be strings
   * (custom expressions).
   *
   * @param target the list to which the parsed String parameters will be added; must not be {@code
   *     null}.
   */
  public record AllExpressions(@NotNull List<String> target) implements ParameterConsumer {
    public static @NotNull AllExpressions of(@NotNull List<String> target) {
      return new AllExpressions(target);
    }

    @Override
    public Optional<String> consume(@NotNull List<? super Object> parameters) {
      for (Object parameter : parameters) {
        if (!(parameter instanceof String s)) {
          return Optional.of(
              "Invalid parameter type: expected a string, got "
                  + parameter.getClass().getSimpleName());
        }
        target.add(s);
      }
      return Optional.empty();
    }
  }

  /**
   * A simple implementation of {@link ParameterConsumer} that expects all parameters to be
   * integers.
   *
   * @param target the list to which the parsed Integer parameters will be added; must not be {@code
   *     null}.
   */
  public record AllIntegers(@NotNull List<Integer> target) implements ParameterConsumer {
    public static @NotNull AllIntegers of(@NotNull List<Integer> target) {
      return new AllIntegers(target);
    }

    @Override
    public Optional<String> consume(@NotNull List<? super Object> parameters) {
      for (Object parameter : parameters) {
        if (!(parameter instanceof Integer n)) {
          return Optional.of(
              "Invalid parameter type: expected an integer, got "
                  + parameter.getClass().getSimpleName());
        }
        target.add(n);
      }
      return Optional.empty();
    }
  }

  /**
   * Parse the parameters from a parameterized ident string and pass them to the given consumer for
   * validation and use. This is a convenience method that combines {@link
   * #extractParameterText(String)} and {@link #consumeParameterText(String, ParameterConsumer)}
   * into a single step for cases where the full parameterized ident string is already available and
   * we just want to extract and consume the parameters.
   *
   * @param parametricIdent the full parameterized ident string (e.g. {@code "foo<a, b<c>, d>"});
   *     must not be {@code null}.
   * @param parameterConsumer the consumer that will receive the parsed parameters for validation
   *     and use; must not be {@code null}.
   */
  public static void consumeParametricIdent(
      @NotNull String parametricIdent, @NotNull ParameterConsumer parameterConsumer) {
    consumeParameterText(extractParameterText(parametricIdent), parameterConsumer);
  }

  /**
   * Parse a comma-separated list of (possibly nested/parameterized) type strings into a list of
   * Type instances, expression strings and numbers.
   *
   * <p>Splitting is performed by {@link #splitAtDepth(String, String, int, boolean)} so that commas
   * inside nested angle-bracket ({@code < >}) or parenthesis ({@code ( )}) groups are never treated
   * as separators.
   *
   * <p>Examples:
   *
   * <pre>{@literal
   *   int32, float64
   *   func.func<"(int32, string) -> (bool)">, float64
   *   func.func<"(int32) -> (bool)">, string
   * }</pre>
   *
   * @param parameterText The comma-separated parameter string (may be empty).
   * @param parameterConsumer A consumer that validates the parsed parameters and uses them. Returns
   *     an optional error message if validation fails.
   * @throws IllegalArgumentException if the parameter string is malformed (e.g. empty parameters,
   *     invalid integers, unbalanced brackets) or if validation fails.
   */
  @Contract(pure = true)
  public static void consumeParameterText(
      @NotNull String parameterText, @NotNull ParameterConsumer parameterConsumer) {
    List<? super @NotNull Object> parameters = new ArrayList<>();

    if (!parameterText.isBlank())
      for (String parameter : splitAtDepth(parameterText, ",", 0, true)) {
        if (parameter.isEmpty()) {
          throw new IllegalArgumentException(
              "Malformed parameter string (empty parameter): " + parameterText);
        }
        // Check if this is a custom expression
        if (parameter.startsWith("\"") && parameter.endsWith("\"")) {
          String rawExpression = parameter.substring(1, parameter.length() - 1);
          parameters.add(unescapeCustomExpression(rawExpression));
          continue;
        }
        // Check if this is a Integer
        if (Character.isDigit(parameter.charAt(0))) {
          try {
            parameters.add(Integer.parseInt(parameter));
            continue;
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Malformed parameter string (invalid Integer): " + parameter, e);
          }
        }

        // Otherwise, treat this as a nested parameterized ident and parse it recursively as a Type.
        parameters.add(fromParameterizedIdent(parameter));
      }

    var result = parameterConsumer.consume(parameters);
    if (result.isPresent()) {
      throw new IllegalArgumentException(
          "Parameter validation failed for parameters: %s; reason: %s"
              .formatted(parameters, result.get()));
    }
  }

  /**
   * Quote a custom expression string by escaping special characters and wrapping it in double
   * quotes.
   *
   * @param expression the raw custom expression to quote.
   * @return the quoted expression string suitable for inclusion in a parameterized ident.
   */
  @Contract(pure = true)
  public static @NotNull String quoteCustomExpression(@NotNull String expression) {
    return "\"" + escapeCustomExpression(expression) + "\"";
  }

  /**
   * Escape a custom expression string by replacing backslashes and double quotes with their escaped
   * forms.
   *
   * @param value the raw custom expression.
   * @return the escaped expression string.
   */
  @Contract(pure = true)
  public static @NotNull String escapeCustomExpression(@NotNull String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * Unquote a custom expression string by removing the surrounding double quotes and unescaping
   * special characters.
   *
   * @param value the quoted custom expression string (must start and end with double quotes).
   * @return the unquoted raw custom expression.
   * @throws IllegalArgumentException if the input string is not properly quoted or contains
   *     malformed escapes
   */
  @Contract(pure = true)
  public static @NotNull String unquoteCustomExpression(@NotNull String value) {
    return unescapeCustomExpression(value.substring(1, value.length() - 1));
  }

  /**
   * Unescape a custom expression string by replacing escaped backslashes and double quotes with
   * their literal forms. This method assumes that the input string has already been stripped of its
   * surrounding double quotes.
   *
   * @param value the escaped custom expression string (must not contain unescaped backslashes or
   *     double quotes).
   * @return the unescaped raw custom expression.
   * @throws IllegalArgumentException if the input string contains malformed escapes (e.g. a
   *     backslash at the end of the string or an unescaped double quote).
   */
  @Contract(pure = true)
  public static @NotNull String unescapeCustomExpression(@NotNull String value) {
    StringBuilder builder = new StringBuilder(value.length());
    boolean escaping = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaping) {
        builder.append(c);
        escaping = false;
      } else if (c == '\\') {
        escaping = true;
      } else {
        builder.append(c);
      }
    }
    if (escaping) {
      throw new IllegalArgumentException("Malformed escaped custom expression: " + value);
    }
    return builder.toString();
  }

  @Contract(pure = true)
  private static int findMatchingGenericEnd(@NotNull String text, int genericStart) {
    AtomicInteger result = new AtomicInteger(-1);
    ExpressionScanner.scan(
        text,
        genericStart,
        null,
        null,
        null,
        // OnClose
        (int i, char c, int newDepth) -> {
          if (c == '>' && newDepth == 0) {
            result.set(i);
            return true; // stop scanning
          }
          return false;
        });

    if (result.get() == -1) {
      throw new IllegalArgumentException(
          "Malformed parameterized ident (unbalanced angle brackets): " + text);
    }
    return result.get();
  }

  /**
   * Split {@code text} by the first occurrence of {@code delimiter} that appears at nesting depth
   * 0, where depth is tracked by counting matched pairs of {@code < >} and {@code ( )}. Quoted
   * substrings (delimited by {@code "..."}) are treated as atomic and may contain delimiters or
   * nested bracket characters without affecting the split.
   *
   * <p>This is the core primitive used by {@link #extractParameterStrings(String)}. It can be
   * reused whenever a string must be split on an arbitrary delimiter sequence while respecting
   * bracket nesting.
   *
   * <p>If the delimiter does not appear at depth 0, the whole input is returned as a single-element
   * list.
   *
   * <p>Examples:
   *
   * <pre>
   *   splitAtDepthZero("i32, string", ",")
   *       → ["i32", " string"]
   * </pre>
   *
   * @param text the string to split; must not be {@code null}.
   * @param delimiter the delimiter sequence to split on; must not be {@code null} or empty.
   * @return an unmodifiable list of the parts (in order, not trimmed); never {@code null}.
   */
  @Contract(pure = true)
  public static @NotNull @Unmodifiable List<String> splitAtDepth(
      @NotNull String text, @NotNull String delimiter, final int depth, boolean tim) {
    assert !delimiter.isEmpty() : "delimiter must not be empty";
    List<String> result = new ArrayList<>();
    AtomicInteger start = new AtomicInteger(0);

    ExpressionScanner.scan(
        text,
        0,
        // OnChar
        (int i, char c, int d) -> {
          if (d == depth && text.startsWith(delimiter, i)) {
            result.add(text.substring(start.get(), i));
            start.set(i + delimiter.length());
          }
          return false;
        },
        null,
        null,
        null);

    // Add the final element which was not split by the delimiter.
    result.add(text.substring(start.get()));
    if (tim) result.replaceAll(String::trim);
    return Collections.unmodifiableList(result);
  }
}
