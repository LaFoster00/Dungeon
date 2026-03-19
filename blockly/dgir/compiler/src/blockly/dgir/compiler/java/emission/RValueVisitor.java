package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.CompilerUtils;
import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import blockly.dgir.compiler.java.IntrinsicRegistry;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedType;
import dgir.core.ir.Type;
import dgir.core.ir.Value;
import dgir.dialect.arith.ArithAttrs;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.builtin.BuiltinAttrs;
import dgir.dialect.builtin.BuiltinOps;
import dgir.dialect.builtin.BuiltinTypes;
import dgir.dialect.func.FuncOps;
import dgir.dialect.mem.MemOps;
import dgir.dialect.scf.ScfOps;
import dgir.dialect.str.StrOps;
import dgir.dialect.str.StrTypes;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static blockly.dgir.compiler.java.Access.isDeclarationAccessibleFrom;
import static blockly.dgir.compiler.java.Access.isTypeUseAccessibleFrom;
import static blockly.dgir.compiler.java.CompilerUtils.*;
import static blockly.dgir.compiler.java.CompilerUtils.resolve;
import static blockly.dgir.compiler.java.emission.EmissionUtils.bindName;
import static blockly.dgir.compiler.java.emission.EmissionUtils.visitRValueNodeList;
import static blockly.dgir.compiler.java.emission.Intrinsics.emitIntrinsic;
import static blockly.dgir.compiler.java.emission.Intrinsics.emitStringIntrinsicMethods;

public class RValueVisitor extends GenericVisitorAdapter<EmitResult<Value>, EmitContext> {
  private static final RValueVisitor INSTANCE = new RValueVisitor();

  public static RValueVisitor get() {
    return INSTANCE;
  }

  static final Map<String, List<String>> intrinsicEnums = new HashMap<>();

  @Override
  public EmitResult<Value> visit(AssignExpr n, EmitContext context) {
    EmitResult<LValueResult> targetRes;
    {
      targetRes = EmitResult.ofNullable(n.getTarget().accept(LValueVisitor.get(), context));
      if (targetRes.isFailure()) return EmitResult.failure();
    }

    EmitResult<Value> valueRes;
    {
      valueRes = EmitResult.ofNullable(n.getValue().accept(this, context));
      if (valueRes.isFailure()) return valueRes;
    }

    targetRes.get().apply(valueRes.get());
    // Return the value of the assignment. (e.g. for use in chained assignments like a = b = 5)
    return EmitResult.of(valueRes.get());
  }

