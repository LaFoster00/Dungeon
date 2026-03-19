package dgir.vm.dialect.mem;

import dgir.core.ir.Operation;
import dgir.dialect.mem.MemOps;
import dgir.dialect.mem.MemTypes;
import dgir.vm.api.Action;
import dgir.vm.api.OpRunner;
import dgir.vm.api.State;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public sealed interface MemRunners {
  final class AllocGcRunner extends OpRunner implements MemRunners {
    public AllocGcRunner() {
      super(MemOps.AllocGcOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      MemTypes.ArrayT type = (MemTypes.ArrayT) op.getOutputOrThrow().getType();
      int size;
      if (type.getWidth().isPresent()) {
        size = type.getWidth().orElseThrow();
      } else {
        size = state.getValueAsOrThrow(op.getOperandOrThrow(0), Integer.class);
      }
      state.setValueForOutput(op, new Object[size]);
      return Action.Next();
    }
  }

  final class ReallocGcRunner extends OpRunner implements MemRunners {
    public ReallocGcRunner() {
      super(MemOps.ReallocGcOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      MemTypes.ArrayT type = (MemTypes.ArrayT) op.getOutputOrThrow().getType();
      int newSize;
      if (type.getWidth().isPresent()) {
        newSize = type.getWidth().orElseThrow();
      } else {
        newSize = state.getValueAsOrThrow(op.getOperandOrThrow(1), Integer.class);
      }
      Object[] oldArray = state.getValueAsOrThrow(op.getOperandOrThrow(0), Object[].class);
      Object[] newArray = Arrays.copyOf(oldArray, newSize);
      state.setValueForOutput(op, newArray);
      return Action.Next();
    }
  }

  final class CastOpRunner extends OpRunner implements MemRunners {
    public CastOpRunner() {
      super(MemOps.CastOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      Object[] sourceArray = state.getValueAsOrThrow(op.getOperandOrThrow(0), Object[].class);
      MemTypes.ArrayT sourceType = (MemTypes.ArrayT) op.getOperandValueOrThrow(0).getType();
      MemTypes.ArrayT targetType = (MemTypes.ArrayT) op.getOutputOrThrow().getType();
      if (sourceType.equals(targetType) || targetType.getWidth().isEmpty()) {
        state.setValueForOutput(op, sourceArray);
      } else {
        int sourceSize = sourceArray.length;
        if (sourceSize != targetType.getWidth().orElseThrow()) {
          return Action.Abort(
              Optional.empty(),
              "Cannot cast array of size "
                  + sourceSize
                  + " to array of size "
                  + targetType.getWidth().orElseThrow());
        }
        state.setValueForOutput(op, sourceArray);
      }
      return Action.Next();
    }
  }

  final class SizeofOpRunner extends OpRunner implements MemRunners {
    public SizeofOpRunner() {
      super(MemOps.SizeofOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      Object[] array = state.getValueAsOrThrow(op.getOperandOrThrow(0), Object[].class);
      state.setValueForNumberOutput(op, array.length);
      return Action.Next();
    }
  }

  final class GetElementOp extends OpRunner implements MemRunners {
    public GetElementOp() {
      super(MemOps.GetElementOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      Object[] array = state.getValueAsOrThrow(op.getOperandOrThrow(0), Object[].class);
      int index = state.getValueAsOrThrow(op.getOperandOrThrow(1), Number.class).intValue();
      state.setValueForOutput(op, array[index]);
      return Action.Next();
    }
  }

  final class SetElementOp extends OpRunner implements MemRunners {
    public SetElementOp() {
      super(MemOps.SetElementOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      Object[] array = state.getValueAsOrThrow(op.getOperandOrThrow(0), Object[].class);
      int index = state.getValueAsOrThrow(op.getOperandOrThrow(1), Number.class).intValue();
      Object value = state.getValueOrThrow(op.getOperandOrThrow(2));
      array[index] = value;
      return Action.Next();
    }
  }
}
