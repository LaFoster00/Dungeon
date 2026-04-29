package dgir.core.ir;

import com.fasterxml.jackson.annotation.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * A region containing an ordered list of {@link Block}s, attached to an {@link Operation}.
 *
 * <p>Regions can also be freestanding ("orphan" regions) while being built up, and then transferred
 * into an operation via {@link #takeRegion(Region)}.
 *
 * <p>Every region always has at least one block — the <em>entry block</em> — which is created
 * automatically if needed. Execution enters a region through this block.
 *
 * <p>Regions may carry <em>body values</em>: typed values that are visible inside the region and
 * act like block/region arguments (e.g. loop induction variables).
 *
 * <pre>{@code
 * Region {
 *   Block entryBlock {
 *     Operation1
 *     ...
 *     TerminatorOperation
 *   }
 *   Block otherBlock { ... }
 * }
 * }</pre>
 *
 * @see Operation
 * @see Block
 */
@JsonPropertyOrder({"regionValues", "blocks"})
public final class Region {

  // =========================================================================
  // Members
  // =========================================================================

  /**
   * The blocks contained in this region, in order. A region always has at least one block (the
   * entry block), which is created automatically if needed.
   */
  private final @NotNull List<Block> blocks = new ArrayList<>();

  /**
   * Values visible inside this region, acting as parameters/arguments (e.g. the induction variable
   * of a for-loop body).
   */
  private final @JsonIdentityReference @Unmodifiable @NotNull List<@NotNull Value> regionValues;

  private final @JsonIgnore @Nullable Operation parent;

  // =========================================================================
  // Constructors
  // =========================================================================

  public Region(
      @Nullable Operation parent, @Nullable List<Value> regionValues, @NotNull List<Block> blocks) {
    this.parent = parent;
    this.regionValues = new ArrayList<>(regionValues == null ? List.of() : regionValues);
    for (Block block : blocks) addBlock(block);
  }

  @JsonCreator
  public Region(
      @JsonProperty("regionValues") @Nullable List<Value> regionValues,
      @JsonProperty("blocks") @NotNull List<Block> blocks) {
    this(null, regionValues, blocks);
    assert !blocks.isEmpty() : "Region must have at least one block.";
  }

  // =========================================================================
  // Blocks
  // =========================================================================

  /**
   * Get the blocks in this region.
   *
   * @return An unmodifiable view of the block list.
   */
  @Contract(pure = true)
  public @NotNull @UnmodifiableView List<Block> getBlocks() {
    return Collections.unmodifiableList(blocks);
  }

  public Block addBlock(@NotNull Block block) {
    return addBlockAt(blocks.size(), block);
  }

  public Block addBlockAt(int index, @NotNull Block block) {
    assert block.getParent().isEmpty() : "Block is already part of a region.";
    assert index >= 0 && index <= blocks.size() : "Index out of bounds.";
    blocks.add(index, block);
    block.setParent(this);
    return block;
  }

  public Block addBlockBefore(@NotNull Block block, @NotNull Block before) {
    return addBlockAt(blocks.indexOf(before), block);
  }

  public Block addBlockAfter(@NotNull Block block, @NotNull Block after) {
    return addBlockAt(blocks.indexOf(after) + 1, block);
  }

  public Block removeBlock(@NotNull Block block) {
    assert blocks.contains(block) : "Block is not part of this region.";
    return removeBlockAt(blocks.indexOf(block));
  }

  public Block removeBlockAt(int index) {
    assert index >= 0 && index < blocks.size() : "Index out of bounds.";
    Block block = blocks.remove(index);
    if (block != null) {
      // Ensure that none of the region values are used or defined in the removed block.
      assert !block.areValuesUsedOrDefined(new HashSet<>(regionValues), false)
          : "Cannot remove block that uses or defines region values of its current parent.";
      block.setParent(null);
    }
    return block;
  }

  @JsonIgnore
  @Contract(pure = true)
  public @NotNull Block getEntryBlock() {
    return blocks.getFirst();
  }

  /**
   * Get the first operation in the entry block.
   *
   * @return The first operation in the entry block.
   */
  @JsonIgnore
  @Contract(pure = true)
  public @NotNull Operation getEntryOperation() {
    var operations = getEntryBlock().getOperations();
    assert !operations.isEmpty() : "Entry block must have at least one operation.";
    return operations.getFirst();
  }

  // =========================================================================
  // Body Values
  // =========================================================================

  @Contract(pure = true)
  public @NotNull @Unmodifiable List<Value> getRegionValues() {
    return regionValues;
  }

  @Contract(pure = true)
  public Optional<Value> getRegionValue(int index) {
    if (index < 0 || index >= regionValues.size()) return Optional.empty();
    return Optional.of(regionValues.get(index));
  }

  @Contract(pure = true)
  public int getRegionValueIndex(@NotNull Value value) {
    return regionValues.indexOf(value);
  }

  // =========================================================================
  // Parent & Transfer
  // =========================================================================

  @Contract(pure = true)
  public @NotNull Optional<Operation> getParent() {
    return Optional.ofNullable(parent);
  }

  @JsonIgnore
  @Contract(pure = true)
  public int getIndex() {
    return parent == null ? -1 : parent.getRegions().indexOf(this);
  }

  /**
   * Move all blocks from {@code other} into this region. Uses of {@code other}'s body values are
   * replaced with the corresponding values from this region.
   *
   * @param other The region to drain. Must have matching region value types and count.
   * @param override If true, all blocks currently contained in this region will be removed first.
   */
  public void takeRegion(@NotNull Region other, boolean override) {
    assertRegionCompatibility(other);

    if (override)
      for (Block block : List.copyOf(getBlocks())) {
        removeBlock(block);
      }

    // Update uses of the other region's body values to point to this region's body values instead.
    for (int i = 0; i < this.regionValues.size(); i++) {
      Value thisBodyValue = this.regionValues.get(i);
      Value otherBodyValue = other.regionValues.get(i);
      if (thisBodyValue != otherBodyValue) otherBodyValue.replaceAllUsesWith(thisBodyValue);
    }

    // Move all blocks from the other region into this region, updating their parent pointers.
    for (Block block : List.copyOf(other.blocks)) {
      other.removeBlock(block);
      addBlock(block);
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Assert that this region is compatible with another region, meaning they have the same number of
   * region values and matching types. This is a precondition for taking blocks from other regions.
   */
  public void assertRegionCompatibility(@NotNull Region other) {
    assert this.regionValues.size() == other.regionValues.size()
        : "Region values of regions must have the same size.";
    for (int i = 0; i < this.regionValues.size(); i++) {
      assert this.regionValues.get(i).getType().equals(other.regionValues.get(i).getType())
          : "Region value types of regions must match.";
    }
  }

  // =========================================================================
  // Object
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("Region[" + getIndex() + "] (");
    for (int i = 0; i < regionValues.size(); i++) {
      builder.append(regionValues.get(i));
      if (i < regionValues.size() - 1) builder.append(", ");
    }

    builder.append(") {");
    builder.append(blocks.stream().map(Block::toString).reduce("", (a, b) -> a + "\n  " + b));
    builder.append("\n}");

    return builder.toString();
  }
}
