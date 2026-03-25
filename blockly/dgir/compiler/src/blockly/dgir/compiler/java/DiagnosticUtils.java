package blockly.dgir.compiler.java;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import dgir.core.debug.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Logger;

public class DiagnosticUtils {
  private static final Logger LOGGER = Logger.getLogger(DiagnosticUtils.class.getName());

  @NotNull
  public static Location loc(@NotNull String filename, @NotNull Node node) {
    if (node.getRange().isEmpty()) {
      LOGGER.warning("No range information available for AST node, using default location.");
      return Location.UNKNOWN;
    }
    Range r = node.getRange().get();
    return new Location(filename, r.begin.line, r.begin.column);
  }

  public static @NotNull String formatJavacDiagnostic(
      @NotNull String filename,
      Integer line,
      Integer column,
      @NotNull String severity,
      @NotNull String message,
      String sourceLine) {
    StringBuilder formatted = new StringBuilder();
    appendBaseMessage(formatted, filename, severity, message, line, column, sourceLine);
    return formatted.toString();
  }

  public @NotNull String formatDiagnostic(
      @NotNull String filename,
      @Nullable List<String> sourceLines,
      Node node,
      @NotNull String message,
      Object... args) {
    return formatDiagnostic(filename, sourceLines, "error", node, message, args);
  }

  public static @NotNull String formatDiagnostic(
      @NotNull String filename,
      @Nullable List<String> sourceLines,
      @NotNull String severity,
      Node node,
      @NotNull String message,
      Object... details) {
    Location loc = loc(filename, node);
    String sourceLine = getSourceLine(loc.line(), sourceLines);
    return formatDiagnostic(
        filename,
        severity,
        message,
        loc.line() > 0 ? loc.line() : null,
        loc.column() > 0 ? loc.column() : null,
        sourceLine,
        details);
  }

  public static @NotNull String formatDiagnostic(
      @NotNull String filename,
      @NotNull String severity,
      @NotNull String message,
      @Nullable Integer line,
      @Nullable Integer column,
      @Nullable String sourceLine,
      Object... details) {
    StringBuilder formatted = new StringBuilder();
    appendBaseMessage(formatted, filename, severity, message, line, column, sourceLine);
    for (Object detail : details) {
      formatted.append("\n");
      appendHeader(formatted, filename, "note", String.valueOf(detail), line, column);
    }
    return formatted.toString();
  }

  public static void appendBaseMessage(
      @NotNull StringBuilder formatted,
      @NotNull String filename,
      @NotNull String severity,
      @NotNull String message,
      @Nullable Integer line,
      @Nullable Integer column,
      @Nullable String sourceLine) {
    appendHeader(formatted, filename, severity, message, line, column);
    appendSourceLine(formatted, filename, sourceLine, column);
  }

  public static void appendHeader(
      @NotNull StringBuilder formatted,
      @NotNull String filename,
      @NotNull String severity,
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
    formatted.append(": ").append(severity).append(": ").append(message);
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
}
