package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.CompilerUtils;
import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import blockly.dgir.compiler.java.IntrinsicRegistry;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import dgir.core.debug.Location;
import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.core.serialization.Utils;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.builtin.BuiltinOps;
import dgir.dialect.cf.CfOps;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;
import dgir.dialect.scf.ScfOps;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static blockly.dgir.compiler.java.CompilerUtils.*;
import static blockly.dgir.compiler.java.emission.EmissionUtils.visitNonValueNodeList;
import static blockly.dgir.compiler.java.emission.EmissionUtils.visitRValueNodeList;

public class NonValueVisitor extends GenericVisitorAdapter<EmitResult<Boolean>, EmitContext> {
  private static final NonValueVisitor INSTANCE = new NonValueVisitor();

  public static NonValueVisitor get() {
    return INSTANCE;
  }

  private record ParameterInfo(String name, Type type, ResolvedType resolvedType) {}

  private EmitResult<ParameterInfo> resolveParameter(Parameter n, EmitContext context) {
    {
      if (n.getAnnotations().isNonEmpty()) {
        context.emitWarning(
            n,
            "Parameter "
                + n.getName()
                + " has annotations. Annotations are not supported and will be ignored. Annotations: "
                + n.getAnnotations());
      }
    }

    {
      if (n.getVarArgsAnnotations().isNonEmpty()) {
        context.emitError(
            n,
            "Parameter "
                + n.getName()
                + " has varargs annotations. Varargs annotations are not supported. Annotations: "
                + n.getVarArgsAnnotations());
        return EmitResult.failure();
      }
    }

    Optional<CompilerUtils.TypeInfo> typeInfo = resolveType(n.getType(), context);
    return typeInfo
        .map(
            info ->
                EmitResult.of(
                    new ParameterInfo(
                        n.getName().getIdentifier(), info.type(), info.resolvedType())))
        .orElseGet(
            () ->
                EmitResult.failure(
                    context, n, "Failed to resolve type of parameter " + n.getName()));
  }

  @Override
  public @NonNull EmitResult<Boolean> visit(CompilationUnit n, EmitContext context) {
    BuiltinOps.ProgramOp program = new BuiltinOps.ProgramOp(context.loc(n));
    try (var programSymScope = new EmitContext.SymbolScope(context, true)) {
      context.setProgramBlock(program.getEntryBlock());

      try (var programInsertion = context.setInsertionPoint(program.getEntryBlock(), -1)) {
        {
          EmitResult<Boolean> result = visitNonValueNodeList(n.getImports(), context);
          if (result.isFailure()) return EmitResult.failure(context, n, "Failed to emit imports");
        }
        if (n.getModule().isPresent()) {
          EmitResult<Boolean> result =
              EmitResult.ofNullable(n.getModule().get().accept(this, context));
          if (result.isFailure()) return result;
        }
        if (n.getPackageDeclaration().isPresent()) {
          EmitResult<Boolean> result =
              EmitResult.ofNullable(n.getPackageDeclaration().get().accept(this, context));
          if (result.isFailure()) return result;
        }
        {
          EmitResult<Boolean> result = visitNonValueNodeList(n.getTypes(), context);
          if (result.isFailure()) return EmitResult.failure();
        }
        if (context.compilationSuccessful()) {
          context.program = program;
        } else {
          String incompleteProgram = Utils.getMapper(true).writeValueAsString(program);
          return EmitResult.failure(context, n, "Incorrect program", incompleteProgram);
        }
      }
      return EmitResult.success(true);
    }
  }

