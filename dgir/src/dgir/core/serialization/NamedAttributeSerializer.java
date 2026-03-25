package dgir.core.serialization;

import dgir.core.ir.NamedAttribute;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/** Serializes a {@link NamedAttribute} to JSON object format. */
public class NamedAttributeSerializer extends StdSerializer<NamedAttribute> {
  /** Constructs the serializer bound to {@link NamedAttribute} class. */
  public NamedAttributeSerializer() {
    super(NamedAttribute.class);
  }

  /**
   * Constructs the serializer with an explicit target class.
   *
   * @param t target class for serialization.
   */
  public NamedAttributeSerializer(Class<?> t) {
    super(t);
  }

  @Override
  public void serialize(NamedAttribute value, JsonGenerator gen, SerializationContext provider)
      throws JacksonException {
    gen.writeStartObject();
    gen.writeStringProperty("name", value.getName());
    gen.writePOJOProperty("attribute", value.getAttribute());
    gen.writeEndObject();
  }
}
