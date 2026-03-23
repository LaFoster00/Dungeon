package Dungeon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark methods and classes as intrinsics, which
 * means that they are not implemented in Java but are instead provided by the
 * underlying system or runtime environment. The value of the annotation should
 * be a string that specifies the qualified name of the intrinsic operation that
 * this method or class corresponds to.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Intrinsic {
  /**
   * Returns the qualified name of the intrinsic operation that this method or
   * class
   * corresponds to.
   * 
   * @return the qualified name of the intrinsic operation
   */
  String value();
}
