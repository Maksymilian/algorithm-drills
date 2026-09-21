package array;

/**
 * Each element of an array holds the index of the next element. Walk from a start index: where does
 * the walk begin repeating itself, and how long is the repeat? For {@code tab = {3, 2, 1, 2, 4}}
 * from {@code start = 0} the walk is
 * <pre>
 *     step  0    1    2    3    4    5
 *           0 -> 3 -> 2 -> 1 -> 2 -> 1 -> ...
 *                     ^---------'
 * </pre>
 * so it enters a cycle at index 2, after 2 steps, and that cycle has length 2 -
 * {@code Cycle[entry=2, stepsBefore=2, length=2]}.
 * <p>
 * <b>Every such walk cycles, and the shape is always a rho.</b> The array is a function
 * {@code f(i) = tab[i]} from {@code 0 .. n-1} to itself, so the walk
 * {@code start, f(start), f(f(start)), ...} has only n values available and must repeat a value
 * within n+1 steps. And because {@code f} is a <i>function</i> - one successor per index - as soon
 * as a value repeats, everything after it repeats identically. The walk is therefore a tail of
 * {@code mu} steps that is never seen again, followed by a cycle of {@code lambda} indices gone
 * round forever: the Greek letter rho, drawn by the walk itself. There is no "no cycle" case to
 * report, and nothing to search - only two numbers to find.
 * <p>
 * <b>Three ways to find them</b>, and the trade is time against memory:
 * <table border="1">
 *   <caption>What each method costs, on an array of n indices whose rho is mu + lambda long</caption>
 *   <tr><th>Method</th><th>Reads of {@code tab}</th><th>Extra space</th></tr>
 *   <tr><td>{@link #findCycle(int[], int)}</td>
 *       <td>mu + lambda</td><td>4n bytes</td></tr>
 *   <tr><td>{@link #findCycleInConstantSpace(int[], int)} (Floyd)</td>
 *       <td>at most 5mu + 4lambda</td><td><b>O(1)</b></td></tr>
 *   <tr><td>{@link #findCycleByBrent(int[], int)} (Brent)</td>
 *       <td>at most 4(mu + lambda) + 2</td><td><b>O(1)</b></td></tr>
 * </table>
 * The first is the obvious one - stamp each index with the step it was first seen at, and the two
 * numbers fall out of the first index seen twice. It is also the only one that touches memory
 * proportional to the <i>array</i> rather than to the <i>walk</i>, and that is the whole point of
 * the other two: a rho is typically far shorter than the array it lives in - for a random {@code f}
 * both mu and lambda average about {@code sqrt(pi n / 8)} - so allocating and clearing n ints to
 * find it costs more than the walk. Measurements in {@code docs/array/next-index-cycle.md}.
 * <p>
 * <b>The input is checked as it is walked</b>, never up front. An index outside {@code 0 .. n-1} is
 * an {@link IllegalArgumentException} rather than an {@link ArrayIndexOutOfBoundsException}, but
 * only once the walk actually reaches it: a full scan would be O(n) time and so would cost the
 * constant-space methods the very thing they are for. {@code {1, 0, 999}} from {@code start = 0} is
 * therefore answered, not rejected - the walk never reads element 2.
 * <p>
 * <b>Where this meets {@link MinimumSwaps}.</b> That class walks cycles too, but over a
 * permutation, and a permutation is exactly the case that cannot have a tail: every index has one
 * predecessor, so no index can be entered from two places and {@code mu} is 0 from wherever you
 * start. Here {@code f} is an arbitrary function, indices may have any number of predecessors, and
 * the tail is the difference.
 */
public class NextIndexCycle {

    /**
     * Where a walk settles into its cycle, and how big that cycle is.
     *
     * @param entry       the first index the walk reaches twice - the index the cycle is entered at
     * @param stepsBefore mu: how many steps the walk takes to reach {@code entry} the first time;
     *                    0 when {@code start} is already on the cycle
     * @param length      lambda: how many indices the cycle holds, 1 for a self-loop
     */
    public record Cycle(int entry, int stepsBefore, int length) {

        public Cycle {
            if (entry < 0) throw new IllegalArgumentException("entry must be an index, but was " + entry);
            if (stepsBefore < 0) throw new IllegalArgumentException("stepsBefore must not be negative, but was " + stepsBefore);
            if (length < 1) throw new IllegalArgumentException("length must be at least 1, but was " + length);
        }

        /**
         * The step at which the walk first stands on an index it has stood on before - the moment
         * the repetition becomes visible, {@code stepsBefore + length}.
         * <p>
         * It is not where the cycle starts: on the example above the walk is at index 1 at step 3
         * and back at index 2 at step 4, so this returns 4 while the cycle began at step 2.
         */
        public int firstRepeatStep() {
            return stepsBefore + length;
        }
    }

