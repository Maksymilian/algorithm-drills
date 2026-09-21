package recursion;

/**
 * {@code F(0) = 0}, {@code F(1) = 1}, {@code F(n) = F(n-1) + F(n-2)} - written three ways, which
 * between them span the whole distance from a computation nobody can afford to one that is over
 * before it starts.
 * <p>
 * <table border="1">
 *   <caption>What each way costs to reach F(n)</caption>
 *   <tr><th>Method</th><th>Additions</th><th>Calls</th><th>Heap</th><th>Stack</th></tr>
 *   <tr><td>{@link #fibonacci(int)}</td>
 *       <td><b>F(n+1) - 1</b></td><td><b>2F(n+1) - 1</b></td><td>none</td><td>n frames</td></tr>
 *   <tr><td>{@link #fibonacciMemoized(int)}</td>
 *       <td>n - 1</td><td>2n - 1</td><td>8(n+1) bytes</td><td>n frames</td></tr>
 *   <tr><td>{@link #fibonacciIterative(int)}</td>
 *       <td>n</td><td>1</td><td><b>none</b></td><td><b>1 frame</b></td></tr>
 * </table>
 * Those first two columns are the whole lesson, and the bold entries are not the same kind of
 * number: {@code F(n+1)} grows by a factor of {@code phi = 1.618...} per step, so the naive
 * recursion costs {@code 1.4 * 1.618^n} calls where the other two cost {@code n}. At n = 40 that is
 * 300 million calls against 40 additions. At n = 92 it is 2.4 * 10^19 calls - some centuries -
 * against 92.
 * <p>
 * <b>Why the naive one is exponential</b> is not that recursion is slow. It is that the call tree
 * recomputes: {@code F(n-2)} is worked out twice, {@code F(n-3)} three times, and in general
 * {@code F(k)} is recomputed {@code F(n-k+1)} times over. Counting the calls,
 * <pre>
 *     C(0) = C(1) = 1,   C(n) = C(n-1) + C(n-2) + 1
 * </pre>
 * which is the Fibonacci recurrence with an extra 1, and solves to {@code C(n) = 2F(n+1) - 1}
 * exactly. The tree is the answer's own size: to add up {@code F(n)} ones, you need {@code F(n)}
 * additions of 1, because that is what the leaves of the tree are.
 * <p>
 * <b>Memoization deletes every repeat</b> and nothing else. The shape of the recursion is
 * untouched - {@link #fibonacciMemoized(int)} is the same three lines with one table lookup in
 * front - but a subtree already computed is never entered again, so each {@code F(k)} is worked out
 * once and the exponential tree collapses to a path. That is the difference between
 * {@code 2F(n+1)} calls and {@code 2n}, bought with {@code 8(n+1)} bytes of table.
 * <p>
 * <b>What memoization does not buy back is the stack.</b> Both recursions still go {@code n} frames
 * deep before the first addition happens, and that depth is the one cost iteration removes
 * entirely. Here it never matters - {@code n} stops at 92 - but the same code over
 * {@link java.math.BigInteger} is a {@link StackOverflowError} at a few tens of thousands, while
 * the loop in {@link #fibonacciIterative(int)} has no such number. See
 * {@code docs/recursion/fibonacci.md}, which measures all of this.
 * <p>
 * <b>Where it all stops: 92.</b> {@code F(92) = 7540113804746346429} is the last Fibonacci number
 * that fits a {@code long}; {@code F(93) = 12200160415121876738} is larger than
 * {@code Long.MAX_VALUE}. Every method here rejects anything past it rather than returning a
 * quietly wrapped answer. It also caps how much memory this problem can possibly use: the whole
 * memo table, from {@code F(0)} to {@code F(92)}, is 93 longs - 744 bytes. The interesting cost of
 * Fibonacci is processor, not memory, until the numbers stop fitting in a register.
 */
public class Fibonacci {

    /** {@code F(92)} is the largest Fibonacci number a {@code long} holds. */
    public static final int LARGEST_IN_A_LONG = 92;

