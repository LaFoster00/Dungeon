import dgir.core.Dialect;
import dgir.core.DgirCoreUtils;
import dgir.core.IrToText;
import dgir.core.debug.Location;
import dgir.core.ir.Operation;
import dgir.core.ir.Block;
import dgir.core.ir.TypeDetails;
import dgir.core.serialization.Utils;
import dgir.dialect.builtin.BuiltinAttrs;
import dgir.dialect.mem.MemTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        IntegerT.INT32(),
        TypeDetails.fromParameterizedIdent(IntegerT.INT32().getParameterizedIdent()));
    assertSame(nestedFunc, TypeDetails.fromParameterizedIdent(nestedFunc.getParameterizedIdent()));
    assertSame(arrayType, TypeDetails.fromParameterizedIdent(arrayType.getParameterizedIdent()));
    assertSame(
        topLevelFunc, TypeDetails.fromParameterizedIdent(topLevelFunc.getParameterizedIdent()));
    assertEquals("func.func<\"(int32) -> (int32)\">", nestedFunc.getParameterizedIdent());

    assertEquals(
        List.of(IntegerT.INT32(), arrayType, nestedFunc),
        TypeDetails.fromParameterString(
            "int32, mem.array<func.func<\"(int32) -> (int32)\">, 4>, func.func<\"(int32) -> (int32)\">"));
  }

  @Test
  public void malformedParameterizedSyntaxIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TypeDetails.fromParameterizedIdent("func.func<\"(int32) -> (int32)"));
    assertThrows(
        IllegalArgumentException.class,
        () -> TypeDetails.fromParameterString("int32,,func.func<\"(int32) -> (int32)\">"));
  }

  @Test
  public void quotedCustomExpressionsArePreservedInParameterStrings() {
    assertEquals(
        List.of("\"(int32, string) -> (bool)\"", "int32"),
        DgirCoreUtils.getParameterStrings("custom<\"(int32, string) -> (bool)\", int32>"));
  }

  @Test
  public void reachingDefSameBlock() {
    Pair<ProgramOp, FuncOp> entry = TestUtils.createProgramOpWithEntryFunc();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcOp = entry.getRight();

    var constOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    funcOp.addOperation(new PrintOp(LOC, constOp.getResult()), 0);
    funcOp.addOperation(new ReturnOp(LOC), 0);

    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reachingDefSuccessorBlock() {
    Pair<ProgramOp, FuncOp> entry = TestUtils.createProgramOpWithEntryFunc();
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

    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reachingDefDominanceViolation() {
    Pair<ProgramOp, FuncOp> entry = TestUtils.createProgramOpWithEntryFunc();
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

    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reachingDefDiamondShape() {
    Pair<ProgramOp, FuncOp> entry = TestUtils.createProgramOpWithEntryFunc();
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

    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void dynamicAttributeSerializationRoundTrip() {
    Pair<ProgramOp, FuncOp> entry = TestUtils.createProgramOpWithEntryFunc();
    FuncOp funcOp = entry.getRight();

    var constOp = funcOp.addOperation(new ConstantOp(LOC, 42), 0);
    Operation operation = constOp.getOperation();
    operation.addDynamicAttribute("tag", new BuiltinAttrs.SymbolRefAttribute("runtime"));
    operation.addDynamicAttribute(
        "priority", new BuiltinAttrs.IntegerAttribute(7, IntegerT.INT32()));

    String json = mapper.writeValueAsString(operation);
    Operation roundTripped = mapper.readValue(json, Operation.class);

    assertEquals("", TestUtils.compareSerializedOperations(mapper, operation, roundTripped));
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
}
