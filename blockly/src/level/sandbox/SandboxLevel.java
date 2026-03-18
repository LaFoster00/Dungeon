package level.sandbox;

import contrib.entities.LeverFactory;
import core.Game;
import core.level.utils.DesignLabel;
import core.level.utils.LevelElement;
import core.utils.Direction;
import core.utils.Point;
import entities.MiscFactory;
import entities.monster.BlocklyMonster;
import level.BlocklyLevel;
import level.LevelManagementUtils;

import java.util.Map;

/**
 * An empty sandbox level used during development and integration testing.
 *
 * <h2>Sandbox guarantees</h2>
 *
 * <ul>
 *   <li>No introductory popups – the hero is placed immediately without any blocking dialogs.
 *   <li>All Blockly blocks are unlocked – every category and direction is available.
 *   <li>Fog of war is disabled so the whole arena is visible.
 *   <li>The camera follows the hero and starts at the default zoom level.
 * </ul>
 *
 * <p>The level is loaded automatically when {@link client.Client} is started with the {@code
 * --sandbox} command-line argument.
 */
public class SandboxLevel extends BlocklyLevel {

  private static final Point STONE_SPAWN = new Point(16f, 12f);
  private static final Point LEVER_SPAWN = new Point(13f, 13f);
  private static final Point PRESSURE_PLATE_SPAWN = new Point(17f, 12f);
  private static final Point BLACK_KNIGHT_SPAWN = new Point(19f, 12f);

  /**
   * Creates a new SandboxLevel.
   *
   * @param layout 2D array containing the tile layout (parsed from {@code sandbox_1.level}).
   * @param designLabel The design label for the level tiles.
   * @param namedPoints Any named points defined in the level file.
   */
  public SandboxLevel(
      LevelElement[][] layout, DesignLabel designLabel, Map<String, Point> namedPoints) {
    super(layout, designLabel, namedPoints, "Sandbox");
    // No blocks are restricted – everything is available for testing.
  }

  @Override
  protected void onFirstTick() {
    LevelManagementUtils.fog(false);
    LevelManagementUtils.cameraFocusHero();
    LevelManagementUtils.centerHero();
    LevelManagementUtils.playerViewDirection(Direction.DOWN);
    LevelManagementUtils.zoomDefault();
    Game.add(MiscFactory.stone(STONE_SPAWN));
    Game.add(LeverFactory.createLever(LEVER_SPAWN));
    Game.add(LeverFactory.pressurePlate(PRESSURE_PLATE_SPAWN, 1f));
    BlocklyMonster.BLACK_KNIGHT
        .builder()
        .speed(4f)
        .viewDirection(Direction.LEFT)
        .addToGame()
        .build(BLACK_KNIGHT_SPAWN);
    // Intentionally no popups – sandbox must never block test execution.
  }
}
