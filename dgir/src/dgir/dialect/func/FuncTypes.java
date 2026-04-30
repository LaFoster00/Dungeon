package dgir.dialect.func;

import dgir.core.ir.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sealed marker interface for all types contributed by the {@link FuncDialect}. */
public sealed interface FuncTypes {
  /** Abstract base class for all type-descriptors contributed by the {@link FuncDialect}. */
  sealed interface FuncTypeDescriptor extends TypeDescriptor {
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

      /**
       * Pattern to match correct function types and extract their arguments via group 1 and 2
       * {@code ^func\.func<"\((.*)\)\s*->\s*\((.*)\)">$}
       */
      private static final Pattern FUNC_TYPE_PATTERN =
          Pattern.compile("^func\\.func<\"\\((.*)\\)\\s*->\\s*\\((.*)\\)\">$");

      @Override
      public @NotNull Function<@NotNull Pair<@NotNull String, @NotNull TypeDetails>, @NotNull Type>
          getParameterizedIdentFactory() {
        return args -> {
          Matcher matcher = FUNC_TYPE_PATTERN.matcher(args.getLeft());
          if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid parameterized ident for func.func type: %s".formatted(args.getLeft()));
          }
          // Everything inside first ()
          String inputsPart = Type.unescapeCustomExpression(matcher.group(1));
          List<Type> inputs = new ArrayList<>();
          Type.consumeParameterText(inputsPart, Type.AllTypes.of(inputs));

          // Everything inside second ()
          String outputPart = Type.unescapeCustomExpression(matcher.group(2));
          Type output = null;
          if (!outputPart.isBlank()) output = Type.fromParameterizedIdent(outputPart);

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
      String inputs = Type.buildParameterList(getInputs());
      String output = getOutput() != null ? getOutput().toString() : "";
      String expression = "(%s) -> (%s)".formatted(inputs, output);
      return Type.buildParameterizedIdent(getDetails(), List.of(expression));
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
