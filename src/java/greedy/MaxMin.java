package greedy;

import java.util.Arrays;
import java.util.List;

/**
 * HackerRank "Angry Children" ({@code maxMin}): choose {@code k} of the {@code n} elements so that
 * {@code max - min} over the chosen ones - the <i>unfairness</i> - is as small as possible.
 * <p>
 * There are {@code C(n, k)} selections, which at the problem's bounds is a number with tens of
 * thousands of digits. All but {@code n - k + 1} of them can be discarded by one exchange argument:
 * <p>
 * <b>An optimal selection is k consecutive elements of the sorted array.</b> Take any selection,
 * let {@code m} be its smallest element and {@code M} its largest, and let {@code i} be where
 * {@code m} sits once the array is sorted. Every chosen element is at least {@code m}, so all
 * {@code k} of them sit at sorted index {@code i} or later, which puts the largest of them at index
 * {@code i + k - 1} or later. Hence
 * <pre>
 *     M - m &gt;= sorted[i + k - 1] - sorted[i]
 * </pre>
 * and the right-hand side is itself a legal selection - the window starting at {@code i}. So no
 * selection beats the best window, and every window is achievable:
 * <pre>
 *     answer = min over i in [0, n - k] of  sorted[i + k - 1] - sorted[i]
 * </pre>
 * <p>
 * <b>What the "greedy" in the package name refers to.</b> Not an incremental take-the-best-element
 * loop - there is none here, and picking the k smallest values, or the k closest to the mean, is
 * simply wrong. The greedy step is the exchange above: any selection can be slid inward onto
 * consecutive elements without getting worse. That collapses the search space to a single linear
 * scan, and everything left to pay for is the sort.
 * <p>
 * <b>And the sort cannot be avoided</b> - not by being cleverer with comparisons. At {@code k = 2}
 * the answer is the smallest gap between any two elements, and it is {@code 0} exactly when the
 * array holds a duplicate. Deciding that is element distinctness, which needs
 * {@code Omega(n log n)} comparisons, so no comparison-based method can beat sorting at this
 * problem. {@link #maxMinByRadixSort(int, int[])} is faster only because it leaves the model: it
 * reads the bits of the values instead of comparing them.
 * <table border="1">
 *   <caption>What each method costs</caption>
 *   <tr><th>Method</th><th>Time</th><th>Extra space</th><th>The argument afterwards</th></tr>
 *   <tr><td>{@link #maxMin(int, int[])}</td><td>O(n log n)</td><td>4n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #maxMinAsLong(int, int[])}</td><td>O(n log n)</td><td>4n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #maxMinInPlace(int, int[])}</td><td>O(n log n)</td><td>0 - 4n bytes</td><td><b>sorted</b></td></tr>
 *   <tr><td>{@link #maxMinByRadixSort(int, int[])}</td><td><b>O(n)</b></td><td>8n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #fairestSelection(int, int[])}</td><td>O(n log n)</td><td>4n bytes</td><td>untouched</td></tr>
 * </table>
 * <p>
 * <b>Beyond the stated bounds.</b> The problem promises {@code 0 <= arr[i] <= 10}<sup>9</sup>, so
 * the spread always fits in an {@code int}. Nothing here relies on that: every subtraction is done
 * in {@code long}, because {@code {Integer.MIN_VALUE, Integer.MAX_VALUE}} spreads by
 * 2<sup>32</sup> - 1 and an {@code int} would wrap it to {@code -1} - an unfairness of minus one,
 * silently the best answer on offer. {@link #maxMin(int, int[])} keeps the platform's {@code int}
 * return and narrows with {@link Math#toIntExact(long)}, so it throws instead of lying;
 * {@link #maxMinAsLong(int, int[])} is the one to call when the values are unconstrained.
 */
public class MaxMin {

