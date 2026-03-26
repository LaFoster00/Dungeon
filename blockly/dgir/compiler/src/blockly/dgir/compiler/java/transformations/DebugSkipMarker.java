package blockly.dgir.compiler.java.transformations;

import blockly.dgir.compiler.java.CompilerUtils;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import static blockly.dgir.compiler.java.CompilerUtils.markDebugSkip;

/**
 * Marker class for debug skips. This class goes over the ast and marks all statements that should
 * be skipped during debugging. This is used to skip statements which are reordered during
 * compilation, such as the condition and update of a for loop. Otherwise, the debugger jumps around
 * in the code.
 */
public class DebugSkipMarker extends VoidVisitorAdapter<Void> {
  @Override
  public void visit(ForStmt n, Void arg) {
    super.visit(n, null);
    markDebugSkip(n.getInitialization());
    n.getCompare().ifPresent(CompilerUtils::markDebugSkip);
    markDebugSkip(n.getUpdate());
  }

  @Override
  public void visit(WhileStmt n, Void arg) {
    super.visit(n, null);
    markDebugSkip(n.getCondition());
  }
}
