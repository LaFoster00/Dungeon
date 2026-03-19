import dgir.core.Dialect;
import dgir.core.debug.Location;
import dgir.dialect.builtin.BuiltinTypes.FloatT;
import dgir.dialect.mem.MemTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static dgir.dialect.arith.ArithOps.ConstantOp;
import static dgir.dialect.builtin.BuiltinAttrs.IntegerAttribute;
import static dgir.dialect.builtin.BuiltinOps.ProgramOp;
import static dgir.dialect.builtin.BuiltinTypes.IntegerT;
import static dgir.dialect.func.FuncOps.FuncOp;
import static dgir.dialect.func.FuncOps.ReturnOp;
import static dgir.dialect.mem.MemOps.*;
import static org.junit.jupiter.api.Assertions.*;

public class MemTests {
  private static final Location LOC = Location.UNKNOWN;

  @BeforeAll
  public static void setup() {
    Dialect.registerAllDialects();
  }

  private static MemTypes.ArrayT intArray(OptionalInt width) {
    return MemTypes.ArrayT.of(IntegerT.INT32, width);
  }

  private static MemTypes.ArrayT floatArray(OptionalInt width) {
    return MemTypes.ArrayT.of(FloatT.FLOAT32, width);
  }

  private static Pair<ProgramOp, FuncOp> newMain() {
    return TestUtils.createProgramOpWithEntryFunc();
  }

