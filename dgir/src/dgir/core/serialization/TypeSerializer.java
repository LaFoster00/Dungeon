package dgir.core.serialization;

import dgir.core.ir.Type;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/** Serializes a {@link Type} to its parameterized ident string representation. */
public class TypeSerializer extends StdSerializer<Type> {
  /** Constructs the serializer bound to {@link Type} class. */
  public TypeSerializer() {
    super(Type.class);
  }

  /**
   * Constructs the serializer with an explicit target class.
   *
   * @param t target class for serialization.
   */
  public TypeSerializer(Class<?> t) {
    super(t);
  }

  @Override
  public void serialize(Type value, JsonGenerator gen, SerializationContext provider)
      throws JacksonException {
    gen.writeString(value.getParameterizedIdent());
  }
}
