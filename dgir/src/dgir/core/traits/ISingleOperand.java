package dgir.core.traits;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Constrains an operation to have exactly one value operand.
 *
 * <p>Convenience accessors {@link #getOperand()} and {@link #getOperandType()} delegate to the
 * first operand slot.
 */
public interface ISingleOperand extends IOpTrait {
  /**
   * Verifies that the operation has exactly one non-null operand.
   *
   * @param operation the operation to verify.
   * @return {@code true} if the single-operand constraint is satisfied.
   */
  @Contract(pure = true)
  static boolean verify(@NotNull Operation operation) {
    // Ensure that the operation only has one operator
    if (operation.getOperands().size() != 1) {
      operation.emitError("Operation must have exactly one operand.");
      return false;
    }
    if (operation.getOperand(0).isEmpty()) {
      operation.emitError("Operation must have non-null operand");
      return false;
    }
    return true;
  }

  /**
   * Returns the single operand value.
   *
   * @return the operand value.
   */
  @Contract(pure = true)
  default @NotNull Value getOperand() {
    return getOperation().getOperandValue(0).orElseThrow();
  }

  /**
   * Returns the type of the single operand value.
   *
   * @return the operand type.
   */
  @Contract(pure = true)
  default @NotNull Type getOperandType() {
    return getOperand().getType();
  }
}
