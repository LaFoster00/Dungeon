package dgir.vm.dialect.mem;

import dgir.core.Dialect;
import dgir.dialect.mem.MemoryDialect;
import dgir.vm.api.DialectRunner;
import dgir.vm.api.OpRunner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MemoryDialectRunner extends DialectRunner {
  private static MemoryDialectRunner instance;

  public static MemoryDialectRunner get() {
    synchronized (MemoryDialectRunner.class) {
      if (instance == null) {
        instance = new MemoryDialectRunner();
      }
    }
    return instance;
  }

  private MemoryDialectRunner() {}

  @Override
  public @NotNull Dialect getDialect() {
    return MemoryDialect.get();
  }

  @Override
  public @NotNull List<@NotNull OpRunner> allRunners() {
    return allRunners(MemRunners.class);
  }
}
