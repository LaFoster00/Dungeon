package dgir.core.ir;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A descriptor for an attribute kind, containing metadata such as its unique identifier, namespace,
 * and owning dialect. This is used to register attributes with {@link AttributeDetails} and to
 * resolve attribute classes during serialization.
 *
 * <h3><strong>Important:</strong> Every attribute descriptor that is discovered via {@code
 * Dialect#allAttributes(Class)} must expose a public static factory method with the signature
 * {@code public static AttributeDescriptor defaultInstance()}.</h3>
 */
public interface AttributeDescriptor {
  /**
   * Get the Java class that is described by this descriptor.
   *
   * @return The Java class of the attribute, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  Class<? extends Attribute> getAttributeClass();

  /**
   * Get the unique identifier for this attribute kind.
   *
   * @return The ident string.
   */
  @Contract(pure = true)
  @NotNull
  String getIdent();

  /**
   * Returns the namespace prefix for this attribute kind.
   *
   * @return the namespace string, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  String getNamespace();

  /**
   * Returns the class of the dialect that contributes this attribute kind.
   *
   * @return the dialect class, never {@code null}.
   */
  @Contract(pure = true)
  @NotNull
  Class<? extends Dialect> getDialect();
}
