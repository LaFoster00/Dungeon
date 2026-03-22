package blockly.dgir.dialect.dg;

import dgir.core.Dialect;
import dgir.core.debug.Location;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.traits.IHasResult;
import dgir.core.traits.INoOperands;
import dgir.core.traits.INoResult;
import dgir.core.traits.ISingleOperand;
import dgir.dialect.builtin.BuiltinTypes;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * Sealed marker interface for all operations in the {@link DungeonDialect}.
 *
 * <p>Every concrete op must both extend {@link DungeonOp} and implement this interface so that
 * {@link Dialect#allOps} can discover it automatically via reflection.
 */
public sealed interface DgOps {
  /**
   * Abstract base class for all operations in the {@code dg} dialect (namespace {@code "dg"}).
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and must implement {@link DgOps} to
   * be enumerated by {@link DungeonDialect}.
   */
  abstract class DungeonOp extends Op {
    protected DungeonOp() {}

    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return DungeonDialect.class;
    }

    @Override
    public @NotNull String getNamespace() {
      return "dg";
    }
  }

  /**
   * Base class for hero commands that take no operands and produce no results.
   *
   * <p>All subclasses are leaf ops that carry their meaning solely through their ident and default
   * attributes.
   */
  abstract class HeroOp extends DungeonOp {
    protected HeroOp() {}

    protected HeroOp(Location location) {
      setOperation(Operation.Create(location, this, null, null, null));
    }
  }

  /**
   * Move the hero one tile in the current view direction.
   *
   * <p>Ident: {@code dg.move}
   */
  final class MoveOp extends HeroOp implements DgOps, INoResult, INoOperands {
    @Override
    public @NotNull String getIdent() {
      return "dg.move";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    private MoveOp() {}

    public MoveOp(Location location) {
      super(location);
    }
  }

  /**
   * Turn the hero left or right.
   *
   * <p>Ident: {@code dg.turn}
   */
  final class RotateOp extends HeroOp implements DgOps, ISingleOperand, INoResult {
    @Override
    public @NotNull String getIdent() {
      return "dg.rotate";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        RotateOp op = operation.as(RotateOp.class).orElseThrow();
        if (!(op.getOperand().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError("Expected operand of type Integer, got " + op.getOperand().getType());
          return false;
        }
        return true;
      };
    }

    private RotateOp() {}

    public RotateOp(Location location, Value turnDir) {
      setOperation(Operation.Create(location, this, List.of(turnDir), null, null));
    }
  }

  /**
   * Use an interactable relative to the hero.
   *
   * <p>Ident: {@code dg.use}
   */
  final class InteractOp extends HeroOp implements DgOps, ISingleOperand, INoResult {
    @Override
    public @NotNull String getIdent() {
      return "dg.interact";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        var op = operation.as(InteractOp.class).orElseThrow();
        if (!(op.getOperand().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError("Expected operand of type Integer, got " + op.getOperand().getType());
          return false;
        }
        return true;
      };
    }

    private InteractOp() {}

    public InteractOp(Location location, Value useDir) {
      setOperation(Operation.Create(location, this, List.of(useDir), null, null));
    }
  }

  /**
   * Push an entity in front of the hero.
   *
   * <p>Ident: {@code dg.push}
   */
  final class PushOp extends HeroOp implements DgOps, INoResult, INoOperands {
    @Override
    public @NotNull String getIdent() {
      return "dg.push";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    private PushOp() {}

    public PushOp(Location location) {
      super(location);
    }
  }

  /**
   * Pull an entity in front of the hero.
   *
   * <p>Ident: {@code dg.pull}
   */
  final class PullOp extends HeroOp implements DgOps, INoResult, INoOperands {
    @Override
    public @NotNull String getIdent() {
      return "dg.pull";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    private PullOp() {}

    public PullOp(Location location) {
      super(location);
    }
  }

  /**
   * Drop an item on the hero's tile.
   *
   * <p>Ident: {@code dg.drop}
   */
  final class DropOp extends HeroOp implements DgOps, INoResult, ISingleOperand {
    @Override
    public @NotNull String getIdent() {
      return "dg.drop";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        var op = operation.as(DropOp.class).orElseThrow();
        if (!(op.getOperand().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError("Expected operand of type Integer, got " + op.getOperand().getType());
          return false;
        }
        return true;
      };
    }

    private DropOp() {}

    public DropOp(Location location) {
      super(location);
    }

    public DropOp(Location location, Value dropType) {
      setOperation(Operation.Create(location, this, List.of(dropType), null, null));
    }
  }

  /**
   * Pick up an item on the hero's tile.
   *
   * <p>Ident: {@code dg.pickup}
   */
  final class PickupOp extends HeroOp implements DgOps, INoResult, INoOperands {
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    @Override
    public @NotNull String getIdent() {
      return "dg.pickup";
    }

    private PickupOp() {}

    public PickupOp(Location location) {
      super(location);
    }
  }

  /**
   * Shoot a fireball in the current view direction.
   *
   * <p>Ident: {@code dg.fireball}
   */
  final class FireballOp extends HeroOp implements DgOps, INoResult, INoOperands {
    @Override
    public @NotNull String getIdent() {
      return "dg.fireball";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    private FireballOp() {}

    public FireballOp(Location location) {
      super(location);
    }
  }

  /**
   * Do nothing for a short time.
   *
   * <p>Ident: {@code dg.rest}
   */
  final class RestOp extends HeroOp implements DgOps, INoResult, INoOperands {
    @Override
    public @NotNull String getIdent() {
      return "dg.rest";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    private RestOp() {}

    public RestOp(Location location) {
      super(location);
    }
  }

  final class IsNearTileOp extends HeroOp implements DgOps, IHasResult {
    @Override
    public @NotNull String getIdent() {
      return "dg.isNearTile";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        var op = operation.as(IsNearTileOp.class).orElseThrow();
        if (op.getOperands().size() != 2) {
          operation.emitError("Expected exactly 2 operands, got " + op.getOperands().size());
          return false;
        }
        if (!(op.getOperandValue(0).orElseThrow().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError(
              "Expected first operand to be of type Integer, got "
                  + op.getOperandValue(0).orElseThrow().getType());
          return false;
        }
        if (!(op.getOperandValue(1).orElseThrow().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError(
              "Expected second operand to be of type Integer, got "
                  + op.getOperandValue(1).orElseThrow().getType());
          return false;
        }
        if (!op.getResultType().equals(BuiltinTypes.IntegerT.BOOL)) {
          operation.emitError(
              "Expected result to be of type Bool, got " + op.getResult().getType());
          return false;
        }
        return true;
      };
    }

    private IsNearTileOp() {}

    public IsNearTileOp(Location location, Value tileType, Value direction) {
      setOperation(
          Operation.Create(
              location, this, List.of(tileType, direction), null, BuiltinTypes.IntegerT.BOOL));
    }
  }

  final class IsActiveOp extends HeroOp implements DgOps, ISingleOperand, IHasResult {
    @Override
    public @NotNull String getIdent() {
      return "dg.isActive";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        var op = operation.as(IsActiveOp.class).orElseThrow();
        if (!(op.getOperand().getType() instanceof BuiltinTypes.IntegerT)) {
          operation.emitError("Expected operand of type Integer, got " + op.getOperand().getType());
          return false;
        }
        if (!op.getResultType().equals(BuiltinTypes.IntegerT.BOOL)) {
          operation.emitError(
              "Expected result to be of type Bool, got " + op.getResult().getType());
          return false;
        }
        return true;
      };
    }

    private IsActiveOp() {}

    public IsActiveOp(Location location, Value direction) {
      setOperation(
          Operation.Create(location, this, List.of(direction), null, BuiltinTypes.IntegerT.BOOL));
    }
  }
}
