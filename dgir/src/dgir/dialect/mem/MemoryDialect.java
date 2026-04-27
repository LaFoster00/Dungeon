package dgir.dialect.mem;

import dgir.core.ir.Dialect;
import dgir.core.ir.AttributeDescriptor;
import dgir.core.ir.Op;
import dgir.core.ir.TypeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/** Dialect registration for memory-related operations and types in namespace {@code mem}. */
public class MemoryDialect extends Dialect {
  private static MemoryDialect instance;

  /**
   * Returns the singleton memory dialect instance.
   *
   * @return the shared {@link MemoryDialect} instance.
   */
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
    return allOpsFromSealedInterface(MemOps.class);
  }

  @Override
  public @NotNull @Unmodifiable List<TypeDescriptor> allTypes() {
    return allTypesFromSealedInterface(MemTypes.MemTypeDescriptor.class);
  }

  @Override
  public @NotNull @Unmodifiable List<AttributeDescriptor> allAttributes() {
    return List.of();
  }
}
