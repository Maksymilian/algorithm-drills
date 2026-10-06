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

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class FibonacciTest {

    private static final int NAIVE_LIMIT = 35;

    private static BigInteger[] sequenceUpTo(int n) {
        BigInteger[] f = new BigInteger[n + 2];
        f[0] = BigInteger.ZERO;
        f[1] = BigInteger.ONE;
        for (int i = 2; i < f.length; i++) {
            f[i] = f[i - 1].add(f[i - 2]);
        }
        return f;
    }

    @Test
    void matchesTheSequenceForEveryNThatFits() {
        BigInteger[] expected = sequenceUpTo(LARGEST_IN_A_LONG);

        for (int n = 0; n <= LARGEST_IN_A_LONG; n++) {
            long want = expected[n].longValueExact();

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

    @Test
    void obeysItsOwnRecurrence() {
        for (int n = 2; n <= LARGEST_IN_A_LONG; n++) {
            assertEquals(fibonacciIterative(n - 1) + fibonacciIterative(n - 2), fibonacciIterative(n),
                    "F(" + n + ") = F(" + (n - 1) + ") + F(" + (n - 2) + ")");
            assertEquals(fibonacciMemoized(n - 1) + fibonacciMemoized(n - 2), fibonacciMemoized(n),
                    "memoized, at " + n);
        }
    }

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

    @Test
    void memoizationIsWhatMakesTheLargeValuesReachableAtAll() {
        assertEquals(7540113804746346429L, fibonacciMemoized(LARGEST_IN_A_LONG));
        assertEquals(7540113804746346429L, fibonacciIterative(LARGEST_IN_A_LONG));

        for (int n = 60; n <= LARGEST_IN_A_LONG; n++) {
            assertEquals(fibonacciIterative(n), fibonacciMemoized(n), "F(" + n + ")");
        }
    }

    @Test
    void holdsNoStateBetweenCallsOrBetweenThreads() {
        assertEquals(fibonacciMemoized(90), fibonacciMemoized(90), "twice in a row");
        assertEquals(55, fibonacciMemoized(10), "a small one after a large one");

        List<Callable<Long>> work = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int n = 40 + (i % 53);
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

    @Test
    void theNaiveRecursionIsCorrectWhileItIsAffordable() {
        BigInteger[] expected = sequenceUpTo(NAIVE_LIMIT);

        for (int n = 0; n <= NAIVE_LIMIT; n++) {
            assertEquals(expected[n].longValueExact(), fibonacci(n), "naive F(" + n + ")");
        }
    }
}
