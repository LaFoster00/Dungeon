package dgir.core.traits;

import dgir.core.SymbolTable;
import dgir.core.ir.Operation;
import dgir.dialect.builtin.BuiltinOps;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marks an operation as acting as a symbol table — a scope in which {@link ISymbol} ops can be
 * declared and looked up by name.
 *
 * <p>The verifier requires the op to have exactly one region. Symbol resolution is delegated to
 * {@link SymbolTable}; both a static and an instance {@code lookupSymbol} convenience method are
 * provided.
 *
 * <p>Examples: {@link BuiltinOps.ProgramOp}.
 */
public interface ISymbolTable extends IOpTrait {
  /**
   * Verifies that the symbol table operation has exactly one region.
   *
   * @param ignored trait receiver required by verifier signature.
   * @return {@code true} if the structure is valid.
   */
  @Contract(pure = true)
  default boolean verify(@NotNull ISymbolTable ignored) {
    if (getOperation().getRegions().size() != 1) {
      getOperation().emitError("Symbol table must have exactly one region.");
      return false;
    }
    return true;
  }

  /**
   * Looks up a symbol by name within the table rooted at {@code op}.
   *
   * @param op operation that provides the lookup scope.
   * @param name symbol name.
   * @return the resolved operation, or {@code null} if not found.
   */
  @Contract(pure = true)
  static @Nullable Operation lookupSymbol(Operation op, String name) {
    return SymbolTable.lookupSymbolIn(op, name);
  }

  /**
   * Looks up a symbol by name in this symbol table.
   *
   * @param name symbol name.
   * @return the resolved operation, or {@code null} if not found.
   */
  @Contract(pure = true)
  default @Nullable Operation lookupSymbol(String name) {
    return lookupSymbol(getOperation(), name);
  }
}
