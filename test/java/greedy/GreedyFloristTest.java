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

import static greedy.GreedyFlorist.getMinimumCost;
import static greedy.GreedyFlorist.getMinimumCostAsLong;
import static greedy.GreedyFlorist.getMinimumCostByCounting;
import static greedy.GreedyFlorist.getMinimumCostInPlace;
import static greedy.GreedyFlorist.purchasePlan;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyFloristTest {

    static Stream<Arguments> knownCases() {
        return Stream.of(
                Arguments.of("sample 0", 3, new int[]{2, 5, 6}, 13L),
                Arguments.of("sample 1", 2, new int[]{2, 5, 6}, 15L),
                Arguments.of("sample 2", 3, new int[]{1, 3, 5, 7, 9}, 29L),

                Arguments.of("no flowers", 3, new int[]{}, 0L),
                Arguments.of("one flower, one friend", 1, new int[]{7}, 7L),
                Arguments.of("more friends than flowers", 5, new int[]{1, 2, 3}, 6L),
                Arguments.of("exactly as many friends as flowers", 3, new int[]{1, 2, 3}, 6L),

                // k = 1 is the worst case: multipliers 1, 2, 3, ... all fall on one buyer.
                Arguments.of("one friend buys everything", 1, new int[]{1, 2, 3}, 10L),
                Arguments.of("one friend, descending input", 1, new int[]{3, 2, 1}, 10L),

                Arguments.of("all equal", 2, new int[]{5, 5, 5, 5}, 30L),
                Arguments.of("free flowers cost nothing whenever bought", 2, new int[]{0, 0, 5}, 5L),
                Arguments.of("all free", 2, new int[]{0, 0, 0}, 0L),

                // Two full blocks then a partial one: k = 2 over 5 flowers is x1 x1 x2 x2 x3.
                Arguments.of("partial last block", 2, new int[]{10, 20, 30, 40, 50},
                        50L + 40L + 2 * 30L + 2 * 20L + 3 * 10L)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownCases")
    void findsTheMinimumCost(String name, int k, int[] c, long expected) {
        assertEveryMethodReturns(expected, k, c, name);
    }

    // --- what each method promises about the array afterwards ------------------------------------

    @Test
    void inPlaceLeavesTheArraySorted() {
        int[] c = {2, 5, 6};

        assertEquals(15L, getMinimumCostInPlace(2, c));
        assertArrayEquals(new int[]{2, 5, 6}, c);            // already ascending

        int[] shuffled = {6, 2, 5};
        assertEquals(15L, getMinimumCostInPlace(2, shuffled));
        assertArrayEquals(new int[]{2, 5, 6}, shuffled);
    }

    @Test
    void theOtherMethodsLeaveTheArrayAlone() {
        int[] c = {6, 2, 5};
        String original = Arrays.toString(c);

        getMinimumCost(2, c);
        assertEquals(original, Arrays.toString(c), "getMinimumCost");
        getMinimumCostAsLong(2, c);
        assertEquals(original, Arrays.toString(c), "getMinimumCostAsLong");
        getMinimumCostByCounting(2, c);
        assertEquals(original, Arrays.toString(c), "getMinimumCostByCounting");
        purchasePlan(2, c);
        assertEquals(original, Arrays.toString(c), "purchasePlan");
    }

    @Test
    void inPlaceRejectsBadInputWithoutTouchingTheArray() {
        int[] c = {3, 1, 2};

        assertThrows(IllegalArgumentException.class, () -> getMinimumCostInPlace(0, c));
        assertArrayEquals(new int[]{3, 1, 2}, c);

        int[] withNegative = {3, -1, 2};
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostInPlace(2, withNegative));
        assertArrayEquals(new int[]{3, -1, 2}, withNegative);
    }

    // --- the witness --------------------------------------------------------------------------------

    @Test
    void thePlanIsThePurchaseItPrices() {
        Random random = new Random(20260914L);

        for (int trial = 0; trial < 400; trial++) {
            int n = random.nextInt(30);
            int k = 1 + random.nextInt(8);
            int[] c = random.ints(n, 0, 100).toArray();
            String where = "k=" + k + " " + Arrays.toString(c);

            int[][] plan = purchasePlan(k, c);

            assertEquals(k, plan.length, "one row per friend: " + where);
            assertEquals(getMinimumCostAsLong(k, c), costOfPlan(plan), "plan costs the answer: " + where);
            assertHoldsEveryFlowerOnce(plan, c, where);

            for (int[] forOneFriend : plan) {
                for (int j = 1; j < forOneFriend.length; j++) {
                    assertTrue(forOneFriend[j - 1] >= forOneFriend[j],
                            "each friend buys dearest first: " + Arrays.toString(forOneFriend) + " " + where);
                }
            }
        }
    }

    @Test
    void thePlanGivesIdleFriendsEmptyRows() {
        int[][] plan = purchasePlan(5, new int[]{1, 2, 3});

        assertEquals(5, plan.length);
        assertEquals(6L, costOfPlan(plan));
        assertArrayEquals(new int[]{3}, plan[0]);
        assertEquals(0, plan[3].length);
        assertEquals(0, plan[4].length);
    }

    /** Sample 1's own walkthrough: one friend takes two flowers, dearest first. */
    @Test
    void thePlanMatchesTheWorkedSample() {
        int[][] plan = purchasePlan(2, new int[]{2, 5, 6});

        assertEquals(15L, costOfPlan(plan));
        assertEquals(2, plan.length);
        assertEquals(2, plan[0].length, "one friend buys two flowers");
        assertEquals(1, plan[1].length, "the other buys one");
        assertEquals(6, plan[0][0], "and the busy friend starts with the dearest flower");
    }

    // --- the input contract --------------------------------------------------------------------------

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> getMinimumCost(1, (int[]) null));
        assertThrows(IllegalArgumentException.class, () -> getMinimumCost(1, (List<Integer>) null));
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostAsLong(1, null));
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostInPlace(1, null));
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostByCounting(1, null));
        assertThrows(IllegalArgumentException.class, () -> purchasePlan(1, null));
    }

    @Test
    void rejectsAGroupWithNoFriendsInIt() {
        for (int k : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertRejected(k, new int[]{1, 2, 3}, "k=" + k);
        }
    }

    @Test
    void rejectsNegativePrices() {
        assertRejected(2, new int[]{-1}, "a single negative");
        assertRejected(2, new int[]{1, 2, -3}, "a negative among positives");
        assertRejected(1, new int[]{Integer.MIN_VALUE}, "the most negative there is");
    }

    @Test
    void rejectsANullPriceInTheList() {
        List<Integer> withNull = new ArrayList<>(List.of(1, 2));
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> getMinimumCost(2, withNull));
    }

    /** The histogram is the one method with a precondition on the values, so it says so. */
    @Test
    void countingRejectsPricesTooLargeToBucket() {
        assertEquals(10_000_000L, getMinimumCostByCounting(1, new int[]{10_000_000}));
        assertThrows(IllegalArgumentException.class,
                () -> getMinimumCostByCounting(1, new int[]{10_000_001}));

        // the others have no such limit
        assertEquals(10_000_001L, getMinimumCostAsLong(1, new int[]{10_000_001}));
    }

    /**
     * The platform returns {@code int}, and the problem's own bounds overrun it: a hundred flowers
     * at a million each, bought by one friend, is 10<sup>6</sup> * (1 + 2 + ... + 100).
     */
    @Test
    void theTotalOutgrowsAnIntWithinTheStatedBounds() {
        int[] c = new int[100];
        Arrays.fill(c, 1_000_000);

        assertEquals(5_050_000_000L, getMinimumCostAsLong(1, c));
        assertEquals(5_050_000_000L, getMinimumCostByCounting(1, c));
        assertEquals(5_050_000_000L, getMinimumCostInPlace(1, c.clone()));
        assertThrows(ArithmeticException.class, () -> getMinimumCost(1, c));
    }

    /**
     * The total outgrowing an {@code int} is not the only overflow on offer: a <i>single</i>
     * flower's price times its multiplier can pass {@link Integer#MAX_VALUE} on its own, which it
     * does here from the 2148th purchase onwards. The widening therefore has to happen at the
     * multiplication and not merely at the accumulation - {@code total += price * multiplier} with
     * {@code total} a {@code long} would still wrap the product before adding it.
     */
    @Test
    void oneFlowersCostAloneCanOutgrowAnInt() {
        int n = 3_000;
        int[] c = new int[n];
        Arrays.fill(c, 1_000_000);                         // one friend, so multipliers run 1..3000

        long expected = 1_000_000L * n * (n + 1) / 2;
        assertEquals(4_501_500_000_000L, expected);
        assertTrue(1_000_000L * n > Integer.MAX_VALUE, "the last flower alone must overflow an int");

        assertEquals(expected, getMinimumCostAsLong(1, c));
        assertEquals(expected, getMinimumCostByCounting(1, c));
        assertEquals(expected, getMinimumCostInPlace(1, c.clone()));
        assertThrows(ArithmeticException.class, () -> getMinimumCost(1, c));
    }

    // --- cross-checks against independent oracles -------------------------------------------------------

    /**
     * The load-bearing test. Every method assumes the multipliers are forced into blocks of
     * {@code k} and that the dearest flower takes the smallest one; this enumerates every way to
     * assign flowers to friends and assumes neither.
     * <p>
     * Within one friend the multipliers really are forced to {@code 1 .. t}, so the oracle is free
     * to pair that friend's own flowers dearest-first - which is why walking a descending array is
     * enough to evaluate an assignment, and why the oracle stays O(n) per assignment.
     */
    @Test
    void matchesBruteForceOverEveryAssignmentOfFlowersToFriends() {
        Random random = new Random(20260914L);

        for (int n = 0; n <= 7; n++) {
            for (int k = 1; k <= 4; k++) {
                for (int trial = 0; trial < 6; trial++) {
                    int[] c = trial % 2 == 0
                            ? random.ints(n, 0, 5).toArray()      // ties everywhere
                            : random.ints(n, 0, 1000).toArray();

                    assertEveryMethodReturns(bruteForceMinimumCost(k, c), k, c,
                            "k=" + k + " " + Arrays.toString(c));
                }
            }
        }
    }

    /**
     * Why negative prices are rejected rather than answered. The forced-multiplier argument assumes
     * a small multiplier is desirable; below zero the opposite holds, and a single friend buying
     * both flowers beats the blocks of {@code k} that every method here would hand out.
     */
    @Test
    void negativePricesWouldMakeTheGreedyAnswerWrong() {
        int[] c = {-10, 1};

        assertEquals(-19L, bruteForceMinimumCost(2, c));          // one friend buys both: 1 - 20
        assertEquals(-9L, costOfBlocksOfK(2, c));                 // what the greedy rule would say
        assertTrue(bruteForceMinimumCost(2, c) < costOfBlocksOfK(2, c));

        assertRejected(2, c, "the counterexample itself");         // so no method answers it
    }

    /** Larger inputs, against the formula applied to an independently sorted copy. */
    @Test
    void matchesTheBlockFormulaOnRandomInput() {
        Random random = new Random(11L);

        for (int trial = 0; trial < 400; trial++) {
            int n = random.nextInt(400);
            int k = 1 + random.nextInt(50);
            int[] c = random.ints(n, 0, 1_000_001).toArray();

            assertEveryMethodReturns(costOfBlocksOfK(k, c), k, c, "k=" + k + " n=" + n);
        }
    }

    // --- large inputs ---------------------------------------------------------------------------------

    /**
     * A million identical flowers in a thousand blocks of a thousand, so the total is known in
     * closed form: {@code 7 * 1000 * (1 + 2 + ... + 1000)}. It also overruns an {@code int}.
     */
    @Test
    void pricesAMillionFlowers() {
        int n = 1_000_000;
        int k = 1_000;
        int[] c = new int[n];
        Arrays.fill(c, 7);

        long expected = 7L * k * (1_000L * 1_001L / 2);
        assertEquals(3_503_500_000L, expected);

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            assertEquals(expected, getMinimumCostAsLong(k, c));
            assertEquals(expected, getMinimumCostByCounting(k, c));
            assertEquals(expected, getMinimumCostInPlace(k, c.clone()));
        });
    }

    /** Five million flowers priced across the problem's whole range, methods against each other. */
    @Test
    void pricesFiveMillionFlowersEveryWhichWay() {
        int n = 5_000_000;
        int k = 4_096;
        int[] c = new Random(3L).ints(n, 0, 1_000_001).toArray();

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            long expected = getMinimumCostAsLong(k, c);

            assertEquals(expected, getMinimumCostByCounting(k, c));
            assertEquals(expected, getMinimumCostInPlace(k, c.clone()));
            assertEquals(expected, costOfPlan(purchasePlan(k, c)));
        });
    }

    // --- helpers ----------------------------------------------------------------------------------------

    private static void assertEveryMethodReturns(long expected, int k, int[] c, String where) {
        assertEquals(expected, getMinimumCostAsLong(k, c), "getMinimumCostAsLong: " + where);
        assertEquals(expected, getMinimumCostInPlace(k, c.clone()), "getMinimumCostInPlace: " + where);
        assertEquals(expected, getMinimumCostByCounting(k, c), "getMinimumCostByCounting: " + where);
        assertEquals(expected, costOfPlan(purchasePlan(k, c)), "purchasePlan: " + where);

        if (expected <= Integer.MAX_VALUE) {
            assertEquals((int) expected, getMinimumCost(k, c), "getMinimumCost: " + where);
            assertEquals((int) expected, getMinimumCost(k, boxed(c)), "getMinimumCost(List): " + where);
        } else {
            assertThrows(ArithmeticException.class, () -> getMinimumCost(k, c),
                    "getMinimumCost must not wrap: " + where);
        }
    }

    private static void assertRejected(int k, int[] c, String where) {
        assertThrows(IllegalArgumentException.class, () -> getMinimumCost(k, c.clone()), "getMinimumCost: " + where);
        assertThrows(IllegalArgumentException.class, () -> getMinimumCost(k, boxed(c)),
                "getMinimumCost(List): " + where);
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostAsLong(k, c.clone()),
                "getMinimumCostAsLong: " + where);
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostInPlace(k, c.clone()),
                "getMinimumCostInPlace: " + where);
        assertThrows(IllegalArgumentException.class, () -> getMinimumCostByCounting(k, c.clone()),
                "getMinimumCostByCounting: " + where);
        assertThrows(IllegalArgumentException.class, () -> purchasePlan(k, c.clone()),
                "purchasePlan: " + where);
    }

    private static void assertHoldsEveryFlowerOnce(int[][] plan, int[] c, String where) {
        Map<Integer, Integer> remaining = new HashMap<>();
        for (int price : c) {
            remaining.merge(price, 1, Integer::sum);
        }
        int planted = 0;
        for (int[] forOneFriend : plan) {
            for (int price : forOneFriend) {
                planted++;
                Integer left = remaining.merge(price, -1, Integer::sum);
                assertTrue(left >= 0, "plan buys more " + price + "s than exist: " + where);
            }
        }
        assertEquals(c.length, planted, "plan must buy every flower: " + where);
    }

    // --- reference implementations --------------------------------------------------------------------

    /**
     * The definition: every assignment of flowers to friends, {@code k^n} of them. Exponential, so
     * small {@code n} only.
     */
    private static long bruteForceMinimumCost(int k, int[] c) {
        int[] descending = c.clone();
        Arrays.sort(descending);
        reverse(descending);

        int n = descending.length;
        long assignments = 1;
        for (int i = 0; i < n; i++) {
            assignments *= k;
        }

        long best = Long.MAX_VALUE;
        int[] boughtBy = new int[k];
        for (long code = 0; code < assignments; code++) {
            Arrays.fill(boughtBy, 0);

            long cost = 0;
            long remaining = code;
            for (int i = 0; i < n; i++) {                   // flowers visited dearest first, so each
                int friend = (int) (remaining % k);         // friend's own multipliers come out 1,2,3..
                remaining /= k;
                cost += (long) descending[i] * ++boughtBy[friend];
            }
            best = Math.min(best, cost);
        }
        return n == 0 ? 0L : best;
    }

    /** The block formula written out again, over an independently sorted copy. */
    private static long costOfBlocksOfK(int k, int[] c) {
        int[] descending = c.clone();
        Arrays.sort(descending);
        reverse(descending);

        long cost = 0;
        for (int i = 0; i < descending.length; i++) {
            cost += (long) descending[i] * (i / k + 1);
        }
        return cost;
    }

    /** What a plan actually costs, read straight off the purchase order. */
    private static long costOfPlan(int[][] plan) {
        long cost = 0;
        for (int[] forOneFriend : plan) {
            for (int j = 0; j < forOneFriend.length; j++) {
                cost += (long) forOneFriend[j] * (j + 1);
            }
        }
        return cost;
    }

    private static void reverse(int[] a) {
        for (int lo = 0, hi = a.length - 1; lo < hi; lo++, hi--) {
            int swap = a[lo];
            a[lo] = a[hi];
            a[hi] = swap;
        }
    }

    private static List<Integer> boxed(int[] a) {
        List<Integer> values = new ArrayList<>(a.length);
        for (int value : a) {
            values.add(value);
        }
        return values;
    }
}
