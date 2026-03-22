package dgir.core;

import dgir.core.debug.Location;
import dgir.core.debug.ValueDebugInfo;
import dgir.core.ir.Block;
import dgir.core.ir.Operation;
import dgir.core.ir.Region;
import dgir.core.ir.Value;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Util class to convert IR to text. */
public class IrToText {
  static final AtomicInteger blockIndex = new AtomicInteger(0);
  static final IdentityHashMap<Block, String> blockNames = new IdentityHashMap<>();

  static final AtomicInteger valueIndex = new AtomicInteger(0);
  static final IdentityHashMap<Value, String> valueNames = new IdentityHashMap<>();

  private static String getBlockName(Block block) {
    return blockNames.computeIfAbsent(block, b -> ".blk_" + blockIndex.getAndIncrement());
  }

  private static String getValueName(Value value) {
    return valueNames.computeIfAbsent(
        value,
        v -> {
          if (v.getDebugInfo().equals(ValueDebugInfo.UNKNOWN)) {
            return "%" + valueIndex.getAndIncrement();
          } else {
            return "%" + v.getDebugInfo().name() + "_" + valueIndex.getAndIncrement();
          }
        });
  }

  private static String indent(String text, int indent) {
    StringBuilder builder = new StringBuilder();
    String indentStr = String.join("", java.util.Collections.nCopies(indent, "\t"));
    for (String line : text.lines().toList()) {
      builder.append(indentStr).append(line).append("\n");
    }
    return builder.toString();
  }

  public static String toText(Operation operation) {
    StringBuilder sb = new StringBuilder();
    if (operation.getOutputValue().isPresent()) {
      sb.append(getValueName(operation.getOutputValue().get()));
      sb.append(" :");
      sb.append(operation.getOutputValue().get().getType());
      sb.append(" = ");
    }

    sb.append(operation.getDetails().ident());
    sb.append(" (");
    if (!operation.getOperands().isEmpty()) sb.append(' ');
    sb.append(
        operation.getOperands().stream()
            .map(operand -> operand.getValue().map(IrToText::getValueName).orElse("null"))
            .collect(Collectors.joining(" , ")));
    if (!operation.getOperands().isEmpty()) sb.append(' ');
    sb.append(")");

    if (!operation.getNamedAttributes().isEmpty()) {
      String attrs =
          operation.getNamedAttributes().stream()
              .map(attr -> "%s = {%s}".formatted(attr.getName(), attr.getAttribute().getStorage()))
              .collect(Collectors.joining(" , "));
      if (!attrs.isEmpty()) {
        sb.append(" [ ");
        sb.append(attrs);
        sb.append(" ]");
      }
    }

    if (!operation.getSuccessors().isEmpty()) {
      sb.append(" ==> [");
      if (!operation.getSuccessors().isEmpty()) sb.append(' ');
      sb.append(
          operation.getSuccessors().stream()
              .map(IrToText::getBlockName)
              .collect(Collectors.joining(" , ")));
      if (!operation.getSuccessors().isEmpty()) sb.append(' ');
      sb.append("]");
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
    if (!region.getBodyValues().isEmpty()) {
      sb.append("( ");
      sb.append(
          region.getBodyValues().stream()
              .map(IrToText::getValueName)
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
    return getBlockName(block)
        + " \n"
        + indent(
            block.getOperations().stream().map(IrToText::toText).collect(Collectors.joining("\n")),
            1);
  }
}
