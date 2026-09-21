package greedy;

import java.util.Arrays;
import java.util.List;

/**
 * HackerRank "Greedy Florist": {@code k} friends buy {@code n} flowers between them. A flower's
 * price is its listed price multiplied by one more than the number of flowers that <i>same buyer</i>
 * has already bought - so a buyer's first flower costs {@code c}, their second {@code 2c}, their
 * third {@code 3c}. Buy all of them for as little as possible.
 * <p>
 * Two independent facts decide the whole problem.
 * <p>
 * <b>1. The multipliers are forced.</b> Each friend has exactly one first purchase, so across the
 * group at most {@code k} flowers can carry a multiplier of 1; at most {@code k} can carry 2, and
 * so on - at most {@code m * k} flowers can carry a multiplier of {@code m} or less. Those bounds
 * cap every scheme, and one scheme meets all of them at once: hand out {@code k} flowers at
 * {@code x1}, then {@code k} at {@code x2}, and so on. The multiset of multipliers is therefore not
 * a choice. It is
 * <pre>
 *     1, 1, ... 1,  2, 2, ... 2,  3, ...      (k of each, until the flowers run out)
 * </pre>
 * <b>2. Given the multipliers, pair them by the rearrangement inequality.</b> If a dearer flower
 * carried a larger multiplier than a cheaper one, swapping the two would change the cost by
 * {@code (m_b - m_a) * (c_a - c_b) < 0} - strictly cheaper. So in an optimal purchase the most
 * expensive flower carries the smallest multiplier, and the answer is
 * <pre>
 *     cost = sum over i of  descending[i] * (i / k + 1)
 * </pre>
 * <p>
 * <b>This one is greedy in the strict sense</b>, which is worth saying because its neighbour
 * {@link MaxMin} is not. There is a genuine incremental rule here - <i>give the most expensive
 * remaining flower the cheapest remaining multiplier</i> - committed to irrevocably, one flower at
 * a time, and fact 2 is the exchange argument proving the commitment is never wrong. That is the
 * textbook shape: a greedy choice property plus a proof that local optimality survives.
 * <table border="1">
 *   <caption>What each method costs</caption>
 *   <tr><th>Method</th><th>Time</th><th>Extra space</th><th>The argument afterwards</th></tr>
 *   <tr><td>{@link #getMinimumCost(int, int[])}</td><td>O(n log n)</td><td>4n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #getMinimumCostAsLong(int, int[])}</td><td>O(n log n)</td><td>4n bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #getMinimumCostInPlace(int, int[])}</td><td>O(n log n)</td><td>0 - 4n bytes</td><td><b>sorted</b></td></tr>
 *   <tr><td>{@link #getMinimumCostByCounting(int, int[])}</td><td><b>O(n + maxPrice)</b></td><td>4*maxPrice bytes</td><td>untouched</td></tr>
 *   <tr><td>{@link #purchasePlan(int, int[])}</td><td>O(n log n)</td><td>4n bytes</td><td>untouched</td></tr>
 * </table>
 * <p>
 * <b>The platform's {@code int} return is too narrow for the problem's own bounds.</b> At the usual
 * constraints - {@code n, k <= 100} and {@code c[i] <= 10}<sup>6</sup> - the worst case is one
 * friend buying a hundred flowers at a million each:
 * <pre>
 *     10^6 * (1 + 2 + ... + 100) = 5 050 000 000        Integer.MAX_VALUE = 2 147 483 647
 * </pre>
 * More than twice over. {@link #getMinimumCost(int, int[])} keeps the platform's signature and
 * narrows with {@link Math#toIntExact(long)}, so it throws rather than wrapping to a negative
 * total; {@link #getMinimumCostAsLong(int, int[])} is the one to actually call.
 * <p>
 * <b>Negative prices break the problem, not just the proof.</b> Fact 1 assumes small multipliers
 * are desirable, which stops being true the moment a price is below zero: on {@code c = {-10, 1}}
 * with {@code k = 2}, the forced-multiplier answer is {@code 1*1 + (-10)*1 = -9}, but letting a
 * single friend buy both gives {@code 1*1 + (-10)*2 = -19}. Every method here rejects a negative
 * price rather than returning the wrong one. Zero is fine.
 */
