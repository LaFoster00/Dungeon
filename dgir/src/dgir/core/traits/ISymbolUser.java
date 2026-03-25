package dgir.core.traits;

import dgir.core.SymbolTable;
import dgir.dialect.func.FuncOps;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static dgir.dialect.builtin.BuiltinAttrs.SymbolRefAttribute;

/**
 * Marks an operation that references a symbol by name and must be verifiable against that symbol.
 *
 * <p>The implementing op must provide {@link #getSymbolRefAttribute()}, which returns the {@link
 * SymbolRefAttribute} carrying the referenced symbol name. The verifier resolves the name in the
 * nearest enclosing {@link ISymbolTable} and emits an error if no matching symbol is found.
 *
 * <p>Examples: {@link FuncOps.CallOp}.
 */
public interface ISymbolUser extends IOpTrait {
  /**
   * Verifies that the referenced symbol can be resolved in the nearest symbol table.
   *
   * @param trait trait receiver required by verifier signature.
   * @return {@code true} if symbol resolution succeeds.
   */
  @Contract(pure = true)
  default boolean verify(@NotNull ISymbolUser trait) {
    var symbolName = trait.getSymbolRefAttribute().getValue();
    var symbolOp = SymbolTable.lookupSymbolInNearestTable(getOperation(), symbolName);
    if (symbolOp.isEmpty()) {
      getOperation().emitError("Could not find symbol " + symbolName);
      return false;
    }
    return true;
  }

  /**
   * Returns the attribute containing the referenced symbol name.
   *
   * @return symbol-reference attribute.
   */
  @Contract(pure = true)
  @NotNull
  SymbolRefAttribute getSymbolRefAttribute();
}
