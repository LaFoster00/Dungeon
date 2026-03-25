package dgir.dialect.arith;

import dgir.core.Dialect;
import dgir.core.debug.Location;
import dgir.core.ir.*;
import dgir.core.traits.IBinaryOperands;
import dgir.core.traits.IHasResult;
import dgir.core.traits.INoOperands;
import dgir.core.traits.ISingleOperand;
import dgir.dialect.str.StrAttrs;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static dgir.dialect.arith.ArithAttrs.BinModeAttr;
import static dgir.dialect.arith.ArithAttrs.BinModeAttr.BinMode;
import static dgir.dialect.arith.ArithAttrs.UnaryModeAttr;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinAttrs.TypeAttribute;
import static dgir.dialect.builtin.BuiltinTypes.*;

/**
 * Sealed marker interface for all operations in the {@link ArithDialect}.
 *
 * <p>Every concrete op must both extend {@link ArithOp} and implement this interface so that {@link
 * Dialect#allOps(Class)} can discover it automatically via reflection.
 */
public sealed interface ArithOps {
  /**
   * Abstract base class for all operations in the {@code arith} (arithmetic) dialect.
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and {@link #getVerifier()}, and must
   * implement {@link ArithOps} to be enumerated by {@link ArithDialect}.
   */
  abstract class ArithOp extends Op {

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Default constructor used during dialect registration. */
    ArithOp() {
      super();
    }

    // =========================================================================
    // Op Info
    // =========================================================================

    /**
     * Returns the dialect that owns this operation.
     *
     * @return the {@link ArithDialect} class.
     */
    @Contract(pure = true)
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return ArithDialect.class;
    }

