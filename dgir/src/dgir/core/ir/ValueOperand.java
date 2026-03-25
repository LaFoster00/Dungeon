package dgir.core.ir;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** A reference to a dynamic {@link Value} used as an input to an {@link Operation}. */
public final class ValueOperand extends Operand<Value, ValueOperand> {

  // =========================================================================
  // Constructors
  // =========================================================================

  /**
   * Creates a value operand owned by an operation.
   *
   * @param owner owning operation.
   * @param value referenced operand value.
   */
  public ValueOperand(@NotNull Operation owner, @NotNull Value value) {
    super(owner, value);
  }

  /**
   * Returns this operand index in {@link Operation#getOperands()}.
   *
   * @return zero-based value-operand index.
   */
  @Override
  public int getIndex() {
    return getOwner().getOperands().indexOf(this);
  }

  // =========================================================================
  // Functions
  // =========================================================================

  /**
   * Returns the type of the referenced operand value.
   *
   * @return the operand value type if present.
   */
  @JsonIgnore
  @Contract(pure = true)
  public @NotNull Optional<Type> getType() {
    return getValue().map(Value::getType);
  }
}
