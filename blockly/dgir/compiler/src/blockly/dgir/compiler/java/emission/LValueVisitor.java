package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import dgir.core.ir.Value;
import dgir.dialect.builtin.BuiltinOps;
import dgir.dialect.mem.MemOps;

public class LValueVisitor extends GenericVisitorAdapter<LValueResult, EmitContext> {
  private static final LValueVisitor INSTANCE = new LValueVisitor();

  public static LValueVisitor get() {
    return INSTANCE;
  }

  @Override
  public LValueResult visit(ArrayAccessExpr n, EmitContext context) {
    return value -> {
      Value array;
      {
        EmitResult<Value> result =
            EmitResult.ofNullable(n.getName().accept(RValueVisitor.get(), context));
        if (result.isFailure()) return EmitResult.failure(context, n, "Failed to resolve array");
        array = result.get();
      }
      Value index;
      {
        EmitResult<Value> result =
            EmitResult.ofNullable(n.getIndex().accept(RValueVisitor.get(), context));
        if (result.isFailure())
          return EmitResult.failure(context, n, "Failed to resolve array index");
        index = result.get();
      }
      context.insert(new MemOps.SetElementOp(context.loc(n), array, index, value));
      return EmitResult.success(true);
    };
  }

  @Override
  public LValueResult visit(NameExpr n, EmitContext context) {
    // An lvalue name expression is a reference to a variable.
    // We need to resolve the name to a location and copy the value to that location.
    // e.g. a = 5;
    return value -> {
      var resolved = EmissionUtils.resolveName(n.getNameAsString(), n, context);
      if (resolved.isEmpty())
        return EmitResult.failure(context, n, "Failed to resolve name " + n.getNameAsString());
      // Copy the value to the resolved location.
      var id = context.insert(new BuiltinOps.IdOp(context.loc(n), value, resolved.get()));
      var previousOpt = id.getPrevious();
      if (previousOpt.isPresent()) {
        if (previousOpt.get().getOutput().map(v -> v.getValue().equals(value)).orElse(false)) {
          // If the previous operation produced the value we are assigning to the name, we can just
          // set the output value
          // of the previous operation to the value we are assigning.
          previousOpt.get().setOutputValue(resolved.get());
          // Remove the id operation since it is not needed anymore.
          id.getParent().orElseThrow().removeOperation(id.getOperation());
          return EmitResult.success(true);
        }
      }
      return EmitResult.success(true);
    };
  }
}
