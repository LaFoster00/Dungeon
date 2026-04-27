package dgir.core.ir;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Holds all basic information about an attribute kind and exposes it through a stable interface.
 *
 * <p>Callers should always use the static lookup methods ({@link #get(String)} and {@link
 * #get(Class)}) rather than constructing instances directly, so that global {@link DGIRContext}
 * caches remain consistent.
 */
public record AttributeDetails(
    @NotNull String ident,
    @NotNull String namespace,
    @NotNull Class<? extends Attribute> type,
    @NotNull Dialect dialect) {

  private AttributeDetails(@NotNull AttributeDescriptor descriptor) {
    this(
        descriptor.getIdent(),
        descriptor.getNamespace(),
        descriptor.getAttributeClass(),
        Dialect.getOrThrow(descriptor.getDialect()));
  }

  // =========================================================================
  // Static Factories
  // =========================================================================

  /**
   * Look up the {@link AttributeDetails} for the given ident string.
   *
   * @param ident the attribute ident string (e.g. {@code "integerAttr"}).
   * @return an optional containing the details if a registered attribute with the given ident
   *     exists, or empty otherwise.
   */
  public static @NotNull Optional<AttributeDetails> get(@NotNull String ident) {
    return Optional.ofNullable(DGIRContext.attributesByIdent.get(ident));
  }

  /**
   * Look up the {@link AttributeDetails} for the given attribute class.
   *
   * @param clazz the attribute class to look up.
   * @return an optional containing the details if a registered attribute with the given class
   *     exists, or empty otherwise.
   */
  public static @NotNull Optional<AttributeDetails> get(@NotNull Class<? extends Attribute> clazz) {
    return Optional.ofNullable(DGIRContext.attributes.get(clazz));
  }

  /**
   * Check whether this attribute kind matches the given class.
   *
   * @param clazz the class to check.
   * @return {@code true} if this details instance describes {@code clazz}.
   */
  @Contract(pure = true)
  public boolean isa(@NotNull Class<? extends Attribute> clazz) {
    return clazz.equals(type);
  }

  // =========================================================================
  // Static Registration
  // =========================================================================

  /**
   * Register the given attribute descriptor in the global {@link DGIRContext}.
   *
   * @param descriptor the descriptor to register.
   */
  public static void insert(@NotNull AttributeDescriptor descriptor) {
    AttributeDetails details = new AttributeDetails(descriptor);
    DGIRContext.attributes.put(details.type(), details);
    DGIRContext.attributesByIdent.put(details.ident(), details);
  }
}
