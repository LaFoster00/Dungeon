package dgir.core.ir;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

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
        Dialect.getOrThrow(descriptor.getDialect()).getNamespace(),
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
