package dgir.dialect.str;

import dgir.core.Dialect;
import dgir.core.debug.Location;
import dgir.core.ir.Op;
import dgir.core.ir.Operation;
import dgir.core.ir.Value;
import dgir.core.traits.IBinaryOperands;
import dgir.core.traits.IHasResult;
import dgir.core.traits.ISingleOperand;
import dgir.dialect.builtin.BuiltinTypes;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Sealed marker interface for all operations in the {@link StrDialect}.
 *
 * <p>Every concrete op must both extend {@link StrOp} and implement this interface so that {@link
 * Dialect#allOps(Class)} can discover it automatically via reflection.
 */
public sealed interface StrOps {
  /**
   * Abstract base class for all operations in the {@code str} dialect.
   *
   * <p>Concrete subclasses must implement {@link #getIdent()} and {@link #getVerifier()}, and must
   * implement {@link StrOps} to be enumerated by {@link StrDialect}.
   */
  abstract class StrOp extends Op {
    /**
     * Returns the dialect that owns this operation.
     *
     * @return the {@link StrDialect} class.
     */
    @Override
    public @NotNull Class<? extends Dialect> getDialect() {
      return StrDialect.class;
    }

    /**
     * Returns the namespace prefix used when printing this operation.
     *
     * @return the fixed {@code "str"} namespace.
     */
    @Override
    public @NotNull String getNamespace() {
      return "str";
    }
  }

  final class ToStringOp extends StrOp implements StrOps, ISingleOperand, IHasResult {
    @Override
    public @NotNull String getIdent() {
      return "str.to_string";
    }

    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        ToStringOp toStringOp = operation.as(ToStringOp.class).orElseThrow();
        if (!toStringOp.getResultType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Result type must be string");
          return false;
        }
        if (!(toStringOp.getOperand().getType() instanceof StrTypes.StrType)
            && !(toStringOp.getOperand().getType() instanceof BuiltinTypes.BuiltinType)) {
          operation.emitError(
              "Operand must be a string or builtin type. Got "
                  + toStringOp.getOperand().getType()
                  + " instead");
          return false;
        }
        return true;
      };
    }

    @SuppressWarnings("unused")
    private ToStringOp() {}

    @SuppressWarnings("unused")
    public ToStringOp(@NotNull Location location, @NotNull Value operand) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(operand), null, StrTypes.StringT.INSTANCE));
    }
  }

  final class ConcatOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.concat"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.concat";
    }

    /**
     * Verifies that both operands and the result are string-compatible.
     *
     * @return a verifier that accepts well-formed concatenations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        ConcatOp concatOp = operation.as(ConcatOp.class).orElseThrow();
        if (!concatOp.getResultType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Result type must be string");
          return false;
        }
        if (!(concatOp.getLhs().getType() instanceof StrTypes.StrType)
            && !(concatOp.getLhs().getType() instanceof BuiltinTypes.BuiltinType)) {
          operation.emitError(
              "LHS operand must be a string or builtin type. Got "
                  + concatOp.getLhs().getType()
                  + " instead");
          return false;
        }
        if (!(concatOp.getRhs().getType() instanceof StrTypes.StrType)
            && !(concatOp.getRhs().getType() instanceof BuiltinTypes.BuiltinType)) {
          operation.emitError(
              "RHS operand must be a string or builtin type. Got "
                  + concatOp.getRhs().getType()
                  + " instead");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private ConcatOp() {}

    /**
     * Creates a concatenation operation for the given operands.
     *
     * @param location the source location of this operation.
     * @param left the left operand.
     * @param right the right operand.
     */
    public ConcatOp(@NotNull Location location, @NotNull Value left, @NotNull Value right) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(left, right), null, StrTypes.StringT.INSTANCE));
    }
  }

  final class LengthOp extends StrOp implements StrOps, ISingleOperand, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.length"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.length";
    }

    /**
     * Verifies that the operand is a string and the result is an int32.
     *
     * @return a verifier that accepts well-formed length operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        LengthOp lengthOp = operation.as(LengthOp.class).orElseThrow();
        if (!lengthOp.getResultType().equals(BuiltinTypes.IntegerT.INT32)) {
          operation.emitError("Result type must be int");
          return false;
        }
        if (!lengthOp.getOperand().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Operand must be string");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private LengthOp() {}

    /**
     * Creates a length operation for the given string.
     *
     * @param location the source location of this operation.
     * @param operand the string operand.
     */
    public LengthOp(@NotNull Location location, @NotNull Value operand) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(operand), null, BuiltinTypes.IntegerT.INT32));
    }
  }

  final class CharAtOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.char_at"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.char_at";
    }

    /**
     * Verifies that the operands and result match the character-at contract.
     *
     * @return a verifier that accepts well-formed character lookups.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        CharAtOp charAtOp = operation.as(CharAtOp.class).orElseThrow();
        if (!charAtOp.getResultType().equals(BuiltinTypes.IntegerT.UINT16)) {
          operation.emitError("Result type must be uint16");
          return false;
        }
        if (!charAtOp.getLhs().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("LHS operand must be string");
          return false;
        }
        if (!charAtOp.getRhs().getType().equals(BuiltinTypes.IntegerT.INT32)) {
          operation.emitError("RHS operand must be int");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private CharAtOp() {}

    /**
     * Creates a character-at operation.
     *
     * @param location the source location of this operation.
     * @param string the string operand.
     * @param index the index operand.
     */
    public CharAtOp(@NotNull Location location, @NotNull Value string, @NotNull Value index) {
      setOperation(
          true,
          Operation.Create(
              location, this, List.of(string, index), null, BuiltinTypes.IntegerT.UINT16));
    }
  }

  final class EqualsOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.equals"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.equals";
    }

    /**
     * Verifies that both operands are strings and the result is boolean.
     *
     * @return a verifier that accepts well-formed equality comparisons.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        EqualsOp equalsOp = operation.as(EqualsOp.class).orElseThrow();
        if (!equalsOp.getResultType().equals(BuiltinTypes.IntegerT.BOOL)) {
          operation.emitError("Result type must be bool");
          return false;
        }
        if (!equalsOp.getLhs().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("LHS operand must be string");
          return false;
        }
        if (!equalsOp.getRhs().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("RHS operand must be string");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private EqualsOp() {}

    /**
     * Creates a string equality comparison.
     *
     * @param location the source location of this operation.
     * @param left the left operand.
     * @param right the right operand.
     */
    public EqualsOp(@NotNull Location location, @NotNull Value left, @NotNull Value right) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(left, right), null, BuiltinTypes.IntegerT.BOOL));
    }
  }

  final class IsEmptyOp extends StrOp implements StrOps, ISingleOperand, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.is_empty"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.is_empty";
    }

    /**
     * Verifies that the operand is a string and the result is boolean.
     *
     * @return a verifier that accepts well-formed emptiness checks.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        IsEmptyOp isEmptyOp = operation.as(IsEmptyOp.class).orElseThrow();
        if (!isEmptyOp.getResultType().equals(BuiltinTypes.IntegerT.BOOL)) {
          operation.emitError("Result type must be bool");
          return false;
        }
        if (!isEmptyOp.getOperand().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Operand must be string");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    private IsEmptyOp() {}

    /**
     * Creates an emptiness-check operation for the given string.
     *
     * @param location the source location of this operation.
     * @param operand the string operand.
     */
    public IsEmptyOp(@NotNull Location location, @NotNull Value operand) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(operand), null, BuiltinTypes.IntegerT.BOOL));
    }
  }

  final class ToLowerCaseOp extends StrOp implements StrOps, ISingleOperand, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.to_lower_case"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.to_lower_case";
    }

    /**
     * Verifies that the operand and result are strings.
     *
     * @return a verifier that accepts well-formed lowercase conversions.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        ToLowerCaseOp op = operation.as(ToLowerCaseOp.class).orElseThrow();
        if (!op.getResultType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Result type must be string");
          return false;
        }
        if (!op.getOperand().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Operand must be string");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private ToLowerCaseOp() {}

    /**
     * Creates a lowercase conversion operation.
     *
     * @param location the source location of this operation.
     * @param operand the string operand.
     */
    public ToLowerCaseOp(@NotNull Location location, @NotNull Value operand) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(operand), null, StrTypes.StringT.INSTANCE));
    }
  }

  final class ToUpperCaseOp extends StrOp implements StrOps, ISingleOperand, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.to_upper_case"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.to_upper_case";
    }

    /**
     * Verifies that the operand and result are strings.
     *
     * @return a verifier that accepts well-formed uppercase conversions.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        ToUpperCaseOp op = operation.as(ToUpperCaseOp.class).orElseThrow();
        if (!op.getResultType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Result type must be string");
          return false;
        }
        if (!op.getOperand().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Operand must be string");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private ToUpperCaseOp() {}

    /**
     * Creates an uppercase conversion operation.
     *
     * @param location the source location of this operation.
     * @param operand the string operand.
     */
    public ToUpperCaseOp(@NotNull Location location, @NotNull Value operand) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(operand), null, StrTypes.StringT.INSTANCE));
    }
  }

  final class TrimOp extends StrOp implements StrOps, ISingleOperand, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.trim"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.trim";
    }

    /**
     * Verifies that the operand and result are strings.
     *
     * @return a verifier that accepts well-formed trim operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        TrimOp op = operation.as(TrimOp.class).orElseThrow();
        if (!op.getResultType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Result type must be string");
          return false;
        }
        if (!op.getOperand().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Operand must be string");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private TrimOp() {}

    /**
     * Creates a trim operation.
     *
     * @param location the source location of this operation.
     * @param operand the string operand.
     */
    public TrimOp(@NotNull Location location, @NotNull Value operand) {
      setOperation(
          true,
          Operation.Create(location, this, List.of(operand), null, StrTypes.StringT.INSTANCE));
    }
  }

  final class SubstringOp extends StrOp implements StrOps, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.substring"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.substring";
    }

    /**
     * Verifies the operand count, operand types, and string result type.
     *
     * @return a verifier that accepts well-formed substring operations.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        SubstringOp op = operation.as(SubstringOp.class).orElseThrow();
        if (!op.getResultType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("Result type must be string");
          return false;
        }
        if (operation.getOperands().size() < 2 || operation.getOperands().size() > 3) {
          operation.emitError("Operation must have at least two operands and at most three");
          return false;
        }
        if (!op.getOperandValue(0).orElseThrow().getType().equals(StrTypes.StringT.INSTANCE)) {
          operation.emitError("LHS operand (string) must be string");
          return false;
        }
        if (!op.getOperandValue(1).orElseThrow().getType().equals(BuiltinTypes.IntegerT.INT32)) {
          operation.emitError("RHS operand (beginIndex) must be int");
          return false;
        }
        if (operation.getOperands().size() == 3
            && !op.getOperandValue(2).orElseThrow().getType().equals(BuiltinTypes.IntegerT.INT32)) {
          operation.emitError("Optional third operand (endIndex) must be int");
          return false;
        }
        return true;
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private SubstringOp() {}

    /**
     * Creates a substring operation with a begin index only.
     *
     * @param location the source location of this operation.
     * @param string the source string.
     * @param beginIndex the inclusive begin index.
     */
    public SubstringOp(
        @NotNull Location location, @NotNull Value string, @NotNull Value beginIndex) {
      setOperation(
          true,
          Operation.Create(
              location, this, List.of(string, beginIndex), null, StrTypes.StringT.INSTANCE));
    }

    /**
     * Creates a substring operation with an explicit end index.
     *
     * @param location the source location of this operation.
     * @param string the source string.
     * @param beginIndex the inclusive begin index.
     * @param endIndex the exclusive end index.
     */
    public SubstringOp(
        @NotNull Location location,
        @NotNull Value string,
        @NotNull Value beginIndex,
        @NotNull Value endIndex) {
      setOperation(
          true,
          Operation.Create(
              location,
              this,
              List.of(string, beginIndex, endIndex),
              null,
              StrTypes.StringT.INSTANCE));
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the source string operand.
     *
     * @return the string value at operand position {@code 0}.
     */
    @Contract(pure = true)
    public @NotNull Value getString() {
      return getOperandValue(0).orElseThrow();
    }

    /**
     * Returns the begin-index operand.
     *
     * @return the begin index at operand position {@code 1}.
     */
    @Contract(pure = true)
    public @NotNull Value getBeginIndex() {
      return getOperandValue(1).orElseThrow();
    }

    /**
     * Returns the optional end-index operand.
     *
     * @return the end index at operand position {@code 2}, or an empty optional if absent.
     */
    @Contract(pure = true)
    public @NotNull Optional<Value> getEndIndex() {
      return getOperandValue(2);
    }
  }

  /**
   * Shared validation helper for binary string operations.
   *
   * @param binaryOperands the binary operation to validate.
   * @return {@code true} if both operands are strings.
   */
  static boolean checkStrictBinaryStringOp(IBinaryOperands binaryOperands) {
    if (!binaryOperands.getLhs().getType().equals(StrTypes.StringT.INSTANCE)) {
      binaryOperands.getOperation().emitError("LHS operand (string) must be string");
      return false;
    }
    if (!binaryOperands.getRhs().getType().equals(StrTypes.StringT.INSTANCE)) {
      binaryOperands.getOperation().emitError("RHS operand must be string");
      return false;
    }
    return true;
  }

  /** Returns whether a string starts with a given prefix. */
  final class StartsWithOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.starts_with"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.starts_with";
    }

    /**
     * Verifies that both operands are strings and the result is boolean.
     *
     * @return a verifier that accepts well-formed prefix checks.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        StartsWithOp op = operation.as(StartsWithOp.class).orElseThrow();
        if (!op.getResultType().equals(BuiltinTypes.IntegerT.BOOL)) {
          operation.emitError("Result type must be bool");
          return false;
        }
        return checkStrictBinaryStringOp(op);
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private StartsWithOp() {}

    /**
     * Creates a prefix-check operation.
     *
     * @param location the source location of this operation.
     * @param string the string operand.
     * @param prefix the prefix operand.
     */
    public StartsWithOp(@NotNull Location location, @NotNull Value string, @NotNull Value prefix) {
      setOperation(
          true,
          Operation.Create(
              location, this, List.of(string, prefix), null, BuiltinTypes.IntegerT.BOOL));
    }
  }

  /** Returns whether a string ends with a given suffix. */
  final class EndsWithOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.ends_with"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.ends_with";
    }

    /**
     * Verifies that both operands are strings.
     *
     * @return a verifier that accepts well-formed suffix checks.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        EndsWithOp op = operation.as(EndsWithOp.class).orElseThrow();
        return checkStrictBinaryStringOp(op);
      };
    }

    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private EndsWithOp() {}

    /**
     * Creates a suffix-check operation.
     *
     * @param location the source location of this operation.
     * @param string the string operand.
     * @param suffix the suffix operand.
     */
    public EndsWithOp(@NotNull Location location, @NotNull Value string, @NotNull Value suffix) {
      setOperation(
          true,
          Operation.Create(
              location, this, List.of(string, suffix), null, BuiltinTypes.IntegerT.BOOL));
    }
  }

  /** Returns the first index of a substring inside a string. */
  final class IndexOfOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.index_of"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.index_of";
    }

    /**
     * Verifies that both operands are strings and the result is int32.
     *
     * @return a verifier that accepts well-formed index lookups.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        IndexOfOp op = operation.as(IndexOfOp.class).orElseThrow();
        if (!op.getResultType().equals(BuiltinTypes.IntegerT.INT32)) {
          operation.emitError("Result type must be int");
          return false;
        }
        return checkStrictBinaryStringOp(op);
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private IndexOfOp() {}

    /**
     * Creates an index-of operation.
     *
     * @param location the source location of this operation.
     * @param string the source string.
     * @param substring the substring to search for.
     */
    public IndexOfOp(@NotNull Location location, @NotNull Value string, @NotNull Value substring) {
      setOperation(
          true,
          Operation.Create(
              location, this, List.of(string, substring), null, BuiltinTypes.IntegerT.INT32));
    }
  }

  /** Returns the last index of a substring inside a string. */
  final class LastIndexOfOp extends StrOp implements StrOps, IBinaryOperands, IHasResult {
    // =========================================================================
    // Type Info
    // =========================================================================

    /**
     * Returns the MLIR-style identifier for this operation.
     *
     * @return the fixed identifier {@code "str.last_index_of"}.
     */
    @Override
    public @NotNull String getIdent() {
      return "str.last_index_of";
    }

    /**
     * Verifies that both operands are strings and the result is int32.
     *
     * @return a verifier that accepts well-formed reverse index lookups.
     */
    @Override
    public @NotNull Function<@NotNull Operation, @NotNull Boolean> getVerifier() {
      return operation -> {
        LastIndexOfOp op = operation.as(LastIndexOfOp.class).orElseThrow();
        if (!op.getResultType().equals(BuiltinTypes.IntegerT.INT32)) {
          operation.emitError("Result type must be int");
          return false;
        }
        return checkStrictBinaryStringOp(op);
      };
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    @SuppressWarnings("unused")
    private LastIndexOfOp() {}

    /**
     * Creates a last-index-of operation.
     *
     * @param location the source location of this operation.
     * @param string the source string.
     * @param substring the substring to search for.
     */
    public LastIndexOfOp(
        @NotNull Location location, @NotNull Value string, @NotNull Value substring) {
      setOperation(
          true,
          Operation.Create(
              location, this, List.of(string, substring), null, BuiltinTypes.IntegerT.INT32));
    }
  }
}
