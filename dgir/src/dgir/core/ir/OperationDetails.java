package dgir.core.ir;

import dgir.core.DGIRContext;
import dgir.core.Dialect;
import dgir.core.traits.IOpTrait;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
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
 * @param emptyConstructor The no-arg constructor — used to create a default op instance (e.g.
 *     during dialect * registration).
 */
public record OperationDetails(
    @NotNull String ident,
    @NotNull Class<? extends Op> type,
    @NotNull Dialect dialect,
    @NotNull Function<Operation, Boolean> verifier,
    @NotNull Set<Class<? extends IOpTrait>> traits,
    @NotNull Map<Class<? extends IOpTrait>, Method> traitVerifiers,
    @NotNull Constructor<? extends Op> emptyConstructor) {
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
                      try {
                        return trait.getMethod("verify", trait);
                      } catch (NoSuchMethodException e) {
                        throw new RuntimeException(
                            "Trait "
                                + trait.getName()
                                + " must have a method called verify that takes an instance of the trait as parameter.",
                            e);
                      }
                    }));

    final var emptyConstructor =
        getSpecificConstructor(type)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Op class " + type.getName() + " must have an empty constructor."));
    emptyConstructor.setAccessible(true);

    return new OperationDetails(
        ident, type, dialect, verifier, traits, traitVerifiers, emptyConstructor);
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
  /**
   * Retrieve a declared constructor of {@code opClass} that matches the given parameter types.
   *
   * @param opClass the op class to inspect.
   * @param parameterTypes the exact parameter types the constructor must have.
   * @return an {@link Optional} containing the constructor, or empty if no such constructor exists.
   */
  @Contract(pure = true)
  static @NotNull Optional<Constructor<? extends Op>> getSpecificConstructor(
      @NotNull Class<? extends Op> opClass, @NotNull Class<?>... parameterTypes) {
    try {
      return Optional.of(opClass.getDeclaredConstructor(parameterTypes));
    } catch (NoSuchMethodException e) {
      return Optional.empty();
    }
  }

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
    try {
      Op op = emptyConstructor().newInstance();
      op.setOperation(operation);
      return Optional.of(clazz.cast(op));
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to create operation instance of type " + clazz.getName(), e);
    }
  }

  /**
   * Wrap the given {@link Operation} in its canonical {@link Op} wrapper.
   *
   * @param operation The backing operation state.
   * @return The op wrapper.
   */
  @Contract(pure = true)
  public @NotNull Op asOp(@NotNull Operation operation) {
    try {
      Op op = emptyConstructor().newInstance();
      op.setOperation(operation);
      return op;
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to create operation instance of type " + type().getName(), e);
    }
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
    Op op = asOp(operation);
    for (Class<? extends IOpTrait> trait : traits()) {
      Method verifier =
          getTraitVerifier(trait)
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "No verifier found for trait " + trait.getName() + " on op " + ident()));
      try {
        boolean result = (boolean) verifier.invoke(trait.cast(op), trait.cast(op));
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

  // =========================================================================
  // Op Construction
  // =========================================================================

  /**
   * Create a default (no-arg) instance of the op represented by this details object. Intended for
   * use during dialect registration and introspection, not for building live IR nodes.
   *
   * @return a freshly constructed default op instance; never {@code null}.
   * @throws RuntimeException if the no-arg constructor cannot be invoked.
   */
  public Op createDefaultInstance() {
    try {
      return emptyConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to create default instance of operation type "
              + type().getName()
              + e.getMessage(),
          e);
    }
  }
}
