package dgir.core.serialization;

import dgir.core.ir.Attribute;
import dgir.core.ir.NamedAttribute;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializes a named attribute object of the shape: {@code {"name": <string>, "attribute":
 * <attribute-object>}}.
 *
 * <p>Malformed input is reported through {@link DeserializationContext#reportInputMismatch} with
 * field-specific messages.
 */
public class NamedAttributeDeserializer extends StdDeserializer<NamedAttribute> {
  /** Constructs the deserializer bound to {@link NamedAttribute} class. */
  public NamedAttributeDeserializer() {
    this(NamedAttribute.class);
  }

  /**
   * Constructs the deserializer with an explicit target class.
   *
   * @param vc target class for deserialization.
   */
  public NamedAttributeDeserializer(Class<?> vc) {
    super(vc);
  }

  /**
   * Deserialize a {@link NamedAttribute} and validate required fields before reading nested values.
   */
  @Override
  public NamedAttribute deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = p.readValueAsTree();

    JsonNode nameNode = node.get("name");
    if (nameNode == null || nameNode.isNull()) {
      return ctxt.reportInputMismatch(NamedAttribute.class, "Missing required field 'name'.");
    }
    if (!nameNode.isString()) {
      return ctxt.reportInputMismatch(NamedAttribute.class, "Field 'name' must be a string.");
    }

    JsonNode attributeNode = node.get("attribute");
    if (attributeNode == null || attributeNode.isNull()) {
      return ctxt.reportInputMismatch(NamedAttribute.class, "Missing required field 'attribute'.");
    }

    Attribute attribute = ctxt.readTreeAsValue(attributeNode, Attribute.class);
    return new NamedAttribute(nameNode.asString(), attribute);
  }
}
