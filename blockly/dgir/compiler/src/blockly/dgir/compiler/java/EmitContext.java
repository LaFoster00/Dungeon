package blockly.dgir.compiler.java;

import blockly.dgir.compiler.SymbolTable.ScopedSymbolTable;
import com.github.javaparser.ast.Node;
import dgir.core.debug.Location;
import dgir.core.ir.Block;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.dialect.builtin.BuiltinOps;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import static blockly.dgir.compiler.java.DiagnosticUtils.formatDiagnostic;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class EmitContext {
  public static final class InsertionPoint implements AutoCloseable {
    private final @NotNull Block block;
    private int index;
    private final @NotNull EmitContext context;
    private final @NotNull Optional<InsertionPoint> previous;

    public InsertionPoint(
        @NotNull Block block,
        int index,
        @NotNull EmitContext context,
        @NotNull Optional<InsertionPoint> previous) {
      this.block = block;
      this.index = index;
      this.context = context;
      this.previous = previous;
    }

    @Override
    public void close() {
      previous().ifPresent(context::setInsertionPoint);
    }

    public Block block() {
      return block;
    }

    public int index() {
      return index;
    }

    public EmitContext context() {
      return context;
    }

    public Optional<InsertionPoint> previous() {
      return previous;
    }
  }

  public static final class SymbolScope implements AutoCloseable {
    private final @NotNull EmitContext context;

    public SymbolScope(@NotNull EmitContext context, boolean isolatedFromAbove) {
      this.context = context;
      context.symbolTable.pushScope(isolatedFromAbove);
    }

    @Override
    public void close() {
      context.symbolTable.popScope();
    }
  }

  private static final Logger LOGGER = Logger.getLogger(EmitContext.class.getName());
  private static final StackWalker CALLER_WALKER =
      StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  private final List<LogRecord> diagnostics = new ArrayList<>();

  private final @NotNull String filename;
  private final @Nullable List<String> sourceLines;

  public @Nullable BuiltinOps.ProgramOp program = null;

  private final ScopedSymbolTable<String, Value> symbolTable = ScopedSymbolTable.createRoot();

  private @Nullable Block programBlock = null;

  /** The point at which the next operation will be inserted. */
  private @Nullable InsertionPoint insertionPoint = null;

  public EmitContext(@NotNull String filename) {
    this(filename, null);
  }

  public EmitContext(@NotNull String filename, @Nullable String source) {
    this.filename = filename;
    sourceLines = source != null ? List.of(source.split("\\R", -1)) : null;
  }

  public boolean compilationSuccessful() {
    return diagnostics.stream()
        .noneMatch(record -> record.getLevel().intValue() >= Level.SEVERE.intValue());
  }

  @NotNull
  public Location loc(@NotNull Node node) {
    return DiagnosticUtils.loc(filename, node);
  }

  public void putSymbol(@NotNull String name, @NotNull Value value) {
    symbolTable.insertScoped(name, value);
  }

  public @NotNull Optional<Value> lookupSymbol(@NotNull String qualifiedMangledName) {
    return symbolTable.lookupScoped(qualifiedMangledName);
  }

  @Contract(pure = true)
  public @NotNull Optional<Block> getProgramBlock() {
    return Optional.ofNullable(programBlock);
  }

  public void setProgramBlock(@NotNull Block block) {
    programBlock = block;
  }

  /**
   * Set the insertion point for the next IR operation to be inserted.
   *
   * @param block the block to insert into. The new operation will be inserted at the index of this
   *     block.
   * @param index the index within the block to insert at. If index is -1, the new operation will be
   *     inserted at the end of the block.
   * @return an optional containing the old insertion point, or empty if there was no old insertion
   *     point.
   */
  public @Nullable InsertionPoint setInsertionPoint(@Nullable Block block, int index) {
    if (block != null) {
      insertionPoint = new InsertionPoint(block, index, this, Optional.ofNullable(insertionPoint));
    } else {
      insertionPoint = null;
    }
    return insertionPoint;
  }

  public void setInsertionPoint(@Nullable InsertionPoint insertionPoint) {
    this.insertionPoint = insertionPoint;
  }

  @SafeVarargs
  public final @NotNull Optional<Operation> findAncestor(Class<? extends Op>... clazz) {
    assert insertionPoint != null : "Insertion block must be set before finding an ancestor.";
    Operation parent = insertionPoint.block.getParentOperation().orElseThrow();
    List<Class<? extends Op>> classes = Arrays.asList(clazz);
    while (parent != null && classes.stream().noneMatch(parent::isa)) {
      parent = parent.getParentOperation().orElse(null);
    }
    return Optional.ofNullable(parent);
  }

  public @NotNull Operation insert(@NotNull Operation op) {
    assert insertionPoint != null : "Insertion block must be set before inserting an operation.";
    if (insertionPoint.index == -1) {
      insertionPoint.block.addOperation(op);
    } else {
      insertionPoint.block.addOperation(op, insertionPoint.index++);
    }
    return op;
  }

  public <OpT extends Op> @NotNull OpT insert(@NotNull OpT op) {
    insert(op.getOperation());
    return op;
  }

  /**
   * Emit an error message for the given AST node and message.
   *
   * @param node the AST node
   * @param message the diagnostic message
   * @param args any additional objects to include in the diagnostic message. These will be appended
   *     to the message.
   */
  public void emitError(Node node, String message, Object... args) {
    String diagnostic = formatDiagnostic(filename, sourceLines, node, message, args);
    diagnostics.add(getLogRecord(Level.SEVERE, diagnostic));
    LOGGER.log(Level.SEVERE, diagnostic);
  }

  /**
   * Emit a warning message for the given AST node and message.
   *
   * @param node the AST node
   * @param message the diagnostic message
   * @param args any additional objects to include in the diagnostic message. These will be appended
   *     to the message.
   */
  public void emitWarning(Node node, String message, Object... args) {
    String diagnostic = formatDiagnostic(filename, sourceLines, node, message, args);
    diagnostics.add(getLogRecord(Level.WARNING, diagnostic));
    LOGGER.log(Level.WARNING, diagnostic);
  }

  /**
   * Emit an info message for the given AST node and message.
   *
   * @param node the AST node
   * @param message the diagnostic message
   * @param args any additional objects to include in the diagnostic message. These will be appended
   *     to the message.
   */
  public void emitInfo(Node node, String message, Object... args) {
    String diagnostic = formatDiagnostic(filename, sourceLines, node, message, args);
    diagnostics.add(getLogRecord(Level.INFO, diagnostic));
    LOGGER.log(Level.INFO, diagnostic);
  }

  public CompilationResult asCompilationResult() {
    if (compilationSuccessful()) {
      assert program != null : "Program must be set before returning a CompilationResult.";
      return new CompilationResult.Success(program, diagnostics);
    } else {
      return new CompilationResult.Failure(diagnostics);
    }
  }

  public void printDiagnostics() {
    SimpleFormatter formatter = new SimpleFormatter();
    System.err.println(
        diagnostics.stream().map(formatter::format).collect(Collectors.joining("\n")));
  }

  private LogRecord getLogRecord(Level level, String diagnostic) {
    Optional<StackWalker.StackFrame> caller = callerFrame();
    LogRecord record = new LogRecord(level, diagnostic);
    record.setLoggerName(LOGGER.getName());
    caller.map(StackWalker.StackFrame::getClassName).ifPresent(record::setSourceClassName);
    caller.map(StackWalker.StackFrame::getMethodName).ifPresent(record::setSourceMethodName);
    return record;
  }

  private Optional<StackWalker.StackFrame> callerFrame() {
    return CALLER_WALKER.walk(
        frames ->
            frames.dropWhile(frame -> frame.getDeclaringClass() == EmitContext.class).findFirst());
  }
}
