package dgir.dialect.mem;

import dgir.core.DgirCoreUtils;
import dgir.core.Dialect;
import dgir.core.ir.Attribute;
import dgir.core.ir.Op;
import dgir.core.ir.Type;
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
    return DgirCoreUtils.Dialect.allOps(MemoryDialect.class, MemOps.class);
  }

  @Override
  public @NotNull @Unmodifiable List<Type> allTypes() {
    return DgirCoreUtils.Dialect.allTypes(MemoryDialect.class, MemTypes.class);
  }

  @Override
  public @NotNull @Unmodifiable List<Attribute> allAttributes() {
    return List.of();
  }
}
