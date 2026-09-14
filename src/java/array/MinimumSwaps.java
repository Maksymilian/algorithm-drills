package array;

import java.util.Arrays;

/**
 * HackerRank "Minimum Swaps 2": given an array holding {@code 1, 2, ... n} in some order, count the
 * fewest swaps of arbitrary pairs needed to sort it.
 * <p>
 * Nothing here sorts in order to count. The array <i>is</i> a permutation written down, and the
 * answer is a property of that permutation. Read position {@code i} as a pointer to the position
 * where its value belongs:
 * <pre>
 *     sigma(i) = arr[i] - 1
 * </pre>
 * That map is a permutation of {@code 0 .. n-1}, so it decomposes into disjoint cycles, and
 * <pre>
 *     minimum swaps = n - (number of cycles)
 * </pre>
 * counting a fixed point as a cycle of length one. Both directions of that equality are two lines:
 * <ul>
 *   <li><b>No fewer.</b> A single swap changes the cycle count by exactly one - swapping two
 *       elements of the same cycle splits it in two, swapping elements of different cycles merges
 *       them. The sorted array is the permutation with {@code n} cycles, so reaching it from
 *       {@code c} cycles cannot take fewer than {@code n - c} swaps.</li>
 *   <li><b>No more.</b> A cycle of length {@code k} is sorted by {@code k - 1} swaps: send any one
 *       element home and what remains is a cycle of length {@code k - 1}. Summed over every cycle,
 *       {@code sum(k_i - 1) = n - c}.</li>
 * </ul>
 * The bound is therefore tight, and the problem is a cycle count - O(n), no comparisons.
 * <p>
 * <b>Not the number a sort reports.</b> Counting the swaps a bubble sort performs answers a
 * different question, because bubble sort may only swap <i>adjacent</i> elements; that count is the
 * number of inversions. On the reversed array the two diverge as far as they can: {@code n(n-1)/2}
 * inversions against {@code floor(n/2)} arbitrary swaps - quadratic against linear. Selection sort,
 * on the other hand, happens to be optimal here, which is exactly what
 * {@link #minimumSwapsInPlace(int[])} is.
 * <p>
 * <b>Five ways to count the cycles</b>, trading processor against memory:
 * <table border="1">
 *   <caption>What each method costs</caption>
 *   <tr><th>Method</th><th>Time</th><th>Extra space</th><th>The argument afterwards</th></tr>
 *   <tr><td>{@link #minimumSwaps(int[])}</td><td>O(n)</td><td>n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #minimumSwapsInPlace(int[])}</td><td>O(n)</td><td><b>O(1)</b></td><td><b>sorted</b></td></tr>
 *   <tr><td>{@link #minimumSwapsMarkingSigns(int[])}</td><td>O(n)</td><td><b>O(1)</b></td><td>untouched</td></tr>
 *   <tr><td>{@link #minimumSwapsByUnionFind(int[])}</td><td>O(n a(n))</td><td>9n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #minimumSwapsOfAnyDistinctValues(int[])}</td><td>O(n log n)</td><td>9n bytes</td><td>untouched</td></tr>
 * </table>
 * The first three are the interesting spread: the same linear walk, paying for the "have I been
 * here?" bit in a side array, in the array's own sign bit, or not at all by consuming the input.
 * Union-find is strictly dominated on both axes and is here for what it generalizes to, not for its
 * cost - see {@code docs/array/minimum-swaps.md}.
 * <p>
 * <b>Beyond the stated bounds.</b> The problem guarantees a permutation of {@code 1 .. n}, and the
 * first four methods hold the caller to it: anything outside that range, or any duplicate, is an
 * {@link IllegalArgumentException} rather than a wrong answer or - for the in-place walk, which
 * would otherwise spin forever on a repeated value - a hang. {@link #minimumSwapsOfAnyDistinctValues(int[])}
 * drops the assumption instead and takes any distinct {@code int}s, at the cost of a sort. Only
 * duplicate <i>values</i> remain out of scope, and for a reason: equal elements make the
 * assignment of values to destinations non-unique, so the answer stops being a cycle count and
 * becomes a maximisation over the possible assignments.
 */
public class MinimumSwaps {

    /**
     * The HackerRank signature: the fewest swaps that sort {@code arr}, leaving {@code arr}
     * untouched.
     * <p>
     * Walks every cycle of {@code i -> arr[i] - 1} once, remembering the positions already visited
     * in a side array, and adds {@code length - 1} per cycle. Time O(n) - each position is entered
     * exactly once across all walks - and n bytes of extra space.
     * <p>
     * The walk doubles as the permutation check, for free. In a genuine permutation every position
     * has exactly one predecessor, so a walk can only ever terminate by arriving back at its own
     * starting point. A duplicate value leaves some position with no predecessor at all; that
     * position is still unvisited when its turn as a start comes round, and its walk necessarily
     * ends somewhere else.
     *
     * @param arr a permutation of {@code 1 .. arr.length}
     * @return the minimum number of swaps
     * @throws IllegalArgumentException if {@code arr} is null, or is not such a permutation
     */
    public static int minimumSwaps(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int n = arr.length;
        boolean[] visited = new boolean[n];
        int swaps = 0;

        for (int start = 0; start < n; start++) {
            if (visited[start]) continue;

            int position = start;
            int length = 0;
            while (!visited[position]) {
                visited[position] = true;
                position = destinationOf(arr[position], n);
                length++;
            }
            if (position != start) throw duplicateAt(position, arr);

            swaps += length - 1;               // a cycle of length k costs k - 1
        }
        return swaps;
    }

