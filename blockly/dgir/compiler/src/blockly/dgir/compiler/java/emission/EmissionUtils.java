package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import dgir.core.debug.ValueDebugInfo;
import dgir.core.ir.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmissionUtils {
  public static @NotNull Optional<Value> resolveName(
      @NotNull String name, @NotNull Node site, EmitContext context) {
    var valueOpt = context.lookupSymbol(name);
    if (valueOpt.isEmpty()) {
      context.emitError(site, "Variable " + name + " is not defined in the current scope.");
      return Optional.empty();
    }
    return valueOpt;
  }

  public static void bindName(
      @NotNull String name, @NotNull Value value, @NotNull Node site, EmitContext context) {
    context.putSymbol(name, value);
    value.setDebugInfo(new ValueDebugInfo(context.loc(site), name));
  }

  public static @NotNull EmitResult<Boolean> visitNonValueNodeList(
      @NotNull NodeList<?> members, @NotNull EmitContext context) {
    List<EmitResult<Boolean>> results =
        members.stream()
            .collect(
                ArrayList::new,
                (emitResults, node) ->
                    emitResults.add(
                        EmitResult.ofNullable(node.accept(NonValueVisitor.get(), context))),
                List::addAll);
    List<Object> failedMembers = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      if (results.get(i).isFailure()) failedMembers.add(members.get(i));
    }
    return failedMembers.isEmpty() ? EmitResult.of(true) : EmitResult.failure();
  }

  public static @NotNull EmitResult<List<Value>> visitRValueNodeList(
      @NotNull NodeList<?> members, @NotNull EmitContext context) {
    List<EmitResult<Value>> results =
        members.stream()
            .collect(
                ArrayList::new,
                (emitResults, node) ->
                    emitResults.add(
                        EmitResult.ofNullable(node.accept(RValueVisitor.get(), context))),
                List::addAll);
    return results.stream().anyMatch(EmitResult::isFailure)
        ? EmitResult.failure()
        : EmitResult.of(results.stream().map(EmitResult::get).toList());
  }
}
