package dgir.core.ir;

import dgir.core.traits.IOpTrait;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Describes an operation kind and exposes its metadata through a stable interface.
 *
 * @param ident The unique identifier string for this operation kind (e.g. {@code
 *     "arith.constant"}).
 * @param type The Java class that represents this operation kind.
 * @param dialect The dialect that contributes this operation kind.
 * @param verifier Returns the verifier function for this operation kind. The verifier is invoked
 *     during the verification phase to check that an operation instance is well-formed.
 * @param traits The set of {@link IOpTrait} interfaces implemented by this operation kind.
 * @param traitVerifiers A map from each registered trait class to its {@code verify} method, used
 *     during trait verification.
 * @param opFactory The factory taking in an operation and producing a typed op wrapper of this
 *     kind. Used by {@link #as} to create typed op instances from raw operations.
 * @param defaultAttributes A supplier of the default attribute list for this operation kind, used
 *     during default instance construction.
 */
public record OperationDetails(
    @NotNull String ident,
    @NotNull Class<? extends Op> type,
    @NotNull Dialect dialect,
    @NotNull Function<@NotNull Operation, @NotNull Boolean> verifier,
    @NotNull Set<Class<? extends IOpTrait>> traits,
    @NotNull Map<Class<? extends IOpTrait>, @NotNull Method> traitVerifiers,
    @NotNull Function<@NotNull Operation, @NotNull Op> opFactory,
    @NotNull Supplier<@NotNull @Unmodifiable List<@NotNull NamedAttribute>> defaultAttributes) {
  /**
   * Build a {@link OperationDetails} instance from a default (no-arg) {@link Op} prototype. All
   * fields are derived by introspecting the op's class and the values returned by its abstract
   * methods.
   *
   * <p>The owning dialect must already be registered in {@link DGIRContext} before this method is
   * called, because {@link Dialect#getOrThrow(Class)} is used to resolve it.
   *
   * @param op a default (no-arg) op prototype; must not be {@code null}.
   * @return a fully populated {@link OperationDetails} instance.
   * @throws RuntimeException if the op class is missing required constructors or any registered
   *     {@link IOpTrait} does not expose the expected {@code verify} method.
   */
  public static @NotNull OperationDetails create(@NotNull Op op) {
    final var ident = op.getIdent();
    final var type = op.getClass();
    final var dialect = Dialect.getOrThrow(op.getDialect());
    final var verifier = op.getVerifier();
    final var opFactory = op.getOpFactory();
    final var defaultAttributes = op.defaultAttributes();
    final Set<Class<? extends IOpTrait>> traits =
        Set.copyOf(
            OperationDetails.getAllInterfaces(type).stream()
                .filter(IOpTrait.class::isAssignableFrom)
                .filter(aClass -> !aClass.equals(IOpTrait.class))
                .map(aClass -> aClass.<IOpTrait>asSubclass(IOpTrait.class))
                .toList());
    final Map<Class<? extends IOpTrait>, Method> traitVerifiers =
        traits.stream()
            .collect(
                Collectors.toMap(
                    trait -> trait,
                    trait -> {
                      Method verify;
                      try {
                        verify = trait.getMethod("verify", Operation.class);
                      } catch (NoSuchMethodException e) {
                        throw new RuntimeException(
                            "Trait "
                                + trait.getName()
                                + " must have a static method called verify that takes an operation as parameter.",
                            e);
                      }
                      if (!verify.canAccess(null)) {
                        throw new RuntimeException(
                            "Verifier method for trait "
                                + trait.getName()
                                + " must be public and static.");
                      }
                      return verify;
                    }));

    return new OperationDetails(
        ident, type, dialect, verifier, traits, traitVerifiers, opFactory, defaultAttributes);
  }

  // =========================================================================
  // Static Registration
  // =========================================================================

  /**
   * Register the given op prototype into the global {@link DGIRContext} caches. If the op already
   * carries a {@link OperationDetails} details instance (i.e. it was previously registered), that
   * instance is reused; otherwise {@link #create(Op)} is called first.
   *
   * <p>This method populates both the unregistered caches (so look-ups that arrive before full
   * dialect initialisation still resolve) and the registered caches (used for all post-init
   * look-ups).
   *
   * @param op the op prototype to register; must not be {@code null}.
   */
  public static void insert(@NotNull Op op) {
    if (op.getOperationOrNull() != null) return;

    OperationDetails details = create(op);
    // Populate the registered caches
    DGIRContext.registeredOperations.put(details.type(), details);
    DGIRContext.registeredOperationsByIdent.put(details.ident(), details);
  }

  // =========================================================================
  // Static Factories
  // =========================================================================

  // Collect interfaces from class hierarchy, including interface inheritance.
  static @NotNull Set<Class<?>> getAllInterfaces(@NotNull Class<?> clazz) {
    Set<Class<?>> interfaces = new LinkedHashSet<>();
    Class<?> current = clazz;
    while (current != null) {
      for (Class<?> iface : current.getInterfaces()) {
        collectInterfaceHierarchy(iface, interfaces);
      }
      current = current.getSuperclass();
    }
    return interfaces;
  }

  static void collectInterfaceHierarchy(@NotNull Class<?> iface, @NotNull Set<Class<?>> out) {
    if (!out.add(iface)) return;
    for (Class<?> parent : iface.getInterfaces()) {
      collectInterfaceHierarchy(parent, out);
    }
  }

  // =========================================================================
  // Static Lookups
  // =========================================================================

  /**
   * Look up a {@link OperationDetails} entry by op class.
   *
   * @param clazz the op class to look up.
   * @return the registered details, or empty if the class has not been registered yet.
   */
  @Contract(pure = true)
  public static @NotNull Optional<OperationDetails> lookup(@NotNull Class<? extends Op> clazz) {
    return Optional.ofNullable(DGIRContext.registeredOperations.get(clazz));
  }

  /**
   * Look up a {@link OperationDetails} entry by operation ident string.
   *
   * @param name the ident string (e.g. {@code "arith.constant"}) to look up.
   * @return the registered details, or empty if the ident has not been registered yet.
   */
  @Contract(pure = true)
  public static @NotNull Optional<OperationDetails> lookup(@NotNull String name) {
    return Optional.ofNullable(DGIRContext.registeredOperationsByIdent.get(name));
  }

  // =========================================================================
  // Delegates
  // =========================================================================

  /**
   * Apply the verifier function to the given operation.
   *
   * @param operation the operation to verify.
   * @return {@code true} if the operation is well-formed, {@code false} otherwise.
   */
  @Contract(pure = true)
  public boolean verify(@NotNull Operation operation) {
    return verifier().apply(operation);
  }

  /**
   * Check whether this operation kind implements the given trait.
   *
   * @param traitClass the trait class to check for.
   * @return {@code true} if the trait is present.
   */
  @Contract(pure = true)
  public boolean hasTrait(Class<? extends IOpTrait> traitClass) {
    return traits().contains(traitClass);
  }

  /**
   * Retrieve the {@code verify} method for the given trait class from the trait-verifier map.
   *
   * @param traitClass the trait whose verifier to retrieve.
   * @return the verifier {@link Method}, or {@code null} if the trait is not registered for this
   *     operation kind.
   */
  @Contract(pure = true)
  public @NotNull Optional<Method> getTraitVerifier(Class<? extends IOpTrait> traitClass) {
    return Optional.ofNullable(traitVerifiers().get(traitClass));
  }

  // =========================================================================
  // Op Instantiation
  // =========================================================================

  /**
   * Wrap the given {@link Operation} in a typed {@code Op} of type {@code clazz}, if this details
   * instance describes that op kind.
   *
   * @param clazz The class of the op to create.
   * @param operation The backing operation state.
   * @return The typed op wrapper, or empty if the kinds do not match.
   */
  @Contract(pure = true)
  public <T extends Op> Optional<T> as(@NotNull Class<T> clazz, @NotNull Operation operation) {
    if (!isa(clazz)) {
      return Optional.empty();
    }
    var result = opFactory.apply(operation);
    assert result.getOperationOrNull() == operation
        : "Op factory for " + ident() + " did not return an op wrapping the given operation";
    return Optional.of(clazz.cast(result));
  }

  /**
   * Wrap the given {@link Operation} in its canonical {@link Op} wrapper.
   *
   * @param operation The backing operation state.
   * @return The op wrapper.
   */
  @Contract(pure = true)
  public @NotNull Op asOp(@NotNull Operation operation) {
    var result = opFactory.apply(operation);
    assert result.getOperationOrNull() == operation
        : "Op factory for " + ident() + " did not return an op wrapping the given operation";
    return result;
  }

  /**
   * Check whether this operation kind matches the given class.
   *
   * @param clazz The type to check for.
   * @return {@code true} if this details instance describes {@code clazz}.
   */
  @Contract(pure = true)
  public boolean isa(@NotNull Class<? extends Op> clazz) {
    return clazz.equals(type());
  }

  /**
   * Verify all traits registered for this operation kind against the given operation. Called before
   * the per-op {@link #verify} so that trait invariants are guaranteed when custom verification
   * runs.
   *
   * @param operation The operation to verify.
   * @return {@code true} if all trait verifiers pass.
   */
  @Contract(pure = true)
  public boolean verifyTraits(@NotNull Operation operation) {
    for (Class<? extends IOpTrait> trait : traits()) {
      Method verifier =
          getTraitVerifier(trait)
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "No verifier found for trait " + trait.getName() + " on op " + ident()));
      try {
        boolean result = (boolean) verifier.invoke(null, operation);
        if (!result) {
          operation.emitError("Operation failed verification for trait " + trait.getName());
          return false;
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to invoke verifier for trait " + trait.getName(), e);
      }
    }
    return true;
  }
}
