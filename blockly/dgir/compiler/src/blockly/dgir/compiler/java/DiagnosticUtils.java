package blockly.dgir.compiler.java;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import dgir.core.debug.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DiagnosticUtils {
  private static final Logger LOGGER = Logger.getLogger(DiagnosticUtils.class.getName());
  private static final StackWalker CALLER_WALKER =
      StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  @NotNull
  public static Location loc(@NotNull String filename, @NotNull Node node) {
    if (node.getRange().isEmpty()) {
      logWarning("No range information available for AST node, using default location.");
      return Location.UNKNOWN;
    }
    Range r = node.getRange().get();
    return new Location(filename, r.begin.line, r.begin.column);
  }

  public static @NotNull String formatJavacDiagnostic(
      @NotNull String filename,
      Integer line,
      Integer column,
      @NotNull String message,
      String sourceLine) {
    StringBuilder formatted = new StringBuilder();
    appendBaseMessage(formatted, filename, message, line, column, sourceLine);
    return formatted.toString();
  }

  public static @NotNull String formatDiagnostic(
      @NotNull String filename,
      @Nullable List<String> sourceLines,
      Node node,
      @NotNull String message,
      Object... details) {
    Location loc = loc(filename, node);
    String sourceLine = getSourceLine(loc.line(), sourceLines);
    return formatDiagnostic(
        filename,
        message,
        loc.line() > 0 ? loc.line() : null,
        loc.column() > 0 ? loc.column() : null,
        sourceLine,
        details);
  }

  public static @NotNull String formatDiagnostic(
      @NotNull String filename,
      @NotNull String message,
      @Nullable Integer line,
      @Nullable Integer column,
      @Nullable String sourceLine,
      Object... details) {
    StringBuilder formatted = new StringBuilder();
    appendBaseMessage(formatted, filename, message, line, column, sourceLine);
    if (details.length == 0) {
      return formatted.toString();
    }
    formatted.append("\nAdditional Details:");
    formatted.append(
        Arrays.stream(details).map(Object::toString).collect(Collectors.joining("\n", "", "")));
    return formatted.toString();
  }

  public static void appendBaseMessage(
      @NotNull StringBuilder formatted,
      @NotNull String filename,
      @NotNull String message,
      @Nullable Integer line,
      @Nullable Integer column,
      @Nullable String sourceLine) {
    appendHeader(formatted, filename, message, line, column);
    appendSourceLine(formatted, filename, sourceLine, column);
  }

  public static void appendHeader(
      @NotNull StringBuilder formatted,
      @NotNull String filename,
      @NotNull String message,
      @Nullable Integer line,
      @Nullable Integer column) {
    formatted.append(filename);
    if (line != null && line > 0) {
      formatted.append(":").append(line);
      if (column != null && column > 0) {
        formatted.append(":").append(column);
      }
    }
    formatted.append(": ").append(message);
  }

  public static void appendSourceLine(
      StringBuilder formatted, @NotNull String filename, String sourceLine, Integer column) {
    if (sourceLine != null) {
      formatted.append("\n").append(sourceLine);
      if (column != null && column > 0) {
        formatted.append("\n").append(caretPrefix(filename, sourceLine, column)).append("^");
      }
    }
  }

  public static @NotNull String caretPrefix(
      @NotNull String filename, @NotNull String sourceLine, int oneBasedColumn) {
    int toIndex = Math.min(Math.max(oneBasedColumn - 1, 0), sourceLine.length());
    StringBuilder prefix = new StringBuilder(toIndex);
    for (int i = 0; i < toIndex; i++) {
      prefix.append(sourceLine.charAt(i) == '\t' ? '\t' : ' ');
    }
    return prefix.toString();
  }

  public static @Nullable String getSourceLine(
      int oneBasedLine, @Nullable List<String> sourceLines) {
    if (sourceLines == null || oneBasedLine <= 0 || oneBasedLine > sourceLines.size()) {
      return null;
    }
    return sourceLines.get(oneBasedLine - 1);
  }

  private static void logWarning(@NotNull String message) {
    if (!LOGGER.isLoggable(Level.WARNING)) {
      return;
    }

    Optional<StackWalker.StackFrame> caller = callerFrame();

    LogRecord record = new LogRecord(Level.WARNING, message);
    record.setLoggerName(LOGGER.getName());
    caller.map(StackWalker.StackFrame::getClassName).ifPresent(record::setSourceClassName);
    caller.map(StackWalker.StackFrame::getMethodName).ifPresent(record::setSourceMethodName);
    LOGGER.log(record);
  }

  private static Optional<StackWalker.StackFrame> callerFrame() {
    return CALLER_WALKER.walk(
        frames ->
            frames
                .dropWhile(
                    frame ->
                        frame.getDeclaringClass() == DiagnosticUtils.class
                            || frame.getDeclaringClass() == EmitContext.class)
                .findFirst());
  }
}
