package blockly.dgir.dialect.dg;

import dgir.core.ir.Dialect;
import dgir.core.ir.AttributeDescriptor;
import dgir.core.ir.Op;
import dgir.core.ir.TypeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class DungeonDialect extends Dialect {
  private static DungeonDialect instance;

  public static @NotNull DungeonDialect get() {
    synchronized (DungeonDialect.class) {
      if (instance == null) {
        instance = new DungeonDialect();
      }
    }
    return instance;
  }

  private DungeonDialect() {}

  @NotNull
  @Override
  public String getNamespace() {
    return "dg";
  }

  @Override
  public @NotNull @Unmodifiable List<Op> allOps() {
    return allOpsFromSealedInterface(DgOps.class);
  }

  @NotNull
  @Unmodifiable
  @Override
  public List<TypeDescriptor> allTypes() {
    return List.of();
  }

  @NotNull
  @Unmodifiable
  @Override
  public List<AttributeDescriptor> allAttributes() {
    return List.of();
  }
}
