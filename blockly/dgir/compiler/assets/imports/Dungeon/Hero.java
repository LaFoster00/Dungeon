package Dungeon;

import Intrinsic;
import Direction;
import ItemType;
import LevelElement;

public class Hero {
  @Intrinsic("Dungeon.Hero.move()")
  public static void move() {
  }

  @Intrinsic("Dungeon.Hero.rotate(Dungeon.Direction)")
  public static void rotate(Direction direction) {
  }

  @Intrinsic("Dungeon.Hero.interact(Dungeon.Direction)")
  public static void interact(Direction direction) {
  }

  @Intrinsic("Dungeon.Hero.push()")
  public static void push() {
  }

  @Intrinsic("Dungeon.Hero.pull()")
  public static void pull() {
  }

  @Intrinsic("Dungeon.Hero.drop(Dungeon.ItemType)")
  public static void drop(ItemType type) {
  }

  @Intrinsic("Dungeon.Hero.pickUp()")
  public static void pickUp() {
  }

  @Intrinsic("Dungeon.Hero.fireball()")
  public static void fireball() {
  }

  @Intrinsic("Dungeon.Hero.rest()")
  public static void rest() {
  }

  @Intrinsic("Dungeon.Hero.isNearTile(Dungeon.LevelElement, Dungeon.Direction)")
  public static boolean isNearTile(LevelElement tile, Direction direction) {
  }

  @Intrinsic("Dungeon.Hero.matchesTile(Dungeon.LevelElement, Dungeon.LevelElement)")
  public static boolean matchesTile(LevelElement target, LevelElement actual) {
  }

  /* TODO: Implement this intrinsic
  @Intrinsic("Dungeon.Hero.isNearComponent(Class, Direction)")
  public static boolean isNearComponent(Class<? extends Component>  component, Direction direction) {}
   */

  @Intrinsic("Dungeon.Hero.active(Dungeon.Direction)")
  public static boolean active(Direction direction) {
  }
}
