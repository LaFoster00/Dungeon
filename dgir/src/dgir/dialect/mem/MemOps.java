package dgir.dialect.mem;

import dgir.core.Dialect;
import dgir.core.debug.Location;
import dgir.core.ir.*;
import dgir.core.traits.IHasResult;
import dgir.core.traits.INoResult;
import dgir.core.traits.ISingleOperand;
import dgir.core.traits.IZeroOrOneOperand;
import dgir.dialect.builtin.BuiltinTypes;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Sealed marker interface for all operations in the {@link MemoryDialect}.
 *
 * <p>Every concrete op must both extend {@link MemOp} and implement this interface so that {@link
 * Dialect#allOps(Class)} can discover it automatically via reflection.
 */
public sealed interface MemOps {
  /**
   * Abstract base class for all operations in the {@code mem} (memory) dialect.
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and {@link #getVerifier()}, and must
   * implement {@link MemOps} to be enumerated by {@link MemoryDialect}.
   */
  abstract class MemOp extends Op {
    // =========================================================================
    // Op Info
    // =========================================================================

    /**
     * Returns the dialect that owns this operation.
     *
     * @return the {@link MemoryDialect} class.
     */
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return MemoryDialect.class;
    }

    /**
     * Returns the namespace prefix used when printing this operation.
     *
     * @return the fixed {@code "mem"} namespace.
     */
    @Override
    public @NotNull String getNamespace() {
      return "mem";
    }
  }

  /**
   * Allocates a GC-managed array in the {@code mem} dialect.
   *
   * <p>The allocation may be specified either by a static width or by a dynamic size operand.
   */
  final class AllocGcOp extends MemOp implements MemOps, IHasResult, IZeroOrOneOperand {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.alloc_gc"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.alloc_gc";
    }

    /**
     * Verifies that the allocation uses a valid size specification.
     *
     * @return a verifier that accepts well-formed allocation operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        AllocGcOp op = operation.as(AllocGcOp.class).orElseThrow();
        if (op.getOperand().isPresent()
            && !(op.getOperand().get().orElseThrow().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError(
              "AllocGcOp dynamic size operand must be of type builtin.int, but got "
                  + op.getOperand().get().get().getType().getParameterizedIdent());
          return false;
        }
        if (op.getStaticSize().isPresent()) {
          if (op.getDynamicSize().isPresent()) {
            operation.emitError("AllocGcOp cannot have both static and dynamic size");
            return false;
          }
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private AllocGcOp() {}

    /**
     * Creates a GC allocation with a static size.
     *
     * @param loc the source location of this operation.
     * @param elementType the array element type.
     * @param staticSize the allocated width.
     * @param explicitBound whether the width should be encoded as a fixed bound.
     */
    public AllocGcOp(
        @NotNull Location loc, @NotNull Type elementType, int staticSize, boolean explicitBound) {
      setOperation(
          Operation.Create(
              loc,
              this,
              null,
              null,
              MemTypes.ArrayT.of(
                  elementType, explicitBound ? OptionalInt.of(staticSize) : OptionalInt.empty())));
    }

    /**
     * Creates a GC allocation whose width is computed dynamically.
     *
     * @param loc the source location of this operation.
     * @param elementType the array element type.
     * @param dynamicSize the runtime size operand.
     */
    public AllocGcOp(@NotNull Location loc, @NotNull Type elementType, @NotNull Value dynamicSize) {
      setOperation(
          Operation.Create(
              loc,
              this,
              List.of(dynamicSize),
              null,
              MemTypes.ArrayT.of(elementType, OptionalInt.empty())));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the statically encoded allocation width, if present.
     *
     * @return the static size, or an empty optional for dynamic allocations.
     */
    public OptionalInt getStaticSize() {
      if (getArrayType().getWidth().isPresent()) {
        return getArrayType().getWidth();
      }
      return OptionalInt.empty();
    }

    /**
     * Returns the dynamic size operand, if present.
     *
     * @return the dynamic size operand, or an empty optional for static allocations.
     */
    public Optional<Value> getDynamicSize() {
      if (getOperand().isPresent()) {
        return Optional.of(getOperand().get().orElseThrow());
      }
      return Optional.empty();
    }

    /**
     * Returns the array result type produced by this allocation.
     *
     * @return the {@link MemTypes.ArrayT} result type.
     */
    public MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  /** Allocates a GC-managed array from an explicit list of element values. */
  final class AllocGcFromElementsOp extends MemOp implements MemOps, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.alloc_gc_from_elements"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.alloc_gc_from_elements";
    }

    /**
     * Verifies that all element operands match the array element type.
     *
     * @return a verifier that accepts well-formed element-based allocations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        AllocGcFromElementsOp op = operation.as(AllocGcFromElementsOp.class).orElseThrow();
        for (ValueOperand operand : op.getOperands()) {
          Value element = operand.getValue().orElseThrow();
          if (!element.getType().equals(op.getArrayType().getElementType())) {
            operation.emitError(
                "AllocGcFromElementsOp element operand type does not match array element type.\nExpected element type: "
                    + op.getArrayType().getElementType().getParameterizedIdent()
                    + "\nProvided element type: "
                    + element.getType().getParameterizedIdent());
            return false;
          }
        }
        if (op.getArrayType().getWidth().isPresent()) {
          if (op.getOperands().size() != op.getArrayType().getWidth().getAsInt()) {
            operation.emitError(
                "AllocGcFromElementsOp number of operands does not match array width.\nExpected width: "
                    + op.getArrayType().getWidth().getAsInt()
                    + "\nProvided operands: "
                    + op.getOperands().size());
          }
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private AllocGcFromElementsOp() {}

    /**
     * Creates a GC allocation from explicit element values.
     *
     * @param loc the source location of this operation.
     * @param elementType the array element type.
     * @param elements the element values used to initialize the array.
     * @param explicitBound whether the array width should be fixed to the element count.
     */
    public AllocGcFromElementsOp(
        Location loc, Type elementType, List<Value> elements, boolean explicitBound) {
      setOperation(
          Operation.Create(
              loc,
              this,
              elements,
              null,
              MemTypes.ArrayT.of(
                  elementType,
                  explicitBound ? OptionalInt.of(elements.size()) : OptionalInt.empty())));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the array result type produced by this allocation.
     *
     * @return the {@link MemTypes.ArrayT} result type.
     */
    public MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  /** Reallocates an existing GC-managed array to a new size. */
  final class ReallocGcOp extends MemOp implements MemOps, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.realloc_gc"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.realloc_gc";
    }

    /**
     * Verifies that the reallocation size and array types are consistent.
     *
     * @return a verifier that accepts well-formed reallocation operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        ReallocGcOp op = operation.as(ReallocGcOp.class).orElseThrow();
        if (op.getStaticSize().isPresent()) {
          if (op.getDynamicSize().isPresent()) {
            operation.emitError("ReallocGcOp cannot have both static and dynamic size");
            return false;
          }
        }
        if (!(op.getOperandValue(0).orElseThrow().getType() instanceof MemTypes.ArrayT inputType)) {
          operation.emitError(
              "ReallocGcOp operand must be of type mem.array, but got "
                  + op.getOperandValue(0).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        MemTypes.ArrayT outputType = op.getArrayType();
        if (!inputType.equals(outputType)) {
          operation.emitError(
              "ReallocGcOp input and output array types must have the same element type and width (can be unbounded).\nInput type: "
                  + inputType.getParameterizedIdent()
                  + "\nOutput type: "
                  + outputType.getParameterizedIdent());
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private ReallocGcOp() {}

    /**
     * Creates a reallocation with a static target size.
     *
     * @param loc the source location of this operation.
     * @param array the array to reallocate.
     * @param newSize the new width.
     * @param explicitBound whether the width should be encoded as a fixed bound.
     */
    public ReallocGcOp(
        @NotNull Location loc, @NotNull Value array, int newSize, boolean explicitBound) {
      if (!(array.getType() instanceof MemTypes.ArrayT arrayType)) {
        throw new IllegalArgumentException(
            "ReallocGcOp operand must be of type mem.array, but got "
                + array.getType().getParameterizedIdent());
      }
      setOperation(
          Operation.Create(
              loc,
              this,
              List.of(array),
              null,
              arrayType.withSize(explicitBound ? OptionalInt.of(newSize) : OptionalInt.empty())));
    }

    /**
     * Creates a reallocation whose target size is provided dynamically.
     *
     * @param loc the source location of this operation.
     * @param array the array to reallocate.
     * @param newSize the runtime size operand.
     */
    public ReallocGcOp(@NotNull Location loc, @NotNull Value array, @NotNull Value newSize) {
      if (!(array.getType() instanceof MemTypes.ArrayT arrayType)) {
        throw new IllegalArgumentException(
            "ReallocGcOp operand must be of type mem.array, but got "
                + array.getType().getParameterizedIdent());
      }
      setOperation(
          Operation.Create(
              loc, this, List.of(array, newSize), null, arrayType.withSize(OptionalInt.empty())));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the statically encoded target width, if present.
     *
     * @return the static size, or an empty optional for dynamic reallocation.
     */
    public OptionalInt getStaticSize() {
      if (getArrayType().getWidth().isPresent()) {
        return getArrayType().getWidth();
      }
      return OptionalInt.empty();
    }

    /**
     * Returns the dynamic size operand, if present.
     *
     * @return the dynamic size operand, or an empty optional for static reallocation.
     */
    public Optional<Value> getDynamicSize() {
      if (getOperand(1).isPresent()) {
        return Optional.of(getOperandValue(1).orElseThrow());
      }
      return Optional.empty();
    }

    /**
     * Returns the array result type produced by this reallocation.
     *
     * @return the {@link MemTypes.ArrayT} result type.
     */
    public MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  /** Casts one array type to another compatible array type. */
  final class CastOp extends MemOp implements MemOps, ISingleOperand, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.cast"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.cast";
    }

    /**
     * Verifies that the input and output array types are compatible.
     *
     * @return a verifier that accepts well-formed cast operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        CastOp op = operation.as(CastOp.class).orElseThrow();
        if (!(op.getOperandValue(0).orElseThrow().getType() instanceof MemTypes.ArrayT inputType)) {
          operation.emitError(
              "CastOp operand must be of type mem.array, but got "
                  + op.getOperandValue(0).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        MemTypes.ArrayT outputType = op.getArrayType();

        if (inputType.equals(outputType)) {
          operation.emitWarning("CastOp input and output array types are the same");
          return true;
        }

        if (!inputType.getElementType().equals(outputType.getElementType())) {
          operation.emitError(
              "CastOp input and output array types must have the same element type.\nInput type: "
                  + inputType.getParameterizedIdent()
                  + "\nOutput type: "
                  + outputType.getParameterizedIdent());
          return false;
        }
        if (inputType.getWidth().isPresent() && outputType.getWidth().isPresent()) {
          if (inputType.getWidth().getAsInt() != outputType.getWidth().getAsInt()) {
            operation.emitError(
                "CastOp input and output array types must have the same width if both are statically sized.\nInput type: "
                    + inputType.getParameterizedIdent()
                    + "\nOutput type: "
                    + outputType.getParameterizedIdent());
            return false;
          }
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private CastOp() {}

    /**
     * Creates an array cast to the provided target type.
     *
     * @param loc the source location of this operation.
     * @param type the target array type.
     * @param array the array value to cast.
     */
    public CastOp(@NotNull Location loc, @NotNull MemTypes.ArrayT type, @NotNull Value array) {
      setOperation(Operation.Create(loc, this, List.of(array), null, type));
    }

    /**
     * Returns the target array type produced by this cast.
     *
     * @return the {@link MemTypes.ArrayT} result type.
     */
    public @NotNull MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  /** Returns the size of an array value as a 64-bit integer. */
  final class SizeofOp extends MemOp implements MemOps, IHasResult, ISingleOperand {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.get_size"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.get_size";
    }

    /**
     * Verifies that the operand is an array value.
     *
     * @return a verifier that accepts well-formed size-of operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        SizeofOp op = operation.as(SizeofOp.class).orElseThrow();
        if (!(op.getOperandValue(0).orElseThrow().getType() instanceof MemTypes.ArrayT)) {
          operation.emitError(
              "GetSizeOp operand must be of type mem.array, but got "
                  + op.getOperandValue(0).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private SizeofOp() {}

    /**
     * Creates a size-of operation for the given array.
     *
     * @param loc the source location of this operation.
     * @param array the array whose size should be computed.
     */
    public SizeofOp(@NotNull Location loc, @NotNull Value array) {
      setOperation(Operation.Create(loc, this, List.of(array), null, BuiltinTypes.IntegerT.INT64));
    }
  }

  /** Loads an element from a GC-managed array. */
  final class GetElementOp extends MemOp implements MemOps, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.get_element"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.get_element";
    }

    /**
     * Verifies that the array and index operands have valid types.
     *
     * @return a verifier that accepts well-formed element reads.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        GetElementOp op = operation.as(GetElementOp.class).orElseThrow();
        if (!(op.getArrayValue().getType() instanceof MemTypes.ArrayT)) {
          operation.emitError(
              "GetElementOp first operand must be of type mem.array, but got "
                  + op.getOperandValue(0).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        if (!(op.getIndexValue().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError(
              "GetElementOp second operand must be of type builtin.int, but got "
                  + op.getOperandValue(1).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private GetElementOp() {}

    /**
     * Creates an element-read operation.
     *
     * @param loc the source location of this operation.
     * @param array the array to read from.
     * @param index the element index.
     */
    public GetElementOp(@NotNull Location loc, @NotNull Value array, @NotNull Value index) {
      if (!(array.getType() instanceof MemTypes.ArrayT arrayType)) {
        throw new IllegalArgumentException(
            "GetElementOp first operand must be of type mem.array, but got "
                + array.getType().getParameterizedIdent());
      }
      setOperation(
          Operation.Create(loc, this, List.of(array, index), null, arrayType.getElementType()));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the array operand.
     *
     * @return the array value at operand position {@code 0}.
     */
    public @NotNull Value getArrayValue() {
      return getOperandValue(0).orElseThrow();
    }

    /**
     * Returns the index operand.
     *
     * @return the index value at operand position {@code 1}.
     */
    public @NotNull Value getIndexValue() {
      return getOperandValue(1).orElseThrow();
    }
  }

  /** Stores an element into a GC-managed array. */
  final class SetElementOp extends MemOp implements MemOps, INoResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "mem.set_element"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "mem.set_element";
    }

    /**
     * Verifies that the array, index, and value operands have valid types.
     *
     * @return a verifier that accepts well-formed element writes.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        SetElementOp op = operation.as(SetElementOp.class).orElseThrow();
        if (!(op.getArrayValue().getType() instanceof MemTypes.ArrayT arrayType)) {
          operation.emitError(
              "SetElementOp first operand must be of type mem.array, but got "
                  + op.getOperandValue(0).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        if (!(op.getIndexValue().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError(
              "SetElementOp second operand must be of type builtin.int, but got "
                  + op.getOperandValue(1).orElseThrow().getType().getParameterizedIdent());
          return false;
        }
        if (!arrayType.getElementType().equals(op.getValueValue().getType())) {
          operation.emitError(
              "SetElementOp value operand is not valid for the element type of the array.\nExpected element type: "
                  + arrayType.getElementType().getParameterizedIdent()
                  + "\nProvided value type: "
                  + op.getValueValue().getType().getParameterizedIdent());
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private SetElementOp() {}

    /**
     * Creates an element-write operation.
     *
     * @param loc the source location of this operation.
     * @param array the array to modify.
     * @param index the element index.
     * @param value the value to store.
     */
    public SetElementOp(
        @NotNull Location loc, @NotNull Value array, @NotNull Value index, @NotNull Value value) {
      setOperation(Operation.Create(loc, this, List.of(array, index, value), null, null));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the array operand.
     *
     * @return the array value at operand position {@code 0}.
     */
    public @NotNull Value getArrayValue() {
      return getOperandValue(0).orElseThrow();
    }

    /**
     * Returns the index operand.
     *
     * @return the index value at operand position {@code 1}.
     */
    public @NotNull Value getIndexValue() {
      return getOperandValue(1).orElseThrow();
    }

    /**
     * Returns the value operand to be written into the array.
     *
     * @return the element value at operand position {@code 2}.
     */
    public @NotNull Value getValueValue() {
      return getOperandValue(2).orElseThrow();
    }
  }
}
