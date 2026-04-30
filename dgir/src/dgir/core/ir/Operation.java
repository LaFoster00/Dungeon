package dgir.core.ir;

import dgir.core.utility.DgirCoreUtils;
import dgir.core.analysis.OperationVerifier;
import dgir.core.debug.Location;
import dgir.core.debug.ValueDebugInfo;
import dgir.core.serialization.OperationDeserializer;
import dgir.core.serialization.OperationSerializer;
import dgir.core.traits.IOpTrait;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Carries the runtime state associated with a concrete operation instance.
 *
 * <p>The design deliberately separates <em>data</em> (this class) from <em>behaviour</em> (the
 * {@link Op} subclass hierarchy). This decoupling makes serialisation and deserialisation
 * straightforward: an {@code Operation} can be created from JSON without instantiating any
 * dialect-specific {@code Op} class, and the {@code Op} wrapper can be reconstructed on demand via
 * {@link #asOp()}.
 *
 * <p>An {@code Operation} is always created through one of the {@link #Create} static factory
 * methods; direct constructor calls are for deserialisation only.
 */
@JsonSerialize(using = OperationSerializer.class)
@JsonDeserialize(using = OperationDeserializer.class)
public final class Operation implements Serializable {

  // =========================================================================
  // Static Factory
  // =========================================================================

  /**
   * Create an {@link Operation} and populate body values for each of its regions.
   *
   * <p>Each element of {@code regionValueTypes} corresponds to one region in declaration order. The
   * values are created as fresh {@link Value} instances typed according to the provided lists and
   * set as the region's body values after creation.
   *
   * @param location the source location of the operation.
   * @param op a default (no-arg) op prototype used to obtain the ident and default attributes.
   * @param operands input value operands, or {@code null} for none.
   * @param successors successor blocks (for branching ops), or {@code null} for none.
   * @param outputType result type, or {@code null} for void ops.
   * @param regionValueTypes per-region lists of region value types; the number of elements
   * @return the newly constructed operation.
   */
  @NotNull
  @SafeVarargs
  public static Operation Create(
      @NotNull Location location,
      @NotNull Op op,
      @Nullable List<Value> operands,
      @Nullable List<Block> successors,
      @Nullable Type outputType,
      @NotNull List<Type>... regionValueTypes) {
    return Create(
        location,
        OperationDetails.lookup(op.getIdent())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        MessageFormat.format("Operation {0} is not registered.", op.getIdent()))),
        operands != null ? operands : List.of(),
        successors != null ? successors : List.of(),
        outputType,
        regionValueTypes);
  }

  /**
   * Create an {@link Operation} with a fixed number of empty regions.
   *
   * @param location the source location of the operation.
   * @param op a default op prototype used to obtain the ident and default attributes.
   * @param operands input value operands, or {@code null} for none.
   * @param successors successor blocks, or {@code null} for none.
   * @param outputType result type, or {@code null} for void ops.
   * @param numRegions number of (initially empty) regions to attach.
   * @return the newly constructed operation.
   * @throws IllegalArgumentException if {@code op}'s ident is not yet registered.
   */
  @SuppressWarnings("unchecked")
  public static @NotNull Operation Create(
      @NotNull Location location,
      @NotNull Op op,
      @Nullable List<Value> operands,
      @Nullable List<Block> successors,
      @Nullable Type outputType,
      int numRegions) {
    return Create(
        location,
        op,
        operands,
        successors,
        outputType,
        Stream.generate(List::<Type>of).limit(numRegions).toArray(List[]::new));
  }

  @SafeVarargs
  public static @NotNull Operation Create(
      @NotNull Location location,
      @NotNull OperationDetails operationDetails,
      @Nullable List<Value> operands,
      @Nullable List<Block> successors,
      @Nullable Type outputType,
      @NotNull List<Type>... regionValueTypes) {
    return new Operation(
        location,
        operationDetails,
        operands != null ? operands : List.of(),
        successors != null ? successors : List.of(),
        outputType,
        regionValueTypes);
  }

  // =========================================================================
  // Members
  // =========================================================================

  /** The unique identifier of this operation. */
  private final @NotNull OperationDetails details;

  /** The input values of this operation. */
  private final @Unmodifiable @NotNull List<@NotNull ValueOperand> operands;

  /** The input blocks of this operation (branch successors). */
  private final @Unmodifiable @NotNull List<@NotNull BlockOperand> blockOperands;

  /** The output of this operation. */
  private final @Nullable OperationResult output;

  /** The attributes of this operation. */
  private final @Unmodifiable @NotNull Map<@NotNull String, @NotNull NamedAttribute> attributes;

  /** The dynamic attributes of this operation. */
  private final @NotNull Map<@NotNull String, @NotNull NamedAttribute> dynamicAttributes;

  /** The regions of this operation. */
  private final @NotNull @Unmodifiable List<@NotNull Region> regions;

  /** The block containing this operation. */
  private @Nullable Block parent = null;

  /** The source location of this operation. */
  private final @NotNull Location location;

  // =========================================================================
  // Constructors
  // =========================================================================

  /**
   * Full constructor for Operation.
   *
   * @param location The source location of this operation.
   * @param details The operation details.
   * @param operands The input values.
   * @param successors The blocks succeeding this operation.
   * @param resultType The output result type.
   * @param regionValueTypes The types of the region values for each region; the number of elements
   *     determines the number of regions created, and the types in each list determine the body
   *     values of the corresponding region.
   */
  @SafeVarargs
  public Operation(
      @NotNull Location location,
      @NotNull OperationDetails details,
      @NotNull List<Value> operands,
      @NotNull List<Block> successors,
      @Nullable Type resultType,
      @NotNull List<Type>... regionValueTypes) {
    this.location = location;

    this.details = details;

    this.output = resultType != null ? new OperationResult(this, resultType) : null;

    this.dynamicAttributes = new HashMap<>();

    List<ValueOperand> operandsList = new ArrayList<>(operands.size());
    for (var operand : operands) operandsList.add(new ValueOperand(this, operand));
    this.operands = Collections.unmodifiableList(operandsList);

    List<BlockOperand> blockOperandsList = new ArrayList<>(successors.size());
    for (int i = 0; i < successors.size(); i++) {
      blockOperandsList.add(i, new BlockOperand(this, successors.get(i)));
    }
    this.blockOperands = Collections.unmodifiableList(blockOperandsList);

    this.attributes =
        details.defaultAttributes().get().stream()
            .collect(Collectors.toMap(NamedAttribute::getName, attr -> attr));

    var regionsList = new ArrayList<Region>(regionValueTypes.length);
    for (List<Type> regionValueType : regionValueTypes) {
      regionsList.add(
          new Region(
              this, regionValueType.stream().map(Value::new).toList(), List.of(new Block())));
    }
    this.regions = Collections.unmodifiableList(regionsList);
  }

  // =========================================================================
  // Verification
  // =========================================================================

  /**
   * Run the {@link OperationVerifier} on this operation.
   *
   * @param recursive {@code true} to also verify all nested operations and blocks.
   * @return {@code true} if verification succeeds.
   */
  @Contract(pure = true)
  public boolean verify(boolean recursive) {
    return new OperationVerifier(recursive).verify(this);
  }

  // =========================================================================
  // Details & Traits
  // =========================================================================

  /**
   * Returns the {@link OperationDetails} that describe this operation kind.
   *
   * @return the details instance, never {@code null}.
   */
  @Contract(pure = true)
  public @NotNull OperationDetails getDetails() {
    return details;
  }

  /**
   * Returns {@code true} if this operation's kind implements the given trait.
   *
   * @param traitClass the trait to check for.
   * @return {@code true} if the trait is present.
   */
  @Contract(pure = true)
  public boolean hasTrait(@NotNull Class<? extends IOpTrait> traitClass) {
    return details.hasTrait(traitClass);
  }

  /**
   * Create a typed Op wrapper for this operation if it matches the given class.
   *
   * @param clazz The class of the op to wrap
   * @return The op wrapper, or empty if this operation is not of the given type.
   */
  @Contract(pure = true)
  public <T extends Op> @NotNull Optional<T> as(@NotNull Class<T> clazz) {
    return getDetails().as(clazz, this);
  }

  /**
   * Create a typed trait wrapper for this operation if it implements the given trait.
   *
   * @param clazz The trait to check for
   * @return The trait wrapper, or empty if this operation does not implement the trait.
   */
  @Contract(pure = true)
  public <T extends IOpTrait> @NotNull Optional<T> asTrait(@NotNull Class<T> clazz) {
    if (!hasTrait(clazz)) return Optional.empty();
    return Optional.of(clazz.cast(asOp()));
  }

  /**
   * Create a generic Op wrapper for this operation.
   *
   * @return The op wrapper.
   */
  @Contract(pure = true)
  public @NotNull Op asOp() {
    return getDetails().asOp(this);
  }

  /**
   * Check if this operation is of the given Op type.
   *
   * @param clazz The type to check for
   * @return true if this operation is of the given type, false otherwise.
   */
  @Contract(pure = true)
  public boolean isa(@NotNull Class<? extends Op> clazz) {
    return getDetails().isa(clazz);
  }

  // =========================================================================
  // Operands & Output
  // =========================================================================

  /**
   * Returns the input value operands of this operation.
   *
   * @return an unmodifiable list of {@link ValueOperand}s.
   */
  @Contract(pure = true)
  public @NotNull @Unmodifiable List<@NotNull ValueOperand> getOperands() {
    return operands;
  }

  /**
   * Returns the operand at the given index, if present.
   *
   * @param index zero-based operand index.
   * @return the operand, or empty if the index is out of range.
   */
  @Contract(pure = true)
  public @NotNull Optional<ValueOperand> getOperand(int index) {
    return operands.size() > index ? Optional.of(operands.get(index)) : Optional.empty();
  }

  /**
   * Returns the operand at the given index, throwing if out of range.
   *
   * @param index zero-based operand index.
   * @return the operand, never {@code null}.
   * @throws NoSuchElementException if the index is out of range.
   */
  @Contract(pure = true)
  public @NotNull ValueOperand getOperandOrThrow(int index) {
    if (index >= operands.size()) throw new NoSuchElementException("No operand at index " + index);
    return operands.get(index);
  }

  /**
   * Returns the value referenced by the operand at the given index, if present.
   *
   * @param i zero-based operand index.
   * @return the referenced {@link Value}, or empty if the index is out of range or unset.
   */
  @Contract(pure = true)
  public @NotNull Optional<Value> getOperandValue(int i) {
    return getOperand(i).flatMap(ValueOperand::getValue);
  }

  /**
   * Returns the value referenced by the operand at the given index, throwing if absent.
   *
   * @param i zero-based operand index.
   * @return the referenced {@link Value}, never {@code null}.
   * @throws NoSuchElementException if the index is out of range or the operand is unset.
   */
  @Contract(pure = true)
  public @NotNull Value getOperandValueOrThrow(int i) {
    if (i >= operands.size()) throw new NoSuchElementException("No operand at index " + i);
    return operands
        .get(i)
        .getValue()
        .orElseThrow(() -> new NoSuchElementException("Operand " + i + " has no value"));
  }

  @Contract(pure = true)
  public @NotNull Optional<Type> getOperandType(int i) {
    return getOperandValue(i).map(Value::getType);
  }

  /**
   * Returns the type of the value referenced by the operand at the given index, throwing if absent.
   *
   * @param i zero-based operand index.
   * @return the {@link Type}, never {@code null}.
   * @throws NoSuchElementException if the index is out of range or the operand is unset.
   */
  @Contract(pure = true)
  public @NotNull Type getOperandTypeOrThrow(int i) {
    return getOperandValueOrThrow(i).getType();
  }

  /**
   * Returns the block operands (successor-block references) of this operation.
   *
   * @return an unmodifiable list of {@link BlockOperand}s.
   */
  @Contract(pure = true)
  public @NotNull @Unmodifiable List<BlockOperand> getBlockOperands() {
    return blockOperands;
  }

  /**
   * Get the successor blocks of this operation via its block operands.
   *
   * @return An unmodifiable list of successor blocks.
   */
  @Contract(pure = true)
  public @NotNull @Unmodifiable List<Block> getSuccessors() {
    return getBlockOperands().stream()
        .map(BlockOperand::getValue)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  /**
   * Returns the {@link OperationResult} for this operation, if it produces a value.
   *
   * @return the result wrapper, or empty for void operations.
   */
  @Contract(pure = true)
  public @NotNull Optional<OperationResult> getOutput() {
    return Optional.ofNullable(output);
  }

  /**
   * Returns the {@link OperationResult} for this operation, throwing if this is a void operation.
   *
   * @return the result wrapper, never {@code null}.
   * @throws NoSuchElementException if this operation has no output.
   */
  @Contract(pure = true)
  public @NotNull OperationResult getOutputOrThrow() {
    if (output == null) throw new NoSuchElementException("Operation has no output: " + this);
    return output;
  }

  /**
   * Returns the output {@link Value} produced by this operation, if any.
   *
   * @return the output value, or empty for void operations.
   */
  @Contract(pure = true)
  public @NotNull Optional<Value> getOutputValue() {
    if (output == null) return Optional.empty();
    return getOutput().map(OperationResult::getValue);
  }

  /**
   * Returns the output {@link Value} produced by this operation, throwing if this is a void
   * operation.
   *
   * @return the output value, never {@code null}.
   * @throws NoSuchElementException if this operation has no output.
   */
  @Contract(pure = true)
  public @NotNull Value getOutputValueOrThrow() {
    if (output == null) throw new NoSuchElementException("Operation has no output: " + this);
    return output.getValue();
  }

  /**
   * Replace the output value of this operation.
   *
   * @param value the new output value; its type must match the existing result type.
   * @throws AssertionError if this operation has no output.
   */
  public void setOutputValue(@NotNull Value value) {
    assert this.output != null : "Trying to set output value of an operation that has no output.";
    this.output.setValue(value);
  }

  // =========================================================================
  // Attributes
  // =========================================================================

  /**
   * Returns all named attributes of this operation.
   *
   * @return an unmodifiable map from attribute name to {@link NamedAttribute}.
   */
  @Contract(pure = true)
  public @NotNull @Unmodifiable Map<String, NamedAttribute> getAttributesMap() {
    return attributes;
  }

  @Contract(pure = true)
  public @NotNull @Unmodifiable List<NamedAttribute> getNamedAttributes() {
    return attributes.values().stream().toList();
  }

  @Contract(pure = true)
  public @NotNull @Unmodifiable List<Attribute> getAttributes() {
    return attributes.values().stream().map(NamedAttribute::getAttributeOrThrow).toList();
  }

  /**
   * Returns the attribute value for the given name, if present and set.
   *
   * @param name the attribute name to look up.
   * @return the {@link Attribute}, or empty if not present or not set.
   */
  @Contract(pure = true)
  public @NotNull Optional<Attribute> getAttribute(@NotNull String name) {
    if (!getAttributesMap().containsKey(name)) return Optional.empty();
    return Optional.of(getAttributesMap().get(name).getAttributeOrThrow());
  }

  /**
   * Returns the attribute for the given name cast to {@code clazz}, if present, set, and of the
   * correct type.
   *
   * @param clazz the expected attribute class.
   * @param name the attribute name.
   * @param <T> the attribute type.
   * @return the typed attribute, or empty if absent, unset, or the wrong type.
   */
  @Contract(pure = true)
  public <T extends Attribute> @NotNull Optional<T> getAttributeAs(
      @NotNull String name, @NotNull Class<T> clazz) {
    var attribute = getAttribute(name);
    if (attribute.isEmpty() || !clazz.isInstance(attribute.get())) return Optional.empty();
    return Optional.of(clazz.cast(attribute.get()));
  }

  /**
   * Returns the attribute for the given name cast to {@code clazz}, throwing if absent, unset, or
   * the wrong type.
   *
   * @param name the attribute name.
   * @param clazz the expected attribute class.
   * @param <T> the attribute type.
   * @return the typed attribute, never {@code null}.
   * @throws NoSuchElementException if the attribute is absent, unset, or the wrong type.
   */
  @Contract(pure = true)
  public <T extends Attribute> @NotNull T getAttributeAsOrThrow(
      @NotNull String name, @NotNull Class<T> clazz) {
    var attribute = getAttributesMap().get(name);
    if (attribute == null)
      throw new NoSuchElementException(
          "Attribute '" + name + "' of type " + clazz.getSimpleName() + " not found on: " + this);
    return clazz.cast(attribute.getAttributeOrThrow());
  }

  /**
   * Set the value of an existing named attribute.
   *
   * @param name the attribute name; the attribute must already exist in the map.
   * @param attribute the new attribute value.
   * @throws AssertionError if no attribute with the given name exists.
   */
  public void setAttribute(@NotNull String name, @NotNull Attribute attribute) {
    NamedAttribute namedAttribute = getAttributesMap().get(name);
    assert namedAttribute != null
        : MessageFormat.format("Attribute with name {0} does not exist.", name);
    namedAttribute.setAttribute(attribute);
  }

  // =========================================================================
  // Dynamic Attributes
  // =========================================================================

  /**
   * Returns all dynamic named attributes of this operation.
   *
   * @return a map from attribute name to {@link NamedAttribute}.
   */
  @Contract(pure = true)
  public @NotNull Map<String, NamedAttribute> getDynamicAttributesMap() {
    return dynamicAttributes;
  }

  @Contract(pure = true)
  public @NotNull @Unmodifiable List<NamedAttribute> getDynamicNamedAttributes() {
    return dynamicAttributes.values().stream().toList();
  }

  @Contract(pure = true)
  public @NotNull @Unmodifiable List<Attribute> getDynamicAttributes() {
    return dynamicAttributes.values().stream().map(NamedAttribute::getAttributeOrThrow).toList();
  }

  /**
   * Returns the dynamic attribute value for the given name, if present and set.
   *
   * @param name the dynamic attribute name to look up.
   * @return the {@link Attribute}, or empty if not present or not set.
   */
  @Contract(pure = true)
  public @NotNull Optional<Attribute> getDynamicAttribute(@NotNull String name) {
    if (!getDynamicAttributesMap().containsKey(name)) return Optional.empty();
    return Optional.of(getDynamicAttributesMap().get(name).getAttributeOrThrow());
  }

  /**
   * Returns the attribute for the given name cast to {@code clazz}, if present, set, and of the
   * correct type.
   *
   * @param clazz the expected attribute class.
   * @param name the attribute name.
   * @param <T> the attribute type.
   * @return the typed attribute, or empty if absent, unset, or the wrong type.
   */
  @Contract(pure = true)
  public <T extends Attribute> @NotNull Optional<T> getDynamicAttributeAs(
      @NotNull String name, @NotNull Class<T> clazz) {
    var attribute = getDynamicAttribute(name);
    if (attribute.isEmpty() || !clazz.isInstance(attribute.get())) return Optional.empty();
    return Optional.of(clazz.cast(attribute.get()));
  }

  /**
   * Returns the dynamic attribute for the given name cast to {@code clazz}, throwing if absent,
   * unset, or the wrong type.
   *
   * @param name the dattribute name.
   * @param clazz the expected attribute class.
   * @param <T> the attribute type.
   * @return the typed attribute, never {@code null}.
   * @throws NoSuchElementException if the attribute is absent, unset, or the wrong type.
   */
  @Contract(pure = true)
  public <T extends Attribute> @NotNull T getDynamicAttributeAsOrThrow(
      @NotNull String name, @NotNull Class<T> clazz) {
    var attribute = getDynamicAttributesMap().get(name);
    if (attribute == null)
      throw new NoSuchElementException(
          "Attribute '" + name + "' of type " + clazz.getSimpleName() + " not found on: " + this);
    return clazz.cast(attribute.getAttributeOrThrow());
  }

  /**
   * Set (or replace) a dynamic attribute by name.
   *
   * @param name the dynamic attribute name.
   * @param attribute the dynamic attribute value.
   */
  public void setDynamicAttribute(@NotNull String name, @NotNull Attribute attribute) {
    NamedAttribute namedAttribute = getDynamicAttributesMap().get(name);
    if (namedAttribute == null) {
      getDynamicAttributesMap().put(name, new NamedAttribute(name, attribute));
      return;
    }
    namedAttribute.setAttribute(attribute);
  }

  /**
   * Add a new dynamic attribute.
   *
   * @param name the dynamic attribute name.
   * @param attribute the dynamic attribute value.
   * @throws AssertionError if a dynamic attribute with the same name already exists.
   */
  public void addDynamicAttribute(@NotNull String name, @NotNull Attribute attribute) {
    assert !getDynamicAttributesMap().containsKey(name)
        : MessageFormat.format("Dynamic attribute with name {0} already exists.", name);
    getDynamicAttributesMap().put(name, new NamedAttribute(name, attribute));
  }

  /**
   * Remove a dynamic attribute.
   *
   * @param name the dynamic attribute name.
   * @return the removed dynamic attribute value, or empty if no entry existed for the given name.
   */
  public @NotNull Optional<Attribute> removeDynamicAttribute(@NotNull String name) {
    NamedAttribute removed = getDynamicAttributesMap().remove(name);
    if (removed == null) return Optional.empty();
    return removed.getAttribute();
  }

  // =========================================================================
  // Regions
  // =========================================================================

  /**
   * Returns the regions attached to this operation.
   *
   * @return an unmodifiable list of {@link Region}s.
   */
  @Contract(pure = true)
  public @NotNull @Unmodifiable List<Region> getRegions() {
    return regions;
  }

  /**
   * Returns the region at the given index, if present.
   *
   * @param index zero-based region index.
   * @return the region, or empty if the index is out of range.
   */
  @Contract(pure = true)
  public @NotNull Optional<Region> getRegion(int index) {
    return regions.size() > index ? Optional.of(regions.get(index)) : Optional.empty();
  }

  /**
   * Returns the region at the given index, throwing if out of range.
   *
   * @param index zero-based region index.
   * @return the region, never {@code null}.
   * @throws NoSuchElementException if the index is out of range.
   */
  @Contract(pure = true)
  public @NotNull Region getRegionOrThrow(int index) {
    if (index >= regions.size())
      throw new NoSuchElementException("No region at index " + index + " on: " + this);
    return regions.get(index);
  }

  /**
   * Returns the first region attached to this operation, if any.
   *
   * @return the first region, or empty if this operation has no regions.
   */
  @Contract(pure = true)
  public @NotNull Optional<Region> getFirstRegion() {
    return regions.isEmpty() ? Optional.empty() : Optional.of(regions.getFirst());
  }

  /**
   * Returns the first region attached to this operation, throwing if none exists.
   *
   * @return the first region, never {@code null}.
   * @throws NoSuchElementException if this operation has no regions.
   */
  @Contract(pure = true)
  public @NotNull Region getFirstRegionOrThrow() {
    if (regions.isEmpty()) throw new NoSuchElementException("Operation has no regions: " + this);
    return regions.getFirst();
  }

  // =========================================================================
  // Parent & Navigation
  // =========================================================================

  /**
   * Returns the block that contains this operation, if any.
   *
   * @return the parent block, or empty if this operation is not yet placed in a block.
   */
  @Contract(pure = true)
  public @NotNull Optional<Block> getParent() {
    return Optional.ofNullable(parent);
  }

  /**
   * Returns the block that contains this operation, throwing if unplaced.
   *
   * @return the parent block, never {@code null}.
   * @throws NoSuchElementException if this operation has no parent block.
   */
  @Contract(pure = true)
  public @NotNull Block getParentOrThrow() {
    if (parent == null) throw new NoSuchElementException("Operation has no parent block: " + this);
    return parent;
  }

  /**
   * Returns the region that contains the parent block of this operation, if any.
   *
   * @return the parent region, or empty if not available.
   */
  @Contract(pure = true)
  public @NotNull Optional<Region> getParentRegion() {
    return getParent().flatMap(Block::getParent);
  }

  /**
   * Returns the region that contains the parent block of this operation, throwing if absent.
   *
   * @return the parent region, never {@code null}.
   * @throws NoSuchElementException if not available.
   */
  @Contract(pure = true)
  public @NotNull Region getParentRegionOrThrow() {
    return getParentOrThrow()
        .getParent()
        .orElseThrow(
            () -> new NoSuchElementException("Parent block has no parent region: " + this));
  }

  /**
   * Returns the operation that owns the parent region of this operation, if any.
   *
   * @return the parent operation, or empty if this operation is at the top of the tree.
   */
  @Contract(pure = true)
  public @NotNull Optional<Operation> getParentOperation() {
    return getParentRegion().flatMap(Region::getParent);
  }

  /**
   * Returns the operation that owns the parent region of this operation, throwing if absent.
   *
   * @return the parent operation, never {@code null}.
   * @throws NoSuchElementException if this operation is at the top of the tree.
   */
  @Contract(pure = true)
  public @NotNull Operation getParentOperationOrThrow() {
    return getParentRegionOrThrow()
        .getParent()
        .orElseThrow(
            () -> new NoSuchElementException("Parent region has no parent operation: " + this));
  }

  /**
   * Set the parent block of this operation. May only be called from {@link Block}.
   *
   * @param parent the new parent block, or {@code null} to detach.
   * @throws AssertionError if called from outside {@link Block}, or if this operation already has a
   *     non-null parent and the new value is also non-null.
   */
  public void setParent(Block parent) {
    assert DgirCoreUtils.getCallingClass() == Block.class
        : MessageFormat.format(
            "Assigning the parent of an operation is only allowed from the Block class. Was called from {0}",
            DgirCoreUtils.getCallingClass().getName());
    assert this.parent == null || parent == null
        : "Operation already has a parent. Unparent first before setting a new parent. (Use the block interface to unparent.)";
    this.parent = parent;
  }

  /**
   * Walks the parent chain and returns the first parent operation that implements the given trait.
   *
   * @param traitClass The trait to search for.
   * @return The first parent operation implementing the trait, or empty if none was found.
   */
  @Contract(pure = true)
  public <T extends IOpTrait> @NotNull Optional<T> getParentWithTrait(
      @NotNull Class<T> traitClass) {
    Optional<Operation> currentParent = getParentOperation();
    if (currentParent.isEmpty()) return Optional.empty();

    while (currentParent.isPresent()) {
      if (currentParent.get().hasTrait(traitClass)) return currentParent.get().asTrait(traitClass);
      currentParent = currentParent.get().getParentOperation();
    }
    return Optional.empty();
  }

  /**
   * Get the index of this operation in its parent block's operations list.
   *
   * @return The index, or -1 if this operation has no parent.
   */
  @Contract(pure = true)
  public int getIndex() {
    return getParent().map(block -> block.getOperationsRaw().indexOf(this)).orElse(-1);
  }

  /**
   * Get the next operation in the same block as this operation.
   *
   * @return The next operation, or empty if there is none.
   */
  @Contract(pure = true)
  public @NotNull Optional<Operation> getNext() {
    return getParent()
        .map(
            block -> {
              int index = block.getOperationsRaw().indexOf(this);
              if (index == -1 || index == block.getOperationsRaw().size() - 1) return null;
              return block.getOperationsRaw().get(index + 1);
            });
  }

  /**
   * Get the previous operation in the same block as this operation.
   *
   * @return The previous operation, or empty if there is none.
   */
  @Contract(pure = true)
  public @NotNull Optional<Operation> getPrevious() {
    return getParent()
        .map(
            block -> {
              int index = block.getOperationsRaw().indexOf(this);
              if (index == -1 || index == 0) return null;
              return block.getOperationsRaw().get(index - 1);
            });
  }

  /**
   * Get the next operation in the same block as this operation, throwing if there is none.
   *
   * @return The next operation, never {@code null}.
   * @throws NoSuchElementException if this is the last operation in its block or has no parent.
   */
  @Contract(pure = true)
  public @NotNull Operation getNextOrThrow() {
    return getNext()
        .orElseThrow(() -> new NoSuchElementException("No next operation for: " + this));
  }

  @Contract(pure = true)
  public @NotNull Location getLocation() {
    return location;
  }

  // =========================================================================
  // Diagnostics
  // =========================================================================

  /**
   * Print an informational message referencing this operation to standard output.
   *
   * @param s the message text.
   */
  @Contract(pure = true)
  public void emitMessage(@NotNull String s) {
    System.out.println(MessageFormat.format("Message: {0}\n\t| {1}", this, s));
  }

  /**
   * Print a warning referencing this operation to standard output (in yellow).
   *
   * @param s the warning text.
   */
  @Contract(pure = true)
  public void emitWarning(@NotNull String s) {
    System.out.println(MessageFormat.format("\u001B[33mWarning: {0}\n\t| {1}\u001B[0m", this, s));
  }

  /**
   * Print an error referencing this operation to standard error.
   *
   * @param s the error text.
   */
  @Contract(pure = true)
  public void emitError(@NotNull String s) {
    System.err.println(MessageFormat.format("Error: {0}\n\t| {1}", this, s));
  }

  // =========================================================================
  // Object
  // =========================================================================

  @Override
  public @NotNull String toString() {
    StringBuilder sb = new StringBuilder();

    if (output != null) {
      sb.append("%");
      if (output.getValue().getDebugInfo().equals(ValueDebugInfo.UNKNOWN)) {
        sb.append(output.getValue().getType());
      } else {
        sb.append(output.getValue().getDebugInfo().name());
      }
      sb.append(" :");
      sb.append(output.getValue().getType());
      sb.append(" = ");
    }

    sb.append(getDetails().ident());

    sb.append(" (");
    sb.append(
        operands.stream()
            .map(op -> op.getValue().map(Value::toString).orElse("null"))
            .collect(Collectors.joining(", ")));
    sb.append(")");

    sb.append(" -> (");
    if (output != null) {
      sb.append(output.getValue());
    }
    sb.append(")");

    if (!attributes.isEmpty()) {
      String attrs =
          attributes.values().stream()
              .map(
                  attr ->
                      MessageFormat.format(
                          "{0} = {1}", attr.getName(), attr.getAttributeOrThrow().getStorage()))
              .collect(Collectors.joining(", "));
      if (!attrs.isEmpty()) {
        sb.append(" [ ");
        sb.append(attrs);
        sb.append(" ]");
      }
    }

    if (!dynamicAttributes.isEmpty()) {
      String dynAttrs =
          dynamicAttributes.values().stream()
              .map(
                  attr ->
                      MessageFormat.format(
                          "{0} = {1}", attr.getName(), attr.getAttributeOrThrow().getStorage()))
              .collect(Collectors.joining(", "));
      if (!dynAttrs.isEmpty()) {
        sb.append(" <dynamic [ ");
        sb.append(dynAttrs);
        sb.append(" ]>");
      }
    }

    if (!getSuccessors().isEmpty()) {
      sb.append("==> [");
      sb.append(
          getSuccessors().stream()
              .map(Block::getIndex)
              .map(Objects::toString)
              .collect(Collectors.joining(", ")));
      sb.append("]");
    }

    if (!location.equals(Location.UNKNOWN)) {
      sb.append(" @ ");
      sb.append(location);
    }

    sb.append(
        regions.stream()
            .map(region -> "{ [" + region.getIndex() + "] {" + region.getBlocks().size() + "} }")
            .reduce("", (a, b) -> a + " " + b));

    return sb.toString();
  }
}
