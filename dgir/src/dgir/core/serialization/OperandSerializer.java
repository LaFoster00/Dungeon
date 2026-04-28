package dgir.core.serialization;

import dgir.core.ir.Block;
import dgir.core.ir.IRObjectWithUseList;
import dgir.core.ir.Operand;
import dgir.core.ir.Value;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class OperandSerializer<
        OperandT extends Operand<OperandT, ValueT>,
        ValueT extends IRObjectWithUseList<ValueT, OperandT>>
    extends StdSerializer<OperandT> {
  protected OperandSerializer() {
    this(Operand.class);
  }

  protected OperandSerializer(Class<?> t) {
    super(t);
  }

  @Override
  public void serialize(OperandT value, JsonGenerator gen, SerializationContext provider)
      throws JacksonException {
    if (value.getValue().orElseThrow() instanceof Value val) {
      // Serialize the operand as a reference to the value's ID.
      var generator = new ValueIdGenerator();
      gen.writeString(generator.generateId(val));
    } else if (value.getValue().orElseThrow() instanceof Block block) {
      // Serialize the operand as a reference to the block's ID.
      var generator = new BlockIdGenerator();
      gen.writeString(generator.generateId(block));
    }
  }
}
