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
      context.insert(new BuiltinOps.IdOp(context.loc(n), value, resolved.get()));
      return EmitResult.success(true);
    };
  }
}
