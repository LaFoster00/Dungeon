package Dungeon;

/**
 * This class represents the hero character in the dungeon game. It provides
 * methods for the hero's actions and interactions with the environment.
 */
public class Hero {
  /**
   * Moves the hero one step forward in the direction it is currently facing.
   */
  @Intrinsic("Dungeon.Hero.move()")
  public static native void move();

  /**
   * Rotates the hero in the specified direction, relative to the current facing
   * direction.
   * 
   * @param direction the direction to rotate
   */
  @Intrinsic("Dungeon.Hero.rotate(Dungeon.Direction)")
  public static native void rotate(Direction direction);

  /**
   * Interacts with the tile in the specified direction, relative to the current
   * facing direction.
   * 
   * @param direction the direction to interact with
   */
  @Intrinsic("Dungeon.Hero.interact(Dungeon.Direction)")
  public static native void interact(Direction direction);

  /**
   * Pushes a movable tile in front of the hero, if possible.
   */
  @Intrinsic("Dungeon.Hero.push()")
  public static native void push();

  /**
   * Pulls a movable tile behind the hero, if possible.
   */
  @Intrinsic("Dungeon.Hero.pull()")
  public static native void pull();

  /**
   * Drops an item of the specified type from the hero's inventory onto the
   * current tile.
   * 
   * @param type the type of item to drop
   */
  @Intrinsic("Dungeon.Hero.drop(Dungeon.ItemType)")
  public static native void drop(ItemType type);

  /**
   * Picks up an item from the current tile and adds it to the hero's inventory,
   * if possible.
   */
  @Intrinsic("Dungeon.Hero.pickUp()")
  public static native void pickUp();

  /**
   * Casts a fireball spell in front of the hero, damaging any enemies in its
   * path.
   */
  @Intrinsic("Dungeon.Hero.fireball()")
  public static native void fireball();

  /**
   * Makes the hero rest for a short period of time.
   */
  @Intrinsic("Dungeon.Hero.rest()")
  public static native void rest();

  /**
   * Checks if there is a tile of the specified type in the specified direction,
   * relative to the current facing direction.
   * 
   * @param tile      the type of tile to check for
   * @param direction the direction to check in
   * @return true if there is a tile of the specified type in the specified
   *         direction, false otherwise
   */
  @Intrinsic("Dungeon.Hero.isNearTile(Dungeon.LevelElement, Dungeon.Direction)")
  public static native boolean isNearTile(LevelElement tile, Direction direction);

  /**
   * Checks if the tile in the specified direction, relative to the current facing
   * direction, matches the target tile type.
   * 
   * @param target the type of tile to check for
   * @param actual the type of tile currently present in the specified direction
   * @return true if the actual tile matches the target tile type, false otherwise
   */
  @Intrinsic("Dungeon.Hero.matchesTile(Dungeon.LevelElement, Dungeon.LevelElement)")
  public static native boolean matchesTile(LevelElement target, LevelElement actual);

  /**
   * Checks if the entity in the specified direction, relative to the current
   * facing direction, is active (e.g., an enemy or an item).
   * 
   * @param direction the direction to check in
   * @return true if the entity is active, false otherwise
   */
  @Intrinsic("Dungeon.Hero.isActive(Dungeon.Direction)")
  public static native boolean isActive(Direction direction);
}
