package zas.admin.zec.backend.agent.tools.ii.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import zas.admin.zec.backend.agent.tools.ii.model.IndexHF;
import zas.admin.zec.backend.agent.tools.ii.model.IndexTA;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RangeHelperTest {

    @Test
    void matchingIdForTargetWithTotalIdConditionReturnsTotalId() {
        String result = RangeHelper.matchingIdForTarget("01-96", new IndexHF());
        assertEquals("05-96", result);
    }

    @Test
    void matchingIdForTargetWithExactIdMatchReturnsInputId() {
        String result = RangeHelper.matchingIdForTarget("42", new IndexTA());
        assertEquals("41-43", result);
    }

    @Test
    void matchingIdForTargetWithSubsetRangeMatchingReturnsLargerRangeKey() {
        // The larger range "05-43" covers the subset defined by "05-09/35-39"
        String result = RangeHelper.matchingIdForTarget("05-09/35-39", new IndexTA());
        assertEquals("05-43", result);
    }

    @Test
    void toRangeWithPlusSignReturnsFixedRange() {
        List<Integer> result = RangeHelper.toRange("77+");
        List<Integer> expected = List.of(77, 79, 80, 81, 82);
        assertEquals(expected, result);
    }

    @Test
    void toRangeWithDashedRangeProducesContinuousNumbers() {
        List<Integer> result = RangeHelper.toRange("05-08");
        List<Integer> expected = IntStream.rangeClosed(5, 8)
                .boxed()
                .toList();
        assertEquals(expected, result);
    }

    @Test
    void toRangeWithSlashContainingDashConcatenatesRanges() {
        List<Integer> result = RangeHelper.toRange("05-07/20-22");
        List<Integer> expectedStream = IntStream.concat(
                IntStream.rangeClosed(5, 7),
                IntStream.rangeClosed(20, 22))
                .boxed()
                .toList();

        assertEquals(expectedStream, result);
    }

    @Test
    void toRangeWithSlashNotContainingDashReturnsMultipleNumbers() {
        List<Integer> result = RangeHelper.toRange("64/66");
        List<Integer> expected = List.of(64, 66);
        assertEquals(expected, result);
    }

    @Test
    void toRangeWithSingleValueReturnsSingletonList() {
        List<Integer> result = RangeHelper.toRange("42");
        List<Integer> expected = List.of(42);
        assertEquals(expected, result);
    }

    @Test
    void matchingIdForTargetWithNoMatchingSubsetThrowsException() {
        Executable executable = () -> RangeHelper.matchingIdForTarget("100", new IndexHF());
        assertThrows(RuntimeException.class, executable);
    }
}