    /**
     * The fewest swaps that sort {@code arr}, <b>by actually sorting it</b> - on return
     * {@code arr} holds {@code 1 .. n} in order.
     * <p>
     * This is selection sort with the search removed: the element that belongs at position
     * {@code i} need not be looked for, because {@code arr[i]} already says where <i>it</i> belongs.
     * Repeatedly swapping {@code arr[i]} to its home walks the cycle through {@code i} and closes
     * it, sending one element home per swap. The count is therefore not just computed but
     * witnessed - the swaps it performs are a shortest sequence.
     * <p>
     * Time O(n): the inner loop runs at most once per element over the whole method, since every
     * iteration puts a value at its final index. Space O(1), which no other method here manages
     * without either a side array or the input's sign bit - the price being that the input is
     * consumed.
     * <p>
     * On malformed input this throws <i>after</i> having already moved elements around; the array
     * is then a rearrangement of what was passed in, not the original order.
     *
     * @param arr a permutation of {@code 1 .. arr.length}, sorted in place
     * @return the minimum number of swaps, which is also the number performed
     * @throws IllegalArgumentException if {@code arr} is null, or is not such a permutation
     */
    public static int minimumSwapsInPlace(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int n = arr.length;
        int swaps = 0;

        for (int i = 0; i < n; i++) {
            while (arr[i] != i + 1) {
                int home = destinationOf(arr[i], n);
                if (arr[home] == arr[i]) throw duplicateAt(home, arr);   // or this would never end

                int displaced = arr[home];
                arr[home] = arr[i];
                arr[i] = displaced;
                swaps++;
            }
        }
        return swaps;
    }

    /**
     * The fewest swaps that sort {@code arr}, in O(1) extra space and with {@code arr} restored
     * before returning.
     * <p>
     * The same cycle walk as {@link #minimumSwaps(int[])}, but the "visited" bit is borrowed from
     * the array itself: the values are guaranteed positive, so negating one marks its position and
     * leaves the magnitude readable. A final pass flips the signs back, so the method is observably
     * non-mutating even though it writes to the caller's array twice - including when it throws,
     * which is what the {@code finally} is for.
     * <p>
     * Time O(n) in three passes rather than one - validate, walk, restore - and O(1) extra space.
     * Against {@link #minimumSwaps(int[])} that is the whole trade, and it does not go the way
     * counting passes suggests. On a random permutation both walks miss cache on every step, but
     * the side array adds a <i>second</i> randomly indexed stream while these two extra passes are
     * sequential and prefetchable - so at n = 10^7 the method that allocates nothing is also the
     * faster one. On a permutation whose walk is already sequential the order reverses, the extra
     * passes being all that is left to pay. Measurements in {@code docs/array/minimum-swaps.md}.
     * <p>
     * Not thread-safe with respect to {@code arr}, and not merely in the usual sense: a concurrent
     * <i>reader</i> of the array will see negative values mid-flight.
     *
     * @param arr a permutation of {@code 1 .. arr.length}; mutated during the call, restored on exit
     * @return the minimum number of swaps
     * @throws IllegalArgumentException if {@code arr} is null, or is not such a permutation
     */
    public static int minimumSwapsMarkingSigns(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int n = arr.length;
        for (int value : arr) {
            destinationOf(value, n);           // the range check, before any sign becomes ambiguous
        }

        int swaps = 0;
        try {
            for (int start = 0; start < n; start++) {
                if (arr[start] < 0) continue;

                int position = start;
                int length = 0;
                while (arr[position] > 0) {
                    int value = arr[position];
                    arr[position] = -value;    // mark, without losing the value
                    position = value - 1;
                    length++;
                }
                if (position != start) throw duplicateAt(position, arr);

                swaps += length - 1;
            }
        } finally {
            for (int i = 0; i < n; i++) {
                if (arr[i] < 0) arr[i] = -arr[i];
            }
        }
        return swaps;
    }

