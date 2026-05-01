import dgir.core.debug.Location;
import dgir.core.ir.*;
import dgir.core.serialization.Utils;
import dgir.core.utility.IrToText;
import dgir.dialect.arith.ArithAttrs;
import dgir.dialect.arith.ArithOps;
import dgir.dialect.builtin.BuiltinAttrs;
import dgir.dialect.mem.MemTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.cf.CfOps.BranchCondOp;
import static dgir.dialect.cf.CfOps.BranchOp;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.func.FuncTypes.FuncType;
import static dgir.dialect.io.IoOps.PrintOp;
import static org.junit.jupiter.api.Assertions.*;

/**
 * These are test for checking the validity of the core IR and traits. These test are mainly there
 * to check if the structural analysis of the IR hold, especially reaching definitions and in that
 * context region visiblity, nesting and isolation.
 */
public class CoreTests {
  static final Location LOC = Location.UNKNOWN;
  static boolean printResult = true;
  static boolean printDotGraph = true;
  static ObjectMapper mapper;

  @BeforeAll
  public static void setup() {
    Dialect.registerAllDialects();
    mapper = Utils.getMapper(true);
  }

  @Test
  public void parameterizedTypeParsingRoundTrips() {
    var nestedFunc = FuncType.of(List.of(IntegerT.INT32()), IntegerT.INT32());
    var arrayType = MemTypes.ArrayT.of(nestedFunc, OptionalInt.of(4));
    var topLevelFunc = FuncType.of(List.of(IntegerT.INT32(), arrayType), IntegerT.INT64());

    assertSame(
        IntegerT.INT32(), Type.fromParameterizedIdent(IntegerT.INT32().getParameterizedIdent()));
    assertSame(nestedFunc, Type.fromParameterizedIdent(nestedFunc.getParameterizedIdent()));
    assertSame(arrayType, Type.fromParameterizedIdent(arrayType.getParameterizedIdent()));
    assertSame(topLevelFunc, Type.fromParameterizedIdent(topLevelFunc.getParameterizedIdent()));
    assertEquals("func.func<\"(int32) -> (int32)\">", nestedFunc.getParameterizedIdent());

    List<Type> deserialized = new ArrayList<>();
    Type.consumeParameterText(
        "int32, mem.array<func.func<\"(int32) -> (int32)\">, 4>, func.func<\"(int32) -> (int32)\">",
        Type.AllTypes.of(deserialized));
    assertEquals(List.of(IntegerT.INT32(), arrayType, nestedFunc), deserialized);
  }

