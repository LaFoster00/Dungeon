package dgir.core.traits;

import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.ir.ValueOperand;
import org.jetbrains.annotations.NotNull;

/**
 * Constrains an operation to have exactly two value operands.
 *
 * <p>Convenience accessors {@link #getLhs()} and {@link #getRhs()} delegate to the first and second
 * operand slots, respectively.
 */
public interface IBinaryOperands extends IOpTrait {
  /**
   * Verifies that the operation has exactly two value operands.
   *
   * @param operation the operation to verify.
   * @return {@code true} if both operands exist.
   */
  static boolean verify(@NotNull Operation operation) {
    if (operation.getOperands().size() != 2) {
      operation.emitError("Operation must have exactly two operands.");
      return false;
    }
    if (operation.getOperandType(0).isEmpty() || operation.getOperandType(1).isEmpty()) {
      operation.emitError("Operation must have non-null operands");
      return false;
    }
    return true;
  }

  /**
   * Returns the left-hand side operand value.
   *
   * @return the first operand value.
   */
  default @NotNull Value getLhs() {
    return getOperation()
        .getOperand(0)
        .flatMap(ValueOperand::getValue)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Expected first operand to be a value for binary operation: "
                        + getOperation()));
  }

  /**
   * Returns the right-hand side operand value.
   *
   * @return the second operand value.
   */
  default @NotNull Value getRhs() {
    return getOperation()
        .getOperand(1)
        .flatMap(ValueOperand::getValue)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Expected second operand to be a value for binary operation: "
                        + getOperation()));
  }
}
