package dgir.core.traits;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an operation as a participant in unstructured control flow.
 *
 * <p>Ops that transfer control between {@link Block}s — such as {@code cf.br} and {@code
 * cf.br_cond} — should implement this trait. It serves as a semantic tag; the default {@code
 * verify} implementation always passes.
 */
public interface IControlFlow extends IOpTrait {
  /**
   * Semantic tag verifier for control-flow operations.
   *
   * @param ignored the operation to verify, ignored since this trait has no invariants.
   * @return always {@code true}.
   */
  @Contract(pure = true)
  static boolean verify(@NotNull Operation ignored) {
    return true;
  }
}
