package dgir.core.ir;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Global registry for all dialects, operations, types, and attributes known to the DGIR.
 *
 * <p>Each category is populated during dialect registration. Accessing unregistered operations,
 * types, or attributes is treated as an error.
 */
public class DGIRContext {

  // =========================================================================
  // Operations
  // =========================================================================

  /** Registered operations by class. */
  public static final @NotNull Map<Class<? extends Op>, OperationDetails> registeredOperations =
      new HashMap<>();

  /** Registered operations by ident. */
  public static final @NotNull Map<String, OperationDetails> registeredOperationsByIdent =
      new HashMap<>();

  // =========================================================================
  // Attributes
  // =========================================================================

  /** Registered attributes by class. */
  public static final @NotNull Map<Class<? extends Attribute>, AttributeDetails> attributes =
      new HashMap<>();

  /** Registered attributes by ident. */
  public static final @NotNull Map<String, AttributeDetails> attributesByIdent = new HashMap<>();

  // =========================================================================
  // Types
  // =========================================================================

  /** Registered types by class. */
  public static final @NotNull Map<Class<? extends Type>, TypeDetails> registeredTypes =
      new HashMap<>();

  /** Registered types by ident. */
  public static final Map<String, TypeDetails> registeredTypesByIdent = new HashMap<>();

  // =========================================================================
  // Dialects
  // =========================================================================

  /** All registered dialects by class. */
  public static final Map<Class<? extends Dialect>, Dialect> registeredDialects = new HashMap<>();

  /** All registered dialects by namespace string. */
  public static final Map<String, Dialect> registeredDialectsByName = new HashMap<>();

  // =========================================================================
  // Static Helpers
  // =========================================================================

  /**
   * Resolve the dialect that owns the given type or operation name.
   *
   * <p>If the name contains a {@code '.'}, the part before the first dot is treated as the dialect
   * namespace. If no matching dialect is found, the builtin dialect ({@code ""}) is returned.
   *
   * @param name The ident string to resolve (e.g. {@code "arith.constant"} or {@code "int32"}).
   * @return The owning {@link Dialect}, or the builtin dialect as a fallback.
   */
  @Contract(pure = true)
  public static @NotNull Optional<Dialect> getReferencedDialect(@NotNull String name) {
    var i = name.indexOf('.');
    if (i >= 0) {
      var namespace = name.substring(0, i);
      var dialect = registeredDialectsByName.get(namespace);
      if (dialect != null) {
        return Optional.of(dialect);
      }
    }
    return Optional.empty();
  }

  /**
   * Check if an operation with the given class is registered in the context.
   *
   * @param opClass The operation class to check.
   * @return true if the operation is registered, false otherwise.
   */
  public boolean isOpRegistered(@NotNull Class<? extends Op> opClass) {
    return registeredOperations.containsKey(opClass);
  }

  /**
   * Check if an attribute with the given class is registered in the context.
   *
   * @param attrClass The attribute class to check.
   * @return true if the attribute is registered, false otherwise.
   */
  public boolean isAttributeRegistered(@NotNull Class<? extends Attribute> attrClass) {
    return attributes.containsKey(attrClass);
  }

  /**
   * Check if a type with the given class is registered in the context.
   *
   * @param typeClass The type class to check.
   * @return true if the type is registered, false otherwise.
   */
  public boolean isTypeRegistered(@NotNull Class<? extends Type> typeClass) {
    return registeredTypes.containsKey(typeClass);
  }

  /**
   * Check if a dialect with the given class is registered in the context.
   *
   * @param dialectClass The dialect class to check.
   * @return true if the dialect is registered, false otherwise.
   */
  public boolean isDialectRegistered(@NotNull Class<? extends Dialect> dialectClass) {
    return registeredDialects.containsKey(dialectClass);
  }
}
