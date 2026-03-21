package dgir.dialect.str;

import dgir.core.Dialect;
import dgir.core.ir.Attribute;
import dgir.core.ir.Op;
import dgir.core.ir.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class StrDialect extends Dialect {
  public static StrDialect instance;

  public static @NotNull StrDialect get() {
    synchronized (StrDialect.class) {
      if (instance == null) {
        instance = new StrDialect();
      }
    }
    return instance;
  }

  private StrDialect() {}

  @Override
  public @NotNull String getNamespace() {
    return "str";
  }

  @Override
  public @NotNull @Unmodifiable List<Op> allOps() {
    return allOps(StrOps.class);
  }

  @Override
  public @NotNull @Unmodifiable List<Type> allTypes() {
    return allTypes(StrTypes.class);
  }

  @Override
  public @NotNull @Unmodifiable List<Attribute> allAttributes() {
    return allAttributes(StrAttrs.class);
  }
}
