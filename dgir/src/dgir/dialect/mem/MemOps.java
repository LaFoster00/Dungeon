package dgir.dialect.mem;

import dgir.core.Dialect;
import dgir.core.debug.Location;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
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

public sealed interface MemOps {
  abstract class MemOp extends Op {
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return MemoryDialect.class;
    }

    @Override
    public @NotNull String getNamespace() {
      return "mem";
    }
  }

  final class AllocGcOp extends MemOp implements MemOps, IHasResult, IZeroOrOneOperand {
    @Override
    public @NotNull String getIdent() {
      return "mem.alloc_gc";
    }

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

    private AllocGcOp() {}

    public AllocGcOp(
        @NotNull Location loc,
        @NotNull MemTypes.ArrayT type,
        @NotNull Optional<Value> dynamicSize) {
      setOperation(
          Operation.Create(loc, this, dynamicSize.map(List::of).orElseGet(List::of), null, type));
    }

    public OptionalInt getStaticSize() {
      if (getArrayType().getWidth().isPresent()) {
        return getArrayType().getWidth();
      }
      return OptionalInt.empty();
    }

    public Optional<Value> getDynamicSize() {
      if (getOperand().isPresent()) {
        return Optional.of(getOperand().get().orElseThrow());
      }
      return Optional.empty();
    }

    public MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  final class ReallocGcOp extends MemOp implements MemOps, IHasResult {
    @Override
    public @NotNull String getIdent() {
      return "mem.realloc_gc";
    }

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

    private ReallocGcOp() {}

    public ReallocGcOp(
        @NotNull Location loc,
        @NotNull MemTypes.ArrayT type,
        @NotNull Value array,
        @NotNull Optional<Value> newSize) {
      setOperation(
          Operation.Create(
              loc,
              this,
              newSize.map(value -> List.of(array, value)).orElseGet(() -> List.of(array)),
              null,
              type));
    }

    public OptionalInt getStaticSize() {
      if (getArrayType().getWidth().isPresent()) {
        return getArrayType().getWidth();
      }
      return OptionalInt.empty();
    }

    public Optional<Value> getDynamicSize() {
      if (getOperand(1).isPresent()) {
        return Optional.of(getOperandValue(1).orElseThrow());
      }
      return Optional.empty();
    }

    public MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  final class CastOp extends MemOp implements MemOps, ISingleOperand, IHasResult {
    @Override
    public @NotNull String getIdent() {
      return "mem.cast";
    }

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

    private CastOp() {}

    public CastOp(@NotNull Location loc, @NotNull MemTypes.ArrayT type, @NotNull Value array) {
      setOperation(Operation.Create(loc, this, List.of(array), null, type));
    }

    public @NotNull MemTypes.ArrayT getArrayType() {
      return (MemTypes.ArrayT) getResultType();
    }
  }

  final class SizeofOp extends MemOp implements MemOps, IHasResult, ISingleOperand {
    @Override
    public @NotNull String getIdent() {
      return "mem.get_size";
    }

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

    private SizeofOp() {}

    public SizeofOp(@NotNull Location loc, @NotNull Value array) {
      setOperation(Operation.Create(loc, this, List.of(array), null, BuiltinTypes.IntegerT.INT64));
    }
  }

  final class GetElementOp extends MemOp implements MemOps, IHasResult {
    @Override
    public @NotNull String getIdent() {
      return "mem.get_element";
    }

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

    private GetElementOp() {}

    public GetElementOp(@NotNull Location loc, @NotNull Value array, @NotNull Value index) {
      if (!(array.getType() instanceof MemTypes.ArrayT arrayType)) {
        throw new IllegalArgumentException(
            "GetElementOp first operand must be of type mem.array, but got "
                + array.getType().getParameterizedIdent());
      }
      setOperation(
          Operation.Create(loc, this, List.of(array, index), null, arrayType.getElementType()));
    }

    public @NotNull Value getArrayValue() {
      return getOperandValue(0).orElseThrow();
    }

    public @NotNull Value getIndexValue() {
      return getOperandValue(1).orElseThrow();
    }
  }

  final class SetElementOp extends MemOp implements MemOps, INoResult {
    @Override
    public @NotNull String getIdent() {
      return "mem.set_element";
    }

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

    private SetElementOp() {}

    public SetElementOp(
        @NotNull Location loc, @NotNull Value array, @NotNull Value index, @NotNull Value value) {
      setOperation(Operation.Create(loc, this, List.of(array, index, value), null, null));
    }

    public @NotNull Value getArrayValue() {
      return getOperandValue(0).orElseThrow();
    }

    public @NotNull Value getIndexValue() {
      return getOperandValue(1).orElseThrow();
    }

    public @NotNull Value getValueValue() {
      return getOperandValue(2).orElseThrow();
    }
  }
}
