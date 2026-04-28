package dgir.core.ir;

import com.fasterxml.jackson.annotation.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

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
@JsonPropertyOrder({"index", "regionValues", "blocks"})
public final class Region {

  // =========================================================================
  // Members
  // =========================================================================

  private final @NotNull List<Block> blocks = new ArrayList<>();

  /**
   * Values visible inside this region, acting as parameters/arguments (e.g. the induction variable
   * of a for-loop body).
   */
  private final @JsonIdentityReference @NotNull List<@NotNull Value> regionValues;

  private final @JsonIgnore @Nullable Operation parent;

  // =========================================================================
  // Constructors
  // =========================================================================

  public Region() {
    this(null, List.of());
  }

  public Region(@Nullable Operation parent) {
    this(parent, List.of());
  }

  public Region(@Nullable Operation parent, List<Type> bodyValueTypes) {
    this.parent = parent;
    this.regionValues = iniRegionValues(bodyValueTypes);
  }

  private Region(
      @NotNull List<Block> blocks, @Nullable Operation parent, @Nullable List<Value> regionValues) {
    this.parent = parent;
    this.regionValues = new ArrayList<>(regionValues == null ? List.of() : regionValues);
    for (Block block : blocks) addBlock(block);
  }

  /** Deserialization factory — body values and blocks are wired up by Jackson. */
  @JsonCreator
  public static Region createRegion(
      @JsonProperty(value = "regionValues") @Nullable List<Value> regionValues,
      @JsonProperty(value = "blocks") @Nullable List<Block> blocks) {
    return new Region(blocks != null ? blocks : List.of(), null, regionValues);
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
    if (block != null) block.setParent(null);
    return block;
  }

  /** Ensure this region has at least one (entry) block. */
  public void ensureEntryBlock() {
    if (this.blocks.isEmpty()) addBlock(new Block());
  }

  @JsonIgnore
  @Contract(pure = true)
  public @NotNull Block getEntryBlock() {
    ensureEntryBlock();
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
  public @NotNull List<Value> getRegionValues() {
    return regionValues;
  }

  @Contract(pure = true)
  public Optional<Value> getBodyValue(int index) {
    if (index < 0 || index >= regionValues.size()) return Optional.empty();
    return Optional.of(regionValues.get(index));
  }

  @Contract(pure = true)
  public int getBodyValueIndex(@NotNull Value value) {
    return regionValues.indexOf(value);
  }

  /**
   * Replace the body values of this region with a new list. Existing uses of the old values are
   * redirected to the corresponding new values.
   *
   * @param regionValues The new body values. Must match the existing list in size and types if any
   *     of the current values are already in use.
   */
  public void setRegionValues(@NotNull List<Value> regionValues) {
    if (!this.regionValues.isEmpty()
        && regionValues.stream().anyMatch(v -> !v.getUses().isEmpty())) {
      assert this.regionValues.size() == regionValues.size()
          : "Body values of regions must have the same size.";
      for (int i = 0; i < this.regionValues.size(); i++) {
        assert this.regionValues.get(i).getType().equals(regionValues.get(i).getType())
            : "Body value types of regions must match.";
      }
    }

    if (!this.regionValues.isEmpty())
      for (int i = 0; i < regionValues.size(); i++)
        this.regionValues.get(i).replaceAllUsesWith(regionValues.get(i));

    this.regionValues.clear();
    this.regionValues.addAll(regionValues);
  }

  public void setBodyValue(@NotNull Value value, int index) {
    assert index >= 0 && index < regionValues.size() : "Index out of bounds.";
    assert regionValues.get(index).getType().equals(value.getType())
        : "Body value type must match.";
    regionValues.set(index, value);
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
   * @param other The region to drain. Must have matching region value types.
   */
  public void takeRegion(@NotNull Region other) {
    assert this.regionValues.size() == other.regionValues.size()
        : "Region values of regions must have the same size.";
    for (int i = 0; i < this.regionValues.size(); i++) {
      assert this.regionValues.get(i).getType().equals(other.regionValues.get(i).getType())
          : "Region value types of regions must match.";
    }

    for (Block block : new ArrayList<>(other.blocks)) {
      other.removeBlock(block);
      addBlock(block);
    }

    for (int i = 0; i < this.regionValues.size(); i++) {
      Value thisBodyValue = this.regionValues.get(i);
      Value otherBodyValue = other.regionValues.get(i);
      if (thisBodyValue != otherBodyValue) otherBodyValue.replaceAllUsesWith(thisBodyValue);
    }
  }

  // =========================================================================
  // Private Helpers
  // =========================================================================

  private static List<Value> iniRegionValues(List<Type> bodyValueTypes) {
    List<Type> types = bodyValueTypes == null ? List.of() : bodyValueTypes;
    List<Value> values = new ArrayList<>(types.size());
    for (Type type : types)
      values.add(new Value(Objects.requireNonNull(type, "region value type cannot be null")));
    return values;
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