public class GreedyFlorist {

    /** Above this, the histogram costs more than the sort it is meant to replace. */
    private static final int MAX_PRICE_FOR_HISTOGRAM = 10_000_000;

    /**
     * The minimum cost of buying every flower in {@code c} with {@code k} friends, leaving
     * {@code c} untouched.
     * <p>
     * This is the platform's signature. Time O(n log n), and 4n bytes for the sorted copy.
     *
     * @param k the number of friends, at least 1
     * @param c the listed price of each flower, none negative; may be empty
     * @return the minimum total cost
     * @throws IllegalArgumentException if {@code c} is null, {@code k < 1}, or a price is negative
     * @throws ArithmeticException      if the total does not fit in an {@code int} - which the
     *                                  problem's own bounds <i>do</i> allow, so prefer
     *                                  {@link #getMinimumCostAsLong(int, int[])}
     */
    public static int getMinimumCost(int k, int[] c) {
        return Math.toIntExact(getMinimumCostAsLong(k, c));
    }

    /**
     * The platform's signature over the {@code List<Integer>} it hands in.
     *
     * @param k the number of friends, at least 1
     * @param c the listed price of each flower, none negative; may be empty
     * @return the minimum total cost
     * @throws IllegalArgumentException if {@code c} is null, {@code k < 1}, or a price is negative
     * @throws NullPointerException     if any price is null
     * @throws ArithmeticException      if the total does not fit in an {@code int}
     */
    public static int getMinimumCost(int k, List<Integer> c) {
        if (c == null) throw new IllegalArgumentException("c must not be null");

        int[] prices = new int[c.size()];
        int next = 0;
        for (Integer price : c) {
            prices[next++] = price;                    // unboxing rejects a null element for us
        }
        return getMinimumCost(k, prices);
    }

    /**
     * Same purchase as {@link #getMinimumCost(int, int[])}, widened so that the total cannot
     * overflow - see the class note, which it does within the stated constraints.
     *
     * @param k the number of friends, at least 1
     * @param c the listed price of each flower, none negative; may be empty
     * @return the minimum total cost
     * @throws IllegalArgumentException if {@code c} is null, {@code k < 1}, or a price is negative
     */
    public static long getMinimumCostAsLong(int k, int[] c) {
        requireBuyable(k, c);

        int[] ascending = c.clone();
        Arrays.sort(ascending);
        return costReadingDownwards(k, ascending);
    }

    /**
     * Same purchase, <b>by sorting the caller's array</b> - on return {@code c} is in ascending
     * order.
     * <p>
     * Allocates no copy of its own, which is not quite the same as allocating nothing:
     * {@link Arrays#sort(int[])} scans for ascending runs first and merges into a fresh
     * {@code int[n]} when it finds the array is built of several, so the copy comes back on partly
     * ordered input. Measured in {@code docs/greedy/max-min.md}, which walks the same trade.
     * <p>
     * Everything is validated before anything is sorted, so a rejected call leaves the array alone.
     *
     * @param k the number of friends, at least 1
     * @param c the listed prices, sorted in place
     * @return the minimum total cost
     * @throws IllegalArgumentException if {@code c} is null, {@code k < 1}, or a price is negative
     */
    public static long getMinimumCostInPlace(int k, int[] c) {
        requireBuyable(k, c);

        Arrays.sort(c);
        return costReadingDownwards(k, c);
    }

