package blockly.dgir.compiler.java.emission;

import blockly.dgir.compiler.java.EmitResult;
import dgir.core.ir.Value;

import java.util.function.Function;

@FunctionalInterface
public interface LValueResult extends Function<Value, EmitResult<Boolean>> {}