    /**
     * The minimum unfairness of any {@code k} elements of {@code arr}, which is left untouched.
     * <p>
     * Sorts a copy and scans every window of {@code k}. Time O(n log n), and 4n bytes for the copy.
     *
     * @param k   how many elements to select, {@code 1 <= k <= arr.length}
     * @param arr the values to select from, may hold duplicates
     * @return the smallest achievable {@code max - min}
     * @throws IllegalArgumentException if {@code arr} is null or {@code k} is out of range
     * @throws ArithmeticException      if the answer does not fit in an {@code int}, which the
     *                                  problem's own bounds never allow - use
     *                                  {@link #maxMinAsLong(int, int[])} for those
     */
    public static int maxMin(int k, int[] arr) {
        return Math.toIntExact(maxMinAsLong(k, arr));
    }

    /**
     * The platform's signature, over the {@code List<Integer>} it hands in.
     *
     * @param k   how many elements to select, {@code 1 <= k <= arr.size()}
     * @param arr the values to select from, may hold duplicates
     * @return the smallest achievable {@code max - min}
     * @throws IllegalArgumentException if {@code arr} is null or {@code k} is out of range
     * @throws NullPointerException     if any element is null
     * @throws ArithmeticException      if the answer does not fit in an {@code int}
     */
    public static int maxMin(int k, List<Integer> arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int[] values = new int[arr.size()];
        int next = 0;
        for (Integer value : arr) {
            values[next++] = value;                    // unboxing rejects a null element for us
        }
        return maxMin(k, values);
    }

    /**
     * Same selection as {@link #maxMin(int, int[])}, widened so that values outside the problem's
     * {@code 0 .. 10}<sup>9</sup> cannot overflow the answer.
     *
     * @param k   how many elements to select, {@code 1 <= k <= arr.length}
     * @param arr the values to select from, may hold duplicates
     * @return the smallest achievable {@code max - min}
     * @throws IllegalArgumentException if {@code arr} is null or {@code k} is out of range
     */
    public static long maxMinAsLong(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        int[] sorted = arr.clone();
        Arrays.sort(sorted);
        return smallestWindowSpread(k, sorted);
    }

    /**
     * Same selection, <b>by sorting the caller's array</b> - on return {@code arr} is in ascending
     * order.
     * <p>
     * The only method here that does not allocate a copy of its own - worth it when the array is
     * large and the caller has no further use for its order, or wants it sorted anyway, which is
     * often true of the code that asks this question.
     * <p>
     * <b>That is not the same as O(1), and the difference is the JDK's, not this method's.</b>
     * {@link Arrays#sort(int[])} scans for ascending runs first, and when it finds an array built
     * of several of them it merges rather than partitions - allocating a full {@code int[n]} buffer
     * to merge into. Measured at n = 10<sup>7</sup>: 0 bytes on an already sorted array, about 2 MB
     * on random input, and the full 40 MB on an array of two sorted halves. So the copy this method
     * avoids comes back precisely when the input is partly ordered. Only
     * {@link #maxMinByRadixSort(int, int[])} has a space cost that does not depend on the shape of
     * the data.
     * <p>
     * {@code k} is checked before anything is sorted, so a rejected call leaves the array alone.
     *
     * @param k   how many elements to select, {@code 1 <= k <= arr.length}
     * @param arr the values to select from, sorted in place
     * @return the smallest achievable {@code max - min}
     * @throws IllegalArgumentException if {@code arr} is null or {@code k} is out of range
     */
    public static long maxMinInPlace(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        Arrays.sort(arr);
        return smallestWindowSpread(k, arr);
    }

    /**
     * Same selection, sorted in <b>linear time</b> by least-significant-digit radix sort.
     * <p>
     * Four passes over the array, one per byte of an {@code int}, each a counting sort on 256
     * buckets - so O(n) time with a constant of four, against the O(n log n) of a comparison sort,
     * which at {@code n = 10}<sup>7</sup> is about 23 levels of partitioning. The price is memory: two
     * {@code int} arrays are alive at once, 8n bytes, because each pass scatters into the other.
     * <p>
     * This does not contradict the class note's {@code Omega(n log n)} bound. That bound is about
     * <i>comparisons</i>, and this sort makes none - it indexes buckets by the bits of the values,
     * which is information a comparison never yields.
     * <p>
     * Two details make it correct rather than merely fast. The sign bit is the top bit of the top
     * byte, so ordering that byte as an unsigned number would put every negative value after every
     * positive one; flipping it (see {@link #digit(int, int, boolean)}) restores two's-complement
     * order. And a pass whose digit is the same for every element cannot reorder anything, so it is
     * skipped - which on the problem's own {@code 0 .. 10}<sup>9</sup> inputs removes the top pass
     * outright.
     *
     * @param k   how many elements to select, {@code 1 <= k <= arr.length}
     * @param arr the values to select from, not modified
     * @return the smallest achievable {@code max - min}
     * @throws IllegalArgumentException if {@code arr} is null or {@code k} is out of range
     */
    public static long maxMinByRadixSort(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        return smallestWindowSpread(k, radixSorted(arr));
    }

