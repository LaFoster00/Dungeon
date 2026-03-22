package coderunner;

import blockly.dgir.vm.dialect.dg.DgActionGateway;
import com.badlogic.gdx.Gdx;
import components.AmmunitionComponent;
import components.BlocklyItemComponent;
import components.HeroActionComponent;
import contrib.components.CollideComponent;
import contrib.components.LeverComponent;
import contrib.modules.interaction.InteractionComponent;
import contrib.systems.EventScheduler;
import contrib.utils.EntityUtils;
import contrib.utils.components.skill.projectileSkill.FireballSkill;
import core.Entity;
import core.Game;
import core.components.PositionComponent;
import core.components.VelocityComponent;
import core.level.Tile;
import core.level.elements.tile.DoorTile;
import core.level.utils.LevelElement;
import core.utils.Direction;
import core.utils.MissingPlayerException;
import core.utils.Point;
import core.utils.Vector2;
import core.utils.components.MissingComponentException;
import entities.MiscFactory;
import entities.monster.BlocklyMonster;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Game-side implementation of {@link DgActionGateway}.
 *
 * <p>Each action is scheduled on the libGDX render (game) thread via {@code
 * Gdx.app.postRunnable(Runnable)}. Component-based actions attach the appropriate {@link
 * HeroActionComponent} to the hero entity and pass {@code onComplete} as its completion callback;
 * the callback fires from inside {@link HeroActionComponent#destroyVmManagedComponent()} once the
 * action finishes, which in turn releases the {@link java.util.concurrent.CountDownLatch} that is
 * blocking the VM thread.
 *
 * <p>For instant, component-less actions (e.g. {@link #use}) the interaction logic runs directly in
 * the {@code postRunnable} and {@code onComplete} is called immediately afterwards.
 *
 * <p>If {@link Game#player()} returns empty for any action, {@code onComplete} is still called so
 * the VM thread is never left blocked indefinitely.
 */
public class DgHeroActionGateway implements DgActionGateway {
  public static float REST_DURATION = 1f;

  /**
   * All this stuff should ideally be in a config file and not hardcoded. TODO Maybe find a willing
   * intern to do this in a future PR?
   */
  private static final float FIREBALL_REST_TIME = 1f;

  private static final float FIREBALL_RANGE = Integer.MAX_VALUE;
  private static final float FIREBALL_SPEED = 15;
  private static final int FIREBALL_DMG = 1;
  private static final boolean IGNORE_FIRST_WALL = false;
  private static final FireballSkill fireballSkill =
      new FireballSkill(
          () -> {
            Entity player = Game.player().orElseThrow(MissingPlayerException::new);
            return player
                .fetch(CollideComponent.class)
                .map(cc -> cc.collider().absoluteCenter())
                .map(p -> p.translate(EntityUtils.getViewDirection(player)))
                .orElseThrow(() -> MissingComponentException.build(player, CollideComponent.class));
          },
          1,
          FIREBALL_SPEED,
          FIREBALL_RANGE,
          FIREBALL_DMG,
          IGNORE_FIRST_WALL);

  @Override
  public void move(@NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          var heroOpt = Game.player();
          if (heroOpt.isEmpty()) {
            onComplete.run();
            return;
          }
          heroOpt.get().add(new HeroActionComponent.Move(onComplete));
        });
  }

  @Override
  public void turn(int dir, @NotNull Runnable onComplete) {
    Direction direction = intToDirection(dir);
    Gdx.app.postRunnable(
        () -> {
          // Update player
          {
            var heroOpt = Game.player();
            if (heroOpt.isEmpty()) {
              onComplete.run();
              return;
            }
            PositionComponent pc =
                heroOpt
                    .get()
                    .fetch(PositionComponent.class)
                    .orElseThrow(
                        () ->
                            MissingComponentException.build(
                                heroOpt.get(), PositionComponent.class));
            pc.viewDirection(pc.viewDirection().applyRelative(direction));
          }
          // Update black night
          {
            var blackNightOpt =
                Game.levelEntities()
                    .filter(entity -> entity.name().equals(BlocklyMonster.BLACK_KNIGHT_NAME))
                    .findFirst();
            blackNightOpt
                .flatMap(
                    entity ->
                        entity
                            .fetch(VelocityComponent.class)
                            .filter(vc -> vc.maxSpeed() > 0)
                            .flatMap(vc -> entity.fetch(PositionComponent.class)))
                .ifPresent(
                    pc -> pc.viewDirection(pc.viewDirection().applyRelative(direction.opposite())));
          }
          onComplete.run();
        });
  }

  @Override
  public void use(@NotNull int dir, @NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          try {
            Direction direction = intToDirection(dir);
            Game.player()
                .ifPresent(
                    hero ->
                        hero.fetch(PositionComponent.class)
                            .ifPresent(
                                pc -> {
                                  Tile tile = resolveTile(pc, direction);
                                  Game.entityAtTile(tile)
                                      .forEach(
                                          entity ->
                                              entity
                                                  .fetch(InteractionComponent.class)
                                                  .ifPresent(
                                                      ic -> ic.triggerInteraction(entity, hero)));
                                }));
          } finally {
            // Interaction is instant; always unblock the VM thread.
            onComplete.run();
          }
        });
  }

  @Override
  public void push(@NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          var heroOpt = Game.player();
          if (heroOpt.isEmpty()) {
            onComplete.run();
            return;
          }
          var moveC = HeroActionComponent.MovePushable.of(true, onComplete);
          if (moveC.isEmpty()) {
            onComplete.run();
            return;
          }
          heroOpt.get().add(moveC.get());
        });
  }

  @Override
  public void pull(@NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          var heroOpt = Game.player();
          if (heroOpt.isEmpty()) {
            onComplete.run();
            return;
          }
          var moveC = HeroActionComponent.MovePushable.of(false, onComplete);
          if (moveC.isEmpty()) {
            onComplete.run();
            return;
          }
          heroOpt.get().add(moveC.get());
        });
  }

  @Override
  public void drop(@NotNull int dropType, @NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          try {
            var heroOpt = Game.player();
            if (heroOpt.isEmpty()) {
              return;
            }
            var hero = heroOpt.get();
            Point heroPos =
                hero.fetch(PositionComponent.class)
                    .map(PositionComponent::position)
                    .map(pos -> pos.translate(0.5f, 0.5f))
                    .orElse(null);

            switch (dropType) {
              // BREADCRUMB
              case 0 -> Game.add(MiscFactory.breadcrumb(heroPos));
              // CLOVER
              case 1 -> Game.add(MiscFactory.clover(heroPos));
              default ->
                  throw new IllegalArgumentException(
                      "Can not convert " + dropType + " to droppable Item.");
            }
          } finally {
            onComplete.run();
          }
        });
  }

  @Override
  public void pickup(@NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          var heroOpt = Game.player();
          if (heroOpt.isEmpty()) {
            onComplete.run();
            return;
          }
          var hero = heroOpt.get();
          // Get the tile at the position of the hero
          hero.fetch(PositionComponent.class)
              .map(PositionComponent::position)
              .map(pos -> pos.translate(0.5f, 0.5f))
              .flatMap(Game::tileAt)
              .map(Game::entityAtTile)
              // Filter the entities to only include entities that are items
              .ifPresent(
                  stream ->
                      stream
                          .filter(e -> e.isPresent(BlocklyItemComponent.class))
                          // Trigger the interaction of each item with the hero, which
                          // should result in the item being picked up and added to the
                          // inventory
                          .forEach(
                              item ->
                                  item.fetch(InteractionComponent.class)
                                      .ifPresent(ic -> ic.triggerInteraction(item, hero))));
          onComplete.run();
        });
  }

  @Override
  public void fireball(@NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          var heroOpt = Game.player();
          if (heroOpt.isEmpty()) {
            onComplete.run();
            return;
          }
          var hero = heroOpt.get();
          hero.fetch(AmmunitionComponent.class)
              .filter(AmmunitionComponent::checkAmmunition)
              .ifPresent(
                  ac -> {
                    fireballSkill.execute(hero);
                    ac.spendAmmo();
                  });

          EventScheduler.scheduleAction(onComplete, (long) (1000 * FIREBALL_REST_TIME));
        });
  }

  @Override
  public void rest(@NotNull Runnable onComplete) {
    Gdx.app.postRunnable(
        () -> {
          var heroOpt = Game.player();
          if (heroOpt.isEmpty()) {
            onComplete.run();
            return;
          }

          EventScheduler.scheduleAction(onComplete, (long) (1000 * REST_DURATION));
        });
  }

  @Override
  public Future<Boolean> isNearTile(int tile, int direction, Runnable onComplete) {
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    Gdx.app.postRunnable(
        () -> {
          Direction realDirection = intToDirection(direction);
          LevelElement tileElement = intToLevelElement(tile);
          // Check the tile the player is standing on
          if (realDirection == Direction.NONE) {
            Tile checkTile =
                Game.player()
                    .flatMap(hero -> hero.fetch(PositionComponent.class))
                    .map(PositionComponent::position)
                    .map(pos -> pos.translate(0.5f, 0.5f))
                    .flatMap(Game::tileAt)
                    .orElse(null);
            result.complete(
                checkTile != null && matchesTile(tileElement, checkTile.levelElement()));
          } else {
            result.complete(
                targetTile(realDirection)
                    .map(t -> matchesTile(tileElement, t.levelElement()))
                    .orElse(false));
          }
          onComplete.run();
        });
    return result;
  }

  @Override
  public Future<Boolean> active(int direction, Runnable onComplete) {
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    Gdx.app.postRunnable(
        () -> {
          result.complete(
              targetTile(intToDirection(direction))
                  .map(DgHeroActionGateway::checkTileForDoorOrLevers)
                  .orElse(false));
          onComplete.run();
        });
    return result;
  }

  // =========================================================================
  // Private helpers
  // =========================================================================

  private Direction intToDirection(int i) {
    return Direction.values()[i];
  }

  private LevelElement intToLevelElement(int i) {
    return LevelElement.values()[i];
  }

  private static boolean matchesTile(LevelElement target, LevelElement actual) {
    if (target == actual) {
      return true;
    }
    // Special case: treat DOOR or EXIT as FLOOR
    return target == LevelElement.FLOOR
        && (actual == LevelElement.DOOR || actual == LevelElement.EXIT);
  }

  /**
   * Resolves the {@link Tile} targeted by a {@code use} operation in the given direction.
   *
   * @param pc the hero's position component.
   * @param dir the use direction.
   * @return the target tile, or {@code null} if no tile exists in that direction.
   */
  private static Tile resolveTile(@NotNull PositionComponent pc, @NotNull Direction dir) {
    return Game.tileAt(
            pc.position().translate(Vector2.of(0.5f, 0.5f)), pc.viewDirection().applyRelative(dir))
        .orElse(null);
  }

  /**
   * Gets the target tile in the given direction relative to the player.
   *
   * @param direction Direction to check relative to player's view direction
   * @return The target tile, or empty if player is not found or target tile doesn't exist
   */
  private static Optional<Tile> targetTile(final Direction direction) {
    // find tile in a direction or empty
    Function<Direction, Optional<Tile>> dirToCheck =
        dir ->
            Game.player()
                .flatMap(hero -> hero.fetch(PositionComponent.class))
                .map(PositionComponent::position)
                .map(pos -> pos.translate(0.5f, 0.5f))
                .map(pos -> pos.translate(dir))
                .flatMap(Game::tileAt);

    // calculate direction to check relative to player's view direction
    return Optional.ofNullable(EntityUtils.getPlayerViewDirection())
        .map(d -> d.applyRelative(direction))
        .flatMap(dirToCheck);
  }

  /**
   * Determines whether the specified tile is in active state.
   *
   * <p>A tile in the given direction is considered active iff
   *
   * <ul>
   *   <li>it is a {@link DoorTile} and it is "open", or
   *   <li>it contains at least one {@link LeverComponent}, and all found levers are in the "on"
   *       state.
   * </ul>
   *
   * @param tile the direction to check
   * @return {@code true} if the tile is active, {@code false} otherwise.
   */
  private static Boolean checkTileForDoorOrLevers(Tile tile) {
    // is this a door? is it open?
    if (tile instanceof DoorTile doorTile) return doorTile.isOpen();

    // find all levers on a given tile and split those into "isOn" (true) and "isOff" (false)
    Map<Boolean, List<LeverComponent>> levers =
        Game.entityAtTile(tile)
            .flatMap(e -> e.fetch(LeverComponent.class).stream())
            .collect(Collectors.partitioningBy(LeverComponent::isOn));

    // there needs to be at least one lever; all levers need to be "isOn" (true)
    return levers.get(false).isEmpty() && !levers.get(true).isEmpty();
  }
}
