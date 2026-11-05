package dynamic;

import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecibinaryNumbersTest {

    private static final int BRUTE_FORCE_MAX_VALUE = 40;
    private static final long BRUTE_FORCE_LIMIT = 1_000_000;

    @Test
    void beginningOfListFromNote() {
        long[] expected = {0, 1, 2, 10, 3, 11, 4, 12, 20, 100};
        for (int x = 1; x <= expected.length; x++) {
            assertEquals(expected[x - 1], DecibinaryNumbers.decibinaryNumbers(x), "x = " + x);
        }
    }

    @Test
    void agreesWithSortedListOfAllRepresentations() {
        List<Long> list = LongStream.range(0, BRUTE_FORCE_LIMIT).boxed()
                .filter(number -> decimalValue(number) < BRUTE_FORCE_MAX_VALUE)
                .sorted(Comparator.comparingLong(DecibinaryNumbersTest::decimalValue).thenComparing(number -> number))
                .toList();
        for (int x = 1; x <= list.size(); x++) {
            assertEquals(list.get(x - 1), DecibinaryNumbers.decibinaryNumbers(x), "x = " + x);
        }
    }

    @Test
    void lastAllowedPositionHasExpectedValue() {
        long result = DecibinaryNumbers.decibinaryNumbers(DecibinaryNumbers.MAX_POSITION);
        assertEquals(285_112, decimalValue(result), "wartość dla x = 10^16");
        assertTrue(result < 2_000_000_000_000_000_000L, "wynik mieści się w long z zapasem: " + result);
    }

    @Test
    void neighbouringPositionsNearLimitAreOrdered() {
        long previous = DecibinaryNumbers.decibinaryNumbers(DecibinaryNumbers.MAX_POSITION - 1_000);
        for (long x = DecibinaryNumbers.MAX_POSITION - 999; x <= DecibinaryNumbers.MAX_POSITION; x++) {
            long current = DecibinaryNumbers.decibinaryNumbers(x);
            assertTrue(isBefore(previous, current), previous + " przed " + current + " dla x = " + x);
            previous = current;
        }
    }

    @Test
    void invalidPositionsAreRejected() {
        for (long x : new long[] {0, -1, Long.MIN_VALUE, DecibinaryNumbers.MAX_POSITION + 1, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> DecibinaryNumbers.decibinaryNumbers(x), "x = " + x);
        }
    }

    private static boolean isBefore(long first, long second) {
        long firstValue = decimalValue(first);
        long secondValue = decimalValue(second);
        return firstValue < secondValue || firstValue == secondValue && first < second;
    }

    private static long decimalValue(long decibinary) {
        long value = 0;
        for (long weight = 1; decibinary > 0; decibinary /= 10, weight *= 2) {
            value += decibinary % 10 * weight;
        }
        return value;
    }
}
