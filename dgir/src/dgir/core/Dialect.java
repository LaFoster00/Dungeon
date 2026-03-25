package dgir.core;

import dgir.core.ir.*;
import dgir.dialect.arith.ArithDialect;
import dgir.dialect.builtin.BuiltinDialect;
import dgir.dialect.builtin.BuiltinOps;
import dgir.dialect.cf.CfDialect;
import dgir.dialect.func.FuncDialect;
import dgir.dialect.io.IoDialect;
import dgir.dialect.mem.MemoryDialect;
import dgir.dialect.scf.ScfDialect;
import dgir.dialect.str.StrDialect;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Base class for all DGIR dialects.
 *
 * <p>A dialect groups a set of related {@link Op operations}, {@link Type types}, and {@link
 * Attribute attributes} under a shared namespace. Dialects are registered once at startup via
 * {@link #registerAllDialects()}, which calls {@link #register()} on each one and populates the
 * global {@link DGIRContext} registries.
 */
public abstract class Dialect {
  Logger logger = Logger.getLogger(getClass().getName());

  /**
   * Cache of already-computed operation prototype lists, keyed by dialect class. Populated lazily
   * by {@link #allOps(Class)} on the first call for each dialect.
   */
  private static final @NotNull Map<Class<? extends Dialect>, @Unmodifiable List<Op>> dialectOps =
      new HashMap<>();

  /**
   * Cache of already-computed attribute prototype lists, keyed by dialect class. Populated lazily
   * by {@link #allAttributes(Class)} on the first call for each dialect.
   */
  private static final @NotNull Map<
          Class<? extends dgir.core.Dialect>, @Unmodifiable List<Attribute>>
      dialectAttributes = new HashMap<>();

  /**
   * Cache of already-computed type prototype lists, keyed by dialect class. Populated lazily by
   * {@link #allTypes(Class)} on the first call for each dialect.
   */
  private static final @NotNull Map<Class<? extends dgir.core.Dialect>, @Unmodifiable List<Type>>
      dialectTypes = new HashMap<>();

  // =========================================================================
  // Dialect Info
  // =========================================================================

  /**
   * The namespace prefix used in operation/type idents (e.g. {@code "arith"}, {@code "func"}).
   *
   * @return the dialect namespace.
   */
  @Contract(pure = true)
  public abstract @NotNull String getNamespace();

  /**
   * All operation prototypes contributed by this dialect.
   *
   * @return an unmodifiable list of operation prototypes.
   */
  @Contract(pure = true)
  public abstract @NotNull @Unmodifiable List<Op> allOps();

  /**
   * All type prototypes contributed by this dialect.
   *
   * @return an unmodifiable list of type prototypes.
   */
  @Contract(pure = true)
  public abstract @NotNull @Unmodifiable List<Type> allTypes();

  /**
   * All attribute prototypes contributed by this dialect.
   *
   * @return an unmodifiable list of attribute prototypes.
   */
  @Contract(pure = true)
  public abstract @NotNull @Unmodifiable List<Attribute> allAttributes();

  // =========================================================================
  // Registration
  // =========================================================================

  /**
   * Register this dialect in the global {@link DGIRContext}. Inserts all ops, types, and attributes
   * into their respective registries.
   */
  public void register() {
    if (DGIRContext.registeredDialects.containsKey(this.getClass())) {
      return;
    }
    DGIRContext.registeredDialects.put(this.getClass(), this);
    DGIRContext.registeredDialectsByName.put(this.getNamespace(), this);

    logger.info("Registering dialect: " + getNamespace());
    for (var op : allOps()) {
      OperationDetails.Registered.insert(op);
    }
    for (var type : allTypes()) {
      TypeDetails.Registered.insert(type);
    }
    for (var attr : allAttributes()) {
      AttributeDetails.Registered.insert(attr);
    }
    logger.info("Registered dialect successfully: " + getNamespace());
  }

  // =========================================================================
  // Static Helpers
  // =========================================================================

  /**
   * Look up a registered dialect by its class.
   *
   * @param dialectClass The class of the dialect to look up (e.g. {@code ArithDialect.class} or
   *     {@code FuncDialect.class}).
   * @return An optional containing the registered dialect, or empty if no such dialect is
   *     registered.
   */
  @Contract(pure = true)
  public static @NotNull Optional<Dialect> get(@NotNull Class<? extends Dialect> dialectClass) {
    return Optional.ofNullable(DGIRContext.registeredDialects.get(dialectClass));
  }

  /**
   * Look up a registered dialect by its class, throwing an exception if no such dialect is
   * registered.
   *
   * @param dialectClass The class of the dialect to look up (e.g. {@code ArithDialect.class} or
   *     {@code FuncDialect.class}).
   * @return The registered dialect.
   */
  @Contract(pure = true)
  public static @NotNull Dialect getOrThrow(@NotNull Class<? extends Dialect> dialectClass) {
    return get(dialectClass)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Dialect not registered: " + dialectClass.getSimpleName()));
  }

  /**
   * Register all built-in dialects in dependency order. Must be called once before constructing any
   * IR.
   */
  public static void registerAllDialects() {
    List<Dialect> dialects =
        List.of(
            ArithDialect.get(),
            BuiltinDialect.get(),
            CfDialect.get(),
            FuncDialect.get(),
            IoDialect.get(),
            MemoryDialect.get(),
            ScfDialect.get(),
            StrDialect.get());
    dialects.forEach(Dialect::register);
  }

  /**
   * Collect all operation prototypes contributed by a dialect by reflectively instantiating every
   * permitted subclass of {@code diOps} via its no-arg constructor.
   *
   * <p>Results are cached so that repeated calls for the same dialect are cheap. The {@code diOps}
   * argument must be a {@code sealed} interface whose every {@code permits} entry is a concrete op
   * class with a declared no-arg constructor.
   *
   * @param diOps the sealed marker interface whose permitted subclasses enumerate the dialect's ops
   *     (e.g. {@link BuiltinOps}).
   * @return an unmodifiable list of op prototypes, one per permitted subclass.
   * @throws AssertionError if {@code diOps} is not a sealed interface.
   * @throws RuntimeException if any permitted subclass lacks a no-arg constructor or its
   *     constructor throws.
   */
  @NotNull
  @Unmodifiable
  public List<Op> allOps(Class<?> diOps) {
    // Check that diOps is a sealed interface
    assert diOps.isSealed() : "IDialectOperations interface must be sealed";

    if (dialectOps.containsKey(this.getClass())) {
      return dialectOps.get(this.getClass());
    }

    // Go over all permitted subclasses of this interface and collect their prototypes. This
    // allows
    // us to avoid
    // having to manually list all operations in the dialect, and instead just have them register
    // themselves via implementing
    // their dialect specific subclass.
    List<Op> ops = new ArrayList<>();

    Class<?>[] permittedSubclasses = diOps.getPermittedSubclasses();
    for (Class<?> subclass : permittedSubclasses) {
      // Get the default constructor for this operation and invoke it to get the prototype, then
      // add
      // it to the list of ops for this dialect.
      try {
        Constructor<?> defaultConstructor = subclass.getDeclaredConstructor();
        boolean isAccessible = defaultConstructor.canAccess(null);
        if (!isAccessible) defaultConstructor.setAccessible(true);
        try {
          Op newOp = (Op) defaultConstructor.newInstance();
          ops.add(newOp);
        } catch (InstantiationException e) {
          throw new RuntimeException(
              "Executing default constructor failed for op: " + subclass.getName(), e);
        } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        if (!isAccessible) defaultConstructor.setAccessible(false);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "Operation class must have a default constructor: " + subclass.getName(), e);
      }
    }
    dialectOps.put(this.getClass(), ops);
    return ops;
  }

  /**
   * Collect all attribute prototypes contributed by a dialect by reflectively instantiating every
   * permitted subclass of {@code diAttrs} via its no-arg constructor.
   *
   * <p>Results are cached so that repeated calls for the same dialect are cheap. The {@code
   * diAttrs} argument must be a {@code sealed} interface whose every {@code permits} entry is a
   * concrete attribute class with a declared no-arg constructor.
   *
   * @param diAttrs the sealed marker interface whose permitted subclasses enumerate the dialect's
   *     attributes.
   * @return an unmodifiable list of attribute prototypes, one per permitted subclass.
   * @throws AssertionError if {@code diAttrs} is not a sealed interface.
   * @throws RuntimeException if any permitted subclass lacks a no-arg constructor or its
   *     constructor throws.
   */
  @NotNull
  @Unmodifiable
  public List<Attribute> allAttributes(Class<?> diAttrs) {
    assert diAttrs.isSealed() : "IDialectAttributes interface must be sealed";

    if (dialectAttributes.containsKey(this.getClass())) {
      return dialectAttributes.get(this.getClass());
    }

    List<Attribute> attrs = new ArrayList<>();
    Class<?>[] permittedSubclasses = diAttrs.getPermittedSubclasses();
    for (Class<?> subclass : permittedSubclasses) {
      try {
        Constructor<?> defaultConstructor = subclass.getDeclaredConstructor();
        boolean isAccessible = defaultConstructor.canAccess(null);
        if (!isAccessible) defaultConstructor.setAccessible(true);
        try {
          Attribute newAttr = (Attribute) defaultConstructor.newInstance();
          attrs.add(newAttr);
        } catch (InstantiationException e) {
          throw new RuntimeException(
              "Executing default constructor failed for attribute: " + subclass.getName(), e);
        } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        if (!isAccessible) defaultConstructor.setAccessible(false);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "Attribute class must have a default constructor: " + subclass.getName(), e);
      }
    }
    dialectAttributes.put(this.getClass(), attrs);
    return attrs;
  }

  /**
   * Collect all type prototypes contributed by a dialect by reflectively instantiating every
   * permitted subclass of {@code diTypes} via its no-arg constructor.
   *
   * <p>Results are cached so that repeated calls for the same dialect are cheap. The {@code
   * diTypes} argument must be a {@code sealed} interface whose every {@code permits} entry is a
   * concrete type class with a declared no-arg constructor.
   *
   * @param diTypes the sealed marker interface whose permitted subclasses enumerate the dialect's
   *     types.
   * @return an unmodifiable list of type prototypes, one per permitted subclass.
   * @throws AssertionError if {@code diTypes} is not a sealed interface.
   * @throws RuntimeException if any permitted subclass lacks a no-arg constructor or its
   *     constructor throws.
   */
  @NotNull
  @Unmodifiable
  public List<Type> allTypes(Class<?> diTypes) {
    assert diTypes.isSealed() : "IDialectTypes interface must be sealed";

    if (dialectTypes.containsKey(this.getClass())) {
      return dialectTypes.get(this.getClass());
    }

    List<Type> types = new ArrayList<>();
    Class<?>[] permittedSubclasses = diTypes.getPermittedSubclasses();
    for (Class<?> subclass : permittedSubclasses) {
      try {
        Constructor<?> defaultConstructor = subclass.getDeclaredConstructor();
        boolean isAccessible = defaultConstructor.canAccess(null);
        if (!isAccessible) defaultConstructor.setAccessible(true);
        try {
          Type newType = (Type) defaultConstructor.newInstance();
          types.addAll(newType.getDefaultTypeInstances());
          if (newType.getDefaultTypeInstances().isEmpty()) {
            types.add(newType);
          }
        } catch (InstantiationException e) {
          throw new RuntimeException(
              "Executing default constructor failed for type: " + subclass.getName(), e);
        } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        if (!isAccessible) defaultConstructor.setAccessible(false);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "Type class must have a default constructor: " + subclass.getName(), e);
      }
    }
    dialectTypes.put(this.getClass(), types);
    return types;
  }
}
