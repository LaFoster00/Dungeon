package dgir.core.serialization;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import dgir.core.ir.Block;

import java.io.Serial;
import java.util.IdentityHashMap;

/** JSON object-id generator for {@link dgir.core.ir.Block} instances using a sequential counter. */
public class BlockIdGenerator extends ObjectIdGenerator<String> {
  @Serial private static final long serialVersionUID = 1L;

  // Persistent id's for all blocks.
  private static final IdentityHashMap<Block, String> blockIds = new IdentityHashMap<>();
  private static int nextId = 0;

  @Override
  public Class<?> getScope() {
    return Block.class;
  }

  @Override
  public ObjectIdGenerator<String> forScope(Class<?> scope) {
    return this;
  }

  @Override
  public ObjectIdGenerator<String> newForSerialization(Object context) {
    blockIds.clear();
    nextId = 0;
    return this;
  }

  @Override
  public ObjectIdGenerator.IdKey key(Object key) {
    if (key == null) {
      return null;
    }
    return new ObjectIdGenerator.IdKey(getClass(), null, key);
  }

  @Override
  public boolean canUseFor(ObjectIdGenerator<?> gen) {
    return (gen.getClass() == getClass()) && (gen.getScope() == getScope());
  }

  @Override
  public String generateId(Object forPojo) {
    Block block = (Block) forPojo;
    return blockIds.computeIfAbsent(block, k -> ".blk_" + nextId++);
  }
}
