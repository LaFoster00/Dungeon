package blockly.dgir.compiler.java;

import blockly.dgir.compiler.java.emission.NonValueVisitor;
import blockly.dgir.compiler.java.transformations.*;
import blockly.dgir.dialect.dg.DungeonDialect;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dgir.core.Dialect;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@SuppressWarnings({"unchecked"})
public class JavaCompiler {
  static Logger logger = Logger.getLogger(JavaCompiler.class.getName());
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
      logger.severe("Failed to parse Java source code: " + e.getMessage());
      logger.severe("Source code:\n" + source);
      return new CompilationResult.Failure(
          List.of(
              "Failed to parse Java source code: "
                  + e.getMessage().substring(0, e.getMessage().indexOf("Problem stacktrace : "))),
          List.of(),
          List.of());
    }

    EmitContext context = new EmitContext(filename);

    new DeadCodeElimination().visit(result, null);
    new SwitchToIf().visit(result, context);
    new LoopLowering().visit(result, null);
    Boolean castEliminationResult = new ImplicitCastElimination().visit(result, context);
    if (castEliminationResult != null) {
      return context.asCompilationResult();
    }
    new LogicalBinaryToConditional().visit(result, context);
    new DeadCodeElimination().visit(result, null);

    // Register all dialects so that we can use them during emission.
    Dialect.registerAllDialects();
    DungeonDialect.get().register();

    NonValueVisitor.get().visit(result, context);
    return context.asCompilationResult();
  }
}
