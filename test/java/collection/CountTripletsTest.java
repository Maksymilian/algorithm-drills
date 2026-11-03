package collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountTripletsTest {

    @Test
    void samplesFromTask() {
        assertEquals(2, CountTriplets.countTriplets(List.of(1L, 2L, 2L, 4L), 2));
        assertEquals(6, CountTriplets.countTriplets(List.of(1L, 3L, 9L, 9L, 27L, 81L), 3));
        assertEquals(4, CountTriplets.countTriplets(List.of(1L, 5L, 5L, 25L, 125L), 5));
    }

    @Test
    void shortInputHasNoTriplets() {
        assertEquals(0, CountTriplets.countTriplets(List.of(), 2), "pusta lista");
        assertEquals(0, CountTriplets.countTriplets(List.of(1L), 2), "jeden element");
        assertEquals(0, CountTriplets.countTriplets(List.of(1L, 2L), 2), "dwa elementy");
    }

    @Test
    void ratioOneCountsCombinationsOfEqualValues() {
        assertEquals(10, CountTriplets.countTriplets(List.of(7L, 7L, 7L, 7L, 7L), 1), "C(5,3)");
        assertEquals(0, CountTriplets.countTriplets(List.of(7L, 7L), 1), "element nie łączy się sam ze sobą");
    }

    @Test
    void orderOfIndicesMatters() {
        assertEquals(0, CountTriplets.countTriplets(List.of(4L, 2L, 1L), 2));
    }

    @Test
    void zeroAndNegativeValues() {
        assertEquals(1, CountTriplets.countTriplets(List.of(0L, 0L, 0L), 3), "same zera");
        assertEquals(1, CountTriplets.countTriplets(List.of(-1L, -2L, -4L), 2), "ujemne");
        assertEquals(0, CountTriplets.countTriplets(List.of(-7L, -3L, -1L), 2), "-7/2 obcina do -3");
    }

    @Test
    void largeInputIsLinearAndUsesLong() {
        List<Long> ones = Collections.nCopies(100_000, 1L);
        long n = ones.size();
        assertEquals(n * (n - 1) * (n - 2) / 6, CountTriplets.countTriplets(ones, 1));
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> CountTriplets.countTriplets(null, 2));
        assertThrows(NullPointerException.class, () -> CountTriplets.countTriplets(Arrays.asList(1L, null), 2));
        assertThrows(IllegalArgumentException.class, () -> CountTriplets.countTriplets(List.of(1L), 0));
        assertThrows(IllegalArgumentException.class, () -> CountTriplets.countTriplets(List.of(1L), -2));
    }

    @Test
    void agreesWithBruteForceOnRandomData() {
        Random random = new Random(11);
        for (int round = 0; round < 500; round++) {
            List<Long> values = randomValues(random);
            long ratio = 1 + random.nextInt(3);
            assertEquals(bruteForce(values, ratio), CountTriplets.countTriplets(values, ratio), values + " r=" + ratio);
        }
    }

    private static List<Long> randomValues(Random random) {
        List<Long> values = new ArrayList<>();
        int size = random.nextInt(15);
        for (int i = 0; i < size; i++) {
            values.add((long) random.nextInt(-9, 10));
        }
        return values;
    }

    private static long bruteForce(List<Long> values, long ratio) {
        long count = 0;
        for (int i = 0; i < values.size(); i++) {
            for (int j = i + 1; j < values.size(); j++) {
                for (int k = j + 1; k < values.size(); k++) {
                    count += isProgression(values.get(i), values.get(j), values.get(k), ratio) ? 1 : 0;
                }
            }
        }
        return count;
    }

    private static boolean isProgression(long first, long second, long third, long ratio) {
        return second == first * ratio && third == second * ratio;
    }
}
