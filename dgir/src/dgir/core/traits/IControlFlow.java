package dgir.core.traits;

import dgir.core.ir.Block;
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
   * @param ignored trait receiver required by verifier signature.
   * @return always {@code true}.
   */
  @Contract(pure = true)
  default boolean verify(@NotNull IControlFlow ignored) {
    return true;
  }
}
