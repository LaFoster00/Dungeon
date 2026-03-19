package blockly.dgir.compiler.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ConversionUtils {
  private ConversionUtils() {}

  public sealed interface Conversion {
    record Boxing(ResolvedPrimitiveType from, Type to) implements Conversion {}

    record Unboxing(ResolvedReferenceType from, Type to) implements Conversion {}

    record Generic(ResolvedType from, Type to) implements Conversion {}

    record None(ResolvedType from, ResolvedType to) implements Conversion {}

    record Invalid(String message) implements Conversion {}
  }

  public static Conversion detectImplicitConversion(ResolvedType from, ResolvedType to) {
    if (from.isPrimitive()) {
      // Boxing: primitive -> box
      if (to.isReferenceType()) {
        if (boxedTypeName(from.asPrimitive()).equals(to.asReferenceType().getQualifiedName())) {
          return new Conversion.Boxing(
              from.asPrimitive(), StaticJavaParser.parseType(to.describe()));
        }
      }
      // Generic: primitive -> object (e.g. generic function argument)
      else if (to.isTypeVariable()) {
        if (to.isAssignableBy(from)) {
          return new Conversion.Generic(from, StaticJavaParser.parseType("Object"));
        }
      }
    }

    if (from.isReferenceType()) {
      // Unboxing: box -> primitive
      if (to.isPrimitive()) {
        Optional<String> unboxed = unboxedTypeName(from.asReferenceType());
        if (unboxed.isPresent() && unboxed.get().equals(to.asPrimitive().describe())) {
          return new Conversion.Unboxing(
              from.asReferenceType(), StaticJavaParser.parseType(to.describe()));
        }
      }
      // Generic: explicitType -> object
      else if (to.isTypeVariable()) {
        if (to.isAssignableBy(from)) {
          return new Conversion.Generic(from, StaticJavaParser.parseType("Object"));
        }
      }
    }

    if (from.isArray()) {
      // Generic: array -> object
      if (to.isArray() || to.asArrayType().getComponentType().isTypeVariable()) {
        // Primitive arrays are not assignable to Object, but reference type arrays are, so only
        // allow the generic conversion for reference type arrays
        if (from.asArrayType().getComponentType().isPrimitive())
          return new Conversion.Invalid(
              "Cannot convert from primitive array type "
                  + from.describe()
                  + " to "
                  + to.describe());
        if (to.isAssignableBy(from)) {
          return new Conversion.Generic(from, StaticJavaParser.parseType("Object"));
        }
      }
    }

    return new Conversion.None(from, to);
  }

  /**
   * Convenience: given a primitive type, return the resolved type of its wrapper. Useful when you
   * want the explicit boxed type without a target type on hand.
   */
  public static @NotNull String boxedTypeName(@NotNull ResolvedPrimitiveType primitive) {
    return primitive.getBoxTypeQName();
  }

  /** Convenience: given a wrapper reference type, return the name of its primitive. */
  public static @NotNull Optional<String> unboxedTypeName(@NotNull ResolvedReferenceType wrapper) {
    if (wrapper.isUnboxable()) {
      return Optional.of(wrapper.toUnboxedType().orElseThrow().describe());
    }
    return Optional.empty();
  }
}
