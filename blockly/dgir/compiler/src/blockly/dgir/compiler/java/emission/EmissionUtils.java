package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import blockly.dgir.compiler.java.IntrinsicRegistry;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import dgir.core.debug.ValueDebugInfo;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.dialect.func.FuncOps;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static blockly.dgir.compiler.java.Access.isDeclarationAccessibleFrom;
import static blockly.dgir.compiler.java.CompilerUtils.fromAstType;
import static blockly.dgir.compiler.java.CompilerUtils.resolve;
import static blockly.dgir.compiler.java.emission.Intrinsics.emitIntrinsic;
import static blockly.dgir.compiler.java.emission.Intrinsics.emitStringIntrinsicMethods;

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
    return failedMembers.isEmpty()
        ? EmitResult.of(true)
        : EmitResult.failure(
            context,
            members.get(0).getParentNode().orElseThrow(),
            "Failed to emit non value members: " + failedMembers);
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

  public static @NotNull EmitResult<Optional<Value>> emitMethodCall(
      MethodCallExpr n, EmitContext context) {
    if (n.getTypeArguments().isPresent()) {
      return EmitResult.failure(
          context,
          n,
          "Method call "
              + n.getName()
              + " has type arguments. Method calls with type arguments are not supported. Type arguments: "
              + n.getTypeArguments().get());
    }

    Optional<ResolvedMethodDeclaration> targetMethodOpt = resolve(n, context);
    if (targetMethodOpt.isEmpty()) {
      return EmitResult.failure(context, n, "Failed to resolve method call " + n.getName());
    }
    ResolvedMethodDeclaration targetMethod = targetMethodOpt.get();
    // If at any point we want to use the string just emit all the methods upfront.
    if (targetMethod.declaringType().getQualifiedName().equals("java.lang.String")) {
      emitStringIntrinsicMethods(targetMethod.declaringType(), context);
    }

    ResolvedReferenceTypeDeclaration callingClass;
    {
      // Make sure the target method is accessible from the current context. This also checks that
      // the method is
      var callingClassOpt = n.findAncestor(ClassOrInterfaceDeclaration.class);
      if (callingClassOpt.isEmpty()) {
        return EmitResult.failure(
            context,
            n,
            "Method call "
                + n.getName()
                + " is not in a class or interface. Method calls must be in a class or interface.");
      }
      var resolvedCallingClassOpt = resolve(callingClassOpt.get(), context);
      if (resolvedCallingClassOpt.isEmpty()) {
        return EmitResult.failure(
            context,
            n,
            "Failed to resolve class or interface "
                + callingClassOpt.get().getNameAsString()
                + " of method call "
                + n.getName());
      }
      callingClass = resolvedCallingClassOpt.get();
      if (!isDeclarationAccessibleFrom(callingClass, targetMethod)) {
        return EmitResult.failure(
            context,
            n,
            "Method callee "
                + targetMethod.getQualifiedName()
                + " is not visible from "
                + callingClass.getQualifiedName());
      }
    }

    EmitResult<Value> scopeResult = null;
    if (n.getScope().isPresent() && !targetMethod.isStatic()) {
      scopeResult = EmitResult.ofNullable(n.getScope().get().accept(RValueVisitor.get(), context));
      if (scopeResult.isFailure())
        return EmitResult.failure(context, n, "Failed to emit scope of method call " + n.getName());
    }

    List<Value> args;
    {
      EmitResult<List<Value>> argumentsResult;
      argumentsResult = visitRValueNodeList(n.getArguments(), context);
      if (argumentsResult.isFailure())
        return EmitResult.failure(
            context, n, "Failed to emit arguments of method call " + n.getName());

      args = new ArrayList<>(argumentsResult.get());

      // Check if the caller arguments with the callee param types and emit casts if necessary
      int varargsIndex = -1;
      for (int i = 0; i < args.size(); i++) {
        Value callArg = args.get(i);
        if (varargsIndex == -1) {
          if (targetMethod.getParam(i).isVariadic()) {
            varargsIndex = i;
          }
        }
        args.set(i, callArg);
      }
    }

    if (!targetMethod.isStatic()) {
      if (scopeResult == null)
        return EmitResult.failure(
            context,
            n,
            "Method call "
                + n.getName()
                + " is an instance method call but has no scope. Instance method calls must have a scope.");
      args.addFirst(scopeResult.get());
    }

    if (IntrinsicRegistry.intrinsics.contains(targetMethod.getQualifiedSignature())) {
      var resultOpt = emitIntrinsic(n, targetMethod.getQualifiedSignature(), args, context);
      if (resultOpt.isFailure()) {
        return EmitResult.failure();
      }
      return EmitResult.of(resultOpt.get());
    }

    String funcName = targetMethod.getQualifiedSignature();
    Optional<Type> returnType = Optional.empty();
    if (!targetMethod.getReturnType().isVoid()) {
      returnType = fromAstType(targetMethod.getReturnType(), n, context);
    }
    FuncOps.CallOp callOp =
        context.insert(new FuncOps.CallOp(context.loc(n), funcName, args, returnType.orElse(null)));

    return EmitResult.of(callOp.getOutputValue());
  }
}
