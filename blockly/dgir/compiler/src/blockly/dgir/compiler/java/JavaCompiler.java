package blockly.dgir.compiler.java;

import blockly.dgir.compiler.java.emission.NonValueVisitor;
import blockly.dgir.compiler.java.transformations.*;
import blockly.dgir.dialect.dg.DungeonDialect;
import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dgir.core.Dialect;
import dgir.core.IrToText;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static blockly.dgir.compiler.java.DiagnosticUtils.formatJavacDiagnostic;

public class JavaCompiler {
  static final Logger LOGGER = Logger.getLogger(JavaCompiler.class.getName());
  static boolean symbolSolverInitialized = false;

  protected JavaCompiler() {}

  public static @NotNull CompilationResult compileSource(
      @NotNull String source, @NotNull String filename) {
    StaticJavaParser.getParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    IntrinsicRegistry.init();
    CompilationUnit result;

    if (!symbolSolverInitialized) {
      symbolSolverInitialized = true;
      Map<String, String> intrinsics;
      try {
        intrinsics = IntrinsicRegistry.loadAllDungeonFiles();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      Path tempdir;
      try {
        tempdir = Files.createTempDirectory("dgir-java-compiler-intrinsics");
      } catch (IllegalArgumentException | IOException e) {
        throw new RuntimeException(
            "Failed to create temporary directory for JavaParser sources", e);
      }
      for (Map.Entry<String, String> entry : intrinsics.entrySet()) {
        Path path = tempdir.resolve(entry.getKey().replace('.', '/') + ".java");
        try {
          Files.createDirectories(path.getParent());
          Files.writeString(path, entry.getValue());
        } catch (IOException e) {
          throw new RuntimeException(
              "Failed to write Java source file for intrinsic " + entry.getKey(), e);
        }
      }

      CombinedTypeSolver typeSolver = new CombinedTypeSolver();
      typeSolver.add(new ReflectionTypeSolver());

      typeSolver.add(new JavaParserTypeSolver(tempdir));
      StaticJavaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
    }
    try {
      result = StaticJavaParser.parse(source);
    } catch (ParseProblemException e) {
      return new CompilationResult.Failure(formatParseProblems(e.getProblems(), filename, source));
    }

    EmitContext context = new EmitContext(filename, source);

    new DeadCodeElimination().visit(result, null);
    LOGGER.info("After dead code elimination:\n" + result);
    new SwitchToIf().visit(result, context);
    LOGGER.info("After switch to if:\n" + result);
    new LoopLowering().visit(result, null);
    LOGGER.info("After loop lowering:\n" + result);
    Boolean castEliminationResult = new ImplicitCastElimination().visit(result, context);
    if (castEliminationResult != null) {
      return context.asCompilationResult();
    }
    LOGGER.info("After implicit cast elimination:\n" + result);
    new LogicalBinaryToConditional().visit(result, context);
    LOGGER.info("After logical binary to conditional:\n" + result);
    new DeadCodeElimination().visit(result, null);
    LOGGER.info("After dead code elimination:\n" + result);
    new DebugSkipMarker().visit(result, null);

    // Register all dialects so that we can use them during emission.
    Dialect.registerAllDialects();
    DungeonDialect.get().register();

    NonValueVisitor.get().visit(result, context);
    CompilationResult compilationResult = context.asCompilationResult();
    if (compilationResult instanceof CompilationResult.Success success) {
      LOGGER.info(
          "Compilation successful with output program: \n"
              + IrToText.toText(success.program().getOperation()));
    }
    return context.asCompilationResult();
  }

  private static @NotNull List<LogRecord> formatParseProblems(
      @NotNull List<Problem> problems, @NotNull String filename, @NotNull String source) {
    if (problems.isEmpty()) {
      return List.of(
          new LogRecord(
              java.util.logging.Level.SEVERE,
              filename
                  + ": error: Failed to parse Java source code, but no problems were reported."));
    }

    List<String> sourceLines = List.of(source.split("\\R", -1));
    List<String> diagnostics = new ArrayList<>(problems.size());
    for (Problem problem : problems) {
      Integer line = null;
      Integer column = null;
      String sourceLine = null;

      Optional<TokenRange> tokenRange = problem.getLocation();
      if (tokenRange.isPresent()) {
        Optional<Range> range = tokenRange.get().toRange();
        if (range.isPresent()) {
          line = range.get().begin.line;
          column = range.get().begin.column;
          if (line > 0 && line <= sourceLines.size()) {
            sourceLine = sourceLines.get(line - 1);
          }
        }
      }
      diagnostics.add(
          formatJavacDiagnostic(filename, line, column, problem.getMessage(), sourceLine));
    }
    return diagnostics.stream()
        .map(msg -> new LogRecord(java.util.logging.Level.SEVERE, msg))
        .toList();
  }
}
