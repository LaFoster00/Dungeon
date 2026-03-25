package dgir.core.traits;

import dgir.core.ir.Type;
import dgir.core.ir.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Constrains an operation to have a result value.
 *
 * <p>Convenience accessor {@link #getResult()} delegates to the first result slot.
 */
public interface IHasResult extends IOpTrait {
  /**
   * Verifies that the operation declares and materializes a result value.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if the operation has a non-empty output and output value.
   */
  default boolean verify(IHasResult ignored) {
    if (getOperation().getOutput().isEmpty()) {
      getOperation().emitError("Operation must have a result.");
      return false;
    }
    if (getOperation().getOutputValue().isEmpty()) {
      getOperation().emitError("Operation must have a result value.");
      return false;
    }
    return true;
  }

  /**
   * Returns the first result value of the operation.
   *
   * @return the operation result value.
   */
  default @NotNull Value getResult() {
    return getOperation()
        .getOutputValue()
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Expected operation to have a result value: " + getOperation()));
  }

  /**
   * Returns the type of the operation result value.
   *
   * @return the result type.
   */
  default @NotNull Type getResultType() {
    return getResult().getType();
  }
}