  @Override
  public EmitResult<Boolean> visit(ImportDeclaration n, EmitContext context) {
    if (IntrinsicRegistry.types.contains(n.getNameAsString())) {
      return EmitResult.success(true);
    }
    return EmitResult.failure(
        context, n, "Import of " + n.getNameAsString() + " is not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(ModuleDeclaration n, EmitContext context) {
    return EmitResult.failure(context, n, "Modules are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(PackageDeclaration n, EmitContext context) {
    return EmitResult.success(true);
  }

  @Override
  public @NotNull EmitResult<Boolean> visit(ClassOrInterfaceDeclaration n, EmitContext context) {
    {
      if (n.isInterface() || n.isRecordDeclaration() || n.isAnnotationDeclaration()) {
        return EmitResult.failure(
            context,
            n,
            "Class "
                + n.getName()
                + " is an interface. Interfaces are not supported. Type: "
                + (n.isInterface() ? "interface" : "record"));
      }
      if (n.isEnumDeclaration()) {
        return EmitResult.failure(
            context, n, "Class " + n.getName() + " is an enum. Enums are not supported.");
      }
      if (!n.isStatic() && n.findAncestor(ClassOrInterfaceDeclaration.class).isPresent()) {
        return EmitResult.failure(
            context,
            n,
            "Class " + n.getName() + " is a non-static inner class. Inner classes must be static.");
      }
    }
    {
      if (n.getExtendedTypes().isNonEmpty())
        return EmitResult.failure(
            context,
            n,
            "Class "
                + n.getName()
                + " extends a class. Extending is not supported. Extended types: "
                + n.getExtendedTypes());
    }
    {
      if (n.getImplementedTypes().isNonEmpty())
        return EmitResult.failure(
            context,
            n,
            "Class "
                + n.getName()
                + " implements an interface. Implementing is not supported. Implemented types: "
                + n.getImplementedTypes());
    }
    {
      if (n.getTypeParameters().isNonEmpty())
        return EmitResult.failure(
            context,
            n,
            "Class "
                + n.getName()
                + " has type parameters. Generics classes are not supported. Type parameters: "
                + n.getTypeParameters());
    }
    {
      if (n.getTypeParameters().isNonEmpty())
        return EmitResult.failure(
            context,
            n,
            "Class "
                + n.getName()
                + " has type parameters. Generics classes are not supported. Type parameters: "
                + n.getTypeParameters());
    }

    {
      if (n.getAnnotations().isNonEmpty())
        context.emitWarning(
            n,
            "Class "
                + n.getName()
                + " has annotations. Annotations are not supported and will be ignored. Annotations: "
                + n.getAnnotations());
    }

    {
      EmitResult<Boolean> result = visitNonValueNodeList(n.getMembers(), context);
      if (result.isFailure()) return EmitResult.failure();
    }

    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(NormalAnnotationExpr n, EmitContext context) {
    context.emitWarning(
        n, "Annotation " + n.getNameAsString() + " is not supported and will be ignored.");
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(SingleMemberAnnotationExpr n, EmitContext context) {
    context.emitWarning(
        n, "Annotation " + n.getNameAsString() + " is not supported and will be ignored.");
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(MarkerAnnotationExpr n, EmitContext context) {
    context.emitWarning(
        n, "Annotation " + n.getNameAsString() + " is not supported and will be ignored.");
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(MethodDeclaration n, EmitContext context) {
    {
      for (Modifier modifier : n.getModifiers()) {
        switch (modifier.getKeyword()) {
          case PUBLIC, PROTECTED, PRIVATE, STATIC -> {}
          default -> {
            return EmitResult.failure(
                context,
                n,
                "Method "
                    + n.getName()
                    + " has modifier "
                    + modifier
                    + ". Modifier "
                    + modifier
                    + " is not supported. Supported modifiers are: public, protected, private, static.");
          }
        }
      }
    }

    if (n.getReceiverParameter().isPresent()) {
      return EmitResult.failure(
          context,
          n,
          "Method "
              + n.getName()
              + " has a receiver parameter. Receiver parameters are not supported. Receiver parameter: "
              + n.getReceiverParameter().get());
    }

    {
      if (n.getThrownExceptions().isNonEmpty())
        return EmitResult.failure(
            context,
            n,
            "Method "
                + n.getName()
                + " declares thrown exceptions. Throwing exceptions is not supported. Thrown exceptions: "
                + n.getThrownExceptions());
    }

    {
      if (n.getAnnotations().isNonEmpty()) {
        context.emitWarning(
            n,
            "Method "
                + n.getName()
                + " has annotations. Annotations are not supported and will be ignored. Annotations: "
                + n.getAnnotations());
      }
    }

    List<ParameterInfo> parameterInfos;
    {
      parameterInfos =
          new ArrayList<>(
              n.getParameters().stream()
                  .map(parameter -> resolveParameter(parameter, context).orElse(null))
                  .toList());
      if (parameterInfos.stream().anyMatch(Objects::isNull)) {
        return EmitResult.failure();
      }
    }

    Type returnType = null;
    if (!n.getType().isVoidType()) {
      Optional<CompilerUtils.TypeInfo> resolvedType = resolveType(n.getType(), context);
      if (resolvedType.isEmpty()) {
        return EmitResult.failure();
      }
      returnType = resolvedType.get().type();
    }

    Optional<ResolvedMethodDeclaration> resolvedN = resolve(n, context);
    if (resolvedN.isEmpty()) {
      return EmitResult.failure();
    }

    try (var methodInsertion =
        context.setInsertionPoint(context.getProgramBlock().orElseThrow(), -1)) {

      // Create the function op.
      String fullyQualifiedMethodName =
          "main".equals(n.getNameAsString()) ? "main" : resolvedN.get().getQualifiedSignature();

      FuncOps.FuncOp funcOp =
          context.insert(
              new FuncOps.FuncOp(
                  context.loc(n),
                  fullyQualifiedMethodName,
                  FuncTypes.FuncType.of(
                      parameterInfos.stream().map(ParameterInfo::type).toList(), returnType)));

      // Emit all statements in the method body. These will insert themselves into the function
      // op. Also, put the function arguments in the symbol table so that they can be referenced
      // in the body.
      try (var funcBodyInsertion = context.setInsertionPoint(funcOp.getEntryBlock(), -1);
          var funcBodySymScope = new EmitContext.SymbolScope(context, true)) {
        for (int i = 0; i < parameterInfos.size(); i++) {
          context.putSymbol(parameterInfos.get(i).name, funcOp.getArgument(i).orElseThrow());
        }

        EmitResult<Boolean> result;
        if (n.getBody().isPresent()) {
          result = EmitResult.ofNullable(n.getBody().get().accept(this, context));
        } else {
          return EmitResult.failure(
              context,
              n,
              "Method "
                  + n.getNameAsString()
                  + " has no body. Abstract methods are not supported.");
        }

        // Make sure we have an implicit return in case the method has a void return type and the
        // last statement is not a return statement.
        funcOp.addImplicitTerminators();

        if (result.isFailure()) return result;
      }
    }

    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(AssertStmt n, EmitContext context) {
    EmitResult<Value> checkResult;
    {
      checkResult = EmitResult.ofNullable(n.getCheck().accept(RValueVisitor.get(), context));
      if (checkResult.isFailure()) return EmitResult.failure(context, n, "Failed to emit check");
    }

    EmitResult<Value> messageResult = null;
    if (n.getMessage().isPresent()) {
      messageResult =
          EmitResult.ofNullable(n.getMessage().get().accept(RValueVisitor.get(), context));
      if (messageResult.isFailure())
        return EmitResult.failure(context, n, "Failed to emit message");
    }

    if (messageResult != null) {
      context.insert(new CfOps.AssertOp(context.loc(n), checkResult.get(), messageResult.get()));
    } else {
      context.insert(new CfOps.AssertOp(context.loc(n), checkResult.get()));
    }
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(BlockStmt n, EmitContext context) {
    EmitResult<Boolean> result;
    {
      result = visitNonValueNodeList(n.getStatements(), context);
      if (result.isFailure()) return EmitResult.failure();
    }
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(BreakStmt n, EmitContext context) {
    if (n.getParentNode().isEmpty() || n.getParentNode().get().getParentNode().isEmpty()) {
      return EmitResult.failure(context, n, "Break statement has no parent node.");
    }

    Optional<Operation> ancestorLoopOpt =
        context.findAncestor(ScfOps.WhileOp.class, ScfOps.ForOp.class);
    if (ancestorLoopOpt.isEmpty()) {
      return EmitResult.failure(context, n, "Break statements are not supported outside loops.");
    }
    Operation ancestorLoop = ancestorLoopOpt.get();

    {
      var breakTrue = context.insert(new ArithOps.ConstantOp(context.loc(n), true));
      var skipTrue = context.insert(new ArithOps.ConstantOp(context.loc(n), true));
      // If we are inside a loop, set the break value to true.
      if (ancestorLoop.isa(ScfOps.ForOp.class)) {
        breakTrue.setOutputValue(
            ancestorLoop.getFirstRegionOrThrow().getBodyValue(1).orElseThrow());
        skipTrue.setOutputValue(ancestorLoop.getFirstRegionOrThrow().getBodyValue(2).orElseThrow());
      } else {
        breakTrue.setOutputValue(ancestorLoop.getRegionOrThrow(1).getBodyValue(0).orElseThrow());
        skipTrue.setOutputValue(ancestorLoop.getRegionOrThrow(1).getBodyValue(1).orElseThrow());
      }
    }

    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(ContinueStmt n, EmitContext context) {
    context.insert(new ScfOps.ContinueOp(context.loc(n)));
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(DoStmt n, EmitContext context) {
    // Same as while loop but body is placed in the condition region before the condition check
    ScfOps.WhileOp whileOp = context.insert(new ScfOps.WhileOp(context.loc(n)));
    {
      try (var conditionRegionSymScope = new EmitContext.SymbolScope(context, false)) {
        Block conditionBlock = whileOp.getConditionRegion().addBlock(new Block());
        Block continueBlock = whileOp.getConditionRegion().addBlock(new Block());
        continueBlock.addOperation(new ScfOps.ContinueOp(context.loc(n.getCondition())));
        Block breakBlock = whileOp.getConditionRegion().addBlock(new Block());
        breakBlock.addOperation(new ScfOps.EndOp(context.loc(n.getCondition())));

        // Open the new scope and place the comparison expression in it.
        try (var bodyInsertion =
            context.setInsertionPoint(whileOp.getConditionRegion().getEntryBlock(), -1)) {

          EmitResult<Boolean> bodyResult;
          {
            bodyResult = EmitResult.ofNullable(n.getBody().accept(this, context));
            if (bodyResult.isFailure()) return bodyResult;
          }

          // If there was a call to break or continue the skip and break flags are added
          // we need to jump to the correct block
          // If not jump to the regular condition check
          if (containsLocalFlag(n.getBody(), "skipBreak")) {
            // Create the branch to break or continue
            // The second operation is the constant op defining the skip-break flag
            context.insert(
                new CfOps.BranchCondOp(
                    context.loc(n),
                    whileOp
                        .getConditionRegion()
                        .getEntryBlock()
                        .getOperations()
                        .get(1)
                        .getOutputValueOrThrow(),
                    breakBlock,
                    conditionBlock));
          } else {
            context.insert(new CfOps.BranchOp(context.loc(n), conditionBlock));
          }
        }
        try (var conditionInsertion = context.setInsertionPoint(conditionBlock, -1)) {
          EmitResult<Value> conditionResult;
          {
            conditionResult =
                EmitResult.ofNullable(n.getCondition().accept(RValueVisitor.get(), context));
            if (conditionResult.isFailure())
              return EmitResult.failure(context, n, "Failed to emit condition");
            Value compareValue = conditionResult.get();
            context.insert(
                new CfOps.BranchCondOp(
                    context.loc(n.getCondition()), compareValue, continueBlock, breakBlock));
          }
        }
      }

      try (var continueInsertion =
              context.setInsertionPoint(whileOp.getBodyRegion().getEntryBlock(), -1);
          var continueSymScope = new EmitContext.SymbolScope(context, false)) {
        // Just add an implicit continue at the end of the loop body to jump back to the
        // condition.
        context.insert(new ScfOps.ContinueOp(context.loc(n)));
      }
    }
    whileOp.addImplicitTerminators();
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(EmptyStmt n, EmitContext context) {
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(ExplicitConstructorInvocationStmt n, EmitContext context) {
    return EmitResult.failure(
        context, n, "Explicit constructor invocation statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(ExpressionStmt n, EmitContext context) {
    var result = EmitResult.ofNullable(n.getExpression().accept(RValueVisitor.get(), context));
    if (result.isFailure()) return EmitResult.failure(context, n, "Failed to emit expression");
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(ForEachStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "For each statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(ForStmt n, EmitContext context) {
    // First emit the initialization outside the for loop.
    {
      EmitResult<List<Value>> initResult;
      {
        initResult = visitRValueNodeList(n.getInitialization(), context);
        if (initResult.isFailure()) return EmitResult.failure();
      }
    }

    // We are using a while op so that we can support more complex update expressions.
    ScfOps.WhileOp whileOp = context.insert(new ScfOps.WhileOp(context.loc(n)));
    // Open the new scope and place the comparison expression in it.
    try (var conditionSymScope = new EmitContext.SymbolScope(context, false)) {
      try (var conditionInsertion =
          context.setInsertionPoint(whileOp.getConditionRegion().getEntryBlock(), -1)) {
        Block conditionContinueBlock = whileOp.getConditionRegion().addBlock(new Block());
        conditionContinueBlock.addOperation(new ScfOps.ContinueOp(context.loc(n)));
        Block conditionBreakBlock = whileOp.getConditionRegion().addBlock(new Block());
        conditionBreakBlock.addOperation(new ScfOps.EndOp(context.loc(n)));

        if (n.getCompare().isPresent()) {
          EmitResult<Value> compareResult =
              EmitResult.ofNullable(n.getCompare().get().accept(RValueVisitor.get(), context));
          if (compareResult.isFailure()) {
            return EmitResult.failure(context, n, "Failed to emit compare expression");
          }
          Value compareValue = compareResult.get();
          context.insert(
              new CfOps.BranchCondOp(
                  context.loc(n.getCompare().get()),
                  compareValue,
                  conditionContinueBlock,
                  conditionBreakBlock));
        }
      }
    }

    // Open a new scope and place the body and update expressions inside it.
    try (var bodySymbolScope = new EmitContext.SymbolScope(context, false)) {
      // Create the body block.
      try (var bodyInsertion =
          context.setInsertionPoint(whileOp.getBodyRegion().getEntryBlock(), -1)) {
        EmitResult<Boolean> bodyResult = EmitResult.ofNullable(n.getBody().accept(this, context));
        if (bodyResult.isFailure()) {
          return EmitResult.failure();
        }

        if (n.getBody().isBlockStmt()) {
          BlockStmt blockStmt = n.getBody().asBlockStmt();
          // If there was a call to break or continue the skip and break flags are added
          // In case they exists we need to create a block for the update and one for the
          // terminate condition
          // Otherwise, just emit the update result.
          if (containsLocalFlag(blockStmt, "skip")) {
            // Create the update block. This is only called if there are no break statements in
            // the loop
            // body.
            Block updateBlock = whileOp.getBodyRegion().addBlock(new Block());
            try (var updateInsertion = context.setInsertionPoint(updateBlock, -1)) {
              EmitResult<List<Value>> updateResult = visitRValueNodeList(n.getUpdate(), context);
              if (updateResult.isFailure()) {
                return EmitResult.failure();
              }
            }

            // Create the break block. This is called if there was a break statement in the loop
            // body.
            Block breakBlock = whileOp.getBodyRegion().addBlock(new Block());
            breakBlock.addOperation(new ScfOps.EndOp(context.loc(n)));

            // Create the branch to break or continue
            // The second operation is the constant op defining the skip flag
            context.insert(
                new CfOps.BranchCondOp(
                    context.loc(n),
                    whileOp
                        .getBodyRegion()
                        .getEntryBlock()
                        .getOperations()
                        .get(1)
                        .getOutputValueOrThrow(),
                    breakBlock,
                    updateBlock));
          } else {
            EmitResult<List<Value>> updateResult = visitRValueNodeList(n.getUpdate(), context);
            if (updateResult.isFailure()) {
              return EmitResult.failure();
            }
          }
        } else {
          return EmitResult.failure(context, n, "Failed to emit body of for loop");
        }
      }
    }
    whileOp.addImplicitTerminators();
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(IfStmt n, EmitContext context) {
    EmitResult<Value> conditionResult;
    {
      conditionResult =
          EmitResult.ofNullable(n.getCondition().accept(RValueVisitor.get(), context));
      if (conditionResult.isFailure())
        return EmitResult.failure(context, n, "Failed to emit condition");
    }
    var ifOp =
        context.insert(new ScfOps.IfOp(context.loc(n), conditionResult.get(), n.hasElseBlock()));

    try (var thenInsertion = context.setInsertionPoint(ifOp.getThenRegion().getEntryBlock(), -1)) {
      EmitResult<Boolean> thenResult;
      {
        thenResult = EmitResult.ofNullable(n.getThenStmt().accept(this, context));
        if (thenResult.isFailure()) return thenResult;
      }
    }

    if (n.hasElseBlock())
      try (var elseInsertion =
          context.setInsertionPoint(ifOp.getElseRegion().orElseThrow().getEntryBlock(), -1)) {
        EmitResult<Boolean> elseResult;
        if (n.getElseStmt().isPresent()) {
          elseResult = EmitResult.ofNullable(n.getElseStmt().get().accept(this, context));
          if (elseResult.isFailure()) return elseResult;
        }
      }

    ifOp.addImplicitTerminators();
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(LabeledStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Labeled statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(LocalClassDeclarationStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Local class declaration statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(LocalRecordDeclarationStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Local record declaration statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(ReturnStmt n, EmitContext context) {
    Location trueLocation = context.loc(n);
    // Move the debug location one further down than the actual return statement, so we can step
    // to the closing curly bracket of functions and inspect the values produced.
    Location debugLocation =
        new Location(trueLocation.file(), trueLocation.line() + 1, trueLocation.column());
    if (n.getExpression().isPresent()) {
      EmitResult<Value> exprRes =
          EmitResult.ofNullable(n.getExpression().get().accept(RValueVisitor.get(), context));
      if (exprRes.isFailure()) {
        return EmitResult.failure(
            context, n, "Failed to emit return expression of return statement");
      }
      context.insert(new FuncOps.ReturnOp(debugLocation, exprRes.get()));
    } else {
      context.insert(new FuncOps.ReturnOp(debugLocation));
    }
    return EmitResult.success(true);
  }

  @Override
  public EmitResult<Boolean> visit(SwitchStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Switch statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(SynchronizedStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Synchronized statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(ThrowStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Throw statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(TryStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Try statements are not supported.");
  }

  @Override
  public EmitResult<Boolean> visit(UnparsableStmt n, EmitContext context) {
    return EmitResult.failure(context, n, "Why are u gey?");
  }

  @Override
  public EmitResult<Boolean> visit(WhileStmt n, EmitContext context) {
    ScfOps.WhileOp whileOp = context.insert(new ScfOps.WhileOp(context.loc(n)));
    {
      // Open the new scope and place the comparison expression in it.
      try (var conditionInsertion =
              context.setInsertionPoint(whileOp.getConditionRegion().getEntryBlock(), -1);
          var conditionSymScope = new EmitContext.SymbolScope(context, false)) {
        Block continueBlock = whileOp.getConditionRegion().addBlock(new Block());
        continueBlock.addOperation(new ScfOps.ContinueOp(context.loc(n.getCondition())));
        Block breakBlock = whileOp.getConditionRegion().addBlock(new Block());
        breakBlock.addOperation(new ScfOps.EndOp(context.loc(n.getCondition())));

        EmitResult<Value> conditionResult;
        {
          conditionResult =
              EmitResult.ofNullable(n.getCondition().accept(RValueVisitor.get(), context));
          if (conditionResult.isFailure())
            return EmitResult.failure(context, n, "Failed to emit condition");
          Value compareValue = conditionResult.get();
          context.insert(
              new CfOps.BranchCondOp(
                  context.loc(n.getCondition()), compareValue, continueBlock, breakBlock));
        }
      }

      try (var bodyInsertion =
              context.setInsertionPoint(whileOp.getBodyRegion().getEntryBlock(), -1);
          var conditionSymScope = new EmitContext.SymbolScope(context, false)) {
        EmitResult<Boolean> bodyResult;
        {
          bodyResult = EmitResult.ofNullable(n.getBody().accept(this, context));
          if (bodyResult.isFailure()) return bodyResult;
        }

        // If there was a call to break or continue the skip and break flags are added
        // In case they exists we need to create a block for the update and one for the
        // terminate condition
        // Otherwise, just emit the update result.
        if (containsLocalFlag(n.getBody(), "skipBreak")) {
          // Create the continue block. This is only called if there are no break statements
          // where hit.
          Block continueBlock = whileOp.getBodyRegion().addBlock(new Block());
          continueBlock.addOperation(new ScfOps.ContinueOp(context.loc(n)));
          // Create the break block. This is called if there was a break statement in the loop
          // body.
          Block breakBlock = whileOp.getBodyRegion().addBlock(new Block());
          breakBlock.addOperation(new ScfOps.EndOp(context.loc(n)));

          // Create the branch to break or continue
          // The second operation is the constant op defining the skip flag
          context.insert(
              new CfOps.BranchCondOp(
                  context.loc(n),
                  whileOp
                      .getBodyRegion()
                      .getEntryBlock()
                      .getOperations()
                      .get(1)
                      .getOutputValueOrThrow(),
                  breakBlock,
                  continueBlock));
        } else {
          return EmitResult.failure(context, n, "Failed to emit body of for loop");
        }
      }
    }
    whileOp.addImplicitTerminators();
    return EmitResult.success(true);
  }
}