    /**
     * Same purchase, in <b>linear time</b> when the prices are bounded - and without sorting at all.
     * <p>
     * The algorithm never needs the prices ordered, only walked from dearest to cheapest. A
     * histogram gives that directly: count how many flowers carry each price, then walk the counts
     * downwards handing out multipliers in blocks of {@code k}. Time O(n + maxPrice), space
     * {@code 4 * maxPrice} bytes - so it wins when the prices are dense relative to their range,
     * which the problem's own {@code c[i] <= 10}<sup>6</sup> makes likely for any large {@code n},
     * and loses badly when a handful of flowers are priced in the millions.
     * <p>
     * That is a space-for-time trade with a hard edge rather than a gentle one, so the precondition
     * is checked: a price above 10<sup>7</sup> is rejected rather than quietly allocating a 40 MB
     * table to hold a dozen flowers.
     *
     * @param k the number of friends, at least 1
     * @param c the listed prices, not modified, none negative and none above 10<sup>7</sup>
     * @return the minimum total cost
     * @throws IllegalArgumentException if {@code c} is null, {@code k < 1}, a price is negative, or
     *                                  a price exceeds 10<sup>7</sup>
     */
    public static long getMinimumCostByCounting(int k, int[] c) {
        requireBuyable(k, c);
        if (c.length == 0) return 0L;

        int dearest = 0;
        for (int price : c) {
            if (price > MAX_PRICE_FOR_HISTOGRAM) {
                throw new IllegalArgumentException("price " + price + " is above the "
                        + MAX_PRICE_FOR_HISTOGRAM + " this method will build a histogram for; "
                        + "use getMinimumCostAsLong instead");
            }
            dearest = Math.max(dearest, price);
        }

        int[] flowersAtPrice = new int[dearest + 1];
        for (int price : c) {
            flowersAtPrice[price]++;
        }

        long total = 0;
        int bought = 0;
        for (int price = dearest; price >= 0; price--) {
            for (int copies = flowersAtPrice[price]; copies > 0; copies--) {
                total += (long) price * (bought / k + 1);
                bought++;
            }
        }
        return total;
    }

    /**
     * Who buys what: {@code plan[f]} is the flowers friend {@code f} buys, in purchase order, so
     * {@code plan[f][j]} is bought at {@code j + 1} times its listed price. The witness to the
     * number the other methods return.
     * <p>
     * Friend {@code f} takes the flowers ranked {@code f, f + k, f + 2k, ...} by descending price,
     * which is why each friend's own list comes out non-increasing - they buy their dearest flower
     * while it is still cheapest to do so. The array always has {@code k} rows; friends with
     * nothing to buy get empty ones.
     * <p>
     * This is <i>an</i> optimal plan, not <i>the</i> optimal plan. Equal prices, and the boundary
     * between two multiplier blocks, both leave genuine ties - on sample 1 the plan below costs 15,
     * and so does swapping which friend takes the cheapest flower.
     *
     * @param k the number of friends, at least 1
     * @param c the listed prices, not modified, none negative
     * @return {@code k} rows, together holding every element of {@code c} exactly once
     * @throws IllegalArgumentException if {@code c} is null, {@code k < 1}, or a price is negative
     */
    public static int[][] purchasePlan(int k, int[] c) {
        requireBuyable(k, c);

        int[] ascending = c.clone();
        Arrays.sort(ascending);
        int n = ascending.length;

        int[][] plan = new int[k][];
        for (int friend = 0; friend < k; friend++) {
            plan[friend] = new int[(n - friend + k - 1) / k];      // ranks friend, +k, +2k, ...
        }
        for (int rank = 0; rank < n; rank++) {
            plan[rank % k][rank / k] = ascending[n - 1 - rank];    // rank 0 is the dearest flower
        }
        return plan;
    }

    /**
     * The cost of {@code ascending} read from the dear end down, which is the descending order the
     * argument calls for without the array having to be reversed. The multiplication is in
     * {@code long}: see the class note on the platform's {@code int}.
     */
    private static long costReadingDownwards(int k, int[] ascending) {
        long total = 0;
        for (int bought = 0, i = ascending.length - 1; i >= 0; i--, bought++) {
            total += (long) ascending[i] * (bought / k + 1);
        }
        return total;
    }

    private static void requireBuyable(int k, int[] c) {
        if (c == null) throw new IllegalArgumentException("c must not be null");
        if (k < 1) throw new IllegalArgumentException("k must be at least 1, but was " + k);

        for (int price : c) {
            if (price < 0) {
                throw new IllegalArgumentException("prices must not be negative, but found " + price
                        + "; a negative flower gets cheaper the later it is bought, which inverts "
                        + "the problem - see the class note");
            }
        }
    }

    private GreedyFlorist() {
    }
}
