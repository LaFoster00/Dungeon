package dgir.core.traits;

/** Marks an operation that must not produce a result value. */
public interface INoResult extends IOpTrait {
  /**
   * Verifies that the operation has no output type.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if the operation has no result.
   */
  default boolean verify(INoResult ignored) {
    if (getOperation().getOutput().isPresent()) {
      getOperation().emitError("Operation must not have a result.");
      return false;
    }
    return true;
  }
}
