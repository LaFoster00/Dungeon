import dgir.vm.dialect.io.IoRunners;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DungeonDialectCompilationTests extends CompilerTestBase {
  @Test
  void allHeroOps() {
    String code =
"""
import Dungeon.Hero;
import Dungeon.Direction;
import Dungeon.ItemType;
import Dungeon.LevelElement;

public class %ClassName {
  public static void main() {
    Hero.move();

    Hero.rotate(Direction.LEFT);
    Hero.rotate(Direction.RIGHT);

    Hero.interact(Direction.HERE);
    Hero.interact(Direction.LEFT);
    Hero.interact(Direction.RIGHT);
    Hero.interact(Direction.INFRONT);
    Hero.interact(Direction.BEHIND);

    Hero.drop(ItemType.CLOVER);
    Hero.drop(ItemType.BREADCRUMB);

    Hero.push();
    Hero.pull();
    Hero.pickUp();
    Hero.fireball();
    Hero.rest();

    boolean nearTile = Hero.isNearTile(LevelElement.WALL, Direction.INFRONT);
    boolean matchesTile = Hero.matchesTile(LevelElement.FLOOR, LevelElement.FLOOR);
    boolean active = Hero.active(Direction.LEFT);
  }
}
""";
    testSource(code, false);
  }

  @Test
  void allIoOps() {
    String code =
"""
import Dungeon.IO;

public class %ClassName {
  public static void main() {
    IO.print("Hello, world!\\n");
    IO.println("Hello, world!");
    IO.printf("Hello, %s!\\n", "world");
    IO.printf("Hello, %s! the %snd\\n", "world", 2);
    String input = IO.nextLine();
    boolean bool = IO.nextBoolean();
    byte b = IO.nextByte();
    short s = IO.nextShort();
    int i = IO.nextInt();
    long l = IO.nextLong();
    float f = IO.nextFloat();
    double d = IO.nextDouble();
    IO.printf("You entered: %s, %b, %d, %d, %d, %d, %f, %f\\n", input, bool, b, s, i, l, f, d);
  }
}
""";
    String simulatedInput =
        "Sex-Dungeon\ntrue\n42\n12345\n67890\n1234567890123456789\n3.14\n2.71828\n";
    IoRunners.ConsoleInRunner.setInputStream(
        new ByteArrayInputStream(simulatedInput.getBytes(UTF_8)));
    testSource(code);
  }
}