    /**
     * Returns the namespace prefix used when printing this operation.
     *
     * @return the fixed {@code "arith"} namespace.
     */
    @Contract(pure = true)
    @Override
    public @NotNull String getNamespace() {
      return "arith";
    }
  }

  /**
   * Unary arithmetic operation in the {@code arith} dialect.
   *
   * <p>The operand and result types must both be numeric, and the operation kind is selected via
   * the required {@code unaryMode} attribute.
   */
  final class UnaryOp extends ArithOp implements ArithOps, ISingleOperand, IHasResult {

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "arith.unary"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "arith.unary";
    }

    /**
     * Returns the default {@code unaryMode} attribute for this operation.
     *
     * @return a single {@link UnaryModeAttr} attribute named {@code unaryMode}.
     */
    @Override
    public @NotNull @Unmodifiable List<@NotNull NamedAttribute> getDefaultAttributes() {
      return List.of(new NamedAttribute("unaryMode", new UnaryModeAttr()));
    }

    /**
     * Verifies operand, result, and mode constraints for this operation.
     *
     * @return a verifier that returns {@code true} when the op is well-formed.
     */
    @Override
    public @NotNull Function<Operation, Boolean> getVerifier() {
      return operation -> {
        var unaryOp = operation.as(UnaryOp.class).orElseThrow();
        Type targetType = unaryOp.getOperand().getType();
        if (!isNumeric(targetType)) {
          operation.emitError("Operands must be numeric");
          return false;
        }
        if (!unaryOp.getResultType().equals(targetType)) {
          operation.emitError("Result type must match the operand type");
          return false;
        }
        if (unaryOp.getAttributeAs("unaryMode", UnaryModeAttr.class).isEmpty()) {
          unaryOp.emitError("Unary operation must define a unaryMode attribute");
          return false;
        }
        UnaryModeAttr.UnaryMode unaryMode =
            unaryOp.getAttributeAs("unaryMode", UnaryModeAttr.class).get().getMode();
        Optional<String> result = unaryMode.verifyOperand(unaryOp);
        if (result.isPresent()) {
          unaryOp.emitError(result.get());
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private UnaryOp() {}

    /**
     * Creates a unary operation.
     *
     * <p>In case of increment or decrement operations the output value will automatically be set to
     * the operand, as these operations conceptually update the operand in-place. For other unary
     * operation kinds, the output value will be left defaulted to the operand type.
     *
     * @param loc the source location of this operation.
     * @param operand the operand to operate on.
     * @param mode the unary operation kind.
     */
    public UnaryOp(
        @NotNull Location loc, @NotNull Value operand, @NotNull UnaryModeAttr.UnaryMode mode) {
      setOperation(Operation.Create(loc, this, List.of(operand), null, operand.getType()));
      getAttributeAs("unaryMode", UnaryModeAttr.class).orElseThrow().setMode(mode);
      switch (mode) {
        case INCREMENT, DECREMENT -> setOutputValue(operand);
        default -> {}
      }
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the configured unary operation mode.
     *
     * @return the {@code unaryMode} attribute value.
     * @throws AssertionError if the attribute is missing.
     */
    public @NotNull UnaryModeAttr.UnaryMode getMode() {
      return getAttributeAs("unaryMode", UnaryModeAttr.class)
          .map(UnaryModeAttr::getMode)
          .orElseThrow(() -> new AssertionError("No unaryMode attribute found."));
    }
  }

  /**
   * Unified binary numeric operation for the {@code arith} dialect.
   *
   * <p>MLIR reference: {@code arith.bin}
   */
  final class BinaryOp extends ArithOp implements ArithOps, IBinaryOperands, IHasResult {

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "arith.bin"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "arith.bin";
    }

    /**
     * Returns the default {@code binMode} attribute for this operation.
     *
     * @return a single {@link BinModeAttr} attribute named {@code binMode}.
     */
    @Override
    public @NotNull @Unmodifiable List<NamedAttribute> getDefaultAttributes() {
      return List.of(new NamedAttribute("binMode", new BinModeAttr()));
    }

    /**
     * Verifies operand, result, and mode constraints for this operation.
     *
     * @return a verifier that returns {@code true} when the op is well-formed.
     */
    @Override
    public @NotNull Function<Operation, Boolean> getVerifier() {
      return operation -> {
        BinaryOp binaryOp = operation.as(BinaryOp.class).orElseThrow();
        Type lhsType = binaryOp.getLhs().getType();
        Type rhsType = binaryOp.getRhs().getType();
        if (!isNumeric(lhsType) || !isNumeric(rhsType)) {
          binaryOp.emitError("Operands must be numeric");
          return false;
        }

        if (binaryOp.getAttributeAs("binMode", BinModeAttr.class).isEmpty()) {
          binaryOp.emitError("Binary operation must define a binMode attribute");
          return false;
        }
        BinMode binMode = binaryOp.getAttributeAs("binMode", BinModeAttr.class).get().getMode();
        Optional<String> modeError = binMode.verifyOperands(binaryOp);
        if (modeError.isPresent()) {
          binaryOp.emitError(modeError.get());
          return false;
        }
        switch (binMode) {
          // Regular arithmetic operations.
          case ADD, SUB, MUL, DIV, DIVUI, MOD, MODUI -> {
            if (!binaryOp.getResult().getType().equals(getDominantType(lhsType, rhsType))) {
              binaryOp.emitError("Result type must be the dominant type of LHS and RHS");
              return false;
            }
          }
          case BOR, BAND, BXOR -> {
            if (!binaryOp.getResult().getType().equals(lhsType)) {
              binaryOp.emitError("Result type must match LHS and RHS type");
              return false;
            }
          }
          // Binary shift operations.
          case LSH, RSHS, RSHU -> {
            if (!binaryOp.getResult().getType().equals(lhsType)) {
              binaryOp.emitError("Result type must match LHS type");
              return false;
            }
          }
          // Logical operations.
          case AND, OR, XOR, EQ, NE, LT, LE, GT, GE -> {
            if (!binaryOp.getResult().getType().equals(IntegerT.BOOL)) {
              binaryOp.emitError("Result type must be boolean");
              return false;
            }
          }
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private BinaryOp() {}

    /**
     * Creates a binary operation with two numeric operands.
     *
     * @param loc the source location of this operation.
     * @param lhs the left-hand operand.
     * @param rhs the right-hand operand.
     * @param binMode the binary operation kind.
     */
    public BinaryOp(
        @NotNull Location loc, @NotNull Value lhs, @NotNull Value rhs, @NotNull BinMode binMode) {
      // Get the right output type for the given operands and binary operation kind.
      Type outputType =
          switch (binMode) {
            // Regular arithmetic operations.
            case ADD, SUB, MUL, DIV, DIVUI, MOD, MODUI ->
                getDominantType(lhs.getType(), rhs.getType());
            // Binary operations.
            case BOR, BAND, BXOR, LSH, RSHS, RSHU -> lhs.getType();
            // Logical operations.
            case AND, OR, XOR, EQ, NE, LT, LE, GT, GE -> IntegerT.BOOL;
          };

      setOperation(Operation.Create(loc, this, List.of(lhs, rhs), null, outputType));
      getAttributeAs("binMode", BinModeAttr.class).orElseThrow().setMode(binMode);
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the configured binary operation mode.
     *
     * @return the {@code binMode} attribute value.
     * @throws AssertionError if the attribute is missing.
     */
    public @NotNull BinMode getMode() {
      return getAttributeAs("binMode", BinModeAttr.class)
          .map(BinModeAttr::getMode)
          .orElseThrow(() -> new AssertionError("No binMode attribute found."));
    }
  }

  /**
   * Casts a numeric operand to a target numeric type.
   *
   * <p>The input and output types must both be numeric, and the result type is taken from the
   * required {@code to} attribute.
   */
  final class CastOp extends ArithOp implements ArithOps, ISingleOperand, IHasResult {

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "arith.cast"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "arith.cast";
    }

    /**
     * Returns the default {@code to} attribute for this operation.
     *
     * @return a single {@link TypeAttribute} attribute named {@code to}.
     */
    @Override
    public @NotNull @Unmodifiable List<NamedAttribute> getDefaultAttributes() {
      return List.of(new NamedAttribute("to", new TypeAttribute()));
    }

    /**
     * Verifies operand, target type, and result type constraints for this operation.
     *
     * @return a verifier that returns {@code true} when the op is well-formed.
     */
    @Override
    public @NotNull Function<Operation, Boolean> getVerifier() {
      return operation -> {
        var castOp = operation.as(CastOp.class).orElseThrow();
        if (!isNumeric(castOp.getOperandType())) {
          castOp.emitError("Cast operand must be numeric");
          return false;
        }
        Type targetType = castOp.getTargetType();
        if (!isNumeric(targetType)) {
          castOp.emitError("Cast target type must be numeric");
          return false;
        }
        if (!castOp.getResultType().equals(targetType)) {
          castOp.emitError("Cast result type must match the target type");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private CastOp() {}

    /**
     * Creates a cast operation.
     *
     * @param loc the source location of this operation.
     * @param value the value to cast.
     * @param targetType the target numeric type.
     */
    public CastOp(@NotNull Location loc, @NotNull Value value, @NotNull Type targetType) {
      setOperation(Operation.Create(loc, this, List.of(value), null, targetType));
      getAttributeAs("to", TypeAttribute.class).orElseThrow().setType(targetType);
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the configured target type of this cast.
     *
     * @return the {@code to} attribute value.
     * @throws AssertionError if the attribute is missing.
     */
    public @NotNull Type getTargetType() {
      return getAttributeAs("to", TypeAttribute.class)
          .map(TypeAttribute::getType)
          .orElseThrow(() -> new AssertionError("No target type attribute found."));
    }
  }

  /**
   * Produces a single constant value in the {@code arith} dialect.
   *
   * <p>The constant is carried by the required {@code "value"} attribute, which must be a {@link
   * TypedAttribute}. The operation result type is taken directly from the attribute type.
   */
  final class ConstantOp extends ArithOp implements ArithOps, INoOperands, IHasResult {

    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "arith.constant"}.
     */
    @Contract(pure = true)
    @Override
    public @NotNull String getIdent() {
      return "arith.constant";
    }

    /**
     * Verifies that the constant is structurally valid.
     *
     * @return a verifier that always accepts the operation.
     */
    @Override
    public @NotNull Function<Operation, Boolean> getVerifier() {
      return ignored -> true;
    }

    /**
     * Returns the default {@code value} attribute for this operation.
     *
     * @return a single {@link IntegerAttribute} attribute named {@code value}.
     */
    @Contract(pure = true)
    @Override
    public @NotNull List<NamedAttribute> getDefaultAttributes() {
      return List.of(new NamedAttribute("value", new IntegerAttribute(0, IntegerT.INT32)));
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private ConstantOp() {}

    /**
     * Create a constant op whose value is given by the typed attribute.
     *
     * @param location the source location of this operation.
     * @param value the typed attribute holding the constant value and its type.
     */
    public ConstantOp(@NotNull Location location, @NotNull TypedAttribute value) {
      setOperation(true, Operation.Create(location, this, null, null, value.getType()));
      getAttributeMap().get("value").setAttribute(value);
    }

    /**
     * Create a string constant.
     *
     * @param location the source location of this operation.
     * @param value the string literal to embed.
     */
    public ConstantOp(@NotNull Location location, @NotNull String value) {
      this(location, new StrAttrs.StringAttribute(value));
    }

    /**
     * Create a 32-bit integer constant.
     *
     * @param location the source location of this operation.
     * @param value the integer value to embed.
     */
    public ConstantOp(@NotNull Location location, int value) {
      this(location, new IntegerAttribute(value, IntegerT.INT32));
    }

    /**
     * Create a boolean ({@code int1}) constant.
     *
     * @param location the source location of this operation.
     * @param value the boolean value to embed.
     */
    public ConstantOp(@NotNull Location location, boolean value) {
      this(location, new IntegerAttribute(value ? 1 : 0, IntegerT.BOOL));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the {@code "value"} typed attribute.
     *
     * @return the value attribute.
     * @throws AssertionError if the attribute is absent.
     */
    @Contract(pure = true)
    public @NotNull TypedAttribute getValueAttribute() {
      return getAttributeAs("value", TypedAttribute.class)
          .orElseThrow(() -> new AssertionError("No value attribute found."));
    }

    /**
     * Returns the type of the constant value.
     *
     * @return the value's type.
     */
    @Contract(pure = true)
    public @NotNull Type getValueType() {
      return getValueAttribute().getType();
    }

    /**
     * Returns the raw storage object of the constant value.
     *
     * @return the value's underlying storage object.
     */
    @Contract(pure = true)
    public Object getValueStorage() {
      return getValueAttribute().getStorage();
    }
  }
}