  @Override
  public EmitResult<Value> visit(BinaryExpr n, EmitContext context) {
    EmitResult<Value> lhsResult;
    {
      lhsResult = EmitResult.ofNullable(n.getLeft().accept(this, context));
      if (lhsResult.isFailure()) return lhsResult;
    }
    EmitResult<Value> rhsResult;
    {
      rhsResult = EmitResult.ofNullable(n.getRight().accept(this, context));
      if (rhsResult.isFailure()) return rhsResult;
    }
    Value lhs = lhsResult.get();
    Value rhs = rhsResult.get();

    if (lhs.getType().equals(StrTypes.StringT.INSTANCE)
        || rhs.getType().equals(StrTypes.StringT.INSTANCE)) {
      if (n.getOperator() != BinaryExpr.Operator.PLUS) {
        context.emitError(n, "Only string concatenation is supported for strings.");
        return EmitResult.failure();
      }
      var concatOp = context.insert(new StrOps.ConcatOp(context.loc(n), lhs, rhs));
      return EmitResult.of(concatOp.getResult());
    } else {
      Optional<ArithAttrs.BinModeAttr.BinMode> binModeOpt =
          Optional.of(
              switch (n.getOperator()) {
                case PLUS -> ArithAttrs.BinModeAttr.BinMode.ADD;
                case MINUS -> ArithAttrs.BinModeAttr.BinMode.SUB;
                case MULTIPLY -> ArithAttrs.BinModeAttr.BinMode.MUL;
                case DIVIDE -> ArithAttrs.BinModeAttr.BinMode.DIV;
                case REMAINDER -> ArithAttrs.BinModeAttr.BinMode.MOD;
                case OR -> ArithAttrs.BinModeAttr.BinMode.OR;
                case AND -> ArithAttrs.BinModeAttr.BinMode.AND;
                case BINARY_OR -> ArithAttrs.BinModeAttr.BinMode.BOR;
                case BINARY_AND -> ArithAttrs.BinModeAttr.BinMode.BAND;
                case XOR -> ArithAttrs.BinModeAttr.BinMode.XOR;
                case EQUALS -> ArithAttrs.BinModeAttr.BinMode.EQ;
                case NOT_EQUALS -> ArithAttrs.BinModeAttr.BinMode.NE;
                case LESS -> ArithAttrs.BinModeAttr.BinMode.LT;
                case GREATER -> ArithAttrs.BinModeAttr.BinMode.GT;
                case LESS_EQUALS -> ArithAttrs.BinModeAttr.BinMode.LE;
                case GREATER_EQUALS -> ArithAttrs.BinModeAttr.BinMode.GE;
                case LEFT_SHIFT -> ArithAttrs.BinModeAttr.BinMode.LSH;
                case SIGNED_RIGHT_SHIFT -> ArithAttrs.BinModeAttr.BinMode.RSHS;
                case UNSIGNED_RIGHT_SHIFT -> ArithAttrs.BinModeAttr.BinMode.RSHU;
              });

      var binOp = context.insert(new ArithOps.BinaryOp(context.loc(n), lhs, rhs, binModeOpt.get()));
      return EmitResult.of(binOp.getResult());
    }
  }

  @Override
  public EmitResult<Value> visit(BooleanLiteralExpr n, EmitContext context) {
    return EmitResult.of(
        context.insert(new ArithOps.ConstantOp(context.loc(n), n.getValue())).getResult());
  }

  @Override
  public EmitResult<Value> visit(CastExpr n, EmitContext context) {
    EmitResult<Value> expressionResult;
    {
      expressionResult = EmitResult.ofNullable(n.getExpression().accept(this, context));
      if (expressionResult.isFailure())
        return EmitResult.failure(context, n.getExpression(), "Failed to emit expression of cast");
    }

    CompilerUtils.TypeInfo typeInfo;
    {
      var resolvedTypeInfo = resolveType(n.getType(), context);
      if (resolvedTypeInfo.isEmpty()) {
        return EmitResult.failure(
            context,
            n,
            "Failed to emit cast of type "
                + expressionResult.get().getType()
                + " to "
                + n.getType());
      }
      typeInfo = resolvedTypeInfo.get();
    }

    Value value = expressionResult.get();
    Type castType = typeInfo.type();
    return EmitResult.of(
        context.insert(new ArithOps.CastOp(context.loc(n), value, castType)).getResult());
  }

  @Override
  public EmitResult<Value> visit(CharLiteralExpr n, EmitContext context) {
    return EmitResult.of(
        context
            .insert(
                new ArithOps.ConstantOp(
                    context.loc(n),
                    new BuiltinAttrs.IntegerAttribute(n.asChar(), BuiltinTypes.IntegerT.UINT16)))
            .getResult());
  }

