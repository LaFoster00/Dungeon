package dgir.core.traits;

import dgir.core.ir.Operation;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an operation that must have no operands.
 */
public interface INoOperands extends IOpTrait {
  /**
   * Verifies that the operation has no operands.
   *
   * @param operation the operation to verify.
   * @return {@code true} if no operands are present.
   */
  static boolean verify(@NotNull Operation operation) {
    if (!operation.getOperands().isEmpty()) {
      operation.emitError("Operation must have no operands.");
      return false;
    }
    return true;
  }
}
