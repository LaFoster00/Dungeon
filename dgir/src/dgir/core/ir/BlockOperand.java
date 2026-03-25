package dgir.core.ir;

import org.jetbrains.annotations.NotNull;

/** A reference to a successor {@link Block} used as an operand in a branching {@link Operation}. */
public class BlockOperand extends Operand<Block, BlockOperand> {

  // =========================================================================
  // Constructors
  // =========================================================================

  /**
   * Creates a block operand owned by an operation.
   *
   * @param owner owning operation.
   * @param block referenced successor block.
   */
  public BlockOperand(@NotNull Operation owner, @NotNull Block block) {
    super(owner, block);
  }

  /**
   * Returns this operand index in {@link Operation#getBlockOperands()}.
   *
   * @return zero-based block-operand index.
   */
  @Override
  public int getIndex() {
    return getOwner().getBlockOperands().indexOf(this);
  }
}