    /**
     * {@code F(n)}, by writing the recurrence down and nothing else.
     * <p>
     * This is the definition compiled: two base cases and a sum. It is also the version to know
     * better than to use, because the call tree has no memory - every subtree is explored afresh,
     * every time it is reached - so it makes {@code 2F(n+1) - 1} calls to perform {@code F(n+1) - 1}
     * useful additions. The cost is the answer's own magnitude.
     * <p>
     * Usable to about n = 45, where it takes a second or two. At n = 60 it is a week; at n = 92,
     * which the signature permits, it is longer than there has been civilisation. The limit is the
     * processor's, never the memory's: the recursion is only {@code n} frames deep and allocates
     * nothing at all.
     *
     * @param n which Fibonacci number, from 0 to {@link #LARGEST_IN_A_LONG}
     * @return {@code F(n)}
     * @throws IllegalArgumentException if {@code n} is negative or past 92
     */
    public static long fibonacci(int n) {
        requireInRange(n);
        return naive(n);
    }

    /** The recurrence itself, with the range check left outside so that it is not paid per call. */
    private static long naive(int n) {
        return n <= 1 ? n : naive(n - 1) + naive(n - 2);
    }

    /**
     * {@code F(n)}, by the same recursion with every repeated subtree deleted.
     * <p>
     * One table, one lookup, one store. A value already worked out is returned instead of being
     * recomputed, so the call tree - which was two branches wide at every node - becomes a single
     * path down to the base cases and a single path back up: {@code 2n - 1} calls and {@code n - 1}
     * additions, from {@code 2F(n+1) - 1} and {@code F(n+1) - 1}.
     * <p>
     * The table is a fresh {@code long[n+1]} per call, so there is no state shared between calls or
     * between threads. Zero means "not yet computed", which needs no filling pass and is safe for
     * exactly one reason: {@code F(k)} is zero only at {@code k = 0}, and that is a base case which
     * is never stored. The same trick as the stamps in {@code array.NextIndexCycle}.
     * <p>
     * Costs {@code 8(n+1)} bytes of heap - 744 at the largest n this signature allows - plus the
     * {@code n} stack frames the recursion needs, which memoization does not remove.
     * {@link #fibonacciIterative(int)} is the version that removes both.
     *
     * @param n which Fibonacci number, from 0 to {@link #LARGEST_IN_A_LONG}
     * @return {@code F(n)}
     * @throws IllegalArgumentException if {@code n} is negative or past 92
     */
    public static long fibonacciMemoized(int n) {
        requireInRange(n);
        return memoized(n, new long[n + 1]);
    }

    private static long memoized(int n, long[] known) {
        if (n <= 1) return n;                        // F(0) and F(1), never stored: 0 means unknown
        if (known[n] != 0) return known[n];
        return known[n] = memoized(n - 1, known) + memoized(n - 2, known);
    }

    /**
     * {@code F(n)}, bottom-up, in two variables.
     * <p>
     * The recurrence looks back exactly two places, so only two places need keeping. That is the
     * whole of it: {@code n} additions, no table, no recursion, no call but this one. Where
     * {@link #fibonacciMemoized(int)} buys linear time with linear memory, this pays nothing for it,
     * because computing the values in increasing order means a value is wanted only while it is
     * still one of the last two.
     * <p>
     * Memoization and this loop visit the same {@code n+1} subproblems in opposite directions -
     * top-down on demand, bottom-up in order - which is the whole distinction between memoized
     * recursion and dynamic programming. Fibonacci is the smallest example of it, and
     * {@code dynamic.MaxSubsetSum} is the same argument on a problem where the order is not
     * obvious.
     *
     * @param n which Fibonacci number, from 0 to {@link #LARGEST_IN_A_LONG}
     * @return {@code F(n)}
     * @throws IllegalArgumentException if {@code n} is negative or past 92
     */
    public static long fibonacciIterative(int n) {
        requireInRange(n);

        long previous = 0;                           // F(0)
        long current = 1;                            // F(1)
        for (int i = 0; i < n; i++) {
            long next = previous + current;
            previous = current;
            current = next;
        }
        return previous;                             // after n steps, previous is F(n)
    }

    private static void requireInRange(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative, but was " + n);
        }
        if (n > LARGEST_IN_A_LONG) {
            throw new IllegalArgumentException(
                    "F(" + n + ") does not fit in a long: F(93) is 12200160415121876738, and Long.MAX_VALUE is "
                            + Long.MAX_VALUE);
        }
    }

    private Fibonacci() {
    }
}
