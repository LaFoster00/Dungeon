package Dungeon;

/**
 * This enum represents the different types of level elements that can be found
 * in the dungeon.
 */
@Intrinsic("Dungeon.LevelElement")
public enum LevelElement {
  /** This field is a blank. */
  SKIP,
  /** This field is a floor-field. */
  FLOOR,
  /** This field is a wall-field. */
  WALL,
  /** This field is a hole-field. */
  HOLE,
  /** This field is the exit-field to the next level. */
  EXIT,
  /** This field is a pit-field. */
  PIT,
  /** This field is a door-field. */
  DOOR,
  /** This field is a portal-field. */
  PORTAL,
  /** This field is a glasswall-field. */
  GLASSWALL,
  /** This field is a gitter-field. */
  GITTER;
}
