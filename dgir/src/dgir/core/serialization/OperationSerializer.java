package dgir.core.serialization;

import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/** Serializes an {@link Operation} to its JSON object form. */
public class OperationSerializer extends StdSerializer<Operation> {
  /** Constructs the serializer bound to {@link Operation} class. */
  public OperationSerializer() {
    super(Operation.class);
  }

  /**
   * Constructs the serializer with an explicit target class.
   *
   * @param t target class for serialization.
   */
  public OperationSerializer(Class<?> t) {
    super(t);
  }

  @Override
  public void serialize(Operation value, JsonGenerator gen, SerializationContext provider)
      throws JacksonException {
    gen.writeStartObject();
    gen.writeStringProperty("ident", value.getDetails().ident());
    if (!value.getLocation().equals(Location.UNKNOWN))
      gen.writePOJOProperty("loc", value.getLocation());
    if (!value.getOperands().isEmpty()) gen.writePOJOProperty("operands", value.getOperands());
    if (!value.getBlockOperands().isEmpty())
      gen.writePOJOProperty("successors", value.getBlockOperands());
    if (!value.getAttributeMap().isEmpty()) {
      // Convert the map to a list of attributes.
      gen.writePOJOProperty("attributes", value.getAttributeMap().values());
    }
    if (value.getOutput().isPresent()) gen.writePOJOProperty("output", value.getOutput());
    if (!value.getRegions().isEmpty()) gen.writePOJOProperty("regions", value.getRegions());
    gen.writeEndObject();
  }
}
