package dgir.core.traits;

import dgir.core.ir.Block;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.dialect.builtin.BuiltinOps;
import dgir.dialect.scf.ScfOps;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Constrains an operation to have exactly one region containing exactly one block.
 *
 * <p>The verifier enforces the one-region / one-block structure. Convenience default methods
 * ({@link #getBlock()}, {@link #addOperation(Operation)}, {@link #addOperation(Op)}) provide direct
 * access to that single block without requiring callers to traverse regions manually.
 *
 * <p>Examples: {@link BuiltinOps.ProgramOp}, {@link ScfOps.ScopeOp}.
 */
public interface ISingleBlock extends IOpTrait {
  /**
   * Verifies that the operation has exactly one region with exactly one block.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if the one-region/one-block constraint is satisfied.
   */
  @Contract(pure = true)
  default boolean verify(@NotNull ISingleBlock ignored) {
    if (getOperation().getRegions().size() != 1) {
      getOperation().emitError("Operation must have exactly one region.");
      return false;
    }
    if (getOperation().getFirstRegion().orElseThrow().getBlocks().size() != 1) {
      getOperation().emitError("Operation's single region must have exactly one block.");
      return false;
    }
    return true;
  }

  /**
   * Returns the single block of the operation.
   *
   * @return the unique block.
   */
  @Contract(pure = true)
  default @NotNull Block getBlock() {
    return getOperation().getFirstRegion().orElseThrow().getEntryBlock();
  }

  /**
   * Appends an operation to the single block.
   *
   * @param operation operation to append.
   * @return the appended operation.
   */
  default @NotNull Operation addOperation(@NotNull Operation operation) {
    getOperation().getFirstRegion().orElseThrow().getEntryBlock().addOperation(operation);
    return operation;
  }

  /**
   * Appends a typed operation to the single block.
   *
   * @param op operation to append.
   * @param <OpT> concrete operation type.
   * @return the appended operation.
   */
  default <OpT extends Op> @NotNull OpT addOperation(@NotNull OpT op) {
    getOperation().getFirstRegion().orElseThrow().getEntryBlock().addOperation(op);
    return op;
  }
}
