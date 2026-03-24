package blockly.dgir.compiler.java;

import dgir.core.DgirCoreUtils;
import dgir.core.IrToText;
import dgir.dialect.builtin.BuiltinOps;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public sealed interface CompilationResult {
  record Success(
      @NotNull BuiltinOps.ProgramOp program,
      @NotNull List<String> infos,
      @NotNull List<String> warnings)
      implements CompilationResult {
    @Override
    public @NonNull String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Success[\n");
      sb.append("PROGRAM=\n\n")
          .append(DgirCoreUtils.indent(IrToText.toText(program.getOperation()), 1));
      sb.append("\n\nINFOS=\n").append(DgirCoreUtils.indent(String.join("\n", infos), 1));
      sb.append("\n\nWARNINGS=\n").append(DgirCoreUtils.indent(String.join("\n", warnings), 1));
      sb.append("]");
      return sb.toString();
    }
  }

  record Failure(
      @NotNull List<String> errors, @NotNull List<String> warnings, @NotNull List<String> infos)
      implements CompilationResult {
    @Override
    public @NonNull String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Failure[\n");
      sb.append("ERRORS=\n").append(DgirCoreUtils.indent(String.join("\n", errors), 1));
      sb.append("\n\nWARNINGS=\n").append(DgirCoreUtils.indent(String.join("\n", warnings), 1));
      sb.append("\n\nINFOS=\n").append(DgirCoreUtils.indent(String.join("\n", infos), 1));
      sb.append("]");
      return sb.toString();
    }
  }
}
