package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitContext;
import blockly.dgir.compiler.java.EmitResult;
import blockly.dgir.dialect.dg.DgOps;
import com.github.javaparser.ast.expr.MethodCallExpr;
import dgir.core.ir.Value;
import dgir.dialect.arith.ArithAttrs;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.builtin.BuiltinTypes;
import dgir.dialect.io.IoOps;
import dgir.dialect.mem.MemOps;
import dgir.dialect.str.StrOps;
import dgir.dialect.str.StrTypes;

import java.util.List;
import java.util.Optional;

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
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value directionValue = args.getFirst();
        context.insert(new DgOps.RotateOp(context.loc(n), directionValue));
        return EmitResult.of(Optional.empty());
      }
      case "Dungeon.Hero.interact(Dungeon.Direction)" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value directionValue = args.getFirst();
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
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value itemTypeValue = args.getFirst();
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
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value levelElementValue = args.getFirst();
        Value directionValue = args.get(1);
        var op =
            context.insert(
                new DgOps.IsNearTileOp(context.loc(n), levelElementValue, directionValue));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "Dungeon.Hero.matchesTile(Dungeon.LevelElement, Dungeon.LevelElement)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value levelElementValue1 = args.getFirst();
        Value levelElementValue2 = args.get(1);
        var op =
            context.insert(
                new ArithOps.BinaryOp(
                    context.loc(n),
                    levelElementValue1,
                    levelElementValue2,
                    ArithAttrs.BinModeAttr.BinMode.EQ));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "Dungeon.Hero.isActive(Dungeon.Direction)" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value directionValue = args.getFirst();
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

      case "Dungeon.Arrays.copyOf(Object[], int)",
          "Dungeon.Arrays.copyOf(byte[], int)",
          "Dungeon.Arrays.copyOf(short[], int)",
          "Dungeon.Arrays.copyOf(char[], int)",
          "Dungeon.Arrays.copyOf(int[], int)",
          "Dungeon.Arrays.copyOf(long[], int)",
          "Dungeon.Arrays.copyOf(float[], int)",
          "Dungeon.Arrays.copyOf(double[], int)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        Value arrayValue = args.getFirst();
        Value newLengthValue = args.get(1);
        var op = context.insert(new MemOps.ReallocGcOp(context.loc(n), arrayValue, newLengthValue));
        op.setOutputValue(arrayValue);
        return EmitResult.of(Optional.of(op.getResult()));
      }

      // String Operations

      case "java.lang.String.length()" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.LengthOp(context.loc(n), args.getFirst()));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.equals(java.lang.Object)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.EqualsOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.charAt(int)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.CharAtOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.isEmpty()" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.IsEmptyOp(context.loc(n), args.getFirst()));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.toLowerCase()" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.ToLowerCaseOp(context.loc(n), args.getFirst()));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.toUpperCase()" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.ToUpperCaseOp(context.loc(n), args.getFirst()));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.trim()" -> {
        if (args.size() != 1) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.TrimOp(context.loc(n), args.getFirst()));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.substring(int)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op =
            context.insert(new StrOps.SubstringOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.substring(int, int)" -> {
        if (args.size() != 3) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op =
            context.insert(
                new StrOps.SubstringOp(context.loc(n), args.getFirst(), args.get(1), args.get(2)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.concat(java.lang.String)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.ConcatOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.startsWith(java.lang.String)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op =
            context.insert(new StrOps.StartsWithOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.endsWith(java.lang.String)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op =
            context.insert(new StrOps.EndsWithOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.indexOf(java.lang.String)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op = context.insert(new StrOps.IndexOfOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }
      case "java.lang.String.lastIndexOf(java.lang.String)" -> {
        if (args.size() != 2) return EmitResult.failure(context, n, "Invalid number of arguments");
        var op =
            context.insert(new StrOps.LastIndexOfOp(context.loc(n), args.getFirst(), args.get(1)));
        return EmitResult.of(Optional.of(op.getResult()));
      }

      default -> context.emitError(n, "Intrinsic method " + intrinsicName + " is not supported.");
    }
    return EmitResult.success(Optional.empty());
  }
}
