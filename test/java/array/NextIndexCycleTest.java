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

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class NextIndexCycleTest {

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

                Arguments.of("one step from the cycle", EXAMPLE, 3, new Cycle(2, 1, 2)),
                Arguments.of("starting on the cycle", EXAMPLE, 2, new Cycle(2, 0, 2)),
                Arguments.of("starting on the cycle's other index", EXAMPLE, 1, new Cycle(1, 0, 2)),
                Arguments.of("starting on the self-loop", EXAMPLE, 4, new Cycle(4, 0, 1)),

                Arguments.of("the only index, pointing at itself", new int[]{0}, 0, new Cycle(0, 0, 1)),
                Arguments.of("two indices swapping", new int[]{1, 0}, 0, new Cycle(0, 0, 2)),
                Arguments.of("a tail into a self-loop", new int[]{1, 2, 3, 3}, 0, new Cycle(3, 3, 1)),
                Arguments.of("everything funnels into one index", new int[]{1, 1, 1}, 0, new Cycle(1, 1, 1)),
                Arguments.of("a tail into a three-cycle", new int[]{1, 2, 3, 4, 2}, 0, new Cycle(2, 2, 3)),

                Arguments.of("one cycle over everything", new int[]{1, 2, 3, 4, 0}, 0, new Cycle(0, 0, 5)),
                Arguments.of("one cycle over everything, from the middle", new int[]{1, 2, 3, 4, 0}, 3, new Cycle(3, 0, 5)),
                Arguments.of("a permutation of two cycles", new int[]{1, 0, 3, 4, 2}, 4, new Cycle(4, 0, 3)),

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

    @Test
    void theWalkSpellsOutThePromptsExample() {
        assertArrayEquals(new int[]{0, 3, 2, 1, 2}, walkToFirstRepeat(EXAMPLE, 0));
        assertArrayEquals(new int[]{4, 4}, walkToFirstRepeat(EXAMPLE, 4));
        assertArrayEquals(new int[]{2, 1, 2}, walkToFirstRepeat(EXAMPLE, 2));
    }

    @Test
    void theFirstRepeatIsLaterThanTheEntry() {
        Cycle cycle = findCycle(EXAMPLE, 0);

        assertEquals(2, cycle.stepsBefore(), "the cycle is entered at step 2");
        assertEquals(4, cycle.firstRepeatStep(), "but the repetition is only visible at step 4");
        assertEquals(cycle.firstRepeatStep() + 1, walkToFirstRepeat(EXAMPLE, 0).length);
    }

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

    @Test
    void handlesARhoAsLongAsTheArray() {
        int n = 1_000_000;
        int tail = 800_000;

        int[] tailIntoCycle = new int[n];
        Arrays.setAll(tailIntoCycle, i -> i + 1);
        tailIntoCycle[n - 1] = tail;

        assertEveryMethodFinds(new Cycle(tail, tail, n - tail), tailIntoCycle, 0);
        assertEveryMethodFinds(new Cycle(n - 1, 0, n - tail), tailIntoCycle, n - 1);
        assertEquals(n + 1, walkToFirstRepeat(tailIntoCycle, 0).length);

        int[] oneCycle = new int[n];
        Arrays.setAll(oneCycle, i -> (i + 1) % n);

        assertEveryMethodFinds(new Cycle(0, 0, n), oneCycle, 0);
        assertEveryMethodFinds(new Cycle(12_345, 0, n), oneCycle, 12_345);
    }

    @Test
    void walksFiftyMillionIndices() {
        int n = 50_000_000;
        int tail = 40_000_000;

        int[] tab = new int[n];
        Arrays.setAll(tab, i -> i + 1);
        tab[n - 1] = tail;

        assertConstantSpaceMethodsFind(new Cycle(tail, tail, n - tail), tab, 0);
        assertConstantSpaceMethodsFind(new Cycle(n - 1, 0, n - tail), tab, n - 1);

        Random random = new Random(20260919L);
        Arrays.setAll(tab, i -> random.nextInt(n));

        Cycle cycle = findCycleInConstantSpace(tab, 0);
        assertEquals(cycle, findCycleByBrent(tab, 0), "Brent disagrees on fifty million random indices");
        assertIsTheCycle(tab, 0, cycle);
        assertTrue(cycle.firstRepeatStep() < n / 1_000,
                () -> "a random rho should be a vanishing fraction of the array, but was " + cycle);
    }

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

    @Test
    void rejectsAnElementThatIsNotAnIndex() {
        for (int[] tab : List.of(
                new int[]{1, 2},
                new int[]{1, -1},
                new int[]{1, Integer.MAX_VALUE},
                new int[]{1, Integer.MIN_VALUE},
                new int[]{1, 2, 3, 9, 0})) {

            for (Method method : METHODS) {
                assertThrows(IllegalArgumentException.class, () -> method.find().apply(tab, 0),
                        method.name() + " on " + Arrays.toString(tab));
            }
            assertThrows(IllegalArgumentException.class, () -> walkToFirstRepeat(tab, 0));
        }
    }

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

    private static boolean nextFunction(int[] tab) {
        for (int i = 0; i < tab.length; i++) {
            if (++tab[i] < tab.length) return true;
            tab[i] = 0;
        }
        return false;
    }

    private static String describe(int[] tab, int start) {
        return " in " + Arrays.toString(tab) + " from " + start;
    }
}
