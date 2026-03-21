package Dungeon;

import Intrinsic;
import Direction;
import ItemType;
import LevelElement;

public class Hero {
  @Intrinsic("Dungeon.Hero.move()")
  public static native void move();

  @Intrinsic("Dungeon.Hero.rotate(Dungeon.Direction)")
  public static native void rotate(Direction direction);

  @Intrinsic("Dungeon.Hero.interact(Dungeon.Direction)")
  public static native void interact(Direction direction);

  @Intrinsic("Dungeon.Hero.push()")
  public static native void push();

  @Intrinsic("Dungeon.Hero.pull()")
  public static native void pull();

  @Intrinsic("Dungeon.Hero.drop(Dungeon.ItemType)")
  public static native void drop(ItemType type);

  @Intrinsic("Dungeon.Hero.pickUp()")
  public static native void pickUp();

  @Intrinsic("Dungeon.Hero.fireball()")
  public static native void fireball();

  @Intrinsic("Dungeon.Hero.rest()")
  public static native void rest();

  @Intrinsic("Dungeon.Hero.isNearTile(Dungeon.LevelElement, Dungeon.Direction)")
  public static native boolean isNearTile(LevelElement tile, Direction direction);

  @Intrinsic("Dungeon.Hero.matchesTile(Dungeon.LevelElement, Dungeon.LevelElement)")
  public static native boolean matchesTile(LevelElement target, LevelElement actual);

  /* TODO: Implement this intrinsic
  @Intrinsic("Dungeon.Hero.isNearComponent(Class, Direction)")
  public static native boolean isNearComponent(Class<? extends Component>  component, Direction direction);
   */

  @Intrinsic("Dungeon.Hero.isActive(Dungeon.Direction)")
  public static native boolean isActive(Direction direction);
}
