package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitContext;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

public class LValueVisitor extends GenericVisitorAdapter<LValueResult, EmitContext> {
  private static final LValueVisitor INSTANCE = new LValueVisitor();

  public static LValueVisitor get() {
    return INSTANCE;
  }
}
