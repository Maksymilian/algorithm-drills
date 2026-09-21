package recursion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static recursion.Fibonacci.LARGEST_IN_A_LONG;
import static recursion.Fibonacci.fibonacci;
import static recursion.Fibonacci.fibonacciIterative;
import static recursion.Fibonacci.fibonacciMemoized;

/**
 * Time-boxed on a thread of its own. The box is not ceremony here: a memoization that fails to
 * memoize is still <i>correct</i>, and the only thing that separates it from the real one is that
 * it does not finish this century.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class FibonacciTest {

    /** The largest n the naive recursion can be asked for in a test: 2F(36) - 1 = 48 315 633 calls. */
    private static final int NAIVE_LIMIT = 35;

    /** The sequence, built the way the definition reads, in arithmetic that cannot overflow. */
    private static BigInteger[] sequenceUpTo(int n) {
        BigInteger[] f = new BigInteger[n + 2];
        f[0] = BigInteger.ZERO;
        f[1] = BigInteger.ONE;
        for (int i = 2; i < f.length; i++) {
            f[i] = f[i - 1].add(f[i - 2]);
        }
        return f;
    }

    // --- against the definition -------------------------------------------------------------------

    /**
     * Every n a {@code long} can hold, against a {@link BigInteger} sequence built independently.
     * The naive recursion is asked only up to 35, for the reason the whole drill is about.
     */
    @Test
    void matchesTheSequenceForEveryNThatFits() {
        BigInteger[] expected = sequenceUpTo(LARGEST_IN_A_LONG);

        for (int n = 0; n <= LARGEST_IN_A_LONG; n++) {
            long want = expected[n].longValueExact();          // throws if the oracle itself overflows

            assertEquals(want, fibonacciMemoized(n), "memoized F(" + n + ")");
            assertEquals(want, fibonacciIterative(n), "iterative F(" + n + ")");
            if (n <= NAIVE_LIMIT) {
                assertEquals(want, fibonacci(n), "naive F(" + n + ")");
            }
        }
    }

    @Test
    void knowsTheValuesAnyoneWouldCheck() {
        assertEquals(0, fibonacciIterative(0));
        assertEquals(1, fibonacciIterative(1));
        assertEquals(1, fibonacciIterative(2));
        assertEquals(55, fibonacci(10));
        assertEquals(6765, fibonacci(20));
        assertEquals(12586269025L, fibonacciMemoized(50));
        assertEquals(7540113804746346429L, fibonacciMemoized(LARGEST_IN_A_LONG));
    }

    /** The recurrence itself, without reference to any particular value of it. */
    @Test
    void obeysItsOwnRecurrence() {
        for (int n = 2; n <= LARGEST_IN_A_LONG; n++) {
            assertEquals(fibonacciIterative(n - 1) + fibonacciIterative(n - 2), fibonacciIterative(n),
                    "F(" + n + ") = F(" + (n - 1) + ") + F(" + (n - 2) + ")");
            assertEquals(fibonacciMemoized(n - 1) + fibonacciMemoized(n - 2), fibonacciMemoized(n),
                    "memoized, at " + n);
        }
    }

    // --- the limit is 92, and it is not arbitrary ----------------------------------------------------

    /**
     * The cap is a fact about {@code long}, not a policy: F(92) fits with room to spare and F(93)
     * does not fit at all. Checked against {@link BigInteger} so that the constant in the class
     * cannot drift away from the arithmetic it describes.
     */
    @Test
    void ninetyTwoIsExactlyWhereALongRunsOut() {
        BigInteger[] f = sequenceUpTo(LARGEST_IN_A_LONG + 1);

        assertTrue(f[92].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0, "F(92) fits");
        assertTrue(f[93].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0, "F(93) does not");
        assertEquals(92, LARGEST_IN_A_LONG);
    }

    @Test
    void rejectsWhatItCannotAnswer() {
        for (int n : new int[]{-1, -2, Integer.MIN_VALUE, 93, 1_000, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> fibonacci(n), "naive at " + n);
            assertThrows(IllegalArgumentException.class, () -> fibonacciMemoized(n), "memoized at " + n);
            assertThrows(IllegalArgumentException.class, () -> fibonacciIterative(n), "iterative at " + n);
        }
    }

    // --- the memoization has to be real ---------------------------------------------------------------

    /**
     * The test that separates memoization from the recursion it is written on top of.
     * <p>
     * Correctness cannot: a memo that never hits returns exactly the same numbers. What it cannot do
     * is return them. Asking for F(92) without memoization is 2F(93) - 1 = 24 400 320 830 243 753 475
     * calls - thousands of years - so this assertion is really about the clock, and the class's
     * 30-second box is what makes it one. Correct, it takes microseconds.
     */
    @Test
    void memoizationIsWhatMakesTheLargeValuesReachableAtAll() {
        assertEquals(7540113804746346429L, fibonacciMemoized(LARGEST_IN_A_LONG));
        assertEquals(7540113804746346429L, fibonacciIterative(LARGEST_IN_A_LONG));

        for (int n = 60; n <= LARGEST_IN_A_LONG; n++) {        // every one of these is unreachable naively
            assertEquals(fibonacciIterative(n), fibonacciMemoized(n), "F(" + n + ")");
        }
    }

    /**
     * The memo is a fresh array per call, so there is nothing to share and nothing to race. Worth a
     * test because the obvious "improvement" - hoisting the table to a static field so that it
     * survives between calls - is what would break this, and it would break it intermittently.
     */
    @Test
    void holdsNoStateBetweenCallsOrBetweenThreads() {
        assertEquals(fibonacciMemoized(90), fibonacciMemoized(90), "twice in a row");
        assertEquals(55, fibonacciMemoized(10), "a small one after a large one");

        List<Callable<Long>> work = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int n = 40 + (i % 53);                             // 40 .. 92, interleaved across threads
            work.add(() -> fibonacciMemoized(n) - fibonacciIterative(n));
        }
        try (ExecutorService threads = Executors.newFixedThreadPool(8)) {
            for (Future<Long> answer : threads.invokeAll(work)) {
                assertEquals(0L, answer.get(), "a thread disagreed with the iterative answer");
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** The naive recursion is correct - that was never in question - for as long as one can wait. */
    @Test
    void theNaiveRecursionIsCorrectWhileItIsAffordable() {
        BigInteger[] expected = sequenceUpTo(NAIVE_LIMIT);

        for (int n = 0; n <= NAIVE_LIMIT; n++) {
            assertEquals(expected[n].longValueExact(), fibonacci(n), "naive F(" + n + ")");
        }
    }
}