  @Override
  public EmitResult<Value> visit(ConditionalExpr n, EmitContext context) {
    EmitResult<Value> conditionRes = EmitResult.ofNullable(n.getCondition().accept(this, context));
    if (conditionRes.isFailure()) return conditionRes;

    ResolvedType resolvedOutputType;
    try {
      resolvedOutputType = n.calculateResolvedType();
    } catch (Exception e) {
      return EmitResult.failure(context, n, "Failed to resolve type of conditional expression", e);
    }
    Optional<Type> outputTypeOpt = fromAstType(resolvedOutputType, n, context);
    if (outputTypeOpt.isEmpty()) {
      return EmitResult.failure(context, n, "Failed to resolve type of conditional expression");
    }

    var ifOp =
        context.insert(
            new ScfOps.IfOp(context.loc(n), conditionRes.get(), true, outputTypeOpt.get()));
    try (var thenInsertion = context.setInsertionPoint(ifOp.getThenRegion().getEntryBlock(), -1)) {
      EmitResult<Value> thenRes = EmitResult.ofNullable(n.getThenExpr().accept(this, context));
      if (thenRes.isFailure()) return thenRes;
      context.insert(new ScfOps.YieldOp(context.loc(n), thenRes.get()));
    }

    try (var elseInsertion =
        context.setInsertionPoint(ifOp.getElseRegion().orElseThrow().getEntryBlock(), -1)) {
      EmitResult<Value> elseRes = EmitResult.ofNullable(n.getElseExpr().accept(this, context));
      if (elseRes.isFailure()) return elseRes;
      context.insert(new ScfOps.YieldOp(context.loc(n), elseRes.get()));
    }

    return EmitResult.success(ifOp.getOutputValue().orElseThrow());
  }

  @Override
  public EmitResult<Value> visit(DoubleLiteralExpr n, EmitContext context) {
    boolean isFloat = n.getValue().toLowerCase(Locale.ROOT).endsWith("f");
    return EmitResult.of(
        context
            .insert(
                new ArithOps.ConstantOp(
                    context.loc(n),
                    new BuiltinAttrs.FloatAttribute(
                        n.asDouble(),
                        isFloat ? BuiltinTypes.FloatT.FLOAT32 : BuiltinTypes.FloatT.FLOAT64)))
            .getResult());
  }

  @Override
  public EmitResult<Value> visit(EnclosedExpr n, EmitContext context) {
    return EmitResult.ofNullable(n.getInner().accept(this, context));
  }

  @Override
  public EmitResult<Value> visit(FieldAccessExpr n, EmitContext context) {
    // Check if the field is an enum constant and if so if that enum constant is part of the
    // intrinsic registry. If so, we can emit it as a constant integer value corresponding to its
    // ordinal in the enum declaration. This allows us to support intrinsic enums without having
    // to emit the full enum declaration and all its constants.
    Optional<ResolvedValueDeclaration> resolvedValueDeclarationOpt =
        CompilerUtils.resolve(n, context);
    if (resolvedValueDeclarationOpt.isEmpty()) {
      return EmitResult.failure();
    }
    ResolvedValueDeclaration resolvedValueDeclaration = resolvedValueDeclarationOpt.get();
    if (resolvedValueDeclaration.isEnumConstant()) {
      ResolvedEnumConstantDeclaration enumConstant = resolvedValueDeclaration.asEnumConstant();
      populateIntrinsicEnumLookup(enumConstant);
      List<String> enumValues = intrinsicEnums.get(enumConstant.getType().describe());
      if (enumValues == null) {
        return EmitResult.failure(
            context,
            n,
            "Failed to emit field access of enum constant "
                + enumConstant.getName()
                + " of type "
                + enumConstant.getType());
      }
      return EmitResult.of(
          context
              .insert(
                  new ArithOps.ConstantOp(
                      context.loc(n),
                      new BuiltinAttrs.IntegerAttribute(
                          enumValues.indexOf(enumConstant.getName()),
                          BuiltinTypes.IntegerT.UINT32)))
              .getResult());
    }
    // Check if we are accessing the length of an array
    else if ("length".equals(n.getName().getId())) {
      ResolvedType scopeType = n.getScope().calculateResolvedType();
      // It is <3
      if (scopeType.isArray()) {
        EmitResult<Value> scopeResult = EmitResult.ofNullable(n.getScope().accept(this, context));
        if (scopeResult.isFailure()) {
          return EmitResult.failure();
        }
        Value arrayValue = scopeResult.get();
        var lengthOp = context.insert(new MemOps.SizeofOp(context.loc(n), arrayValue));
        return EmitResult.of(lengthOp.getResult());
      }
    }
    return EmitResult.failure(context, n, "Field access is not supported.");
  }

