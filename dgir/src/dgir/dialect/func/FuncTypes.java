package dgir.dialect.func;

import dgir.core.DgirCoreUtils;
import dgir.core.Dialect;
import dgir.core.ir.Type;
import dgir.core.ir.TypeDescriptor;
import dgir.core.ir.TypeDetails;
import dgir.core.ir.TypeUniquer;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Sealed marker interface for all types contributed by the {@link FuncDialect}. */
public sealed interface FuncTypes {
  /** Abstract base class for all type-descriptors contributed by the {@link FuncDialect}. */
  sealed interface FuncTypeDescriptor extends TypeDescriptor {
    @Override
    default @NotNull String getNamespace() {
      return "func";
    }

    @Override
    default @NotNull Class<? extends Dialect> getDialect() {
      return FuncDialect.class;
    }

    final class FunctionDescriptor implements FuncTypeDescriptor {
      @Contract(pure = true)
      public static @NotNull @Unmodifiable List<TypeDescriptor> getDescriptors() {
        return List.of(new FunctionDescriptor());
      }

      @Override
      public @NotNull Class<? extends Type> getTypeClass() {
        return FuncType.class;
      }

      @Override
      public @NotNull String getIdent() {
        return "func.func";
      }

      @Override
      public @NotNull Function<Object, Boolean> getValidator() {
        // Function types validate their internal signature shape, not storage values.
        return value -> true;
      }

      @Override
      public void initDefaultTypeInstances() {}

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type>
          getParameterizedIdentFactory() {
        return args -> {
          if (!args.getLeft().contains("<")) {
            return FuncType.empty();
          }
          // Extract the single parameter (the full "(inputs) -> (output)" string), then
          // split on "->" at depth 0 so nested func types containing "->" are never split
          // prematurely.
          String param =
              TypeDetails.unquoteCustomExpression(
                  DgirCoreUtils.getParameterStrings(args.getLeft()).getFirst());
          List<String> arrowParts = DgirCoreUtils.splitAtDepthZero(param, "->");
          String inputsPart = arrowParts.get(0).trim();
          String outputPart = arrowParts.get(1).trim();
          List<Type> inputs;
          {
            inputsPart = inputsPart.substring(1, inputsPart.length() - 1).trim();
            inputs = TypeDetails.fromParameterString(inputsPart);
          }
          Type output;
          {
            outputPart = outputPart.substring(1, outputPart.length() - 1).trim();
            if (outputPart.isEmpty()) {
              output = null;
            } else {
              output = TypeDetails.fromParameterizedIdent(outputPart);
            }
          }
          return FuncType.of(inputs, output);
        };
      }
    }
  }

  /**
   * Function signature type in the {@code func} dialect.
   *
   * <p>A {@code FuncType} describes a function's parameter types and optional return type:
   *
   * <pre>
   *   func.func&lt;"(int32, string) -&gt; (bool)"&gt;
   * </pre>
   *
   * <p>The {@link #getParameterizedIdent()} method renders the full signature; simple (void/no-arg)
   * function types can be compared by this string.
   */
  final class FuncType extends Type implements FuncTypes {

    // =========================================================================
    // Type Info
    // =========================================================================

    @Contract(pure = true)
    @Override
    public @NotNull String getParameterizedIdent() {
      String signature =
          "("
              + String.join(", ", getInputs().stream().map(Type::getParameterizedIdent).toList())
              + ") -> ("
              + (getOutput() == null ? "" : getOutput().getParameterizedIdent())
              + ")";
      return "func.func<\"" + TypeDetails.quoteCustomExpression(signature) + "\">";
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Returns the canonical empty function type.
     *
     * @return the empty function signature.
     */
    public static FuncType empty() {
      return TypeUniquer.uniqueInstance(new FuncType());
    }

    /**
     * Returns a function type with the given inputs and output.
     *
     * @param inputs the ordered list of parameter types.
     * @param output the return type, or {@code null} for void.
     * @return a canonicalized {@link FuncType} instance.
     */
    public static FuncType of(@NotNull List<Type> inputs, @Nullable Type output) {
      return TypeUniquer.uniqueInstance(new FuncType(inputs, output));
    }

    // =========================================================================
    // Members
    // =========================================================================

    /** The ordered list of input types (never {@code null}, may be empty). */
    private final List<Type> inputs;

    /** The return type, or {@code null} for void functions. */
    private final @Nullable Type output;

    // =========================================================================
    // Constructors
    // =========================================================================

    /** Create a no-argument void function type. */
    private FuncType() {
      super("func.func");
      inputs = List.of();
      output = null;
    }

    /**
     * Create a function type with the given input types and return type.
     *
     * @param inputs the ordered list of parameter types; must not be {@code null}.
     * @param output the return type, or {@code null} for a void function.
     */
    private FuncType(@NotNull List<Type> inputs, @Nullable Type output) {
      super("func.func");
      this.inputs = Collections.unmodifiableList(inputs);
      this.output = output;
    }

    // =========================================================================
    // Functions
    // =========================================================================

    /**
     * Returns the ordered list of input (parameter) types.
     *
     * @return immutable list of input types.
     */
    @Contract(pure = true)
    public @NotNull List<Type> getInputs() {
      return inputs;
    }

    /**
     * Returns the return type of this function, or {@code null} for void functions.
     *
     * @return the return type, or {@code null}.
     */
    @Contract(pure = true)
    public @Nullable Type getOutput() {
      return output;
    }
  }
}
