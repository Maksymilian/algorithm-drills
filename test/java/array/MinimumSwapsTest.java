package array;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static array.MinimumSwaps.minimumSwaps;
import static array.MinimumSwaps.minimumSwapsByUnionFind;
import static array.MinimumSwaps.minimumSwapsInPlace;
import static array.MinimumSwaps.minimumSwapsMarkingSigns;
import static array.MinimumSwaps.minimumSwapsOfAnyDistinctValues;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class MinimumSwapsTest {

    static Stream<Arguments> knownCases() {
        return Stream.of(
                Arguments.of("sample 0", new int[]{4, 3, 1, 2}, 3),
                Arguments.of("sample 1", new int[]{2, 3, 4, 1, 5}, 3),
                Arguments.of("sample 2", new int[]{1, 3, 5, 2, 4, 6, 7}, 3),
                Arguments.of("the worked example", new int[]{7, 1, 3, 2, 4, 5, 6}, 5),

                Arguments.of("empty", new int[]{}, 0),
                Arguments.of("single element", new int[]{1}, 0),
                Arguments.of("already sorted", new int[]{1, 2, 3, 4, 5}, 0),
                Arguments.of("one transposition", new int[]{2, 1}, 1),
                Arguments.of("one transposition among fixed points", new int[]{1, 2, 5, 4, 3, 6}, 1),
                Arguments.of("two disjoint transpositions", new int[]{2, 1, 4, 3}, 2),
                Arguments.of("a 3-cycle costs 2", new int[]{2, 3, 1}, 2),
                Arguments.of("the other 3-cycle also costs 2", new int[]{3, 1, 2}, 2),

                Arguments.of("reversed, n=3", new int[]{3, 2, 1}, 1),
                Arguments.of("reversed, n=4", new int[]{4, 3, 2, 1}, 2),
                Arguments.of("reversed, n=5", new int[]{5, 4, 3, 2, 1}, 2),
                Arguments.of("reversed, n=8", new int[]{8, 7, 6, 5, 4, 3, 2, 1}, 4),

                Arguments.of("a single n-cycle, n=6", new int[]{2, 3, 4, 5, 6, 1}, 5),
                Arguments.of("a single n-cycle the other way, n=6", new int[]{6, 1, 2, 3, 4, 5}, 5)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownCases")
    void countsTheMinimumSwaps(String name, int[] arr, int expected) {
        assertEveryMethodReturns(expected, arr, name);
    }

    @Test
    void inPlaceLeavesTheArraySorted() {
        int[] arr = {7, 1, 3, 2, 4, 5, 6};

        assertEquals(5, minimumSwapsInPlace(arr));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7}, arr);
    }

    @Test
    void theOtherMethodsLeaveTheArrayAlone() {
        int[] arr = {4, 3, 1, 2};
        String original = Arrays.toString(arr);

        minimumSwaps(arr);
        assertEquals(original, Arrays.toString(arr), "minimumSwaps");
        minimumSwapsMarkingSigns(arr);
        assertEquals(original, Arrays.toString(arr), "minimumSwapsMarkingSigns");
        minimumSwapsByUnionFind(arr);
        assertEquals(original, Arrays.toString(arr), "minimumSwapsByUnionFind");
        minimumSwapsOfAnyDistinctValues(arr);
        assertEquals(original, Arrays.toString(arr), "minimumSwapsOfAnyDistinctValues");
    }

    @Test
    void markingSignsRestoresTheArrayEvenWhenItRejectsTheInput() {
        int[] arr = {3, 3, 1};

        assertThrows(IllegalArgumentException.class, () -> minimumSwapsMarkingSigns(arr));
        assertArrayEquals(new int[]{3, 3, 1}, arr);
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> minimumSwaps(null));
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsInPlace(null));
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsMarkingSigns(null));
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsByUnionFind(null));
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsOfAnyDistinctValues(null));
    }

    @Test
    void rejectsValuesOutsideOneToN() {
        for (int[] arr : List.of(
                new int[]{0, 2},
                new int[]{1, 3},
                new int[]{2, 3},
                new int[]{-1, 2},
                new int[]{1, 2, 7},
                new int[]{Integer.MIN_VALUE, 2},
                new int[]{1, Integer.MAX_VALUE})) {

            assertRejected(arr, "out of range " + Arrays.toString(arr));
        }
    }

    @Test
    void rejectsDuplicates() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int[] arr : List.of(
                    new int[]{1, 1},
                    new int[]{2, 2},
                    new int[]{1, 1, 3},
                    new int[]{2, 2, 1},
                    new int[]{3, 3, 1},
                    new int[]{1, 2, 2, 4},
                    new int[]{2, 1, 4, 4})) {

                assertRejected(arr, "duplicate in " + Arrays.toString(arr));
            }
        });
    }

    @Test
    void theGeneralMethodRejectsOnlyDuplicates() {
        assertEquals(3, minimumSwapsOfAnyDistinctValues(new int[]{40, -7, 0, 13}));
        assertThrows(IllegalArgumentException.class,
                () -> minimumSwapsOfAnyDistinctValues(new int[]{5, 5}));
    }

    @Test
    void matchesBreadthFirstSearchOverEveryPermutationUpToEightElements() {
        for (int n = 0; n <= 8; n++) {
            Map<Long, Integer> distances = shortestSwapDistances(n);
            assertEquals(factorial(n), distances.size(), "permutations of " + n + " elements reached");

            for (Map.Entry<Long, Integer> reached : distances.entrySet()) {
                int[] arr = decode(reached.getKey(), n);
                assertEveryMethodReturns(reached.getValue(), arr, Arrays.toString(arr));
            }
        }
    }

    @Test
    void matchesPermutationsBuiltFromAKnownCycleStructure() {
        Random random = new Random(20260914L);

        for (int trial = 0; trial < 300; trial++) {
            int n = random.nextInt(200);
            int[] cycleLengths = randomPartitionOf(n, random);
            int[] arr = permutationWithCycles(cycleLengths, random);

            assertEveryMethodReturns(n - cycleLengths.length, arr,
                    "n=" + n + " cycles=" + Arrays.toString(cycleLengths));
        }
    }

    @Test
    void matchesNaiveSelectionSortOnRandomPermutations() {
        Random random = new Random(7L);

        for (int trial = 0; trial < 300; trial++) {
            int[] arr = randomPermutation(random.nextInt(120), random);
            assertEveryMethodReturns(swapsPerformedBySelectionSort(arr), arr, Arrays.toString(arr));
        }
    }

    @Test
    void relabellingTheValuesMonotonicallyDoesNotChangeTheAnswer() {
        Random random = new Random(11L);

        for (int trial = 0; trial < 300; trial++) {
            int[] arr = randomPermutation(1 + random.nextInt(80), random);

            int[] relabelled = new int[arr.length];
            for (int i = 0; i < arr.length; i++) {
                relabelled[i] = 1_000_000 * arr[i] - 500_000_003;
            }

            assertEquals(minimumSwaps(arr), minimumSwapsOfAnyDistinctValues(relabelled),
                    Arrays.toString(arr));
        }
    }

    @Test
    void countsTenMillionElementsInASingleCycle() {
        int n = 10_000_000;
        int[] arr = new int[n];
        Arrays.setAll(arr, i -> (i + 1) % n + 1);

        assertEquals(n - 1, minimumSwaps(arr));
        assertEquals(n - 1, minimumSwapsMarkingSigns(arr));
        assertEquals(n - 1, minimumSwapsByUnionFind(arr));
        assertEquals(n - 1, minimumSwapsInPlace(arr.clone()));
    }

    @Test
    void countsTenMillionElementsInPairs() {
        int n = 10_000_000;
        int[] arr = new int[n];
        Arrays.setAll(arr, i -> (i % 2 == 0 ? i + 2 : i));

        assertEquals(n / 2, minimumSwaps(arr));
        assertEquals(n / 2, minimumSwapsMarkingSigns(arr));
        assertEquals(n / 2, minimumSwapsByUnionFind(arr));

        int[] sorted = arr.clone();
        assertEquals(n / 2, minimumSwapsInPlace(sorted));
        assertEquals(n, sorted[n - 1]);
    }

    @Test
    void countsTenMillionArbitraryDistinctValues() {
        int n = 10_000_000;
        int[] values = new int[n];
        Arrays.setAll(values, i -> Integer.MIN_VALUE + 3 * ((i + 1) % n));

        assertEquals(n - 1, minimumSwapsOfAnyDistinctValues(values));
    }

    private static void assertEveryMethodReturns(int expected, int[] arr, String where) {
        assertEquals(expected, minimumSwaps(arr.clone()), "minimumSwaps: " + where);
        assertEquals(expected, minimumSwapsInPlace(arr.clone()), "minimumSwapsInPlace: " + where);
        assertEquals(expected, minimumSwapsMarkingSigns(arr.clone()), "minimumSwapsMarkingSigns: " + where);
        assertEquals(expected, minimumSwapsByUnionFind(arr.clone()), "minimumSwapsByUnionFind: " + where);
        assertEquals(expected, minimumSwapsOfAnyDistinctValues(arr.clone()),
                "minimumSwapsOfAnyDistinctValues: " + where);
    }

    private static void assertRejected(int[] arr, String where) {
        assertThrows(IllegalArgumentException.class, () -> minimumSwaps(arr.clone()), "minimumSwaps: " + where);
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsInPlace(arr.clone()),
                "minimumSwapsInPlace: " + where);
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsMarkingSigns(arr.clone()),
                "minimumSwapsMarkingSigns: " + where);
        assertThrows(IllegalArgumentException.class, () -> minimumSwapsByUnionFind(arr.clone()),
                "minimumSwapsByUnionFind: " + where);
    }

    private static Map<Long, Integer> shortestSwapDistances(int n) {
        int[] sorted = new int[n];
        Arrays.setAll(sorted, i -> i + 1);

        Map<Long, Integer> distances = new HashMap<>();
        Deque<int[]> frontier = new ArrayDeque<>();
        distances.put(encode(sorted), 0);
        frontier.add(sorted);

        while (!frontier.isEmpty()) {
            int[] current = frontier.remove();
            int distance = distances.get(encode(current));

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int[] next = current.clone();
                    next[i] = current[j];
                    next[j] = current[i];

                    if (distances.putIfAbsent(encode(next), distance + 1) == null) {
                        frontier.add(next);
                    }
                }
            }
        }
        return distances;
    }

    private static long encode(int[] permutation) {
        long packed = 0;
        for (int value : permutation) {
            packed = (packed << 4) | value;
        }
        return packed;
    }

    private static int[] decode(long packed, int n) {
        int[] permutation = new int[n];
        for (int i = n - 1; i >= 0; i--) {
            permutation[i] = (int) (packed & 0xF);
            packed >>>= 4;
        }
        return permutation;
    }

    private static long factorial(int n) {
        long product = 1;
        for (int i = 2; i <= n; i++) {
            product *= i;
        }
        return product;
    }

    private static int swapsPerformedBySelectionSort(int[] arr) {
        int[] working = arr.clone();
        int swaps = 0;

        for (int i = 0; i < working.length; i++) {
            int smallest = i;
            for (int j = i + 1; j < working.length; j++) {
                if (working[j] < working[smallest]) smallest = j;
            }
            if (smallest != i) {
                int held = working[i];
                working[i] = working[smallest];
                working[smallest] = held;
                swaps++;
            }
        }
        return swaps;
    }

    private static int[] randomPartitionOf(int n, Random random) {
        int[] lengths = new int[n];
        int count = 0;

        for (int remaining = n; remaining > 0; ) {
            int length = 1 + random.nextInt(Math.min(remaining, 6));
            lengths[count++] = length;
            remaining -= length;
        }
        return Arrays.copyOf(lengths, count);
    }

    private static int[] permutationWithCycles(int[] cycleLengths, Random random) {
        int n = Arrays.stream(cycleLengths).sum();
        List<Integer> positions = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions, random);

        int[] arr = new int[n];
        for (int start = 0, cycle = 0; cycle < cycleLengths.length; cycle++) {
            int length = cycleLengths[cycle];
            for (int offset = 0; offset < length; offset++) {
                int from = positions.get(start + offset);
                int to = positions.get(start + (offset + 1) % length);
                arr[from] = to + 1;
            }
            start += length;
        }
        return arr;
    }

    private static int[] randomPermutation(int n, Random random) {
        int[] arr = new int[n];
        Arrays.setAll(arr, i -> i + 1);

        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int held = arr[i];
            arr[i] = arr[j];
            arr[j] = held;
        }
        return arr;
    }
}
