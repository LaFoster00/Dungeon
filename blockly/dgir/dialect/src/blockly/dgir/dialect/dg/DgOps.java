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
 * {@link Dialect#allOps(Class)} can discover it automatically via reflection.
 */
public sealed interface DgOps {
  /**
   * Abstract base class for all operations in the {@code dg} dialect (namespace {@code "dg"}).
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and must implement {@link DgOps} to
   * be enumerated by {@link DungeonDialect}.
   */
  abstract class DungeonOp extends Op {
    /** Creates a new dungeon operation base instance. */
    protected DungeonOp() {}

    /**
     * Returns the dialect that owns this operation.
     *
     * @return the {@link DungeonDialect} class.
     */
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return DungeonDialect.class;
    }

    /**
     * Returns the namespace prefix used when printing this operation.
     *
     * @return the fixed {@code "dg"} namespace.
     */
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
    /** Creates a new hero operation base instance. */
    protected HeroOp() {}

    /**
     * Creates an initialized hero operation.
     *
     * @param location the source location of this operation.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.move"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.move";
    }

    /**
     * Verifies that the operation is structurally valid.
     *
     * @return a verifier that always accepts the operation.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    /** Creates a default move-op instance for dialect registration. */
    private MoveOp() {}

    /**
     * Creates a move operation.
     *
     * @param location the source location of this operation.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.rotate"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.rotate";
    }

    /**
     * Verifies that the rotation direction operand is an integer.
     *
     * @return a verifier that accepts well-formed rotate operations.
     */
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

    /** Creates a default rotate-op instance for dialect registration. */
    private RotateOp() {}

    /**
     * Creates a rotate operation.
     *
     * @param location the source location of this operation.
     * @param turnDir the direction operand.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.interact"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.interact";
    }

    /**
     * Verifies that the interaction direction operand is an integer.
     *
     * @return a verifier that accepts well-formed interact operations.
     */
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

    /** Creates a default interact-op instance for dialect registration. */
    private InteractOp() {}

    /**
     * Creates an interact operation.
     *
     * @param location the source location of this operation.
     * @param useDir the interaction direction operand.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.push"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.push";
    }

    /**
     * Verifies that the operation is structurally valid.
     *
     * @return a verifier that always accepts the operation.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    /** Creates a default push-op instance for dialect registration. */
    private PushOp() {}

    /**
     * Creates a push operation.
     *
     * @param location the source location of this operation.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.pull"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.pull";
    }

    /**
     * Verifies that the operation is structurally valid.
     *
     * @return a verifier that always accepts the operation.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    /** Creates a default pull-op instance for dialect registration. */
    private PullOp() {}

    /**
     * Creates a pull operation.
     *
     * @param location the source location of this operation.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.drop"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.drop";
    }

    /**
     * Verifies that the drop-type operand is an integer when present.
     *
     * @return a verifier that accepts well-formed drop operations.
     */
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

    /** Creates a default drop-op instance for dialect registration. */
    private DropOp() {}

    /**
     * Creates a drop operation without an explicit item type.
     *
     * @param location the source location of this operation.
     */
    public DropOp(Location location) {
      super(location);
    }

    /**
     * Creates a drop operation with an explicit item type.
     *
     * @param location the source location of this operation.
     * @param dropType the item type operand.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.pickup"}.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.pickup"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.pickup";
    }

    /** Creates a default pickup-op instance for dialect registration. */
    private PickupOp() {}

    /**
     * Creates a pickup operation.
     *
     * @param location the source location of this operation.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.fireball"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.fireball";
    }

    /**
     * Verifies that the operation is structurally valid.
     *
     * @return a verifier that always accepts the operation.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    /** Creates a default fireball-op instance for dialect registration. */
    private FireballOp() {}

    /**
     * Creates a fireball operation.
     *
     * @param location the source location of this operation.
     */
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
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.rest"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.rest";
    }

    /**
     * Verifies that the operation is structurally valid.
     *
     * @return a verifier that always accepts the operation.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return ignored -> true;
    }

    /** Creates a default rest-op instance for dialect registration. */
    private RestOp() {}

    /**
     * Creates a rest operation.
     *
     * @param location the source location of this operation.
     */
    public RestOp(Location location) {
      super(location);
    }
  }

  /** Checks whether the hero is near a given tile. */
  final class IsNearTileOp extends HeroOp implements DgOps, IHasResult {
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.isNearTile"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.isNearTile";
    }

    /**
     * Verifies that both operands are integers and the result is boolean.
     *
     * @return a verifier that accepts well-formed proximity checks.
     */
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

    /** Creates a default is-near-tile-op instance for dialect registration. */
    private IsNearTileOp() {}

    /**
     * Creates a proximity check operation.
     *
     * @param location the source location of this operation.
     * @param tileType the tile operand.
     * @param direction the direction operand.
     */
    public IsNearTileOp(Location location, Value tileType, Value direction) {
      setOperation(
          Operation.Create(
              location, this, List.of(tileType, direction), null, BuiltinTypes.IntegerT.BOOL));
    }
  }

  /** Checks whether a target is currently active. */
  final class IsActiveOp extends HeroOp implements DgOps, ISingleOperand, IHasResult {
    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "dg.isActive"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "dg.isActive";
    }

    /**
     * Verifies that the operand is an integer and the result is boolean.
     *
     * @return a verifier that accepts well-formed active checks.
     */
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

    /** Creates a default is-active-op instance for dialect registration. */
    private IsActiveOp() {}

    /**
     * Creates an activity check operation.
     *
     * @param location the source location of this operation.
     * @param direction the direction operand.
     */
    public IsActiveOp(Location location, Value direction) {
      setOperation(
          Operation.Create(location, this, List.of(direction), null, BuiltinTypes.IntegerT.BOOL));
    }
  }
}