  @Test
  public void allocGcWithDynamicIntegerSize() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 8), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    assertEquals(intArray(OptionalInt.empty()), alloc.getArrayType());
    assertEquals(size.getResult(), alloc.getDynamicSize().orElseThrow());
    assertTrue(alloc.getStaticSize().isEmpty());

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void allocGcRejectsNonIntegerDynamicSize() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var notAnInt = funcMain.addOperation(new ConstantOp(LOC, "oops"), 0);
    funcMain.addOperation(
        new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(notAnInt.getResult())), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void allocGcRejectsStaticAndDynamicSizeTogether() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 8), 0);
    funcMain.addOperation(
        new AllocGcOp(LOC, intArray(OptionalInt.of(4)), Optional.of(size.getResult())), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reallocGcWithStaticSizeAndNoDynamicOperand() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var dynSize = funcMain.addOperation(new ConstantOp(LOC, 4), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(dynSize.getResult())), 0);
    var castToStatic =
        funcMain.addOperation(new CastOp(LOC, intArray(OptionalInt.of(4)), alloc.getResult()), 0);
    var realloc =
        funcMain.addOperation(
            new ReallocGcOp(
                LOC, intArray(OptionalInt.of(4)), castToStatic.getResult(), Optional.empty()),
            0);

    assertEquals(intArray(OptionalInt.of(4)), realloc.getArrayType());
    assertTrue(realloc.getStaticSize().isPresent());
    assertTrue(realloc.getDynamicSize().isEmpty());

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reallocGcWithDynamicSizeAndUnboundedArray() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var initialSize = funcMain.addOperation(new ConstantOp(LOC, 4), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(initialSize.getResult())),
            0);
    var newSize = funcMain.addOperation(new ConstantOp(LOC, 16), 0);

    funcMain.addOperation(
        new ReallocGcOp(
            LOC,
            intArray(OptionalInt.empty()),
            alloc.getResult(),
            Optional.of(newSize.getResult())),
        0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reallocGcRejectsStaticAndDynamicSizeTogether() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 4), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var fixed =
        funcMain.addOperation(new CastOp(LOC, intArray(OptionalInt.of(4)), alloc.getResult()), 0);
    var newSize = funcMain.addOperation(new ConstantOp(LOC, 12), 0);

    funcMain.addOperation(
        new ReallocGcOp(
            LOC, intArray(OptionalInt.of(4)), fixed.getResult(), Optional.of(newSize.getResult())),
        0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reallocGcRejectsNonArrayInput() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var notArray = funcMain.addOperation(new ConstantOp(LOC, 99), 0);
    funcMain.addOperation(
        new ReallocGcOp(LOC, intArray(OptionalInt.empty()), notArray.getResult(), Optional.empty()),
        0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void reallocGcRejectsInputOutputTypeMismatch() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 4), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    funcMain.addOperation(
        new ReallocGcOp(LOC, floatArray(OptionalInt.empty()), alloc.getResult(), Optional.empty()),
        0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void castBetweenCompatibleArrayTypes() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 3), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var cast =
        funcMain.addOperation(new CastOp(LOC, intArray(OptionalInt.of(3)), alloc.getResult()), 0);

    assertEquals(intArray(OptionalInt.of(3)), cast.getArrayType());

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void castSameTypeIsValid() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 3), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    funcMain.addOperation(new CastOp(LOC, intArray(OptionalInt.empty()), alloc.getResult()), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void castRejectsDifferentElementTypes() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 3), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    funcMain.addOperation(new CastOp(LOC, floatArray(OptionalInt.empty()), alloc.getResult()), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void castRejectsDifferentStaticWidths() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 3), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var fixed =
        funcMain.addOperation(new CastOp(LOC, intArray(OptionalInt.of(3)), alloc.getResult()), 0);
    funcMain.addOperation(new CastOp(LOC, intArray(OptionalInt.of(4)), fixed.getResult()), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void sizeofWithArrayOperand() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 5), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var sizeof = funcMain.addOperation(new SizeofOp(LOC, alloc.getResult()), 0);

    assertEquals(IntegerT.INT64, sizeof.getResult().getType());

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void sizeofRejectsNonArrayOperand() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var notArray = funcMain.addOperation(new ConstantOp(LOC, 7), 0);
    funcMain.addOperation(new SizeofOp(LOC, notArray.getResult()), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void getElementWithValidOperands() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 6), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var index = funcMain.addOperation(new ConstantOp(LOC, 2), 0);
    var getElement =
        funcMain.addOperation(new GetElementOp(LOC, alloc.getResult(), index.getResult()), 0);

    assertEquals(IntegerT.INT32, getElement.getResult().getType());

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertTrue(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void getElementRejectsNonArrayFirstOperand() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var value = funcMain.addOperation(new ConstantOp(LOC, 10), 0);
    var index = funcMain.addOperation(new ConstantOp(LOC, 0), 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            funcMain.addOperation(new GetElementOp(LOC, value.getResult(), index.getResult()), 0));
  }

  @Test
  public void getElementRejectsNonIntegerIndex() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 6), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var index = funcMain.addOperation(new ConstantOp(LOC, "bad index"), 0);
    funcMain.addOperation(new GetElementOp(LOC, alloc.getResult(), index.getResult()), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }

  @Test
  public void getElementConstructorRejectsNonArrayValue() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    FuncOp funcMain = entry.getRight();

    var notArray = funcMain.addOperation(new ConstantOp(LOC, 1), 0);
    var index = funcMain.addOperation(new ConstantOp(LOC, 0), 0);

    assertThrows(
        IllegalArgumentException.class,
        () -> new GetElementOp(LOC, notArray.getResult(), index.getResult()));
  }

  @Test
  public void setElementVerifierAcceptsValidValueTyping() {
    var arrayType = intArray(OptionalInt.empty());
    var arrayValue =
        new AllocGcOp(
                LOC,
                arrayType,
                Optional.of(
                    new ConstantOp(LOC, new IntegerAttribute(4, IntegerT.INT64)).getResult()))
            .getResult();
    var index = new ConstantOp(LOC, 0).getResult();
    var value = new ConstantOp(LOC, 42).getResult();

    var setElement = new SetElementOp(LOC, arrayValue, index, value);
    assertTrue(setElement.getVerifier().apply(setElement.getOperation()));
  }

  @Test
  public void setElementVerifierRejectsNonArrayFirstOperand() {
    var notArray = new ConstantOp(LOC, 4).getResult();
    var index = new ConstantOp(LOC, 0).getResult();
    var value = new ConstantOp(LOC, 42).getResult();

    var setElement = new SetElementOp(LOC, notArray, index, value);
    assertFalse(setElement.getVerifier().apply(setElement.getOperation()));
  }

  @Test
  public void setElementVerifierRejectsNonIntegerIndex() {
    var arrayType = intArray(OptionalInt.empty());
    var arrayValue =
        new AllocGcOp(
                LOC,
                arrayType,
                Optional.of(
                    new ConstantOp(LOC, new IntegerAttribute(4, IntegerT.INT64)).getResult()))
            .getResult();
    var badIndex = new ConstantOp(LOC, "x").getResult();
    var value = new ConstantOp(LOC, 42).getResult();

    var setElement = new SetElementOp(LOC, arrayValue, badIndex, value);
    assertFalse(setElement.getVerifier().apply(setElement.getOperation()));
  }

  @Test
  public void setElementVerifierRejectsValueWithWrongType() {
    var arrayType = intArray(OptionalInt.empty());
    var arrayValue =
        new AllocGcOp(
                LOC,
                arrayType,
                Optional.of(
                    new ConstantOp(LOC, new IntegerAttribute(4, IntegerT.INT64)).getResult()))
            .getResult();
    var index = new ConstantOp(LOC, 0).getResult();
    var badValue = new ConstantOp(LOC, "not an int").getResult();

    var setElement = new SetElementOp(LOC, arrayValue, index, badValue);
    assertFalse(setElement.getVerifier().apply(setElement.getOperation()));
  }

  @Test
  public void setElementIsRejectedInFullOperationVerification() {
    Pair<ProgramOp, FuncOp> entry = newMain();
    ProgramOp programOp = entry.getLeft();
    FuncOp funcMain = entry.getRight();

    var size = funcMain.addOperation(new ConstantOp(LOC, 2), 0);
    var alloc =
        funcMain.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);
    var index = funcMain.addOperation(new ConstantOp(LOC, 0), 0);
    var value = funcMain.addOperation(new ConstantOp(LOC, 9), 0);
    funcMain.addOperation(
        new SetElementOp(LOC, alloc.getResult(), index.getResult(), value.getResult()), 0);

    funcMain.addOperation(new ReturnOp(LOC), 0);
    assertFalse(TestUtils.testValidityAndSerialization(programOp));
  }
}