  private void populateIntrinsicEnumLookup(ResolvedEnumConstantDeclaration enumConstant) {
    try {
      ResolvedEnumDeclaration enumTypeDeclaration =
          enumConstant
              .getType()
              .asReferenceType()
              .getTypeDeclaration()
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "Failed to resolve enum type declaration of enum constant "
                              + enumConstant.getName()))
              .asEnum();
      // Intrinsic enum constants can be resolved to integers at compile time, so we can emit them
      // directly as constants.
      if (IntrinsicRegistry.intrinsics.contains(enumTypeDeclaration.getQualifiedName())) {
        // Only populate the cache if needed
        if (intrinsicEnums.containsKey(enumConstant.getType().describe())) {
          return;
        }
        List<String> enumValues =
            intrinsicEnums.computeIfAbsent(
                enumConstant.getType().describe(), k -> new ArrayList<>());
        for (ResolvedEnumConstantDeclaration entry : enumTypeDeclaration.getEnumConstants()) {
          enumValues.add(entry.getName());
        }
      }
    } catch (Exception e) {
      System.err.println(
          "Failed to populate intrinsic enum lookup for "
              + enumConstant.getName()
              + "\n"
              + e.getMessage());
    }
  }

  @Override
  public EmitResult<Value> visit(IntegerLiteralExpr n, EmitContext context) {
    return EmitResult.of(
        context
            .insert(
                new ArithOps.ConstantOp(
                    context.loc(n),
                    new BuiltinAttrs.IntegerAttribute(
                        n.asNumber().longValue(), BuiltinTypes.IntegerT.INT32)))
            .getResult());
  }

  @Override
  public EmitResult<Value> visit(LongLiteralExpr n, EmitContext context) {
    return EmitResult.of(
        context
            .insert(
                new ArithOps.ConstantOp(
                    context.loc(n),
                    new BuiltinAttrs.IntegerAttribute(
                        n.asNumber().longValue(), BuiltinTypes.IntegerT.INT64)))
            .getResult());
  }

  @Override
  public EmitResult<Value> visit(MethodCallExpr n, EmitContext context) {
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
      scopeResult = EmitResult.ofNullable(n.getScope().get().accept(this, context));
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
      context.emitInfo(n, "Intrinsic: " + targetMethod.getQualifiedSignature());
      return emitIntrinsic(n, targetMethod.getQualifiedSignature(), args, context);
    }

    String funcName = targetMethod.getQualifiedSignature();
    Optional<Type> returnType = Optional.empty();
    if (!targetMethod.getReturnType().isVoid()) {
      returnType = fromAstType(targetMethod.getReturnType(), n, context);
      if (returnType.isEmpty()) {
        return EmitResult.failure(
            context, n, "Failed to resolve return type of method " + n.getNameAsString());
      }
    }
    FuncOps.CallOp callOp =
        context.insert(new FuncOps.CallOp(context.loc(n), funcName, args, returnType.orElse(null)));

    // TODO need to handle this with value producing function call and not.
    return EmitResult.of(callOp.getOutputValue());
  }

  @Override
  public EmitResult<Value> visit(NameExpr n, EmitContext context) {
    Optional<Value> resolved = EmissionUtils.resolveName(n.getName().asString(), n, context);
    return resolved.map(EmitResult::of).orElseGet(EmitResult::failure);
  }

  @Override
  public EmitResult<Value> visit(NullLiteralExpr n, EmitContext context) {
    return EmitResult.failure(context, n, "Null literals are not supported.");
  }

  @Override
  public EmitResult<Value> visit(StringLiteralExpr n, EmitContext context) {
    return EmitResult.of(
        context
            .insert(new ArithOps.ConstantOp(context.loc(n), n.getValue().replace("\\n", "\n")))
            .getResult());
  }

  @Override
  public EmitResult<Value> visit(UnaryExpr n, EmitContext context) {
    EmitResult<Value> operandResult;
    {
      operandResult = EmitResult.ofNullable(n.getExpression().accept(this, context));
      if (operandResult.isFailure()) return EmitResult.failure();
    }
    Value operand = operandResult.get();

    boolean postfix = false;
    boolean invalid = false;
    ArithAttrs.UnaryModeAttr.UnaryMode unaryMode =
        switch (n.getOperator()) {
          case PLUS -> null;
          case MINUS -> ArithAttrs.UnaryModeAttr.UnaryMode.NEGATE;
          case PREFIX_INCREMENT -> ArithAttrs.UnaryModeAttr.UnaryMode.INCREMENT;
          case PREFIX_DECREMENT -> ArithAttrs.UnaryModeAttr.UnaryMode.DECREMENT;
          case LOGICAL_COMPLEMENT -> ArithAttrs.UnaryModeAttr.UnaryMode.LOGICAL_COMPLEMENT;
          case BITWISE_COMPLEMENT -> ArithAttrs.UnaryModeAttr.UnaryMode.COMPLEMENT;
          case POSTFIX_INCREMENT -> {
            postfix = true;
            yield ArithAttrs.UnaryModeAttr.UnaryMode.INCREMENT;
          }
          case POSTFIX_DECREMENT -> {
            postfix = true;
            yield ArithAttrs.UnaryModeAttr.UnaryMode.DECREMENT;
          }
        };

    if (unaryMode == null) {
      if (invalid)
        return EmitResult.failure(context, n, "Unsupported unary operator " + n.getOperator());
      else return EmitResult.success(operandResult.get());
    }

    Value result = null;
    if (postfix) {
      // Copy the value to a new value and return that so we can modify the increment target.
      BuiltinOps.IdOp idOp = context.insert(new BuiltinOps.IdOp(context.loc(n), operand));
      result = idOp.getResult();
    }
    ArithOps.UnaryOp unary =
        context.insert(new ArithOps.UnaryOp(context.loc(n), operand, unaryMode));
    result = result == null ? unary.getResult() : result;
    return EmitResult.of(result);
  }

  @Override
  public @NotNull EmitResult<Value> visit(
      @NotNull VariableDeclarationExpr n, @NotNull EmitContext context) {
    {
      if (n.getAnnotations().isNonEmpty())
        context.emitWarning(
            n,
            "Variable declaration has annotations. Annotations are not supported and will be ignored. Annotations: "
                + n.getAnnotations());
    }
    if (n.getVariables().isEmpty()) {
      return EmitResult.failure(
          context, n, "Variable declaration must declare at least one variable.");
    }

    for (VariableDeclarator varDecl : n.getVariables()) {
      if (varDecl.getInitializer().isEmpty()) {
        return EmitResult.failure(context, n, "Variable declaration must have an initializer.");
      }

      EmitResult<Value> initializerResult =
          EmitResult.ofNullable(varDecl.getInitializer().orElseThrow().accept(this, context));
      if (initializerResult.isFailure()) return initializerResult;
      Value initValue = initializerResult.get();

      // Get the resolved variable declaration so that we can get the type of the variable and
      // check
      // that it is accessible from the current context.
      Optional<TypeInfo> initializerTypeInfo = resolveType(varDecl.getType(), context);
      if (initializerTypeInfo.isEmpty()) {
        return EmitResult.failure();
      }
      // Check that the target variable type is accessible in the current context
      {
        // The class in which the variable is declared
        var contextClass =
            resolve(varDecl.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow(), context);
        if (contextClass.isEmpty()) {
          return EmitResult.failure();
        }

        if (!isTypeUseAccessibleFrom(
            contextClass.get(), initializerTypeInfo.get().resolvedType())) {
          return EmitResult.failure(
              context,
              varDecl,
              "Variable type "
                  + initializerTypeInfo.get().resolvedType()
                  + " for variable "
                  + varDecl.getName()
                  + " is not visible from "
                  + contextClass);
        }
      }

      bindName(varDecl.getName().asString(), initValue, varDecl, context);
    }

    return EmitResult.of(new Value(BuiltinTypes.IntegerT.INT32));
  }
}