    /**
     * Where the walk from {@code start} enters its cycle, and the cycle's length, by stamping every
     * index with the step it was first seen at.
     * <p>
     * The first index seen twice is the cycle entry, by definition, and both answers are readable
     * the moment the walk arrives there a second time: the stamp is {@code mu}, and the distance
     * back to it is {@code lambda}. One pass, {@code mu + lambda} reads of {@code tab} - the fewest
     * of any method here - and 4n bytes of stamps.
     * <p>
     * The stamps hold the step <i>plus one</i>, so that the untouched zero of a fresh array already
     * means "not seen" and the method needs no initial fill.
     *
     * @param tab   every element an index into {@code tab} itself
     * @param start the index the walk begins at
     * @return where the cycle is entered, after how many steps, and how long it is
     * @throws IllegalArgumentException if {@code tab} is null or empty, {@code start} is not an
     *                                  index into it, or the walk reaches an element that is not
     */
    public static Cycle findCycle(int[] tab, int start) {
        requireWalkable(tab, start);

        int[] firstSeen = new int[tab.length];       // 0 means unvisited; otherwise the step, plus one
        int index = start;
        int step = 0;

        while (firstSeen[index] == 0) {
            firstSeen[index] = ++step;               // = (step at this index) + 1
            index = nextOf(tab, index);
        }

        int stepsBefore = firstSeen[index] - 1;      // this index was here before, and is here again now
        return new Cycle(index, stepsBefore, step - stepsBefore);
    }

    /**
     * The same two numbers in O(1) extra space, by Floyd's tortoise and hare.
     * <p>
     * Run one pointer at one index per step and another at two. Both end up on the cycle, the fast
     * one gaining a place per step, so it catches the slow one from behind and they meet - at some
     * index {@code m} after {@code t} slow steps. That meeting alone gives neither answer, but it
     * gives the fact both are read off:
     * <pre>
     *     the hare walked 2t, the tortoise t, and they stand on the same index
     *         =&gt;  the difference 2t - t = t is a whole number of laps       =&gt;  lambda divides t
     * </pre>
     * <b>Finding mu.</b> Send one pointer back to {@code start} and walk both one step at a time.
     * After {@code mu} steps the restarted pointer is at the entry; the other has gone
     * {@code t + mu} steps, which is {@code mu} plus a whole number of laps, so it is at the entry
     * too. They cannot have met earlier: before step {@code mu} the restarted pointer is on the
     * tail, and the other - already past {@code mu} steps - is on the cycle. So the first index at
     * which they agree is the entry, and the number of steps it took is {@code mu}.
     * <p>
     * <b>Finding lambda</b> is then one lap around from the entry.
     * <p>
     * Three phases - meet, find the entry, measure the lap - of at most {@code mu + lambda} steps
     * each, the hare reading twice per step: at most {@code 5mu + 4lambda} reads of {@code tab},
     * against {@code mu + lambda} for {@link #findCycle(int[], int)}. That is the price of the
     * memory, and on a rho much shorter than the array it is a bargain - see the class
     * documentation.
     *
     * @param tab   every element an index into {@code tab} itself
     * @param start the index the walk begins at
     * @return where the cycle is entered, after how many steps, and how long it is
     * @throws IllegalArgumentException if {@code tab} is null or empty, {@code start} is not an
     *                                  index into it, or the walk reaches an element that is not
     */
    public static Cycle findCycleInConstantSpace(int[] tab, int start) {
        requireWalkable(tab, start);

        int tortoise = nextOf(tab, start);           // one step in
        int hare = nextOf(tab, tortoise);            // two steps in
        while (tortoise != hare) {
            tortoise = nextOf(tab, tortoise);
            hare = nextOf(tab, nextOf(tab, hare));
        }

        int stepsBefore = 0;
        tortoise = start;                            // back to the beginning; now both step once
        while (tortoise != hare) {
            tortoise = nextOf(tab, tortoise);
            hare = nextOf(tab, hare);
            stepsBefore++;
        }
        int entry = tortoise;

        int length = 1;
        for (int index = nextOf(tab, entry); index != entry; index = nextOf(tab, index)) {
            length++;
        }
        return new Cycle(entry, stepsBefore, length);
    }