    /**
     * The fewest swaps that sort {@code arr}, counting cycles as connected components.
     * <p>
     * Union every position with the position its value belongs at. Because the map is a
     * permutation, the components are exactly its cycles, so the answer is again
     * {@code n - components}. Time O(n a(n)) with union by size and path halving - a(n) being the
     * inverse Ackermann function, at most 4 for any array that fits in memory - and 8n bytes for
     * the two int arrays, plus another n for the separate permutation check it cannot fold in.
     * <p>
     * On cost alone this loses to every other method here: same linear order, nine times the
     * memory of {@link #minimumSwaps(int[])}, and pointer-chasing where the others walk. It is
     * included for the question a cycle walk cannot answer.
     * <p>
     * Every walk in this file assumes swaps are <b>unrestricted</b> - any two positions, at any
     * time - and the cycle decomposition says nothing once that goes. Given instead a list of
     * position pairs that <i>may</i> be swapped, the components of that graph answer
     * <b>reachability</b>: an element can arrive at a position exactly when the two share a
     * component, so the array is sortable if and only if every component already holds the values
     * belonging to its own positions, and the closest reachable arrangement is each component
     * sorted within itself.
     * <p>
     * The <i>count</i> does not generalise with it. {@code n - components} is not the restricted
     * cost - allowing only the pairs {@code (0,1)} and {@code (1,2)} leaves one component, so that
     * formula says 2, while {@code [3, 2, 1]} demonstrably needs 3. It must be wrong, too:
     * adjacent-only swaps are bubble sort, whose cost is the inversion count. Counting swaps under
     * a restricted graph is the token swapping problem, and it is NP-hard in general.
     *
     * @param arr a permutation of {@code 1 .. arr.length}
     * @return the minimum number of swaps
     * @throws IllegalArgumentException if {@code arr} is null, or is not such a permutation
     */
    public static int minimumSwapsByUnionFind(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        int n = arr.length;

        // Union-find merges silently: a duplicate value would union an edge twice and simply be
        // absorbed, so unlike the cycle walks this one cannot check as it goes.
        requirePermutation(arr);

        int[] parent = new int[n];
        int[] size = new int[n];
        Arrays.setAll(parent, i -> i);
        Arrays.fill(size, 1);

        int components = n;
        for (int i = 0; i < n; i++) {
            int a = find(parent, i);
            int b = find(parent, destinationOf(arr[i], n));
            if (a == b) continue;

            if (size[a] < size[b]) {           // union by size, so trees stay shallow
                int swap = a;
                a = b;
                b = swap;
            }
            parent[b] = a;
            size[a] += size[b];
            components--;
        }
        return n - components;
    }

    /**
     * The fewest swaps that sort any array of <b>distinct</b> {@code int}s - the same problem with
     * the {@code 1 .. n} assumption dropped.
     * <p>
     * Only one thing about the values matters to the cycle count: where each one belongs, which is
     * its rank. Sorting a copy recovers the ranks, and a binary search per element turns the array
     * into the permutation the other methods take for granted. Time O(n log n), dominated by the
     * sort, and 9n bytes.
     * <p>
     * The sort is unavoidable here, and it is the reason the {@code 1 .. n} promise is worth as
     * much as it is: it hands the caller the ranks for free, and with them a linear answer to a
     * question that otherwise cannot be answered without ordering the values.
     * <p>
     * Distinctness is not a technicality. With duplicates there is no single "where it belongs" -
     * equal values may be sent to any of their destinations - and the minimum is then taken over
     * every such assignment rather than read off one.
     *
     * @param a any array of distinct integers, not modified
     * @return the minimum number of swaps
     * @throws IllegalArgumentException if {@code a} is null or holds a repeated value
     */
    public static int minimumSwapsOfAnyDistinctValues(int[] a) {
        if (a == null) throw new IllegalArgumentException("a must not be null");
        int n = a.length;

        int[] sorted = a.clone();
        Arrays.sort(sorted);
        for (int i = 1; i < n; i++) {
            if (sorted[i] == sorted[i - 1]) {
                throw new IllegalArgumentException("values must be distinct, but " + sorted[i] + " repeats");
            }
        }

        int[] destination = new int[n];
        for (int i = 0; i < n; i++) {
            destination[i] = Arrays.binarySearch(sorted, a[i]);   // the rank of a[i], i.e. its home
        }

        boolean[] visited = new boolean[n];
        int swaps = 0;
        for (int start = 0; start < n; start++) {
            if (visited[start]) continue;

            int length = 0;
            for (int position = start; !visited[position]; position = destination[position]) {
                visited[position] = true;
                length++;
            }
            swaps += length - 1;
        }
        return swaps;
    }

    /** Where a value belongs, 0-based, checking that it is a value this array is allowed to hold. */
    private static int destinationOf(int value, int n) {
        if (value < 1 || value > n) {
            throw new IllegalArgumentException(
                    "arr must hold 1.." + n + " but found " + value);
        }
        return value - 1;
    }

    /** The walk ended at {@code position} instead of where it started, so something is repeated. */
    private static IllegalArgumentException duplicateAt(int position, int[] arr) {
        return new IllegalArgumentException(
                "arr must hold 1.." + arr.length + " without duplicates, but " + (position + 1) + " repeats");
    }

    /** An explicit check, for the methods whose own traversal does not amount to one. */
    private static void requirePermutation(int[] arr) {
        int n = arr.length;
        boolean[] seen = new boolean[n];
        for (int value : arr) {
            int home = destinationOf(value, n);
            if (seen[home]) throw duplicateAt(home, arr);
            seen[home] = true;
        }
    }

    /** Iterative, with path halving: no recursion to overflow, and the tree flattens as it reads. */
    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private MinimumSwaps() {
    }
}
