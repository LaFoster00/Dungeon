package dgir.core.traits;

import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.Region;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Marks an operation that can only contain global operations. This is used to mark the top-level
 * container of a module, which can only contain global operations.
 */
public interface IGlobalContainer extends IOpTrait {
  /**
   * Verifies that all contained operations implement {@link IGlobal}.
   *
   * @param operation the operation to verify.
   * @return {@code true} if all nested operations are global.
   */
  @Contract(pure = true)
  static boolean verify(@NotNull Operation operation) {
    // Ensure that all operations contained in the regions are global operations.
    for (Region region : operation.getRegions()) {
      for (Block block : region.getBlocks()) {
        for (Operation nestedOperation : block.getOperations()) {
          if (!nestedOperation.hasTrait(IGlobal.class)) {
            nestedOperation.emitError(
                "Operation is not a global operation and cannot be contained in a global container.");
            return false;
          }
        }
      }
    }
    return true;
  }
}
