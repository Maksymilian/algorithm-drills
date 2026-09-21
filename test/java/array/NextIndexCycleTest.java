package array;

import array.NextIndexCycle.Cycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static array.NextIndexCycle.findCycle;
import static array.NextIndexCycle.findCycleByBrent;
import static array.NextIndexCycle.findCycleInConstantSpace;
import static array.NextIndexCycle.walkToFirstRepeat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every test is time-boxed, on a thread of its own, because the characteristic failure of a cycle
 * walk is not a wrong answer but an endless one - a pointer that advances at the wrong speed never
 * catches the one it is chasing, and no assertion is ever reached. Correct, the whole class runs in
 * well under a second; broken that way, it fails the build instead of stopping it.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class NextIndexCycleTest {

    /** The array from the prompt: 0 -> 3 -> 2 -> 1 -> 2 -> 1 ..., plus a self-loop at 4. */
    private static final int[] EXAMPLE = {3, 2, 1, 2, 4};

    private record Method(String name, BiFunction<int[], Integer, Cycle> find) {
    }

    private static final List<Method> METHODS = List.of(
            new Method("findCycle", NextIndexCycle::findCycle),
            new Method("findCycleInConstantSpace", NextIndexCycle::findCycleInConstantSpace),
            new Method("findCycleByBrent", NextIndexCycle::findCycleByBrent));

    static Stream<Arguments> knownCases() {
        return Stream.of(
                Arguments.of("the prompt's walk", EXAMPLE, 0, new Cycle(2, 2, 2)),

                // The same array entered elsewhere: from inside the cycle there is no tail at all,
                // and index 4 is a component of its own that index 0 can never reach.
                Arguments.of("one step from the cycle", EXAMPLE, 3, new Cycle(2, 1, 2)),
                Arguments.of("starting on the cycle", EXAMPLE, 2, new Cycle(2, 0, 2)),
                Arguments.of("starting on the cycle's other index", EXAMPLE, 1, new Cycle(1, 0, 2)),
                Arguments.of("starting on the self-loop", EXAMPLE, 4, new Cycle(4, 0, 1)),

                Arguments.of("the only index, pointing at itself", new int[]{0}, 0, new Cycle(0, 0, 1)),
                Arguments.of("two indices swapping", new int[]{1, 0}, 0, new Cycle(0, 0, 2)),
                Arguments.of("a tail into a self-loop", new int[]{1, 2, 3, 3}, 0, new Cycle(3, 3, 1)),
                Arguments.of("everything funnels into one index", new int[]{1, 1, 1}, 0, new Cycle(1, 1, 1)),
                Arguments.of("a tail into a three-cycle", new int[]{1, 2, 3, 4, 2}, 0, new Cycle(2, 2, 3)),

                // A permutation has no tail from anywhere: every index has exactly one predecessor,
                // so no walk can ever arrive at an index from two different places.
                Arguments.of("one cycle over everything", new int[]{1, 2, 3, 4, 0}, 0, new Cycle(0, 0, 5)),
                Arguments.of("one cycle over everything, from the middle", new int[]{1, 2, 3, 4, 0}, 3, new Cycle(3, 0, 5)),
                Arguments.of("a permutation of two cycles", new int[]{1, 0, 3, 4, 2}, 4, new Cycle(4, 0, 3)),

                // The longest tail an array of this size can have, and the shortest cycle.
                Arguments.of("maximal tail", new int[]{1, 2, 3, 4, 4}, 0, new Cycle(4, 4, 1))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownCases")
    void findsWhereTheCycleStartsAndHowLongItIs(String name, int[] tab, int start, Cycle expected) {
        for (Method method : METHODS) {
            assertEquals(expected, method.find().apply(tab, start), name + " via " + method.name());
        }
        assertIsTheCycle(tab, start, expected);
    }

    // --- the walk itself ---------------------------------------------------------------------------

    @Test
    void theWalkSpellsOutThePromptsExample() {
        assertArrayEquals(new int[]{0, 3, 2, 1, 2}, walkToFirstRepeat(EXAMPLE, 0));
        assertArrayEquals(new int[]{4, 4}, walkToFirstRepeat(EXAMPLE, 4));
        assertArrayEquals(new int[]{2, 1, 2}, walkToFirstRepeat(EXAMPLE, 2));
    }

    /** "When the cycle occurs" has two readings, and the record answers both without confusing them. */
    @Test
    void theFirstRepeatIsLaterThanTheEntry() {
        Cycle cycle = findCycle(EXAMPLE, 0);

        assertEquals(2, cycle.stepsBefore(), "the cycle is entered at step 2");
        assertEquals(4, cycle.firstRepeatStep(), "but the repetition is only visible at step 4");
        assertEquals(cycle.firstRepeatStep() + 1, walkToFirstRepeat(EXAMPLE, 0).length);
    }

    // --- the definition, exhaustively ---------------------------------------------------------------

    /**
     * The load-bearing test: <b>every</b> function on up to six indices, walked from every start -
     * 50 069 arrays, 296 675 walks - checked against the definition rather than against another
     * implementation, and cross-checked between the three.
     * <p>
     * Six is enough to contain every shape that exists: an empty tail and a maximal one, a
     * self-loop, a cycle over everything, several components, and unreachable indices.
     */
    @Test
    void everyFunctionOnUpToSixIndices() {
        for (int n = 1; n <= 6; n++) {
            int[] tab = new int[n];
            do {
                for (int start = 0; start < n; start++) {
                    assertEveryMethodIsRight(tab, start);
                }
            } while (nextFunction(tab));
        }
    }

    @Test
    void agreesOnRandomArrays() {
        Random random = new Random(20260919L);

        for (int trial = 0; trial < 2000; trial++) {
            int n = 1 + random.nextInt(3000);
            int[] tab = new int[n];
            Arrays.setAll(tab, i -> random.nextInt(n));

            assertEveryMethodIsRight(tab, random.nextInt(n));
        }
    }

    /**
     * Random arrays only ever produce a short rho - both halves average about
     * {@code sqrt(pi n / 8)}, so at n = 3000 the walk is some 30 steps long and the deep cases
     * never come up by chance. These two are built to be deep, and everything here is iterative,
     * so the depth costs no stack.
     */
    @Test
    void handlesARhoAsLongAsTheArray() {
        int n = 1_000_000;
        int tail = 800_000;

        int[] tailIntoCycle = new int[n];                          // 0 -> 1 -> ... -> n-1 -> 800 000
        Arrays.setAll(tailIntoCycle, i -> i + 1);
        tailIntoCycle[n - 1] = tail;

        assertEveryMethodFinds(new Cycle(tail, tail, n - tail), tailIntoCycle, 0);
        assertEveryMethodFinds(new Cycle(n - 1, 0, n - tail), tailIntoCycle, n - 1);
        assertEquals(n + 1, walkToFirstRepeat(tailIntoCycle, 0).length);

        int[] oneCycle = new int[n];                               // a single cycle over everything
        Arrays.setAll(oneCycle, i -> (i + 1) % n);

        assertEveryMethodFinds(new Cycle(0, 0, n), oneCycle, 0);
        assertEveryMethodFinds(new Cycle(12_345, 0, n), oneCycle, 12_345);
    }

    /**
     * Fifty million indices - 200 MB of ints, in the 512 MB heap this class is run in (see the
     * pom). Only the constant-space methods are asked: {@link NextIndexCycle#findCycle(int[], int)}
     * would want a second 200 MB for its stamps, which is most of what is left, and the point of
     * the other two is that they want nothing at all.
     * <p>
     * The one array is used twice, overwritten in place rather than allocated again, for the two
     * regimes that matter at this size. First a rho as long as the array, which walks all fifty
     * million indices - Floyd reads 2.1 * 10^8 times to do it. Then the same array filled at
     * random, where the rho is suddenly a few thousand steps in two hundred megabytes: that is the
     * normal case, and the reason a method that must first allocate and zero an array as long as
     * {@code tab} is the wrong one for it.
     */
    @Test
    void walksFiftyMillionIndices() {
        int n = 50_000_000;
        int tail = 40_000_000;

        int[] tab = new int[n];                                    // 200 MB
        Arrays.setAll(tab, i -> i + 1);
        tab[n - 1] = tail;

        assertConstantSpaceMethodsFind(new Cycle(tail, tail, n - tail), tab, 0);
        assertConstantSpaceMethodsFind(new Cycle(n - 1, 0, n - tail), tab, n - 1);

        Random random = new Random(20260919L);
        Arrays.setAll(tab, i -> random.nextInt(n));                // the same 200 MB, now arbitrary

        Cycle cycle = findCycleInConstantSpace(tab, 0);
        assertEquals(cycle, findCycleByBrent(tab, 0), "Brent disagrees on fifty million random indices");
        assertIsTheCycle(tab, 0, cycle);
        assertTrue(cycle.firstRepeatStep() < n / 1_000,
                () -> "a random rho should be a vanishing fraction of the array, but was " + cycle);
    }

    // --- the input contract --------------------------------------------------------------------------

    @Test
    void rejectsAnArrayThatCannotBeWalked() {
        for (Method method : METHODS) {
            BiFunction<int[], Integer, Cycle> find = method.find();

            assertThrows(IllegalArgumentException.class, () -> find.apply(null, 0), method.name() + " on null");
            assertThrows(IllegalArgumentException.class, () -> find.apply(new int[0], 0), method.name() + " on an empty array");
            assertThrows(IllegalArgumentException.class, () -> find.apply(new int[]{0}, -1), method.name() + " from -1");
            assertThrows(IllegalArgumentException.class, () -> find.apply(new int[]{0}, 1), method.name() + " from n");
            assertThrows(IllegalArgumentException.class, () -> find.apply(new int[]{0}, Integer.MIN_VALUE), method.name() + " from MIN_VALUE");
        }
        assertThrows(IllegalArgumentException.class, () -> walkToFirstRepeat(null, 0));
        assertThrows(IllegalArgumentException.class, () -> walkToFirstRepeat(new int[0], 0));
        assertThrows(IllegalArgumentException.class, () -> walkToFirstRepeat(new int[]{0}, 1));
    }

    /** An element that is not an index is an argument problem, not an {@code ArrayIndexOutOfBounds}. */
    @Test
    void rejectsAnElementThatIsNotAnIndex() {
        for (int[] tab : List.of(
                new int[]{1, 2},                                   // one past the end
                new int[]{1, -1},
                new int[]{1, Integer.MAX_VALUE},
                new int[]{1, Integer.MIN_VALUE},
                new int[]{1, 2, 3, 9, 0})) {                       // only reached after three steps

            for (Method method : METHODS) {
                assertThrows(IllegalArgumentException.class, () -> method.find().apply(tab, 0),
                        method.name() + " on " + Arrays.toString(tab));
            }
            assertThrows(IllegalArgumentException.class, () -> walkToFirstRepeat(tab, 0));
        }
    }

    /** The checking is done by the walk, so what the walk never reads is never judged. */
    @Test
    void ignoresNonsenseItNeverWalksOver() {
        int[] tab = {1, 0, 999, -7};

        assertEveryMethodFinds(new Cycle(0, 0, 2), tab, 0);
        assertArrayEquals(new int[]{0, 1, 0}, walkToFirstRepeat(tab, 0));
    }

    @Test
    void leavesTheArrayAlone() {
        int[] tab = EXAMPLE.clone();

        assertEveryMethodFinds(new Cycle(2, 2, 2), tab, 0);
        walkToFirstRepeat(tab, 0);

        assertArrayEquals(EXAMPLE, tab);
    }

    @Test
    void aCycleHasAtLeastOneIndexInIt() {
        assertThrows(IllegalArgumentException.class, () -> new Cycle(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cycle(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Cycle(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Cycle(0, -1, 1));
    }

    // --- helpers --------------------------------------------------------------------------------------

    /** For arrays large enough that a second one of the same size is not a reasonable thing to ask. */
    private static void assertConstantSpaceMethodsFind(Cycle expected, int[] tab, int start) {
        assertEquals(expected, findCycleInConstantSpace(tab, start), "Floyd from " + start);
        assertEquals(expected, findCycleByBrent(tab, start), "Brent from " + start);
        assertIsTheCycle(tab, start, expected);
    }

    private static void assertEveryMethodFinds(Cycle expected, int[] tab, int start) {
        for (Method method : METHODS) {
            assertEquals(expected, method.find().apply(tab, start), method.name() + " from " + start);
        }
        assertIsTheCycle(tab, start, expected);
    }

    /** All three methods, plus the walk, plus the definition - the whole check for one input. */
    private static void assertEveryMethodIsRight(int[] tab, int start) {
        Cycle cycle = findCycle(tab, start);
        assertIsTheCycle(tab, start, cycle);

        assertEquals(cycle, findCycleInConstantSpace(tab, start),
                () -> "Floyd disagrees" + describe(tab, start));
        assertEquals(cycle, findCycleByBrent(tab, start),
                () -> "Brent disagrees" + describe(tab, start));

        int[] walk = walkToFirstRepeat(tab, start);
        assertEquals(cycle.firstRepeatStep() + 1, walk.length, () -> "walk length" + describe(tab, start));
        assertEquals(start, walk[0], () -> "the walk starts elsewhere" + describe(tab, start));
        assertEquals(cycle.entry(), walk[cycle.stepsBefore()], () -> "the entry is not where the walk enters" + describe(tab, start));
        assertEquals(walk[cycle.stepsBefore()], walk[walk.length - 1], () -> "the walk does not close" + describe(tab, start));
        for (int step = 0; step + 1 < walk.length; step++) {
            assertEquals(tab[walk[step]], walk[step + 1], () -> "the walk does not follow tab" + describe(tab, start));
        }
    }

    /**
     * Checks a result against the definition, using neither implementation: the walk stands on
     * {@code entry} after {@code stepsBefore} steps, {@code length} is the <i>smallest</i> number
     * of steps returning {@code entry} to itself, and nothing the walk touched before then is on
     * that cycle - which is what makes {@code stepsBefore} the first moment it could be entered.
     * <p>
     * Those three together admit exactly one answer, so this is an oracle and not a weaker
     * restatement: a cycle really reached, really closed at that length, and not reached earlier.
     */
    private static void assertIsTheCycle(int[] tab, int start, Cycle cycle) {
        int index = start;
        for (int step = 0; step < cycle.stepsBefore(); step++) {
            index = tab[index];
        }
        assertEquals(cycle.entry(), index,
                () -> "the walk is not at the entry after " + cycle.stepsBefore() + " steps" + describe(tab, start));

        boolean[] onCycle = new boolean[tab.length];
        for (int lap = 0; lap < cycle.length(); lap++) {
            assertFalse(onCycle[index], () -> "the cycle closes before its claimed length" + describe(tab, start));
            onCycle[index] = true;
            index = tab[index];
        }
        assertEquals(cycle.entry(), index,
                () -> "walking the claimed length does not return to the entry" + describe(tab, start));

        index = start;
        for (int step = 0; step < cycle.stepsBefore(); step++) {
            assertFalse(onCycle[index], () -> "the cycle was already entered before step " + cycle.stepsBefore() + describe(tab, start));
            index = tab[index];
        }
    }

    /** Counts {@code tab} as an n-digit number in base n: every function on n indices, once. */
    private static boolean nextFunction(int[] tab) {
        for (int i = 0; i < tab.length; i++) {
            if (++tab[i] < tab.length) return true;
            tab[i] = 0;
        }
        return false;
    }

    /** Only built when an assertion has already failed. */
    private static String describe(int[] tab, int start) {
        return " in " + Arrays.toString(tab) + " from " + start;
    }
}
