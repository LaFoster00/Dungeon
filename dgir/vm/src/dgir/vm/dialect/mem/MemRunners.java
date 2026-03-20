package dgir.vm.dialect.mem;

import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.dialect.builtin.BuiltinTypes;
import dgir.dialect.mem.MemOps;
import dgir.dialect.mem.MemTypes;
import dgir.vm.api.Action;
import dgir.vm.api.OpRunner;
import dgir.vm.api.State;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public sealed interface MemRunners {
  static Object[] fillArrayWithDefaultValues(Object[] array, Type elementType) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == null) {
        switch (elementType) {
          case BuiltinTypes.IntegerT integerT -> array[i] = integerT.convertToValidNumber(0);
          case BuiltinTypes.FloatT floatT -> array[i] = floatT.convertToValidNumber(0.0);
          default -> array[i] = null;
        }
      }
    }
    return array;
  }

  static Object[] arrayWithDefaultValues(int size, Type elementType) {
    Object[] array = new Object[size];
    return fillArrayWithDefaultValues(array, elementType);
  }

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
      state.setValueForOutput(op, arrayWithDefaultValues(size, type.getElementType()));
      return Action.Next();
    }
  }

  final class AllocGcFromElementsRunner extends OpRunner implements MemRunners {
    public AllocGcFromElementsRunner() {
      super(MemOps.AllocGcFromElementsOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      MemTypes.ArrayT type = (MemTypes.ArrayT) op.getOutputOrThrow().getType();
      Object[] elements = new Object[op.getOperands().size()];
      for (int i = 0; i < elements.length; i++) {
        Object element = state.getValueOrThrow(op.getOperandOrThrow(i));
        if (type.getElementType().validate(element)) {
          elements[i] = element;
        } else {
          return Action.Abort(
              Optional.empty(),
              "Element at index "
                  + i
                  + " with value "
                  + element
                  + " is not valid for array of type "
                  + type);
        }
      }
      state.setValueForOutput(op, elements);
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
      state.setValueForOutput(op, fillArrayWithDefaultValues(newArray, type.getElementType()));
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
