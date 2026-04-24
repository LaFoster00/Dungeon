package dgir.dialect.str;

import dgir.core.Dialect;
import dgir.core.ir.AttributeDescriptor;
import dgir.core.ir.Op;
import dgir.core.ir.TypeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/** Dialect registration for string operations, types, and attributes in namespace {@code str}. */
public class StrDialect extends Dialect {
  public static StrDialect instance;

  /**
   * Returns the singleton string dialect instance.
   *
   * @return the shared {@link StrDialect} instance.
   */
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
  public @NotNull @Unmodifiable List<TypeDescriptor> allTypes() {
    return allTypes(StrTypes.StrTypeDescriptor.class);
  }

  @Override
  public @NotNull @Unmodifiable List<AttributeDescriptor> allAttributes() {
    return allAttributes(StrAttrs.StrAttrDescriptor.class);
  }
}
