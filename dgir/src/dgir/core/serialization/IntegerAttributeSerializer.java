package dgir.core.serialization;

import dgir.dialect.builtin.BuiltinAttrs;
import dgir.dialect.builtin.BuiltinTypes;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.util.NameTransformer;

/** Serializes builtin {@code integerAttr} values as compact JSON objects. */
public class IntegerAttributeSerializer extends StdSerializer<BuiltinAttrs.IntegerAttribute> {
  private boolean unwrapping = false;
  private NameTransformer unwrapper = NameTransformer.NOP;

  /** Constructs the serializer bound to {@link BuiltinAttrs.IntegerAttribute} class. */
  public IntegerAttributeSerializer() {
    super(BuiltinAttrs.IntegerAttribute.class);
  }

  public IntegerAttributeSerializer(boolean unwrapping, NameTransformer unwrapper) {
    super(BuiltinAttrs.IntegerAttribute.class);
    this.unwrapping = unwrapping;
    this.unwrapper = unwrapper;
  }

  /**
   * Constructs the serializer with an explicit target class.
   *
   * @param t target class for serialization.
   */
  public IntegerAttributeSerializer(Class<?> t) {
    super(t);
  }

  @Override
  public void serialize(
      BuiltinAttrs.IntegerAttribute value, JsonGenerator gen, SerializationContext provider)
      throws JacksonException {
    writeIntegerAttribute(value, gen, unwrapping, unwrapper);
  }

  @Override
  public ValueSerializer<BuiltinAttrs.IntegerAttribute> unwrappingSerializer(
      NameTransformer unwrapper) {
    return new IntegerAttributeSerializer(true, unwrapper);
  }

  @Override
  public void serializeWithType(
      BuiltinAttrs.IntegerAttribute value,
      JsonGenerator gen,
      SerializationContext provider,
      TypeSerializer typeSer)
      throws JacksonException {
    // Type id comes from the existing "ident" property, so normal object serialization is enough.
    writeIntegerAttribute(value, gen, unwrapping, unwrapper);
  }

  private static void writeIntegerAttribute(
      BuiltinAttrs.IntegerAttribute value,
      JsonGenerator gen,
      boolean unwrapping,
      NameTransformer unwrapper)
      throws JacksonException {
    if (!unwrapping) gen.writeStartObject();
    gen.writeStringProperty(unwrapper.transform("ident"), value.getIdent());
    gen.writePOJOProperty(unwrapper.transform("type"), value.getType());
    BuiltinTypes.IntegerT integerType = (BuiltinTypes.IntegerT) value.getType();
    if (integerType.equals(BuiltinTypes.IntegerT.BOOL())) {
      gen.writeBooleanProperty(unwrapper.transform("value"), value.getValue().byteValue() != 0);
    } else {
      if (integerType.isSigned()) {
        gen.writeNumberProperty(unwrapper.transform("value"), value.getValue().longValue());
      } else {
        String unsignedValue =
            String.valueOf(
                switch (value.getValue()) {
                  case Byte b -> Byte.toUnsignedInt(b);
                  case Short s -> Short.toUnsignedInt(s);
                  case Integer i -> Integer.toUnsignedLong(i);
                  case Long l -> Long.toUnsignedString(l);
                  default ->
                      throw new IllegalStateException(
                          "Unexpected value type: " + value.getValue().getClass());
                });
        gen.writeRaw(",");
        if (gen.getPrettyPrinter() != null) gen.getPrettyPrinter().beforeObjectEntries(gen);
        gen.writeRaw("\"" + unwrapper.transform("value") + "\" : " + unsignedValue);
      }
    }
    if (!unwrapping) gen.writeEndObject();
  }
}