    /**
     * The same two numbers in O(1) extra space again, by Brent's algorithm - fewer reads than
     * Floyd's, and it finds {@code lambda} first rather than last.
     * <p>
     * Park the tortoise and let the hare run. If the hare comes back to the parked index, the
     * distance it covered <i>is</i> the cycle length - no second lap needed to measure it. The only
     * question is how long to let it run before giving up and re-parking, and the answer is: double
     * the allowance every time. Park at the hare's position, allow {@code power} steps, then
     * {@code 2 * power}, and so on. The first allowance that is both at least {@code lambda} and
     * taken from a parking spot on the cycle succeeds, and the doubling reaches it in
     * O(log(mu + lambda)) restarts while never overshooting by more than a factor of two.
     * <p>
     * <b>Finding mu</b> then needs no restart trick: put one pointer {@code lambda} steps ahead of
     * another and walk them together. They agree exactly when the trailing one reaches the entry,
     * for the same reason as in {@link #findCycleInConstantSpace(int[], int)} - a lead of a whole
     * lap is invisible on the cycle and unmistakable on the tail.
     * <p>
     * The tortoise parks at positions {@code 1, 3, 7, ... 2^k - 1} with an allowance of
     * {@code 2^k}, and the first {@code k} with {@code 2^k > mu} and {@code 2^k >= lambda} ends the
     * search - so the hare walks fewer than {@code 2 max(mu + 1, lambda) + lambda} steps, and the
     * phases after it {@code lambda} and {@code 2mu}: at most {@code 4(mu + lambda) + 2} reads of
     * {@code tab}.
     * The worst case is barely under Floyd's, but every step here reads once where Floyd's hare
     * reads twice, and the lap is counted during the search rather than walked again afterwards.
     * Measured on random arrays it comes to about four fifths of Floyd's reads -
     * {@code docs/array/next-index-cycle.md}.
     *
     * @param tab   every element an index into {@code tab} itself
     * @param start the index the walk begins at
     * @return where the cycle is entered, after how many steps, and how long it is
     * @throws IllegalArgumentException if {@code tab} is null or empty, {@code start} is not an
     *                                  index into it, or the walk reaches an element that is not
     */
    public static Cycle findCycleByBrent(int[] tab, int start) {
        requireWalkable(tab, start);

        int power = 1;
        int length = 1;
        int tortoise = start;
        int hare = nextOf(tab, start);

        while (tortoise != hare) {
            if (power == length) {                   // allowance used up: re-park, and double it
                tortoise = hare;
                power = power > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : power * 2;
                length = 0;
            }
            hare = nextOf(tab, hare);
            length++;
        }

        hare = start;                                // lambda is known: give one pointer that lead
        for (int lap = 0; lap < length; lap++) {
            hare = nextOf(tab, hare);
        }

        int stepsBefore = 0;
        tortoise = start;
        while (tortoise != hare) {
            tortoise = nextOf(tab, tortoise);
            hare = nextOf(tab, hare);
            stepsBefore++;
        }
        return new Cycle(tortoise, stepsBefore, length);
    }

    /**
     * The walk itself, up to and including the first index it reaches twice:
     * {@code {3, 2, 1, 2, 4}} from 0 gives {@code [0, 3, 2, 1, 2]}.
     * <p>
     * The repeated index appears twice - at position {@code mu}, where the cycle is entered, and
     * again {@code lambda} later at the end - so the array holds {@code mu + lambda + 1} indices.
     * It is the picture in the class documentation, as data: for reading, and for tests that want
     * to see the walk rather than a summary of it. If only the two numbers are wanted, every other
     * method here is cheaper.
     *
     * @param tab   every element an index into {@code tab} itself
     * @param start the index the walk begins at
     * @return the indices visited, the first repeated one included twice
     * @throws IllegalArgumentException if {@code tab} is null or empty, {@code start} is not an
     *                                  index into it, or the walk reaches an element that is not
     */
    public static int[] walkToFirstRepeat(int[] tab, int start) {
        Cycle cycle = findCycle(tab, start);

        int[] walk = new int[cycle.firstRepeatStep() + 1];
        int index = start;
        for (int step = 0; step < walk.length; step++) {
            walk[step] = index;
            index = tab[index];                      // already validated by the walk above
        }
        return walk;
    }

    /** Whether a walk can start at all - the one check that cannot wait for the walk to find it. */
    private static void requireWalkable(int[] tab, int start) {
        if (tab == null) throw new IllegalArgumentException("tab must not be null");
        if (tab.length == 0) throw new IllegalArgumentException("tab must not be empty: there is nowhere to walk");
        if (start < 0 || start >= tab.length) {
            throw new IllegalArgumentException(
                    "start must be an index in 0.." + (tab.length - 1) + " but was " + start);
        }
    }

    /** One step of the walk, rejecting an element that is not an index into the array. */
    private static int nextOf(int[] tab, int index) {
        int next = tab[index];
        if (next < 0 || next >= tab.length) {
            throw new IllegalArgumentException(
                    "tab[" + index + "] must be an index in 0.." + (tab.length - 1) + " but was " + next);
        }
        return next;
    }

    private NextIndexCycle() {
    }
}
