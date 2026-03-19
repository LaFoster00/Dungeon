package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import blockly.dgir.dialect.dg.DgOps;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import dgir.core.SymbolTable;
import dgir.core.debug.Location;
import dgir.core.ir.Value;
import dgir.dialect.arith.ArithAttrs;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.builtin.BuiltinTypes;
import dgir.dialect.func.FuncOps;
import dgir.dialect.func.FuncTypes;
import dgir.dialect.io.IoOps;
import dgir.dialect.str.StrOps;
import dgir.dialect.str.StrTypes;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import static blockly.dgir.compiler.java.emission.EmissionUtils.visitRValueNodeList;

public class Intrinsics {
  public static EmitResult<Optional<Value>> emitIntrinsic(
      MethodCallExpr n, String intrinsicName, List<Value> args, EmitContext context) {
    switch (intrinsicName) {

      // Hero Operations
      case "Dungeon.Hero.move()" -> {
        context.insert(new DgOps.MoveOp(context.loc(n)));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.rotate(Dungeon.Direction)" -> {
        EmitResult<List<Value>> arguments = visitRValueNodeList(n.getArguments(), context);
        if (arguments.isFailure() || arguments.get().size() != 1) return EmitResult.failure();
        Value directionValue = arguments.get().getFirst();
        context.insert(new DgOps.RotateOp(context.loc(n), directionValue));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.interact(Dungeon.Direction)" -> {
        EmitResult<List<Value>> arguments = visitRValueNodeList(n.getArguments(), context);
        if (arguments.isFailure() || arguments.get().size() != 1) return EmitResult.failure();
        Value directionValue = arguments.get().getFirst();
        context.insert(new DgOps.InteractOp(context.loc(n), directionValue));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.push()" -> {
        context.insert(new DgOps.PushOp(context.loc(n)));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.pull()" -> {
        context.insert(new DgOps.PullOp(context.loc(n)));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.drop(Dungeon.ItemType)" -> {
        EmitResult<List<Value>> arguments = visitRValueNodeList(n.getArguments(), context);
        if (arguments.isFailure() || arguments.get().size() != 1) return EmitResult.failure();
        Value itemTypeValue = arguments.get().getFirst();
        context.insert(new DgOps.DropOp(context.loc(n), itemTypeValue));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.pickUp()" -> {
        context.insert(new DgOps.PickupOp(context.loc(n)));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.fireball()" -> {
        context.insert(new DgOps.FireballOp(context.loc(n)));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.rest()" -> {
        context.insert(new DgOps.RestOp(context.loc(n)));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.isNearTile(Dungeon.LevelElement, Dungeon.Direction)" -> {
        EmitResult<List<Value>> arguments = visitRValueNodeList(n.getArguments(), context);
        if (arguments.isFailure() || arguments.get().size() != 2) return EmitResult.failure();
        Value levelElementValue = arguments.get().getFirst();
        Value directionValue = arguments.get().get(1);
        var op =
            context.insert(
                new DgOps.IsNearTileOp(context.loc(n), levelElementValue, directionValue));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "Dungeon.Hero.matchesTile(Dungeon.LevelElement, Dungeon.LevelElement)" -> {
        EmitResult<List<Value>> arguments = visitRValueNodeList(n.getArguments(), context);
        if (arguments.isFailure() || arguments.get().size() != 2) return EmitResult.failure();
        Value levelElementValue1 = arguments.get().getFirst();
        Value levelElementValue2 = arguments.get().get(1);
        var op =
            context.insert(
                new ArithOps.BinaryOp(
                    context.loc(n),
                    levelElementValue1,
                    levelElementValue2,
                    ArithAttrs.BinModeAttr.BinMode.EQ));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "Dungeon.Hero.active(Dungeon.Direction)" -> {
        EmitResult<List<Value>> arguments = visitRValueNodeList(n.getArguments(), context);
        if (arguments.isFailure() || arguments.get().size() != 1) return EmitResult.failure();
        Value directionValue = arguments.get().getFirst();
        var op = context.insert(new DgOps.IsActiveOp(context.loc(n), directionValue));
        return EmitResult.of(Optional.of(op.getResult()));
      }

      // IO Operations

      case "Dungeon.IO.print(java.lang.String)" -> {
        context.insert(new IoOps.PrintOp(context.loc(n), args.getFirst()));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.IO.println(java.lang.String)" -> {
        var formatString = context.insert(new ArithOps.ConstantOp(context.loc(n), "%s\n"));
        context.insert(
            new IoOps.PrintOp(context.loc(n), formatString.getResult(), args.getFirst()));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.IO.printf(java.lang.String, java.lang.Object...)" -> {
        context.insert(new IoOps.PrintOp(context.loc(n), args));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.IO.nextFloat()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.FloatT.FLOAT32));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextDouble()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.FloatT.FLOAT64));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextBoolean()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.IntegerT.BOOL));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextByte()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.IntegerT.INT8));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextShort()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.IntegerT.INT16));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextInt()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.IntegerT.INT32));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextLong()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), BuiltinTypes.IntegerT.INT64));
        return EmitResult.of(Optional.of(result.getResult()));
      }
      case "Dungeon.IO.nextLine()" -> {
        var result =
            context.insert(new IoOps.ConsoleInOp(context.loc(n), StrTypes.StringT.INSTANCE));
        return EmitResult.of(Optional.of(result.getResult()));
      }

      default -> context.emitError(n, "Intrinsic method " + intrinsicName + " is not supported.");
    }
    return EmitResult.success(Optional.empty());
  }

  /**
   * Emit intrinsic methods for the String class, such as length() and charAt(int index).
   *
   * @param n the class declaration to check if it is the String class and emit the intrinsic
   *     methods for it.
   * @param context the emit context.
   */
  public static void emitStringIntrinsicMethods(
      @NotNull ResolvedReferenceTypeDeclaration n, @NotNull EmitContext context) {
    if (!n.getName().equals("String")) {
      return;
    }
    Location loc = Location.UNKNOWN;

    if (SymbolTable.lookupSymbolIn(
            context.getProgramBlock().orElseThrow().getParentOperation().orElseThrow(),
            "java.lang.String.length()")
        != null) {
      return;
    }

    // Insert the operations at the end of the program
    try (var endOfProgramInsertion =
        context.setInsertionPoint(
            context.getProgramBlock().orElseThrow(),
            context.getProgramBlock().orElseThrow().getOperations().size())) {

      // Emit length() method
      {
        FuncOps.FuncOp lengthFunc =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.length()",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE), BuiltinTypes.IntegerT.INT32)));
        try (var bodyInsertion = context.setInsertionPoint(lengthFunc.getEntryBlock(), -1)) {
          StrOps.LengthOp lengthOp =
              context.insert(new StrOps.LengthOp(loc, lengthFunc.getArgument(0).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, lengthOp.getResult()));
        }
      }

      // Emit equals() method
      {
        FuncOps.FuncOp equalsFunc =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.equals(java.lang.Object)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, StrTypes.StringT.INSTANCE),
                        BuiltinTypes.IntegerT.BOOL)));
        try (var bodyInsertion = context.setInsertionPoint(equalsFunc.getEntryBlock(), -1)) {
          StrOps.EqualsOp equalsOp =
              context.insert(
                  new StrOps.EqualsOp(
                      loc,
                      equalsFunc.getArgument(0).orElseThrow(),
                      equalsFunc.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, equalsOp.getResult()));
        }
      }

      // Emit charAt(int index) method
      {
        FuncOps.FuncOp charAtFunc =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.charAt(int)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, BuiltinTypes.IntegerT.INT32),
                        BuiltinTypes.IntegerT.UINT16)));
        try (var bodyInsertion = context.setInsertionPoint(charAtFunc.getEntryBlock(), -1)) {
          StrOps.CharAtOp charAtOp =
              context.insert(
                  new StrOps.CharAtOp(
                      loc,
                      charAtFunc.getArgument(0).orElseThrow(),
                      charAtFunc.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, charAtOp.getResult()));
        }
      }

      // Emit isEmpty() method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.isEmpty()",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE), BuiltinTypes.IntegerT.BOOL)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.IsEmptyOp op =
              context.insert(new StrOps.IsEmptyOp(loc, func.getArgument(0).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit toLowerCase(Locale) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.toLowerCase()",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE), StrTypes.StringT.INSTANCE)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.ToLowerCaseOp op =
              context.insert(new StrOps.ToLowerCaseOp(loc, func.getArgument(0).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit toUpperCase(Locale) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.toUpperCase()",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE), StrTypes.StringT.INSTANCE)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.ToUpperCaseOp op =
              context.insert(new StrOps.ToUpperCaseOp(loc, func.getArgument(0).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit trim() method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.trim()",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE), StrTypes.StringT.INSTANCE)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.TrimOp op =
              context.insert(new StrOps.TrimOp(loc, func.getArgument(0).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit substring(int) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.substring(int)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, BuiltinTypes.IntegerT.INT32),
                        StrTypes.StringT.INSTANCE)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.SubstringOp op =
              context.insert(
                  new StrOps.SubstringOp(
                      loc, func.getArgument(0).orElseThrow(), func.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit substring(int, int) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.substring(int, int)",
                    FuncTypes.FuncType.of(
                        List.of(
                            StrTypes.StringT.INSTANCE,
                            BuiltinTypes.IntegerT.INT32,
                            BuiltinTypes.IntegerT.INT32),
                        StrTypes.StringT.INSTANCE)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.SubstringOp op =
              context.insert(
                  new StrOps.SubstringOp(
                      loc,
                      func.getArgument(0).orElseThrow(),
                      func.getArgument(1).orElseThrow(),
                      func.getArgument(2).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit concat(String) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.concat(java.lang.String)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, StrTypes.StringT.INSTANCE),
                        StrTypes.StringT.INSTANCE)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.ConcatOp op =
              context.insert(
                  new StrOps.ConcatOp(
                      loc, func.getArgument(0).orElseThrow(), func.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit startsWith(String) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.startsWith(java.lang.String)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, StrTypes.StringT.INSTANCE),
                        BuiltinTypes.IntegerT.BOOL)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.StartsWithOp op =
              context.insert(
                  new StrOps.StartsWithOp(
                      loc, func.getArgument(0).orElseThrow(), func.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit endsWith(String) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.endsWith(java.lang.String)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, StrTypes.StringT.INSTANCE),
                        BuiltinTypes.IntegerT.BOOL)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.EndsWithOp op =
              context.insert(
                  new StrOps.EndsWithOp(
                      loc, func.getArgument(0).orElseThrow(), func.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }

      // Emit indexOf(String) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.indexOf(java.lang.String)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, StrTypes.StringT.INSTANCE),
                        BuiltinTypes.IntegerT.INT32)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.IndexOfOp op =
              context.insert(
                  new StrOps.IndexOfOp(
                      loc, func.getArgument(0).orElseThrow(), func.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(Location.UNKNOWN, op.getResult()));
        }
      }

      // Emit lastIndexOf(String) method
      {
        FuncOps.FuncOp func =
            context.insert(
                new FuncOps.FuncOp(
                    loc,
                    "java.lang.String.lastIndexOf(java.lang.String)",
                    FuncTypes.FuncType.of(
                        List.of(StrTypes.StringT.INSTANCE, StrTypes.StringT.INSTANCE),
                        BuiltinTypes.IntegerT.INT32)));
        try (var bodyInsertion = context.setInsertionPoint(func.getEntryBlock(), -1)) {
          StrOps.LastIndexOfOp op =
              context.insert(
                  new StrOps.LastIndexOfOp(
                      loc, func.getArgument(0).orElseThrow(), func.getArgument(1).orElseThrow()));
          context.insert(new FuncOps.ReturnOp(loc, op.getResult()));
        }
      }
    }
  }
}
