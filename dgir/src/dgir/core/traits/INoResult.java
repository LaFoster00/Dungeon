package dgir.core.traits;

import dgir.core.ir.Operation;
import org.jetbrains.annotations.NotNull;

/** Marks an operation that must not produce a result value. */
public interface INoResult extends IOpTrait {
  /**
   * Verifies that the operation has no output type.
   *
   * @param operation the operation to verify.
   * @return {@code true} if the operation has no result.
   */
  static boolean verify(@NotNull Operation operation) {
    if (operation.getOutput().isPresent()) {
      operation.emitError("Operation must not have a result.");
      return false;
    }
    return true;
  }
}
