package dgir.core.serialization;

import dgir.core.ir.Op;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/** Serializes an {@link Op} by delegating to its wrapped {@link Operation}. */
public class OpSerializer extends StdSerializer<Op> {
  /** Constructs the serializer bound to {@link Op} class. */
  public OpSerializer() {
    super(Op.class);
  }

  /**
   * Constructs the serializer with an explicit target class.
   *
   * @param t target class for serialization.
   */
  public OpSerializer(Class<?> t) {
    super(t);
  }

  @Override
  public void serialize(Op value, JsonGenerator gen, SerializationContext provider)
      throws JacksonException {
    gen.writePOJO(value.getOperation());
  }
}
