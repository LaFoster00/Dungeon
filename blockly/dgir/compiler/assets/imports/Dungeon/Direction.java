package Dungeon;

/**
 * This enum represents the different directions that can be used for movement
 * and interaction in the dungeon game.
 */
@Intrinsic("Dungeon.Direction")
public enum Direction {
  /**
   * Represents a position or movement in front of the current entity.
   */
  INFRONT,
  /**
   * Represents a position or movement to the right of the current entity.
   */
  RIGHT,
  /**
   * Represents a position or movement behind the current entity.
   */
  BEHIND,
  /**
   * Represents a position or movement to the left of the current entity.
   */
  LEFT,
  /**
   * Represents the current position (no movement).
   */
  HERE;
}