  @Test
  public void malformedParameterizedSyntaxIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Type.fromParameterizedIdent("func.func<\"(int32) -> (int32)"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Type.consumeParameterText(
                "int32,,func.func<\"(int32) -> (int32)\">", parameters -> Optional.empty()));
  }

  @Test
  public void quotedCustomExpressionsAreUnquotedInParameterStrings() {
    assertEquals(
        List.of("(int32, string) -> (bool)", "int32"),
        Type.extractParameterStrings("custom<\"(int32, string) -> (bool)\", int32>"));
  }

  @Test
  public void reachingDefSameBlock() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var constOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    funcOp.addOperation(new PrintOp(LOC, constOp.getResult()), 0);
    funcOp.addOperation(new ReturnOp(LOC), 0);

    assertTrue(DgirTestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reachingDefSuccessorBlock() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();
    Block entryBlock = funcOp.getRegion().getEntryBlock();

    // Create a new block
    Block targetBlock = new Block();
    funcOp.getRegion().addBlock(targetBlock);

    // Entry block: define val, branch to target
    var constOp = entryBlock.addOperation(new ConstantOp(LOC, 42));
    entryBlock.addOperation(new BranchOp(LOC, targetBlock));

    // Target block: use val, return
    targetBlock.addOperation(new PrintOp(LOC, constOp.getResult()));
    targetBlock.addOperation(new ReturnOp(LOC));

    assertTrue(DgirTestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reachingDefDominanceViolation() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();
    Block entryBlock = funcOp.getEntryBlock();

    Block leftBlock = funcOp.addBlock(new Block());
    Block rightBlock = funcOp.addBlock(new Block());
    Block mergeBlock = funcOp.addBlock(new Block());

    // Entry branches conditionally to left or right
    var cond = entryBlock.addOperation(new ConstantOp(LOC, true));
    entryBlock.addOperation(new BranchCondOp(LOC, cond.getResult(), leftBlock, rightBlock));

    // Left block: defines val, branches to merge
    var val = leftBlock.addOperation(new ConstantOp(LOC, 100));
    leftBlock.addOperation(new BranchOp(LOC, mergeBlock));

    // Right block: branches to merge (does NOT define val)
    rightBlock.addOperation(new BranchOp(LOC, mergeBlock));

    // Merge block: uses val
    // This is a violation because 'val' is not defined on the path through 'rightBlock'.
    mergeBlock.addOperation(new PrintOp(LOC, val.getResult()));
    mergeBlock.addOperation(new ReturnOp(LOC));

    assertFalse(DgirTestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reachingDefDiamondShape() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();
    Block entryBlock = funcOp.getEntryBlock();

    Block leftBlock = funcOp.addBlock(new Block());
    Block rightBlock = funcOp.addBlock(new Block());
    Block mergeBlock = funcOp.addBlock(new Block());

    // Entry defines val
    var val = entryBlock.addOperation(new ConstantOp(LOC, true));
    entryBlock.addOperation(new BranchCondOp(LOC, val.getResult(), leftBlock, rightBlock));

    // Left uses val
    leftBlock.addOperation(new PrintOp(LOC, val.getResult()));
    leftBlock.addOperation(new BranchOp(LOC, mergeBlock));

    // Right uses val
    rightBlock.addOperation(new PrintOp(LOC, val.getResult()));
    rightBlock.addOperation(new BranchOp(LOC, mergeBlock));

    // Merge uses val
    mergeBlock.addOperation(new PrintOp(LOC, val.getResult()));
    mergeBlock.addOperation(new ReturnOp(LOC));

    assertTrue(DgirTestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void dynamicAttributeSerializationRoundTrip() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    FuncOp funcOp = entry.getRight();

    var constOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    Operation operation = constOp.getOperation();
    operation.addDynamicAttribute("tag", new BuiltinAttrs.SymbolRefAttribute("runtime"));
    operation.addDynamicAttribute(
        "priority", new BuiltinAttrs.IntegerAttribute(7, IntegerT.INT32()));

    String json = mapper.writeValueAsString(operation);
    Operation roundTripped = mapper.readValue(json, Operation.class);

    assertEquals("", DgirTestUtils.compareSerializedOperations(mapper, operation, roundTripped));
    assertTrue(
        roundTripped
            .getDynamicAttributeAs("tag", BuiltinAttrs.SymbolRefAttribute.class)
            .isPresent());
    assertEquals(
        "runtime",
        roundTripped
            .getDynamicAttributeAsOrThrow("tag", BuiltinAttrs.SymbolRefAttribute.class)
            .getValue());
    assertEquals(
        7,
        roundTripped
            .getDynamicAttributeAsOrThrow("priority", BuiltinAttrs.IntegerAttribute.class)
            .getValue()
            .intValue());
    assertTrue(roundTripped.toString().contains("<dynamic ["));
    assertTrue(IrToText.toText(roundTripped).contains("<dynamic ["));

    assertTrue(roundTripped.removeDynamicAttribute("tag").isPresent());
    assertTrue(roundTripped.getDynamicAttribute("tag").isEmpty());
  }

  /**
   * Equivalent of this bril code
   *
   * @main { a: int = const 47; b: int = const 42; cond: bool = const true; br cond .left .right;
   *     <p>.left: b: int = const 1; c: int = const 5; jmp .end;
   *     <p>.right: a: int = const 2; c: int = const 10; jmp .end;
   *     <p>.end: d: int = sub a c; print d; }
   */
  @Test
  public void condition() {
    Pair<ProgramOp, FuncOp> entry = DgirTestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    Value c = new Value(IntegerT.INT64());

    Block entryBlock = funcOp.getEntryBlock();
    Block leftBlock = funcOp.addBlock(new Block());
    Block rightBlock = funcOp.addBlock(new Block());
    Block endBlock = funcOp.addBlock(new Block());

    var a = entryBlock.addOperation(new ConstantOp(LOC, 47L));
    var b = entryBlock.addOperation(new ConstantOp(LOC, 42L));
    var cond = entryBlock.addOperation(new ConstantOp(LOC, true));
    entryBlock.addOperation(new BranchCondOp(LOC, cond.getResult(), leftBlock, rightBlock));

    leftBlock.addOperation(new ConstantOp(LOC, 1L)).setOutputValue(b.getResult());
    leftBlock.addOperation(new ConstantOp(LOC, 5L)).setOutputValue(c);
    leftBlock.addOperation(new BranchOp(LOC, endBlock));

    rightBlock.addOperation(new ConstantOp(LOC, 2L)).setOutputValue(a.getResult());
    rightBlock.addOperation(new ConstantOp(LOC, 10L)).setOutputValue(c);
    rightBlock.addOperation(new BranchOp(LOC, endBlock));

    endBlock.addOperation(
        new ArithOps.BinaryOp(LOC, a.getResult(), c, ArithAttrs.BinModeAttr.BinMode.SUB));
    endBlock.addOperation(new ReturnOp(LOC));

    assertTrue(DgirTestUtils.testValidityAndSerialization(programOp));
  }
}
