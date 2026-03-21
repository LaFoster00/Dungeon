import core.utils.Point;
import entities.monster.BlocklyMonster;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DungeonJavaCodeTests extends DungeonCompilerTestBase {
  @Test
  void testHelloWorld() throws InterruptedException, IOException {
    sendCode(
        """
          public static void main() {
            IO.println("Hello, world!");
            Hero.move();
        }
        """);
    waitForCompletion();
  }

  @Test
  void moveInFacingDirection() throws InterruptedException, IOException {
    Point start = playerPosition();
    var startDirection = playerDirection();
    sendCode(
        """
            public static void main() {
              Hero.move();
            }
          """);
    waitForCompletion();
    assertNear(
        start.translate(startDirection.x(), startDirection.y()),
        playerPosition(),
        "Hero.move should move exactly one tile in the current facing direction");
  }

  @Test
  void moveToRelativeLeft() throws InterruptedException, IOException {
    Point start = playerPosition();
    var expectedDirection = playerDirection().turnLeft();

    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.LEFT);
              Hero.move();
            }
          """);
    waitForCompletion();

    assertNear(
        start.translate(expectedDirection.x(), expectedDirection.y()),
        playerPosition(),
        "Turning left and moving should move one tile to the hero's relative left");
  }

  @Test
  void moveToRelativeRight() throws InterruptedException, IOException {
    Point start = playerPosition();
    var expectedDirection = playerDirection().turnRight();
    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.RIGHT);
              Hero.move();
            }
          """);
    waitForCompletion();
    assertNear(
        start.translate(expectedDirection.x(), expectedDirection.y()),
        playerPosition(),
        "Turning right and moving should move one tile to the hero's relative right");
  }

  @Test
  void moveToRelativeBehind() throws InterruptedException, IOException {
    Point start = playerPosition();
    var expectedDirection = playerDirection().opposite();
    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.RIGHT);
              Hero.rotate(Direction.RIGHT);
              Hero.move();
            }
          """);
    waitForCompletion();
    assertNear(
        start.translate(expectedDirection.x(), expectedDirection.y()),
        playerPosition(),
        "Turning around and moving should move one tile behind the initial facing direction");
  }

  @Test
  void pushMovesHeroAndStone() throws InterruptedException, IOException {
    Point heroStart = playerPosition();
    Point stoneStart = entityPosition("stone");
    var sideDirection = playerDirection().turnLeft();

    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.LEFT);
              Hero.move();
              Hero.move();
              Hero.move();
              Hero.push();
            }
          """);
    waitForCompletion();

    assertNear(
        heroStart.translate(4 * sideDirection.x(), 4 * sideDirection.y()),
        playerPosition(),
        "Push should move hero into the pushable's previous tile");
    assertNear(
        stoneStart.translate(sideDirection.x(), sideDirection.y()),
        entityPosition("stone"),
        "Push should move the stone one tile in facing direction");
  }

  @Test
  void pullMovesHeroBackAndStoneToHeroTile() throws InterruptedException, IOException {
    Point heroStart = playerPosition();
    Point stoneStart = entityPosition("stone");
    var sideDirection = playerDirection().turnLeft();

    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.LEFT);
              Hero.move();
              Hero.move();
              Hero.move();
              Hero.pull();
            }
          """);
    waitForCompletion();

    assertNear(
        heroStart.translate(2 * sideDirection.x(), 2 * sideDirection.y()),
        playerPosition(),
        "Pull should move hero one tile backward relative to its facing direction");
    assertNear(
        stoneStart.translate(-sideDirection.x(), -sideDirection.y()),
        entityPosition("stone"),
        "Pull should drag the stone onto the hero's previous tile");
  }

  @Test
  void checksAndInteractCanBeValidated() throws InterruptedException, IOException {
    Point start = playerPosition();
    var expectedDirection = playerDirection().turnLeft();

    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.LEFT);
              Hero.move();

              if (Hero.isActive(Direction.LEFT)) {
                Hero.move();
              }

              Hero.interact(Direction.LEFT);

              if (!Hero.isActive(Direction.LEFT)) {
                Hero.move();
              }

              if (!Hero.isNearTile(LevelElement.FLOOR, Direction.HERE)) {
                assert false : "Hero should be near the floor";
                Hero.move();
              }

              if (!Hero.matchesTile(LevelElement.FLOOR, LevelElement.DOOR)) {
                IO.println("Floor and Door don't match, as expected");
                Hero.move();
              }
            }
          """);
    waitForCompletion();

    assertNear(
        start.translate(expectedDirection.scale(2).x(), expectedDirection.scale(2).y()),
        playerPosition(),
        "Checks should guide control flow and still end exactly one tile to the left");
    assertEquals(
        expectedDirection,
        playerDirection(),
        "Direction should stay unchanged when check branches are evaluated correctly");
  }

  @Test
  void darkKnightMirrorsTurnAndMove() throws InterruptedException, IOException {
    Point heroStart = playerPosition();
    var heroStartDirection = playerDirection();
    Point knightStart = entityPosition(BlocklyMonster.BLACK_KNIGHT_NAME);
    var knightStartDirection = entityDirection(BlocklyMonster.BLACK_KNIGHT_NAME);

    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.LEFT);
              Hero.move();
            }
          """);
    waitForCompletion();

    var expectedHeroDirection = heroStartDirection.turnLeft();
    var expectedKnightDirection = knightStartDirection.turnRight();

    assertNear(
        heroStart.translate(expectedHeroDirection.x(), expectedHeroDirection.y()),
        playerPosition(),
        "Hero should move one tile after turning left");
    assertEquals(expectedHeroDirection, playerDirection(), "Hero should keep the turned direction");

    assertNear(
        knightStart.translate(expectedKnightDirection.x(), expectedKnightDirection.y()),
        entityPosition(BlocklyMonster.BLACK_KNIGHT_NAME),
        "Black knight should mirror the hero movement in opposite turn direction");
    assertEquals(
        expectedKnightDirection,
        entityDirection(BlocklyMonster.BLACK_KNIGHT_NAME),
        "Black knight should mirror hero turns in opposite direction");
  }

  @Test
  void executionStopsWhenHeroFallsIntoPit() throws InterruptedException, IOException {
    sendCode(
        """
            public static void main() {
              Hero.rotate(Direction.BEHIND);
              for (int i = 0; i < 1000; i++) {
                Hero.move();
              }
            }
          """);

    waitForCompletion(Duration.ofSeconds(2));
    assertFalse(
        isRunning(), "Execution should stop after the hero fails (e.g. by falling into a pit)");
  }

  private static void assertNear(Point expected, Point actual, String message) {
    assertTrue(
        actual.distance(expected) <= 0.2,
        message + " (expected " + expected + ", actual " + actual + ")");
  }
}
