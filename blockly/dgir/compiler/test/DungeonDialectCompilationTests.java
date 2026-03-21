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
    boolean active = Hero.isActive(Direction.LEFT);
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

  @Test
  void arrayOps() {
    String code =
"""
import Dungeon.Arrays;

public class %ClassName {
  public static void main() {
    {
      int[] arr = new int[2];
      arr[0] = 1;
      arr[1] = 2;
      assert arr.length == 2 : "Expected length to be 2, but got " + arr.length;
      assert arr[0] == 1 : "Expected arr[0] to be 1, but got " + arr[0];
      assert arr[1] == 2 : "Expected arr[1] to be 2, but got " + arr[1];
      arr = Arrays.copyOf(arr, 3);
      assert arr.length == 3 : "Expected arr.length to be 3, but got " + arr.length;
      assert arr[2] == 0 : "Expected arr[2] to be 0, but got " + arr[2];
    }
    {
      int[] arr = new int[]{1, 2, 3};
      assert arr.length == 3 : "Expected length to be 3, but got " + arr.length;
      assert arr[0] == 1 : "Expected arr[0] to be 1, but got " + arr[0];
      assert arr[1] == 2  : "Expected arr[1] to be 2, but got " + arr[1];
      assert arr[2] == 3 : "Expected arr[2] to be 3, but got " + arr[2];
      arr = Arrays.copyOf(arr, 2);
      assert arr.length == 2 : "Expected arr.length to be 2, but got " + arr.length;
      assert arr[0] == 1 : "Expected arr[0] to be 1, but got " + arr[0];
    }
  }
}
""";
    testSource(code);
  }
}
