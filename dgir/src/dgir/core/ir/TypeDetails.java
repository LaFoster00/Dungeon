package dgir.core.ir;

import dgir.core.utility.DgirCoreUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Holds all basic information about a type kind and exposes it through a stable interface.
 *
 * <p>Callers should always use the static factory method {@link #get(String)} rather than
 * constructing instances directly, so that the global {@link DGIRContext} caches are kept
 * consistent.
 *
 * @param ident The unique identifier string for this type (e.g. {@code "int"} or {@code
 *     "func.func"}).
 * @param type The Java class that represents this type.
 * @param dialect The dialect that contributes this type.
 * @param validator Returns the validator function that checks whether a given value is compatible
 *     with this type.
 * @param parameterizedIdentFactory A factory function that takes a parameterized ident string and
 *     returns a corresponding Type instance. This is used for parameterized types to reconstruct a
 *     Type instance from its parameterized ident (e.g. {@code "ptr<int32>"}). For non-parameterized
 *     types, this can be a simple function that ignores the input and returns the default instance.
 */
public record TypeDetails(
    @NotNull String ident,
    @NotNull String namespace,
    @NotNull Class<? extends Type> type,
    @NotNull Dialect dialect,
    @NotNull Function<Object, Boolean> validator,
    @NotNull Function<Pair<String, TypeDetails>, Type> parameterizedIdentFactory) {

  private TypeDetails(@NotNull TypeDescriptor descriptor) {
    this(
        descriptor.getIdent(),
        descriptor.getNamespace(),
        descriptor.getTypeClass(),
        Dialect.getOrThrow(descriptor.getDialect()),
        descriptor.getValidator(),
        descriptor.getParameterizedIdentFactory());
  }

  // =========================================================================
  // Static Factories
  // =========================================================================

  /**
   * Look up the {@link TypeDetails} for the given ident string.
   *
   * @param ident the type ident string (e.g. {@code "int32"} or {@code "func.func"}).
   * @return an optional containing the details if a registered type with the given ident exists, or
   *     empty otherwise.
   */
  public static @NotNull Optional<TypeDetails> get(@NotNull String ident) {
    return Optional.ofNullable(DGIRContext.registeredTypesByIdent.get(ident));
  }

  // =========================================================================
  // Static Helpers
  // =========================================================================

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
    if (normalizedIdent.isEmpty()) {
      throw new IllegalArgumentException("Parameterized ident must not be empty.");
    }

    int genericStart = normalizedIdent.indexOf('<');
    String baseIdent = normalizedIdent;
    if (genericStart != -1) {
      int genericEnd = findMatchingGenericEnd(normalizedIdent, genericStart);
      if (genericEnd != normalizedIdent.length() - 1) {
        throw new IllegalArgumentException(
            "Malformed parameterized ident (unexpected trailing content): " + parameterizedIdent);
      }

      baseIdent = normalizedIdent.substring(0, genericStart).trim();
      if (baseIdent.isEmpty()) {
        throw new IllegalArgumentException(
            "Malformed parameterized ident (missing base ident): " + parameterizedIdent);
      }

      String parameterText = normalizedIdent.substring(genericStart + 1, genericEnd);
      if (parameterText.isBlank()) {
        throw new IllegalArgumentException(
            "Malformed parameterized ident (empty parameter list): " + parameterizedIdent);
      }
    }

    return TypeDetails.get(baseIdent)
        .map(
            typeDetails ->
                typeDetails.parameterizedIdentFactory.apply(
                    Pair.of(parameterizedIdent, typeDetails)))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Cannot create type from parameterized ident with unregistered base type: "
                        + parameterizedIdent));
  }

  /**
   * Parse a comma-separated list of (possibly nested/parameterized) type strings into a list of
   * Type instances.
   *
   * <p>Splitting is performed by {@link DgirCoreUtils#splitAtDepthZero(String, String)} so that
   * commas inside nested angle-bracket ({@code < >}) or parenthesis ({@code ( )}) groups are never
   * treated as separators.
   *
   * <p>Examples:
   *
   * <pre>{@literal
   *   int32, float64
   *   func.func<"(int32, string) -> (bool)">, float64
   *   func.func<"(int32) -> (bool)">, string
   * }</pre>
   *
   * @param parameterString The comma-separated parameter string (may be empty).
   * @return The list of created Type instances; empty if {@code parameterString} is blank.
   */
  @Contract(pure = true)
  public static @NotNull List<Type> fromParameterString(@NotNull String parameterString) {
    if (parameterString.isBlank()) {
      return List.of();
    }

    List<Type> parameters = new ArrayList<>();
    for (String parameter : DgirCoreUtils.splitAtDepthZero(parameterString, ",")) {
      String trimmedParameter = parameter.trim();
      if (trimmedParameter.isEmpty()) {
        throw new IllegalArgumentException(
            "Malformed parameter string (empty parameter): " + parameterString);
      }
      parameters.add(fromParameterizedIdent(trimmedParameter));
    }
    return List.copyOf(parameters);
  }

  /**
   * Quote a custom type expression so it can be embedded as a parameter value.
   *
   * <p>Backslashes and double quotes are escaped using JSON-style escaping.
   *
   * @param value the raw custom expression.
   * @return the quoted and escaped expression payload.
   */
  @Contract(pure = true)
  public static @NotNull String quoteCustomExpression(@NotNull String value) {
    return escapeCustomExpression(value);
  }

  /**
   * Unquote a custom type expression previously wrapped with {@link
   * #quoteCustomExpression(String)}.
   *
   * @param parameter the possibly quoted custom expression.
   * @return the unquoted expression if it was quoted, otherwise the original value.
   */
  @Contract(pure = true)
  public static @NotNull String unquoteCustomExpression(@NotNull String parameter) {
    if (parameter.length() >= 2 && parameter.startsWith("\"") && parameter.endsWith("\"")) {
      return unescapeCustomExpression(parameter.substring(1, parameter.length() - 1));
    }
    return parameter;
  }

  @Contract(pure = true)
  private static @NotNull String escapeCustomExpression(@NotNull String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Contract(pure = true)
  private static @NotNull String unescapeCustomExpression(@NotNull String value) {
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
    int depth = 0;
    boolean inQuotes = false;
    for (int i = genericStart; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inQuotes) {
        if (c == '"' && text.charAt(i - 1) != '\\') {
          inQuotes = false;
        }
        continue;
      }
      if (c == '"') {
        inQuotes = true;
        continue;
      }
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
        if (depth == 0) {
          return i;
        }
        if (depth < 0) {
          break;
        }
      }
    }

    throw new IllegalArgumentException(
        "Malformed parameterized ident (unbalanced angle brackets): " + text);
  }

  // =========================================================================
  // Static Registration
  // =========================================================================

  /**
   * Register the given type in the global {@link DGIRContext}. This should only be called from a
   * dialect's {@code init()} method during dialect initialization. This will populate both the
   * unregistered and registered caches in the {@link DGIRContext} to ensure that look-ups work both
   * before and after registration.
   */
  public static void insert(@NotNull TypeDescriptor descriptor) {
    TypeDetails details = new TypeDetails(descriptor);
    // Populate the registered caches
    DGIRContext.registeredTypes.put(details.type(), details);
    DGIRContext.registeredTypesByIdent.put(details.ident(), details);
  }
}
