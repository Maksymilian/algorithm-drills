package greedy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static greedy.MaxMin.fairestSelection;
import static greedy.MaxMin.maxMin;
import static greedy.MaxMin.maxMinAsLong;
import static greedy.MaxMin.maxMinByRadixSort;
import static greedy.MaxMin.maxMinInPlace;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxMinTest {

    static Stream<Arguments> knownCases() {
        return Stream.of(
                Arguments.of("sample 0", 3, new int[]{10, 100, 300, 200, 1000, 20, 30}, 20L),
                Arguments.of("sample 1", 4, new int[]{1, 2, 3, 4, 10, 20, 30, 40, 100, 200}, 3L),
                Arguments.of("sample 2", 2, new int[]{1, 2, 1, 2, 1}, 0L),

                Arguments.of("k = 1 is always fair", 1, new int[]{100, 1, 50}, 0L),
                Arguments.of("k = n leaves no choice", 3, new int[]{100, 1, 50}, 99L),
                Arguments.of("single element", 1, new int[]{7}, 0L),
                Arguments.of("two elements", 2, new int[]{7, 4}, 3L),
                Arguments.of("all equal", 3, new int[]{5, 5, 5, 5}, 0L),
                Arguments.of("duplicates make it free", 2, new int[]{1, 9, 4, 9}, 0L),

                Arguments.of("already ascending", 3, new int[]{1, 2, 3, 40, 50, 60}, 2L),
                Arguments.of("already descending", 3, new int[]{60, 50, 40, 3, 2, 1}, 2L),

                Arguments.of("best window in the middle", 3, new int[]{1, 500, 501, 502, 900, 2000}, 2L),
                Arguments.of("the k smallest are the wrong answer", 3, new int[]{0, 10, 20, 30, 31, 32}, 2L),

                Arguments.of("negatives", 3, new int[]{-10, -9, -8, 0, 100}, 2L),
                Arguments.of("negative to positive", 2, new int[]{-1, 1, -2, 7}, 1L),
                Arguments.of("spread of exactly Integer.MAX_VALUE", 2,
                        new int[]{Integer.MIN_VALUE, -1, Integer.MAX_VALUE}, 2147483647L)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownCases")
    void findsTheMinimumUnfairness(String name, int k, int[] arr, long expected) {
        assertEveryMethodReturns(expected, k, arr, name);
    }

    @Test
    void inPlaceLeavesTheArraySorted() {
        int[] arr = {10, 100, 300, 200, 1000, 20, 30};

        assertEquals(20L, maxMinInPlace(3, arr));
        assertArrayEquals(new int[]{10, 20, 30, 100, 200, 300, 1000}, arr);
    }

    @Test
    void theOtherMethodsLeaveTheArrayAlone() {
        int[] arr = {10, 100, 300, 200, 1000, 20, 30};
        String original = Arrays.toString(arr);

        maxMin(3, arr);
        assertEquals(original, Arrays.toString(arr), "maxMin");
        maxMinAsLong(3, arr);
        assertEquals(original, Arrays.toString(arr), "maxMinAsLong");
        maxMinByRadixSort(3, arr);
        assertEquals(original, Arrays.toString(arr), "maxMinByRadixSort");
        fairestSelection(3, arr);
        assertEquals(original, Arrays.toString(arr), "fairestSelection");
    }

    @Test
    void inPlaceRejectsBadKWithoutTouchingTheArray() {
        int[] arr = {3, 1, 2};

        assertThrows(IllegalArgumentException.class, () -> maxMinInPlace(4, arr));
        assertThrows(IllegalArgumentException.class, () -> maxMinInPlace(0, arr));
        assertArrayEquals(new int[]{3, 1, 2}, arr);
    }

    @Test
    void theSelectionIsTheAnswerItReports() {
        Random random = new Random(20260914L);

        for (int trial = 0; trial < 400; trial++) {
            int n = 1 + random.nextInt(40);
            int[] arr = random.ints(n, -50, 51).toArray();
            int k = 1 + random.nextInt(n);
            String where = "k=" + k + " " + Arrays.toString(arr);

            int[] selection = fairestSelection(k, arr);

            assertEquals(k, selection.length, where);
            assertArrayEquals(sortedCopy(selection), selection, "selection must be ascending: " + where);
            assertEquals(maxMinAsLong(k, arr), (long) selection[k - 1] - selection[0], where);
            assertIsSubMultisetOf(selection, arr, where);
        }
    }

    @Test
    void theSelectionIsTheEarliestWindowOnATie() {
        assertArrayEquals(new int[]{1, 2}, fairestSelection(2, new int[]{5, 2, 6, 1}));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> maxMin(1, (int[]) null));
        assertThrows(IllegalArgumentException.class, () -> maxMin(1, (List<Integer>) null));
        assertThrows(IllegalArgumentException.class, () -> maxMinAsLong(1, null));
        assertThrows(IllegalArgumentException.class, () -> maxMinInPlace(1, null));
        assertThrows(IllegalArgumentException.class, () -> maxMinByRadixSort(1, null));
        assertThrows(IllegalArgumentException.class, () -> fairestSelection(1, null));
    }

    @Test
    void rejectsAKItCannotSelect() {
        int[] arr = {1, 2, 3};

        for (int k : new int[]{0, -1, Integer.MIN_VALUE, 4, 100, Integer.MAX_VALUE}) {
            assertRejected(k, arr, "k=" + k);
        }
        assertRejected(1, new int[]{}, "empty array");
    }

    @Test
    void rejectsANullElementInTheList() {
        List<Integer> withNull = new ArrayList<>(List.of(1, 2));
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> maxMin(2, withNull));
    }

    @Test
    void doesNotWrapOnSpreadsWiderThanAnInt() {
        int[] extremes = {Integer.MIN_VALUE, Integer.MAX_VALUE};

        assertEquals(4294967295L, maxMinAsLong(2, extremes));
        assertEquals(4294967295L, maxMinByRadixSort(2, extremes));
        assertEquals(4294967295L, maxMinInPlace(2, extremes.clone()));
        assertThrows(ArithmeticException.class, () -> maxMin(2, extremes));

        int[] withMiddle = {Integer.MAX_VALUE, Integer.MIN_VALUE, -1};
        assertEquals(2147483647L, maxMinAsLong(2, withMiddle));
        assertEquals(2147483647, maxMin(2, withMiddle));
    }

    @Test
    void matchesBruteForceOverEverySelectionUpToTenElements() {
        Random random = new Random(20260914L);

        for (int n = 1; n <= 10; n++) {
            for (int trial = 0; trial < 40; trial++) {
                int[] arr = assortedValues(n, trial, random);

                for (int k = 1; k <= n; k++) {
                    long expected = bruteForceMinimumUnfairness(k, arr);
                    assertEveryMethodReturns(expected, k, arr, "k=" + k + " " + Arrays.toString(arr));
                }
            }
        }
    }

    @Test
    void matchesAHandScanOfTheSortedArrayOnRandomInput() {
        Random random = new Random(5L);

        for (int trial = 0; trial < 400; trial++) {
            int n = 1 + random.nextInt(300);
            int[] arr = random.ints(n, -100_000, 100_001).toArray();
            int k = 1 + random.nextInt(n);

            assertEveryMethodReturns(scanSortedCopy(k, arr), k, arr, "k=" + k + " n=" + n);
        }
    }

    @Test
    void radixSortOrdersAcrossTheSignBit() {
        int[] arr = {Integer.MAX_VALUE, -1, 0, Integer.MIN_VALUE, 1, -2,
                Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, 255, 256, -255, -256};

        for (int k = 1; k <= arr.length; k++) {
            assertEquals(maxMinAsLong(k, arr), maxMinByRadixSort(k, arr), "k=" + k);
        }
    }

    @Test
    void radixSortSkipsUniformPassesWithoutSkippingWork() {
        Random random = new Random(13L);

        for (int shift = 0; shift < 32; shift += 8) {
            int[] arr = new int[200];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = random.nextInt(256) << shift;
            }
            for (int k : new int[]{1, 2, 7, 199, 200}) {
                assertEquals(maxMinAsLong(k, arr), maxMinByRadixSort(k, arr), "shift=" + shift + " k=" + k);
            }
        }
    }

    @Test
    void selectsFromTenMillionElements() {
        int n = 10_000_000;
        int[] arr = shuffledIdentity(n, new Random(3L));

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            assertEquals(1L, maxMinAsLong(2, arr));
            assertEquals(4_999_999L, maxMinAsLong(5_000_000, arr));
            assertEquals(n - 1L, maxMinAsLong(n, arr));

            assertEquals(4_999_999L, maxMinByRadixSort(5_000_000, arr));
            assertEquals(4_999_999L, maxMinInPlace(5_000_000, arr.clone()));
        });
    }

    @Test
    void selectsFromTenMillionElementsWithHeavyDuplication() {
        int n = 10_000_000;
        int[] arr = new int[n];
        Arrays.setAll(arr, i -> i % 1000);

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            assertEquals(0L, maxMinAsLong(10_000, arr));
            assertEquals(0L, maxMinByRadixSort(10_000, arr));
            assertEquals(999L, maxMinAsLong(n, arr));
        });
    }

    private static void assertEveryMethodReturns(long expected, int k, int[] arr, String where) {
        assertEquals(expected, maxMinAsLong(k, arr), "maxMinAsLong: " + where);
        assertEquals(expected, maxMinInPlace(k, arr.clone()), "maxMinInPlace: " + where);
        assertEquals(expected, maxMinByRadixSort(k, arr), "maxMinByRadixSort: " + where);

        int[] selection = fairestSelection(k, arr);
        assertEquals(k, selection.length, "fairestSelection length: " + where);
        assertEquals(expected, (long) selection[k - 1] - selection[0], "fairestSelection spread: " + where);

        if (expected <= Integer.MAX_VALUE) {
            assertEquals((int) expected, maxMin(k, arr), "maxMin: " + where);
            assertEquals((int) expected, maxMin(k, boxed(arr)), "maxMin(List): " + where);
        } else {
            assertThrows(ArithmeticException.class, () -> maxMin(k, arr), "maxMin must not wrap: " + where);
        }
    }

    private static void assertRejected(int k, int[] arr, String where) {
        assertThrows(IllegalArgumentException.class, () -> maxMin(k, arr.clone()), "maxMin: " + where);
        assertThrows(IllegalArgumentException.class, () -> maxMin(k, boxed(arr)), "maxMin(List): " + where);
        assertThrows(IllegalArgumentException.class, () -> maxMinAsLong(k, arr.clone()), "maxMinAsLong: " + where);
        assertThrows(IllegalArgumentException.class, () -> maxMinInPlace(k, arr.clone()), "maxMinInPlace: " + where);
        assertThrows(IllegalArgumentException.class, () -> maxMinByRadixSort(k, arr.clone()),
                "maxMinByRadixSort: " + where);
        assertThrows(IllegalArgumentException.class, () -> fairestSelection(k, arr.clone()),
                "fairestSelection: " + where);
    }

    private static void assertIsSubMultisetOf(int[] selection, int[] arr, String where) {
        Map<Integer, Integer> available = new HashMap<>();
        for (int value : arr) {
            available.merge(value, 1, Integer::sum);
        }
        for (int value : selection) {
            Integer left = available.get(value);
            assertNotNull(left, value + " is not in the input: " + where);
            assertTrue(left > 0, "too many copies of " + value + ": " + where);
            available.put(value, left - 1);
        }
    }

    private static long bruteForceMinimumUnfairness(int k, int[] arr) {
        int n = arr.length;
        long best = Long.MAX_VALUE;

        for (int selection = 0; selection < (1 << n); selection++) {
            if (Integer.bitCount(selection) != k) continue;

            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                if ((selection & (1 << i)) != 0) {
                    min = Math.min(min, arr[i]);
                    max = Math.max(max, arr[i]);
                }
            }
            best = Math.min(best, max - min);
        }
        return best;
    }

    private static long scanSortedCopy(int k, int[] arr) {
        int[] sorted = sortedCopy(arr);
        long best = Long.MAX_VALUE;

        for (int start = 0; start + k <= sorted.length; start++) {
            best = Math.min(best, (long) sorted[start + k - 1] - sorted[start]);
        }
        return best;
    }

    private static int[] assortedValues(int n, int trial, Random random) {
        return switch (trial % 4) {
            case 0 -> random.ints(n, 0, 4).toArray();
            case 1 -> random.ints(n, -20, 21).toArray();
            case 2 -> random.ints(n, 0, 1_000_000_001).toArray();
            default -> random.ints(n)
                    .map(v -> switch (Math.floorMod(v, 4)) {
                        case 0 -> Integer.MIN_VALUE;
                        case 1 -> Integer.MAX_VALUE;
                        case 2 -> 0;
                        default -> v;
                    })
                    .toArray();
        };
    }

    private static int[] shuffledIdentity(int n, Random random) {
        int[] arr = new int[n];
        Arrays.setAll(arr, i -> i);

        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int held = arr[i];
            arr[i] = arr[j];
            arr[j] = held;
        }
        return arr;
    }

    private static int[] sortedCopy(int[] arr) {
        int[] sorted = arr.clone();
        Arrays.sort(sorted);
        return sorted;
    }

    private static List<Integer> boxed(int[] arr) {
        List<Integer> values = new ArrayList<>(arr.length);
        for (int value : arr) {
            values.add(value);
        }
        return values;
    }
}
