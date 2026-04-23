package dgir.dialect.mem;

import dgir.core.Dialect;
import dgir.core.ir.AttributeDescriptor;
import dgir.core.ir.Op;
import dgir.core.ir.TypeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class MemoryDialect extends Dialect {
  private static MemoryDialect instance;

  public static @NotNull MemoryDialect get() {
    synchronized (MemoryDialect.class) {
      if (instance == null) {
        instance = new MemoryDialect();
      }
      return instance;
    }
  }

  private MemoryDialect() {}

  @Override
  public @NotNull String getNamespace() {
    return "mem";
  }

  @Override
  public @NotNull @Unmodifiable List<Op> allOps() {
    return allOps(MemOps.class);
  }

  @Override
  public @NotNull @Unmodifiable List<TypeDescriptor> allTypes() {
    return allTypes(MemTypes.MemTypeDescriptor.class);
  }

  @Override
  public @NotNull @Unmodifiable List<AttributeDescriptor> allAttributes() {
    return List.of();
  }
}
