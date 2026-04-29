package dgir.core.serialization;

import dgir.core.debug.Location;
import dgir.core.ir.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deserializes an {@link Operation} from its JSON object form.
 *
 * <p>Required field: {@code ident}. Optional fields ({@code operands}, {@code attributes}, {@code
 * dynamicAttributes}, {@code output}, {@code successors}, {@code regions}, {@code loc}) are
 * validated when present. Malformed input is reported via {@link
 * DeserializationContext#reportInputMismatch}.
 */
public class OperationDeserializer extends StdDeserializer<Operation> {
  /**
   * Stores unresolved successor references until all nested regions/blocks/operations are available
   * for identity resolution.
   */
  private static final Map<Operation, Map<BlockOperand, JsonNode>> unresolvedSuccessorReferences =
      new HashMap<>();

  /** Constructs the deserializer bound to {@link Operation} class. */
  public OperationDeserializer() {
    this(Operation.class);
  }

  /**
   * Constructs the deserializer with an explicit target class.
   *
   * @param vc target class for deserialization.
   */
  public OperationDeserializer(Class<?> vc) {
    super(vc);
  }

  /** Deserialize a full operation payload, then resolve successor references in a second step. */
  @Override
  public Operation deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
    JsonNode node = jp.readValueAsTree();

    // Step 1: read the operation kind first so we can look up the registered IR definition.
    JsonNode identNode = node.get("ident");
    if (identNode == null || identNode.isNull()) {
      return ctxt.reportInputMismatch(Operation.class, "Missing required field 'ident'.");
    }
    if (!identNode.isString()) {
      return ctxt.reportInputMismatch(Operation.class, "Field 'ident' must be a string.");
    }

    String ident = identNode.asString();
    var operationDetails = OperationDetails.lookup(ident);
    if (operationDetails.isEmpty()) {
      return ctxt.reportInputMismatch(
          Operation.class,
          "Operation '%s' must be registered to deserialize. Load its dialect first.",
          ident);
    }

    // Step 2: deserialize optional operands if the payload provides them.
    List<Value> operands = null;
    JsonNode operandsNode = node.get("operands");
    if (operandsNode != null && !operandsNode.isNull()) {
      if (!operandsNode.isArray()) {
        return ctxt.reportInputMismatch(Operation.class, "Field 'operands' must be an array.");
      }
      operands = new ArrayList<>();
      for (JsonNode operandNode : operandsNode) {
        Value value = ctxt.readTreeAsValue(operandNode, Value.class);
        operands.add(value);
      }
    }

    // Step 3: deserialize declared attributes and dynamic attributes separately.
    List<NamedAttribute> attributes = null;
    JsonNode attributesNode = node.get("attributes");
    if (attributesNode != null && !attributesNode.isNull()) {
      if (!attributesNode.isArray()) {
        return ctxt.reportInputMismatch(Operation.class, "Field 'attributes' must be an array.");
      }
      attributes = new ArrayList<>();
      for (JsonNode attributeNode : attributesNode) {
        NamedAttribute attribute = ctxt.readTreeAsValue(attributeNode, NamedAttribute.class);
        attributes.add(attribute);
      }
    }

    List<NamedAttribute> dynamicAttributes = null;
    JsonNode dynamicAttributesNode = node.get("dynamicAttributes");
    if (dynamicAttributesNode != null && !dynamicAttributesNode.isNull()) {
      if (!dynamicAttributesNode.isArray()) {
        return ctxt.reportInputMismatch(
            Operation.class, "Field 'dynamicAttributes' must be an array.");
      }
      dynamicAttributes = new ArrayList<>();
      for (JsonNode dynamicAttributeNode : dynamicAttributesNode) {
        NamedAttribute dynamicAttribute =
            ctxt.readTreeAsValue(dynamicAttributeNode, NamedAttribute.class);
        dynamicAttributes.add(dynamicAttribute);
      }
    }

    // Step 4: read the optional output value so we can derive the result type.
    Value outputValue = null;
    JsonNode outputNode = node.get("output");
    if (outputNode != null && !outputNode.isNull()) {
      outputValue = ctxt.readTreeAsValue(outputNode, Value.class);
    }

    // Step 5: successors are resolved later, so create placeholders now and remember their JSON.
    List<Block> successors = null;
    Map<Block, JsonNode> unresolvedSuccessors = new HashMap<>();
    JsonNode successorsNode = node.get("successors");
    if (successorsNode != null && !successorsNode.isNull()) {
      if (!successorsNode.isArray()) {
        return ctxt.reportInputMismatch(Operation.class, "Field 'successors' must be an array.");
      }
      successors = new ArrayList<>();
      for (JsonNode successorNode : successorsNode) {
        // Use placeholders first; resolve to real blocks once all child regions are read.
        Block placeHolderBlock = new Block();
        successors.add(placeHolderBlock);
        unresolvedSuccessors.put(placeHolderBlock, successorNode);
      }
    }

    // Step 6: materialize nested regions before binding any successor block references.
    List<Region> regions = null;
    JsonNode regionsNode = node.get("regions");
    if (regionsNode != null && !regionsNode.isNull()) {
      if (!regionsNode.isArray()) {
        return ctxt.reportInputMismatch(Operation.class, "Field 'regions' must be an array.");
      }
      regions = new ArrayList<>();
      for (JsonNode regionNode : regionsNode) {
        Region region = ctxt.readTreeAsValue(regionNode, Region.class);
        regions.add(region);
      }
    }

    // Step 7: load the source location if present; otherwise keep the unknown sentinel.
    Location location = Location.UNKNOWN;
    JsonNode locNode = node.get("loc");
    if (locNode != null && !locNode.isNull()) {
      location = ctxt.readTreeAsValue(locNode, Location.class);
    }

    // Step 8: now that child regions exist, walk their operations and resolve any deferred
    // successor references to real blocks.
    if (regions != null) {
      for (Region region : regions) {
        for (Block block : region.getBlocks()) {
          for (Operation operation : block.getOperations()) {
            Map<BlockOperand, JsonNode> unresolvedReferences =
                unresolvedSuccessorReferences.get(operation);
            if (unresolvedReferences != null) {
              for (Map.Entry<BlockOperand, JsonNode> entry : unresolvedReferences.entrySet()) {
                BlockOperand blockOperand = entry.getKey();
                JsonNode blockId = entry.getValue();
                if (blockId == null || blockId.isNull()) {
                  return ctxt.reportInputMismatch(
                      Operation.class, "Encountered unresolved successor block reference.");
                }
                // Resolve the block identity only after the target region has been materialized.
                Block targetBlock = ctxt.readTreeAsValue(blockId, Block.class);
                blockOperand.setValue(targetBlock);
              }
            }
          }
        }
      }
    }

    Operation operation;
    operation =
        Operation.Create(
            location,
            operationDetails.get(),
            operands,
            successors,
            outputValue != null ? outputValue.getType() : null,
            regions != null ? regions.size() : 0);
    if (outputValue != null) operation.setOutputValue(outputValue);

    if (attributes != null) {
      for (NamedAttribute attribute : attributes) {
        operation.setAttribute(attribute.getName(), attribute.getAttributeOrThrow());
      }
    }

    if (dynamicAttributes != null) {
      for (NamedAttribute dynamicAttribute : dynamicAttributes) {
        operation.setDynamicAttribute(
            dynamicAttribute.getName(), dynamicAttribute.getAttributeOrThrow());
      }
    }

    if (regions != null) {
      for (int i = 0; i < regions.size(); i++) {
        operation.getRegions().get(i).setRegionValues(regions.get(i).getRegionValues());
        operation.getRegions().get(i).takeRegion(regions.get(i));
      }
    }

    // Step 9: collect any successor operands whose targets still need to be bound by a parent
    // region deserializer, and keep their JSON ids for that later pass.
    Map<BlockOperand, JsonNode> unresolvedBlockOperands = new HashMap<>();
    for (BlockOperand blockOperand : operation.getBlockOperands()) {
      Block placeholder = blockOperand.getValue().orElse(null);
      if (placeholder == null) {
        return ctxt.reportInputMismatch(
            Operation.class, "Encountered unset successor placeholder.");
      }
      JsonNode unresolvedId = unresolvedSuccessors.get(placeholder);
      if (unresolvedId == null || unresolvedId.isNull()) {
        return ctxt.reportInputMismatch(
            Operation.class,
            "Missing unresolved successor id for block operand in operation '%s'.",
            ident);
      }
      // Keep unresolved ids so parent-region deserialization can bind forward edges later.
      unresolvedBlockOperands.put(blockOperand, unresolvedId);
    }
    if (!unresolvedBlockOperands.isEmpty()) {
      // Store the deferred bindings keyed by this operation so nested deserializations can
      // complete successor resolution once all surrounding blocks are available.
      unresolvedSuccessorReferences.put(operation, unresolvedBlockOperands);
    }

    return operation;
  }
}
