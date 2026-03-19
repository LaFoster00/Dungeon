import dgir.core.debug.Location;
import dgir.dialect.arith.ArithOps.ConstantOp;
import dgir.dialect.builtin.BuiltinOps.ProgramOp;
import dgir.dialect.builtin.BuiltinTypes.IntegerT;
import dgir.dialect.func.FuncOps.FuncOp;
import dgir.dialect.func.FuncOps.ReturnOp;
import dgir.dialect.io.IoOps.PrintOp;
import dgir.dialect.mem.MemOps.*;
import dgir.dialect.mem.MemTypes;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VM-level execution tests for mem ops (AllocGcOp, ReallocGcOp, CastOp, SizeofOp, GetElementOp,
 * SetElementOp). Tests verify runtime behavior of memory allocation, resizing, casting, and element
 * access.
 */
public class MemTest extends VmTestBase {
  private static final Location LOC = Location.UNKNOWN;

  private static MemTypes.ArrayT intArray(OptionalInt width) {
    return MemTypes.ArrayT.of(IntegerT.INT32, width);
  }

  @Test
  void allocGcCreatesArrayOfCorrectSize() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var sizeOp = main.addOperation(new ConstantOp(LOC, 5), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(sizeOp.getResult())), 0);
    var sizeofOp = main.addOperation(new SizeofOp(LOC, allocOp.getResult()), 0);

    main.addOperation(new PrintOp(LOC, sizeofOp.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "5");
  }

  @Test
  void reallocGcIncreasesArraySize() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var initialSize = main.addOperation(new ConstantOp(LOC, 3), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(initialSize.getResult())),
            0);

    var newSize = main.addOperation(new ConstantOp(LOC, 8), 0);
    var reallocOp =
        main.addOperation(
            new ReallocGcOp(
                LOC,
                intArray(OptionalInt.empty()),
                allocOp.getResult(),
                Optional.of(newSize.getResult())),
            0);
    var sizeofOp = main.addOperation(new SizeofOp(LOC, reallocOp.getResult()), 0);

    main.addOperation(new PrintOp(LOC, sizeofOp.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "8");
  }

  @Test
  void reallocGcDecreaseArraySize() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var initialSize = main.addOperation(new ConstantOp(LOC, 10), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(initialSize.getResult())),
            0);

    var newSize = main.addOperation(new ConstantOp(LOC, 4), 0);
    var reallocOp =
        main.addOperation(
            new ReallocGcOp(
                LOC,
                intArray(OptionalInt.empty()),
                allocOp.getResult(),
                Optional.of(newSize.getResult())),
            0);
    var sizeofOp = main.addOperation(new SizeofOp(LOC, reallocOp.getResult()), 0);

    main.addOperation(new PrintOp(LOC, sizeofOp.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "4");
  }

  @Test
  void reallocGcPreservesElementsWhenShrinking() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var initialSize = main.addOperation(new ConstantOp(LOC, 5), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(initialSize.getResult())),
            0);

    // Set some elements
    var idx0 = main.addOperation(new ConstantOp(LOC, 0), 0);
    var val42 = main.addOperation(new ConstantOp(LOC, 42), 0);
    main.addOperation(
        new SetElementOp(LOC, allocOp.getResult(), idx0.getResult(), val42.getResult()), 0);

    var idx2 = main.addOperation(new ConstantOp(LOC, 2), 0);
    var val99 = main.addOperation(new ConstantOp(LOC, 99), 0);
    main.addOperation(
        new SetElementOp(LOC, allocOp.getResult(), idx2.getResult(), val99.getResult()), 0);

    // Shrink to size 3 (preserves indices 0-2)
    var newSize = main.addOperation(new ConstantOp(LOC, 3), 0);
    var reallocOp =
        main.addOperation(
            new ReallocGcOp(
                LOC,
                intArray(OptionalInt.empty()),
                allocOp.getResult(),
                Optional.of(newSize.getResult())),
            0);

    // Read back preserved elements
    var getElem0 =
        main.addOperation(new GetElementOp(LOC, reallocOp.getResult(), idx0.getResult()), 0);
    var getElem2 =
        main.addOperation(new GetElementOp(LOC, reallocOp.getResult(), idx2.getResult()), 0);

    main.addOperation(new PrintOp(LOC, getElem0.getResult()), 0);
    main.addOperation(new PrintOp(LOC, getElem2.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "4299");
  }

  @Test
  void castBetweenStaticAndDynamicSizes() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var size = main.addOperation(new ConstantOp(LOC, 7), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    // Cast to static-size array
    var castOp =
        main.addOperation(new CastOp(LOC, intArray(OptionalInt.of(7)), allocOp.getResult()), 0);
    var sizeofOp = main.addOperation(new SizeofOp(LOC, castOp.getResult()), 0);

    main.addOperation(new PrintOp(LOC, sizeofOp.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "7");
  }

  @Test
  void castFromStaticToDynamicType() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var size = main.addOperation(new ConstantOp(LOC, 4), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    // Cast to static size
    var castStatic =
        main.addOperation(new CastOp(LOC, intArray(OptionalInt.of(4)), allocOp.getResult()), 0);

    // Cast back to dynamic
    var castDynamic =
        main.addOperation(
            new CastOp(LOC, intArray(OptionalInt.empty()), castStatic.getResult()), 0);
    var sizeofOp = main.addOperation(new SizeofOp(LOC, castDynamic.getResult()), 0);

    main.addOperation(new PrintOp(LOC, sizeofOp.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "4");
  }

  @Test
  void getElementReadsCorrectValue() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var size = main.addOperation(new ConstantOp(LOC, 3), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    // Set multiple elements
    for (int i = 0; i < 3; i++) {
      var idx = main.addOperation(new ConstantOp(LOC, i), 0);
      var val = main.addOperation(new ConstantOp(LOC, (i + 1) * 10), 0);
      main.addOperation(
          new SetElementOp(LOC, allocOp.getResult(), idx.getResult(), val.getResult()), 0);
    }

    // Read them back
    for (int i = 0; i < 3; i++) {
      var idx = main.addOperation(new ConstantOp(LOC, i), 0);
      var getElem =
          main.addOperation(new GetElementOp(LOC, allocOp.getResult(), idx.getResult()), 0);
      main.addOperation(new PrintOp(LOC, getElem.getResult()), 0);
    }

    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "102030");
  }

  @Test
  void setElementModifiesArrayInPlace() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var size = main.addOperation(new ConstantOp(LOC, 2), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    // Set element 0 to 100
    var idx0 = main.addOperation(new ConstantOp(LOC, 0), 0);
    var val100 = main.addOperation(new ConstantOp(LOC, 100), 0);
    main.addOperation(
        new SetElementOp(LOC, allocOp.getResult(), idx0.getResult(), val100.getResult()), 0);

    // Set element 1 to 200
    var idx1 = main.addOperation(new ConstantOp(LOC, 1), 0);
    var val200 = main.addOperation(new ConstantOp(LOC, 200), 0);
    main.addOperation(
        new SetElementOp(LOC, allocOp.getResult(), idx1.getResult(), val200.getResult()), 0);

    // Read back both
    var getElem0 =
        main.addOperation(new GetElementOp(LOC, allocOp.getResult(), idx0.getResult()), 0);
    var getElem1 =
        main.addOperation(new GetElementOp(LOC, allocOp.getResult(), idx1.getResult()), 0);

    main.addOperation(new PrintOp(LOC, getElem0.getResult()), 0);
    main.addOperation(new PrintOp(LOC, getElem1.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "100200");
  }

  @Test
  void sizeofReturnsInt64() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var size = main.addOperation(new ConstantOp(LOC, 12), 0);
    var allocOp =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size.getResult())), 0);

    var sizeofOp = main.addOperation(new SizeofOp(LOC, allocOp.getResult()), 0);

    // Verify result type is INT64
    assertEquals(IntegerT.INT64, sizeofOp.getResult().getType());

    main.addOperation(new PrintOp(LOC, sizeofOp.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "12");
  }

  @Test
  void multipleAllocsCreateDistinctArrays() {
    ProgramOp program = new ProgramOp(LOC);
    FuncOp main = program.addOperation(new FuncOp(LOC, "main"));

    var size1 = main.addOperation(new ConstantOp(LOC, 2), 0);
    var alloc1 =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size1.getResult())), 0);

    var size2 = main.addOperation(new ConstantOp(LOC, 3), 0);
    var alloc2 =
        main.addOperation(
            new AllocGcOp(LOC, intArray(OptionalInt.empty()), Optional.of(size2.getResult())), 0);

    // Set different values in each
    var idx0 = main.addOperation(new ConstantOp(LOC, 0), 0);
    var val99 = main.addOperation(new ConstantOp(LOC, 99), 0);
    main.addOperation(
        new SetElementOp(LOC, alloc1.getResult(), idx0.getResult(), val99.getResult()), 0);

    var val88 = main.addOperation(new ConstantOp(LOC, 88), 0);
    main.addOperation(
        new SetElementOp(LOC, alloc2.getResult(), idx0.getResult(), val88.getResult()), 0);

    // Verify independence
    var get1 = main.addOperation(new GetElementOp(LOC, alloc1.getResult(), idx0.getResult()), 0);
    var get2 = main.addOperation(new GetElementOp(LOC, alloc2.getResult(), idx0.getResult()), 0);

    main.addOperation(new PrintOp(LOC, get1.getResult()), 0);
    main.addOperation(new PrintOp(LOC, get2.getResult()), 0);
    main.addOperation(new ReturnOp(LOC), 0);

    runProgram(program, "9988");
  }
}
