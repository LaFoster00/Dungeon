package dgir.core.traits;

/**
 * Marks an operation that must have no operands.
 */
public interface INoOperands extends IOpTrait {
  /**
   * Verifies that the operation has no operands.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if no operands are present.
   */
  default boolean verify(INoOperands ignored) {
    if (!getOperation().getOperands().isEmpty()) {
      getOperation().emitError("Operation must have no operands.");
      return false;
    }
    return true;
  }
}
