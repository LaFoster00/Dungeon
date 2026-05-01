package dgir.core.utility;

import dgir.core.debug.Location;
import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.Region;
import dgir.core.serialization.BlockIdGenerator;
import dgir.core.serialization.ValueIdGenerator;
import dgir.dialect.builtin.BuiltinOps;

import java.util.stream.Collectors;

import static dgir.core.utility.DgirCoreUtils.indent;

/** Util class to convert IR to text. */
public class IrToText {
  private static final ValueIdGenerator valueIdGenerator = new ValueIdGenerator();
  private static final BlockIdGenerator blockIdGenerator = new BlockIdGenerator();

  public static String toText(Operation operation) {
    if (operation.isa(BuiltinOps.ProgramOp.class)) {
      ValueIdGenerator.reset();
      BlockIdGenerator.reset();
      return operation.getFirstRegionOrThrow().getEntryBlock().getOperations().stream()
          .map(IrToText::toText)
          .collect(Collectors.joining("\n"));
    }
    StringBuilder sb = new StringBuilder();
    if (operation.getOutputValue().isPresent()) {
      sb.append(valueIdGenerator.generateId(operation.getOutputValue().get()));
      sb.append(" :");
      sb.append(operation.getOutputValue().get().getType());
      sb.append(" = ");
    }

    sb.append(operation.getDetails().ident());
    if (!operation.getOperands().isEmpty()) sb.append(' ');
    sb.append(
        operation.getOperands().stream()
            .map(operand -> operand.getValue().map(valueIdGenerator::generateId).orElse("null"))
            .collect(Collectors.joining(", ")));
    if (!operation.getOperands().isEmpty()) sb.append(' ');

    if (!operation.getSuccessors().isEmpty()) {
      sb.append(" ==> ");
      sb.append(
          operation.getSuccessors().stream()
              .map(blockIdGenerator::generateId)
              .collect(Collectors.joining(", ")));
    }

    if (!operation.getNamedAttributes().isEmpty()) {
      String attrs =
          operation.getNamedAttributes().stream()
              .map(
                  attr ->
                      "%s = {%s}"
                          .formatted(attr.getName(), attr.getAttributeOrThrow().getStorage()))
              .collect(Collectors.joining(", "));
      if (!attrs.isEmpty()) {
        sb.append(" [");
        sb.append(attrs);
        sb.append("]");
      }
    }

    if (!operation.getDynamicNamedAttributes().isEmpty()) {
      String dynamicAttrs =
          operation.getDynamicNamedAttributes().stream()
              .map(
                  attr ->
                      "%s = {%s}"
                          .formatted(attr.getName(), attr.getAttributeOrThrow().getStorage()))
              .collect(Collectors.joining(", "));
      if (!dynamicAttrs.isEmpty()) {
        sb.append(" <dynamic[");
        sb.append(dynamicAttrs);
        sb.append("]>");
      }
    }

    if (!operation.getLocation().equals(Location.UNKNOWN)) {
      sb.append(" @");
      sb.append(operation.getLocation());
    }

    for (Region region : operation.getRegions()) {
      sb.append("\n").append(toText(region));
    }

    return sb.toString();
  }

  public static String toText(Region region) {
    StringBuilder sb = new StringBuilder();
    if (!region.getRegionValues().isEmpty()) {
      sb.append("( ");
      sb.append(
          region.getRegionValues().stream()
              .map(value -> "%s :%s".formatted(valueIdGenerator.generateId(value), value.getType()))
              .collect(Collectors.joining(" , ")));
      sb.append(" ) ");
    }
    sb.append("{");
    StringBuilder bodyBuilder = new StringBuilder();
    for (Block block : region.getBlocks()) {
      bodyBuilder.append("\n").append(toText(block));
    }
    sb.append(indent(bodyBuilder.toString(), 1));
    sb.append("}");
    return sb.toString();
  }

  public static String toText(Block block) {
    return blockIdGenerator.generateId(block)
        + ": \n"
        + indent(
            block.getOperations().stream().map(IrToText::toText).collect(Collectors.joining("\n")),
            1);
  }
}
