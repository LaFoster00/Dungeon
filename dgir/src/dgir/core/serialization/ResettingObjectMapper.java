package dgir.core.serialization;

import tools.jackson.databind.json.JsonMapper;

/**
 * ObjectMapper that resets DGIR id generators before serializing to a String. Ensures IDs start
 * from 0 for each writeValueAsString call.
 */
public class ResettingObjectMapper extends JsonMapper {

  public ResettingObjectMapper(Builder builder) {
    super(builder);
  }

  @Override
  public String writeValueAsString(Object value) {
    // Reset id generators so each call starts fresh.
    BlockIdGenerator.reset();
    ValueIdGenerator.reset();
    return super.writeValueAsString(value);
  }

  // Also override writeValue methods producing String/bytes to be thorough.
  @Override
  public byte[] writeValueAsBytes(Object value) {
    BlockIdGenerator.reset();
    ValueIdGenerator.reset();
    return super.writeValueAsBytes(value);
  }

  @Override
  public void writeValue(java.io.Writer w, Object value) {
    BlockIdGenerator.reset();
    ValueIdGenerator.reset();
    super.writeValue(w, value);
  }

  @Override
  public void writeValue(java.io.OutputStream out, Object value) {
    BlockIdGenerator.reset();
    ValueIdGenerator.reset();
    super.writeValue(out, value);
  }
}
