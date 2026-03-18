package blockly.dgir.vm.dialect.dg;

import blockly.dgir.dialect.dg.DgOps;
import dgir.core.ir.Operation;
import dgir.vm.api.Action;
import dgir.vm.api.OpRunner;
import dgir.vm.api.State;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public sealed interface DgRunners {

  /**
   * Posts {@code schedule} to the game thread via the {@link DgActionGateway}, then blocks the VM
   * thread on the latch until the action signals completion. Handles {@link InterruptedException}
   * by re-interrupting the thread and returning an {@link Action.Abort}.
   */
  private static @NotNull Action awaitAction(@NotNull CountDownLatch done) {
    try {
      done.await();
      return Action.Next();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Action.Abort(Optional.of(e), "Action interrupted by VM stop/reload");
    }
  }

  // =========================================================================
  // Runner implementations
  // =========================================================================

  final class MoveRunner extends OpRunner implements DgRunners {
    public MoveRunner() {
      super(DgOps.MoveOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().move(done::countDown);
      return awaitAction(done);
    }
  }

  final class TurnRunner extends OpRunner implements DgRunners {
    public TurnRunner() {
      super(DgOps.RotateOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      var dir = state.getValueAsOrThrow(op.getOperandOrThrow(0), Integer.class);
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().turn(dir, done::countDown);
      return awaitAction(done);
    }
  }

  final class UseRunner extends OpRunner implements DgRunners {
    public UseRunner() {
      super(DgOps.InteractOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      var dir = state.getValueAsOrThrow(op.getOperandOrThrow(0), Integer.class);
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().use(dir, done::countDown);
      return awaitAction(done);
    }
  }

  final class PushRunner extends OpRunner implements DgRunners {
    public PushRunner() {
      super(DgOps.PushOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().push(done::countDown);
      return awaitAction(done);
    }
  }

  final class PullRunner extends OpRunner implements DgRunners {
    public PullRunner() {
      super(DgOps.PullOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().pull(done::countDown);
      return awaitAction(done);
    }
  }

  final class DropRunner extends OpRunner implements DgRunners {
    public DropRunner() {
      super(DgOps.DropOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      var dropType = state.getValueAsOrThrow(op.getOperandOrThrow(0), Integer.class);
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().drop(dropType, done::countDown);
      return awaitAction(done);
    }
  }

  final class PickupRunner extends OpRunner implements DgRunners {
    public PickupRunner() {
      super(DgOps.PickupOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().pickup(done::countDown);
      return awaitAction(done);
    }
  }

  final class FireballRunner extends OpRunner implements DgRunners {
    public FireballRunner() {
      super(DgOps.FireballOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().fireball(done::countDown);
      return awaitAction(done);
    }
  }

  final class RestRunner extends OpRunner implements DgRunners {
    public RestRunner() {
      super(DgOps.RestOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      CountDownLatch done = new CountDownLatch(1);
      DgActionGateway.get().rest(done::countDown);
      return awaitAction(done);
    }
  }

  final class IsNearTileRunner extends OpRunner implements DgRunners {
    public IsNearTileRunner() {
      super(DgOps.IsNearTileOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      var tile = state.getValueAsOrThrow(op.getOperandOrThrow(0), Integer.class);
      var dir = state.getValueAsOrThrow(op.getOperandOrThrow(1), Integer.class);
      CountDownLatch done = new CountDownLatch(1);
      Future<Boolean> resultFuture = DgActionGateway.get().isNearTile(tile, dir, done::countDown);
      boolean result;
      try {
        result = resultFuture.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      state.setValueForNumberOutput(op, result);
      return awaitAction(done);
    }
  }

  final class IsActiveRunner extends OpRunner implements DgRunners {
    public IsActiveRunner() {
      super(DgOps.IsActiveOp.class);
    }

    @Override
    protected @NotNull Action runImpl(@NotNull Operation op, @NotNull State state) {
      var dir = state.getValueAsOrThrow(op.getOperandOrThrow(0), Integer.class);
      CountDownLatch done = new CountDownLatch(1);
      Future<Boolean> resultFuture = DgActionGateway.get().active(dir, done::countDown);
      boolean result;
      try {
        result = resultFuture.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      state.setValueForNumberOutput(op, result);
      return awaitAction(done);
    }
  }
}
