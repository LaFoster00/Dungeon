package dgir.dialect.mem;

import dgir.core.DgirCoreUtils;
import dgir.core.Dialect;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDetails;
import dgir.core.ir.TypeUniquer;
import dgir.dialect.builtin.BuiltinTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

public sealed interface MemTypes {
  abstract class MemType extends Type {
    @Override
    public @NotNull String getNamespace() {
      return "mem";
    }

    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return MemoryDialect.class;
    }
  }

  final class ArrayT extends MemType implements MemTypes {
    @Override
    public @NotNull String getIdent() {
      return "mem.array";
    }

    @Override
    public @NotNull Function<Object, Boolean> getValidator() {
      return value -> {
        if (!(value instanceof Object[] objects)) return false;
        if (width != -1 && objects.length != width) return false;
        for (Object object : objects) {
          if (object != null && !elementType.getValidator().apply(object)) return false;
        }
        return true;
      };
    }

    @Override
    public @NotNull @Unmodifiable List<Type> getDefaultTypeInstances() {
      return List.of();
    }

    @Override
    public @NotNull String getParameterizedIdent() {
      return "mem.array<"
          + elementType.getParameterizedIdent()
          + (width != -1 ? ", " + width : "")
          + ">";
    }

    @Override
    public Function<Pair<String, TypeDetails>, Type> getParameterizedStringFactory() {
      return args -> {
        List<String> params = DgirCoreUtils.getParameterStrings(args.getLeft());
        if (params.isEmpty() || params.size() > 2) {
          throw new IllegalArgumentException("Invalid number of parameters for array type");
        }
        Type elementType = TypeDetails.fromParameterizedIdent(params.getFirst());
        OptionalInt width =
            params.size() == 2
                ? OptionalInt.of(Integer.parseInt(params.get(1)))
                : OptionalInt.empty();
        return ArrayT.of(elementType, width);
      };
    }

    private final @NotNull Type elementType;
    private final int width;

    private ArrayT() {
      elementType = BuiltinTypes.IntegerT.INT32;
      width = -1;
    }

    private ArrayT(@NotNull Type elementType, int width) {
      this.elementType = elementType;
      this.width = width;
    }

    public static @NotNull ArrayT of(@NotNull Type elementType, @NotNull OptionalInt width) {
      return TypeUniquer.uniqueInstance(new ArrayT(elementType, width.orElse(-1)));
    }

    public @NotNull ArrayT withSize(@NotNull OptionalInt size) {
      return ArrayT.of(elementType, size);
    }

    public @NotNull ArrayT withElementType(@NotNull Type elementType) {
      return ArrayT.of(elementType, getWidth());
    }

    public @NotNull Type getElementType() {
      return elementType;
    }

    public @NotNull OptionalInt getWidth() {
      return width == -1 ? OptionalInt.empty() : OptionalInt.of(width);
    }
  }
}
