package Dungeon;

import java.lang.annotation.ElementType;

// Marker annotation your compiler looks for
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Intrinsic {
  String value(); // the IR opcode name
}