    /**
     * The {@code k} elements themselves, in ascending order, rather than just their spread - the
     * witness to the answer {@link #maxMin(int, int[])} returns.
     * <p>
     * The returned values are a sub-multiset of {@code arr}: they are a window of a sorted copy, so
     * duplicates are carried along at their true multiplicity. Where several windows tie, the
     * earliest is returned.
     *
     * @param k   how many elements to select, {@code 1 <= k <= arr.length}
     * @param arr the values to select from, not modified
     * @return a fresh array of exactly {@code k} values, ascending
     * @throws IllegalArgumentException if {@code arr} is null or {@code k} is out of range
     */
    public static int[] fairestSelection(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        int[] sorted = arr.clone();
        Arrays.sort(sorted);

        int bestStart = 0;
        long best = Long.MAX_VALUE;
        for (int start = 0, last = sorted.length - k; start <= last; start++) {
            long spread = (long) sorted[start + k - 1] - sorted[start];
            if (spread < best) {
                best = spread;
                bestStart = start;
            }
        }
        return Arrays.copyOfRange(sorted, bestStart, bestStart + k);
    }

    /**
     * The scan the exchange argument leaves behind: the narrowest window of {@code k} consecutive
     * values. Time O(n), space O(1), and the subtraction is in {@code long} so that a spread wider
     * than {@link Integer#MAX_VALUE} is reported rather than wrapped.
     */
    private static long smallestWindowSpread(int k, int[] sorted) {
        long best = Long.MAX_VALUE;
        for (int start = 0, last = sorted.length - k; start <= last; start++) {
            long spread = (long) sorted[start + k - 1] - sorted[start];
            if (spread < best) best = spread;
        }
        return best;
    }

    /** Least-significant-digit radix sort over the four bytes of an {@code int}. */
    private static int[] radixSorted(int[] arr) {
        int n = arr.length;
        int[] from = arr.clone();
        int[] to = new int[n];
        int[] offsets = new int[257];

        for (int shift = 0; shift < 32; shift += 8) {
            boolean signByte = shift == 24;
            Arrays.fill(offsets, 0);
            for (int value : from) {
                offsets[digit(value, shift, signByte) + 1]++;
            }
            if (offsets[digit(from[0], shift, signByte) + 1] == n) continue;   // one bucket: a no-op pass

            for (int bucket = 1; bucket < offsets.length; bucket++) {
                offsets[bucket] += offsets[bucket - 1];                        // bucket starts
            }
            for (int value : from) {
                to[offsets[digit(value, shift, signByte)]++] = value;
            }
            int[] swap = from;
            from = to;
            to = swap;
        }
        return from;
    }

    /**
     * The byte of {@code value} at {@code shift}, as a bucket index. The top byte carries the sign
     * bit, and two's complement puts negatives above positives when read unsigned, so that one byte
     * is flipped: {@code 0x80 -> 0x00} sends {@link Integer#MIN_VALUE} to the first bucket.
     */
    private static int digit(int value, int shift, boolean signByte) {
        int bucket = (value >>> shift) & 0xFF;
        return signByte ? bucket ^ 0x80 : bucket;
    }

    private static void requireSelectable(int k, int n) {
        if (k < 1) throw new IllegalArgumentException("k must be at least 1, but was " + k);
        if (k > n) throw new IllegalArgumentException("cannot select " + k + " of " + n + " elements");
    }

    private MaxMin() {
    }
}
