package dgir.dialect.builtin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dgir.core.Dialect;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDescriptor;
import dgir.core.ir.TypeDetails;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Sealed marker interface for all types contributed by the {@link BuiltinDialect}.
 *
 * <p>Every concrete type must implement this interface so that {@link Dialect#allTypes(Class)} can
 * discover it automatically via reflection.
 */
public sealed interface BuiltinTypes {
  /** Abstract base class for all type-descriptors contributed by the {@link BuiltinDialect}. */
  sealed interface BuiltinTypeDescriptor extends TypeDescriptor {
    @Override
    default @NotNull String getNamespace() {
      return "";
    }

    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return BuiltinDialect.class;
    }

    // =========================================================================
    // Type Info
    // =========================================================================

    final class IntegerDescriptor implements BuiltinTypeDescriptor {
      private final String ident;
      private final @NotNull Supplier<Type> nonParametricInstance;
      private final Function<Object, Boolean> validator;

      IntegerDescriptor() {
        this(1, true);
      }

      public IntegerDescriptor(int width, boolean signed) {
        assert width == 1 || width == 8 || width == 16 || width == 32 || width == 64
            : "Invalid integer width: " + width;
        this.ident = IntegerT.identFromWidthAndSign(width, signed);
        this.nonParametricInstance =
            switch (width) {
              case 1 -> IntegerT::BOOL;
              case 8 -> signed ? IntegerT::INT8 : IntegerT::UINT8;
              case 16 -> signed ? IntegerT::INT16 : IntegerT::UINT16;
              case 32 -> signed ? IntegerT::INT32 : IntegerT::UINT32;
              case 64 -> signed ? IntegerT::INT64 : IntegerT::UINT64;
              default -> throw new IllegalArgumentException("Invalid integer width: " + width);
            };
        this.validator =
            value -> {
              if (!(value instanceof Number number)) return false;

              return switch (number) {
                case Byte ignored when width == 1 || width == 8 -> true;
                case Short ignored when width == 16 -> true;
                case Integer ignored when width == 32 -> true;
                case Long ignored when width == 64 -> true;
                default -> false;
              };
            };
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return IntegerT.class;
      }

      @Override
      public @NotNull Supplier<Type> getNonParametricInstance() {
        return nonParametricInstance;
      }

      @Override
      public @NotNull String getIdent() {
        return ident;
      }

      @Override
      public Function<Object, Boolean> getValidator() {
        return validator;
      }

      @Override
      public @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(
            new IntegerDescriptor(1, true),
            new IntegerDescriptor(8, true),
            new IntegerDescriptor(16, true),
            new IntegerDescriptor(32, true),
            new IntegerDescriptor(64, true),
            new IntegerDescriptor(8, false),
            new IntegerDescriptor(16, false),
            new IntegerDescriptor(32, false),
            new IntegerDescriptor(64, false));
      }
    }

    final class FloatDescriptor implements BuiltinTypeDescriptor {
      private final String ident;
      private final @NotNull Supplier<Type> nonParametricInstance;
      private final Function<Object, Boolean> validator;

      public FloatDescriptor(int width) {
        assert width == 32 || width == 64 : "Invalid float width: " + width;
        this.ident = FloatT.identFromWidth(width);
        this.nonParametricInstance =
            switch (width) {
              case 32 -> FloatT::FLOAT32;
              case 64 -> FloatT::FLOAT64;
              default -> throw new IllegalArgumentException("Invalid float width: " + width);
            };
        this.validator =
            value -> {
              if (!(value instanceof Number)) return false;

              return switch (value) {
                case Float ignored when width == 32 -> true;
                case Double ignored when width == 64 -> true;
                default -> false;
              };
            };
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return FloatT.class;
      }

      @Override
      public @NotNull Supplier<Type> getNonParametricInstance() {
        return nonParametricInstance;
      }

      @Override
      public @NotNull String getIdent() {
        return ident;
      }

      @Override
      public Function<Object, Boolean> getValidator() {
        return validator;
      }

      @Override
      public @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(new FloatDescriptor(32), new FloatDescriptor(64));
      }
    }
  }

  // =========================================================================
  // Utility Helpers
  // =========================================================================

  /**
   * Returns whether a type is numeric.
   *
   * @param type the type to inspect.
   * @return {@code true} for integer and floating-point types.
   */
  static boolean isNumeric(@NotNull Type type) {
    return type instanceof IntegerT || type instanceof FloatT;
  }

  /**
   * Returns the dominant numeric type for two numeric operands.
   *
   * @param lhsType the left-hand operand type.
   * @param rhsType the right-hand operand type.
   * @return the wider and/or more precise numeric type.
   * @throws IllegalArgumentException if either type is not numeric.
   */
  static @NotNull Type getDominantType(@NotNull Type lhsType, @NotNull Type rhsType) {
    if (!isNumeric(lhsType) || !isNumeric(rhsType)) {
      throw new IllegalArgumentException(
          "Dominant type requires numeric operands. Got " + lhsType + " and " + rhsType);
    }

    if (lhsType instanceof FloatT || rhsType instanceof FloatT) {
      int lhsFloatWidth = lhsType instanceof FloatT floatT ? floatT.getWidth() : 0;
      int rhsFloatWidth = rhsType instanceof FloatT floatT ? floatT.getWidth() : 0;
      int lhsIntWidth = lhsType instanceof IntegerT intT ? intT.getWidth() : 0;
      int rhsIntWidth = rhsType instanceof IntegerT intT ? intT.getWidth() : 0;
      int desiredWidth =
          Math.max(Math.max(lhsFloatWidth, rhsFloatWidth), Math.max(lhsIntWidth, rhsIntWidth));
      return desiredWidth > 32 ? FloatT.FLOAT64() : FloatT.FLOAT32();
    }

    int lhsWidth = ((IntegerT) lhsType).getWidth();
    boolean lhsIsSigned = ((IntegerT) lhsType).isSigned();
    int rhsWidth = ((IntegerT) rhsType).getWidth();
    boolean rhsIsSigned = ((IntegerT) rhsType).isSigned();
    // By default, the result is signed if both operands are signed. However, if one operand is
    // wider than the other, we take the signedness of the wider operand. This allows operations
    // like int8 + uint32 to yield a uint32 result, which is more intuitive and prevents accidental
    // overflow.
    boolean shouldBeSigned = lhsIsSigned && rhsIsSigned;
    if (lhsIsSigned != rhsIsSigned) {
      if (lhsWidth > rhsWidth) {
        shouldBeSigned = lhsIsSigned;
      } else {
        shouldBeSigned = rhsIsSigned;
      }
    }
    return integerTypeByWidth(Math.max(lhsWidth, rhsWidth), shouldBeSigned);
  }

  /**
   * Returns the canonical integer type for a given width and signedness.
   *
   * @param width the bit width.
   * @param isSigned whether the integer is signed.
   * @return the matching {@link IntegerT} singleton.
   * @throws IllegalArgumentException if the width is unsupported.
   */
  static @NotNull IntegerT integerTypeByWidth(int width, boolean isSigned) {
    return switch (width) {
      case 1 -> IntegerT.INT1();
      case 8 -> isSigned ? IntegerT.INT8() : IntegerT.UINT8();
      case 16 -> isSigned ? IntegerT.INT16() : IntegerT.UINT16();
      case 32 -> isSigned ? IntegerT.INT32() : IntegerT.UINT32();
      case 64 -> isSigned ? IntegerT.INT64() : IntegerT.UINT64();
      default -> throw new IllegalArgumentException("Invalid integer width: " + width);
    };
  }

  /**
   * Fixed-width integer type in the {@code builtin} dialect.
   *
   * <p>Supported widths: {@code 1} (bool), {@code 8}, {@code 16}, {@code 32}, {@code 64}.
   *
   * <p>Canonical ident: {@code int} (the width is not part of the ident — instances are compared by
   * parameterized ident, e.g. {@code int<32>}).
   *
   * <p>Pre-built singleton instances are available as static constants:
   *
   * <pre>
   *   IntegerT.BOOL / IntegerT.INT1  — 1-bit boolean
   *   IntegerT.INT8                  — 8-bit signed integer
   *   IntegerT.INT16                 — 16-bit signed integer
   *   IntegerT.INT32                 — 32-bit signed integer
   *   IntegerT.INT64                 — 64-bit signed integer
   *   IntegerT.UINT8                 — 8-bit unsigned integer
   *   IntegerT.UINT16                — 16-bit unsigned integer
   *   IntegerT.UINT32                — 32-bit unsigned integer
   *   IntegerT.UINT64                — 64-bit unsigned integer
   * </pre>
   */
  final class IntegerT extends Type implements BuiltinTypes {

    // =========================================================================
    // Static Fields
    // =========================================================================

    private static final IntegerT[] integerTypeCache = new IntegerT[9];

    public static void populateCache() {
      if (integerTypeCache[0] != null) return; // already populated
      if (TypeDetails.get("int1").isEmpty()) {
        throw new IllegalStateException(
            "IntegerT cache must be populated after type registration. Ensure that BuiltinDialect is registered before any types are accessed.");
      }
      integerTypeCache[0] = new IntegerT(1, true);
      integerTypeCache[1] = new IntegerT(8, true);
      integerTypeCache[2] = new IntegerT(16, true);
      integerTypeCache[3] = new IntegerT(32, true);
      integerTypeCache[4] = new IntegerT(64, true);
      integerTypeCache[5] = new IntegerT(8, false);
      integerTypeCache[6] = new IntegerT(16, false);
      integerTypeCache[7] = new IntegerT(32, false);
      integerTypeCache[8] = new IntegerT(64, false);
    }

    /** 1-bit integer used as a boolean ({@code false} = 0, {@code true} = 1). */
    public static IntegerT INT1() {
      return integerTypeCache[0];
    }

    /** Alias for {@link #INT1}. */
    public static IntegerT BOOL() {
      return INT1();
    }

    /** 8-bit signed integer. */
    public static IntegerT INT8() {
      return integerTypeCache[1];
    }

    /** 16-bit signed integer. */
    public static IntegerT INT16() {
      return integerTypeCache[2];
    }

    /** 32-bit signed integer. */
    public static IntegerT INT32() {
      return integerTypeCache[3];
    }

    /** 64-bit signed integer. */
    public static IntegerT INT64() {
      return integerTypeCache[4];
    }

    /** 8-bit unsigned integer. */
    public static IntegerT UINT8() {
      return integerTypeCache[5];
    }

    /** 16-bit unsigned integer. */
    public static IntegerT UINT16() {
      return integerTypeCache[6];
    }

    /** 32-bit unsigned integer. */
    public static IntegerT UINT32() {
      return integerTypeCache[7];
    }

    /** 64-bit unsigned integer. */
    public static IntegerT UINT64() {
      return integerTypeCache[8];
    }

    public static final byte FALSE = 0;
    public static final byte TRUE = 1;

    public static byte toByte(boolean value) {
      return value ? TRUE : FALSE;
    }

    public static boolean toBoolean(byte value) {
      return value == TRUE;
    }

    public static String identFromWidthAndSign(int width, boolean isSigned) {
      return (isSigned ? "" : "u") + "int" + width;
    }

    /**
     * Equality is based on the parameterized ident, ignoring the "u" prefix for unsigned types.
     * This allows signed and unsigned types of the same width to be considered equal for type
     * checking purposes, since the signedness is mostly a semantic detail that is important for the
     * arithmetic operations.
     */
    public boolean equal(@Nullable Object obj) {
      if (obj instanceof Type other) {
        String normalizedOther = other.getParameterizedIdent().replace("u", "");
        String normalizedThis = getParameterizedIdent().replace("u", "");
        return normalizedThis.equals(normalizedOther);
      }
      return false;
    }

    // =========================================================================
    // Members
    // =========================================================================

    /** The bit-width of this integer type (1, 8, 16, 32, or 64). */
    private final int width;

    /** Whether this type is signed. */
    private final boolean signed;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Create an integer type with the given bit-width and signedness.
     *
     * @param width must be one of 1, 8, 16, 32, or 64.
     * @param isSigned whether this type is signed.
     */
    private IntegerT(int width, boolean isSigned) {
      super(identFromWidthAndSign(width, isSigned));
      this.width = width;
      this.signed = isSigned;
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the bit-width of this integer type.
     *
     * @return the bit-width (1, 8, 16, 32, or 64).
     */
    @Contract(pure = true)
    public int getWidth() {
      return width;
    }

    /**
     * Returns whether this integer type is signed.
     *
     * @return {@code true} if this type is signed, {@code false} otherwise.
     */
    @Contract(pure = true)
    public boolean isSigned() {
      return signed;
    }

    /**
     * Take a number of any integer type and convert it to the correct Java type for this {@code
     * IntegerT}. For example, if this is {@link #INT16} and the input is a {@code Byte}, it is
     * widened to a {@code Short}.
     *
     * <p>For the 1-bit boolean type, any nonzero input is converted to 1, and zero is converted to
     * 0.
     *
     * <p>Signedness is implicitly handled. If you want to store a value of 255 in a byte, just pass
     * 255 to the function. The conversion to byte will cause the "signed" value to be -1, which is
     * the correct two's complement representation of 255 in a byte. During execution, it is the
     * responsibility of the runtime to call the correctly signed operations.
     *
     * <p>If you want to assign large unsigned values to long variables, you can use {@code UINT64}
     * and pass in a {@code Long} value. The conversion will not change the bits, so a value like
     * 2^63 will be represented as -2^63 in the resulting {@code Long}. Again, it is the
     * responsibility of the runtime to handle this correctly. {@code 0xFFFFFFFFFFFFFFFFL} is the
     * largest value that can be represented in an {@code Unsigned Long}.
     *
     * @param number the number to convert
     * @return the converted number in the narrowest Java type that matches this width.
     */
    @Contract(pure = true)
    public Number convertToValidNumber(long number) {
      return switch (width) {
        case 1 -> (byte) (number == 0 ? 0 : 1);
        case 8 -> (byte) number;
        case 16 -> (short) number;
        case 32 -> (int) number;
        case 64 -> number;
        default -> throw new RuntimeException("Invalid integer width: " + width);
      };
    }

    /**
     * For a given number, return its normalized long representation according to this integer type.
     * For signed types, this is just the long value of the number. For unsigned types, this is the
     * long value masked to the appropriate number of bits. For example, if this is {@code uint8}
     * and the input number is -1 (which would be 0xFFFFFFFFFFFFFFFF in two's complement), the
     * normalized long representation would be 255 (0xFF), which is the correct unsigned
     * interpretation of the bits.
     *
     * @param number the number to normalize
     * @return the normalized long representation of the number.
     */
    public long normalizedLongRepresentation(long number) {
      if (isSigned()) {
        return number;
      } else {
        return number & (1L << width) - 1L;
      }
    }
  }

  /**
   * Floating-point type in the {@code builtin} dialect.
   *
   * <p>Supported widths: {@code 32} (single-precision) and {@code 64} (double-precision).
   *
   * <p>Pre-built singleton instances:
   *
   * <pre>
   *   FloatT.FLOAT32 — 32-bit IEEE 754 float
   *   FloatT.FLOAT64 — 64-bit IEEE 754 double
   * </pre>
   */
  final class FloatT extends Type implements BuiltinTypes {

    // =========================================================================
    // Static Fields
    // =========================================================================
    private static final FloatT[] floatTypeCache = new FloatT[2];

    public static void populateCache() {
      if (floatTypeCache[0] != null) return; // already populated
      if (TypeDetails.get("int1").isEmpty()) {
        throw new IllegalStateException(
            "FloatT cache must be populated after type registration. Ensure that BuiltinDialect is registered before any types are accessed.");
      }
      floatTypeCache[0] = new FloatT(32);
      floatTypeCache[1] = new FloatT(64);
    }

    /** 32-bit single-precision floating-point type. */
    public static FloatT FLOAT32() {
      return floatTypeCache[0];
    }

    /** 64-bit double-precision floating-point type. */
    public static FloatT FLOAT64() {
      return floatTypeCache[1];
    }

    public static String identFromWidth(float width) {
      return "float" + width;
    }

    // =========================================================================
    // Members
    // =========================================================================

    /** The bit-width of this floating-point type (32 or 64). */
    private final int width;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a default 32-bit float type. */
    FloatT() {
      this(32);
    }

    /**
     * Create a floating-point type with the given bit-width.
     *
     * @param width must be either 32 or 64.
     */
    private FloatT(int width) {
      super(identFromWidth(width));
      this.width = width;
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the bit-width of this floating-point type.
     *
     * @return the bit-width (32 or 64).
     */
    @JsonIgnore
    public int getWidth() {
      return width;
    }

    /**
     * Converts a numeric value to the Java primitive wrapper matching this float width.
     *
     * @param number the number to convert.
     * @return the converted number as a {@link Float} or {@link Double}.
     */
    public Number convertToValidNumber(Number number) {
      return switch (width) {
        case 32 -> number.floatValue();
        case 64 -> number.doubleValue();
        default -> throw new RuntimeException("Invalid float width: " + width);
      };
    }
  }
}
