package dgir.core.traits;

import dgir.dialect.func.FuncOps;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an operation as a <em>global</em> — one that may legally appear at the top level of a
 * module or inside a {@link IGlobalContainer}.
 *
 * <p>The verifier checks that the direct parent operation (if any) implements {@link
 * IGlobalContainer}. An op with no parent is implicitly considered global.
 *
 * <p>Examples: {@link FuncOps.FuncOp}.
 */
public interface IGlobal extends IOpTrait {
  /**
   * Verifies that the parent operation is a global container when present.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if the op is top-level or parented by a global container.
   */
  @Contract(pure = true)
  default boolean verify(@NotNull IGlobal ignored) {
    return getOperation()
        .getParentOperation()
        // Ensure that the op is inside a global container op
        .map(parent -> parent.hasTrait(IGlobalContainer.class))
        // If the op is not inside another op it can be viewed as a global
        .orElse(true);
  }
}
