package blockly.dgir.compiler.java;

import dgir.core.DgirCoreUtils;
import dgir.core.IrToText;
import dgir.dialect.builtin.BuiltinOps;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

public sealed interface CompilationResult {
  record Success(@NotNull BuiltinOps.ProgramOp program, @NotNull List<LogRecord> diagnostics)
      implements CompilationResult {
    @Override
    public @NonNull String toString() {
      SimpleFormatter formatter = new SimpleFormatter();
      StringBuilder sb = new StringBuilder();
      sb.append("Success[\n");
      sb.append("PROGRAM=\n\n")
          .append(DgirCoreUtils.indent(IrToText.toText(program.getOperation()), 1));
      sb.append("\n\n");
      sb.append("DIAGNOSTICS=\n")
          .append(
              DgirCoreUtils.indent(
                  diagnostics.stream()
                      .map(formatter::format)
                      .map(s -> s.substring(s.indexOf("\n") + 1))
                      .collect(Collectors.joining("/n", "", "/n")),
                  1));
      sb.append("]");
      return sb.toString();
    }
  }

  record Failure(@NotNull List<LogRecord> diagnostics) implements CompilationResult {
    @Override
    public @NonNull String toString() {
      SimpleFormatter formatter = new SimpleFormatter();
      return diagnostics.stream()
          .map(formatter::format)
          .map(s -> s.substring(s.indexOf("\n") + 1))
          .collect(Collectors.joining("\n", "", "\n"));
    }
  }
}
