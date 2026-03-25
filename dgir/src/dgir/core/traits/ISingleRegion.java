package dgir.core.traits;

import dgir.core.ir.*;
import dgir.dialect.func.FuncOps;
import dgir.dialect.scf.ScfOps;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Constrains an operation to have exactly one region.
 *
 * <p>The verifier enforces the single-region structure. Convenience default methods give direct
 * access to the region, its entry block, individual blocks by index, body arguments ({@link
 * #getArgument(int)}), and block/operation insertion.
 *
 * <p>Examples: {@link FuncOps.FuncOp}, {@link ScfOps.ForOp}, {@link ScfOps.ScopeOp}.
 */
public interface ISingleRegion extends IOpTrait {
  /**
   * Verifies that the operation owns exactly one region.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if the operation has exactly one region.
   */
  @Contract(pure = true)
  default boolean verify(@NotNull ISingleRegion ignored) {
    if (getOperation().getRegions().size() != 1) {
      getOperation().emitError("Operation must have exactly one region.");
      return false;
    }
    return true;
  }

  /**
   * Returns the single region.
   *
   * @return the operation's only region.
   */
  @Contract(pure = true)
  default @NotNull Region getRegion() {
    return getOperation()
        .getFirstRegion()
        .orElseThrow(() -> new RuntimeException("Operation must have exactly one region."));
  }

  /**
   * Returns a body argument from the single region.
   *
   * @param index argument index.
   * @return the argument value if present.
   */
  @Contract(pure = true)
  default @NotNull Optional<Value> getArgument(int index) {
    return getRegion().getBodyValue(index);
  }

  /**
   * Appends an operation to a block in the single region.
   *
   * @param operation operation to insert.
   * @param blockIndex target block index.
   * @return the inserted operation.
   */
  default @NotNull Operation addOperation(@NotNull Operation operation, int blockIndex) {
    return getRegion().getBlocks().get(blockIndex).addOperation(operation);
  }

  /**
   * Appends a typed operation to a block in the single region.
   *
   * @param op operation to insert.
   * @param blockIndex target block index.
   * @param <OpT> operation subtype.
   * @return the inserted operation.
   */
  default <OpT extends Op> @NotNull OpT addOperation(@NotNull OpT op, int blockIndex) {
    return getRegion().getBlocks().get(blockIndex).addOperation(op);
  }

  /**
   * Returns the entry block of the single region.
   *
   * @return the region entry block.
   */
  @Contract(pure = true)
  default @NotNull Block getEntryBlock() {
    return getRegion().getEntryBlock();
  }

  /**
   * Returns a block by index from the single region.
   *
   * @param index block index.
   * @return the block if it exists.
   */
  @Contract(pure = true)
  default @NotNull Optional<Block> getBlock(int index) {
    if (index >= getRegion().getBlocks().size()) return Optional.empty();
    return Optional.ofNullable(getRegion().getBlocks().get(index));
  }

  /**
   * Adds a block to the single region.
   *
   * @param block block to append.
   * @return the appended block.
   */
  default @NotNull Block addBlock(@NotNull Block block) {
    getRegion().addBlock(block);
    return block;
  }
}
