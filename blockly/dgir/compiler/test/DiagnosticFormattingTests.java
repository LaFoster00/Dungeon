import blockly.dgir.compiler.java.CompilationResult;
import blockly.dgir.compiler.java.JavaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DiagnosticFormattingTests {
  @Test
  void parseErrorsAreRenderedInJavacStyle() {
    String source =
"""
public class ParseDemo {
  public static void main() {
    int x = ;
  }
}
""";

    CompilationResult result = JavaCompiler.compileSource(source, "ParseDemo.java");
    assertInstanceOf(CompilationResult.Failure.class, result);

    CompilationResult.Failure failure = (CompilationResult.Failure) result;
    assertFalse(failure.diagnostics().isEmpty());

    String firstError = failure.toString();
    assertTrue(firstError.contains("SEVERE: "));
    assertTrue(firstError.contains("ParseDemo.java:3:"));
    assertTrue(firstError.contains("int x = ;"));
    assertTrue(firstError.contains("^"));
  }

  @Test
  void semanticErrorsIncludeSourceContextAndCaret() {
    String source =
"""
public class SemanticDemo {
  public static void main() {
    int x = null;
  }
}
""";

    CompilationResult result = JavaCompiler.compileSource(source, "SemanticDemo.java");
    assertInstanceOf(CompilationResult.Failure.class, result);

    CompilationResult.Failure failure = (CompilationResult.Failure) result;
    assertFalse(failure.diagnostics().isEmpty());

    String firstError = failure.toString();
    assertTrue(firstError.contains("SEVERE: "));
    assertTrue(firstError.contains("SemanticDemo.java:3:"));
    assertTrue(firstError.contains("int x = null;"));
    assertTrue(firstError.contains("^"));
  }

  @Test
  void failureToStringReturnsPlainDiagnosticText() {
    String source =
"""
public class StringifyDemo {
  public static void main() {
    int x = ;
  }
}
""";

    CompilationResult result = JavaCompiler.compileSource(source, "StringifyDemo.java");
    assertInstanceOf(CompilationResult.Failure.class, result);

    String rendered = result.toString();
    assertTrue(rendered.contains("SEVERE: "));
    assertTrue(rendered.contains("StringifyDemo.java:3:"));
  }
}
