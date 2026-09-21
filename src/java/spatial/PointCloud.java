package spatial;

import java.util.List;

/**
 * Billions of integer points in the plane, built once, then asked repeatedly: <b>how many of them
 * lie in this circle?</b>
 * <p>
 * The interesting word in the problem is <i>billions</i>. It settles the representation before any
 * algorithm is chosen, and it decides which algorithm is worth having.
 * <p>
 * <b>1. Nothing here can be an object.</b> In C++ the question's {@code vector<pair<int,int>>} is
 * already eight bytes a point and contiguous - exactly right. Read literally into Java it becomes a
 * {@code List<int[]>}: two billion little arrays, each with its own header, behind two billion
 * references - 32 to 40 bytes a point, so 64 GB and upwards to hold 16 GB of coordinates, every one
 * of them something the garbage collector must trace. The same points in two {@code int[]} are
 * exactly 8 bytes each, contiguous, and two objects in total. {@link #of(List)} exists to accept
 * the question's signature as Java writes it; {@link #PointCloud(int[], int[])} is the constructor
 * to use at this scale, and it takes ownership of the arrays rather than copying them, because a
 * copy is another 16 GB.
 * <p>
 * <b>2. Scanning is out.</b> A pass over two billion points is a couple of seconds of memory
 * bandwidth per query - fine once, hopeless for a query that is asked often. It stays as
 * {@link #countInCircleByScan(int, int, int)} because it is the honest answer for a single query,
 * and because it is what the other two are tested against.
 * <p>
 * <b>3. So the constructor builds an index</b> - a uniform grid, in three arrays and no objects:
 * <pre>
 *     xs, ys        the points, reordered in place so that one cell's points are contiguous
 *     cellStart     where each cell's points begin in xs/ys - cells + 1 ints, row-major
 * </pre>
 * {@code cellStart} does two jobs at once, which is the whole trick of this class. Read as
 * boundaries it says where a cell's points are. Read as what it is - a running total - it answers
 * <i>how many points lie in cells i..k of row j</i> as {@code cellStart[jk + 1] - cellStart[ji]},
 * in one subtraction, because a row's cells are contiguous in row-major order. A count over a whole
 * row of the circle's interior therefore costs no more than a count over one cell, and costs
 * <i>nothing per point</i>.
 * <p>
 * <b>The query, then, never looks at the points it is counting</b> - only at the ones it is unsure
 * about. Going row of cells by row of cells:
 * <ul>
 *   <li>the cells whose every corner is inside the circle are added with one subtraction for the
 *       whole run of them;</li>
 *   <li>only the cells the circle's edge passes through are opened up and their points tested
 *       one at a time.</li>
 * </ul>
 * There are O(r/s) such edge cells for a radius {@code r} and cell size {@code s} - the perimeter,
 * measured in cells - so the work is proportional to the <b>circumference</b> of the circle and to
 * the points near it, never to the area or to the points inside. Measured over a billion points, a
 * circle holding 785 million of them is answered by reading about a thousandth of that.
 * <table border="1">
 *   <caption>What each method costs, on n points with c points per cell</caption>
 *   <tr><th>Method</th><th>Points tested</th><th>Cells visited</th></tr>
 *   <tr><td>{@link #countInCircleByScan(int, int, int)}</td><td>n</td><td>-</td></tr>
 *   <tr><td>{@link #countInCircleByCells(int, int, int)}</td><td>O(c r/s)</td><td>O((r/s)^2)</td></tr>
 *   <tr><td>{@link #countInCircle(int, int, int)}</td><td>O(c r/s)</td><td><b>O(r/s)</b></td></tr>
 * </table>
 * The last two differ only in how the interior is summed: cell by cell, or a row at a time through
 * the running totals. Both leave the interior's <i>points</i> alone; only the second leaves the
 * interior's <i>cells</i> alone as well. Measurements in {@code docs/spatial/points-in-a-circle.md}.
 * <p>
 * <b>Exact arithmetic throughout.</b> A circle is {@code dx*dx + dy*dy <= r*r} and nothing else -
 * no square roots of distances, no doubles anywhere a comparison depends on them. Coordinates span
 * the whole of {@code int}, so {@code dx} needs a {@code long} and {@code dx*dx} would overflow
 * even that; every test therefore rejects on {@code |dx| > r} before it squares anything, which
 * bounds the product to {@code (2^31 - 1)^2} and the sum to just under {@code Long.MAX_VALUE}. The
 * one root taken, {@link #isqrt(long)}, is corrected to an exact integer floor before it is used.
 * <p>
 * <b>Limits.</b> A Java array holds at most {@code Integer.MAX_VALUE} elements, so one instance
 * holds at most 2 147 483 647 points - 17 GB of coordinates. Past that the arrays would have to be
 * chunked into blocks, which changes every index expression in this class and nothing about the
 * algorithm. The grid is uniform, so it suits points that are spread out; for heavily clustered
 * data a k-d tree answers the same query with the same "whole subtree is inside" shortcut and
 * without the assumption - see the notes.
 */
public class PointCloud {

    /** How many points a cell should hold on average, when the caller does not say. */
    private static final int DEFAULT_POINTS_PER_CELL = 64;

    /** At 4 bytes each, 2^26 cells is 256 MB of index - the point past which cells cost more than they save. */
    private static final int MAX_CELLS = 1 << 26;

    private final int[] xs;
    private final int[] ys;
    private final int[] cellStart;               // cells + 1 running totals, row-major: offsets and prefix sums at once
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int shift;                     // cell size is 1 << shift, so a cell index is a shift, not a division
    private final int cols;
    private final int rows;

    /**
     * Indexes {@code xs[i], ys[i]} as the points of the cloud, with a cell size chosen to hold
     * about {@link #DEFAULT_POINTS_PER_CELL} points.
     *
     * @param xs the x coordinates - <b>taken over, and reordered in place</b>, not copied
     * @param ys the y coordinates, reordered with them
     * @throws IllegalArgumentException if either is null or they are of different lengths
     */
    public PointCloud(int[] xs, int[] ys) {
        this(xs, ys, DEFAULT_POINTS_PER_CELL);
    }

    /**
     * Indexes {@code xs[i], ys[i]} with an explicit cell size target.
     * <p>
     * The target decides one trade and only one. Small cells mean a finer grid: fewer points
     * wrongly suspected of being near the edge, so less work per query - and more cells, each
     * costing 4 bytes of {@code cellStart} whether or not anything is in it. Large cells mean the
     * opposite: an index of nothing, and an edge that drags more points into the exact test. The
     * edge work grows linearly with the cell size, the index shrinks quadratically, and the default
     * of 64 is chosen so that a cell is a few cache lines of points - the size at which opening one
     * costs about what a single random read costs.
     *
     * @param xs                 the x coordinates - <b>taken over and reordered</b>, not copied
     * @param ys                 the y coordinates, reordered with them
     * @param targetPointsPerCell how many points a cell should hold on average, at least 1
     * @throws IllegalArgumentException if the arrays are null or of different lengths, or the
     *                                  target is not positive
     */
    public PointCloud(int[] xs, int[] ys, int targetPointsPerCell) {
        if (xs == null || ys == null) throw new IllegalArgumentException("xs and ys must not be null");
        if (xs.length != ys.length) {
            throw new IllegalArgumentException(
                    "xs and ys must be the same length, but were " + xs.length + " and " + ys.length);
        }
        if (targetPointsPerCell < 1) {
            throw new IllegalArgumentException("targetPointsPerCell must be at least 1, but was " + targetPointsPerCell);
        }

        this.xs = xs;
        this.ys = ys;
        int n = xs.length;

        int loX = Integer.MAX_VALUE, hiX = Integer.MIN_VALUE;
        int loY = Integer.MAX_VALUE, hiY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (xs[i] < loX) loX = xs[i];
            if (xs[i] > hiX) hiX = xs[i];
            if (ys[i] < loY) loY = ys[i];
            if (ys[i] > hiY) hiY = ys[i];
        }
        if (n == 0) {                            // an empty cloud still has to answer questions
            loX = loY = 0;
            hiX = hiY = 0;
        }
        this.minX = loX;
        this.minY = loY;
        this.maxX = hiX;
        this.maxY = hiY;

        this.shift = chooseShift((long) hiX - loX, (long) hiY - loY, cellBudget(n, targetPointsPerCell));
        this.cols = columnsAt((long) hiX - loX, shift);
        this.rows = columnsAt((long) hiY - loY, shift);

        this.cellStart = new int[cols * rows + 1];
        countIntoCellStart();
        movePointsIntoCellOrder();
    }

    /**
     * The question's own signature, {@code vector<pair<int,int>>}, as Java writes it - each element
     * an {@code int[]{x, y}} - copied once into the two arrays the class actually keeps.
     * <p>
     * Convenient, and the wrong way in at the size this class is for: the list alone is around
     * 32 bytes per point against the 8 this stores, so a cloud that fits once it is built may well
     * not fit while it is being handed over. Prefer {@link #PointCloud(int[], int[])}, and read the
     * points straight into those two arrays.
     *
     * @param points the points, each a two-element array
     * @return a cloud over a private copy of them
     * @throws IllegalArgumentException if the list, an element, or an element's length is wrong
     */
    public static PointCloud of(List<int[]> points) {
        if (points == null) throw new IllegalArgumentException("points must not be null");

        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            int[] point = points.get(i);
            if (point == null || point.length != 2) {
                throw new IllegalArgumentException("points[" + i + "] must be an {x, y} pair");
            }
            xs[i] = point[0];
            ys[i] = point[1];
        }
        return new PointCloud(xs, ys);
    }

    /**
     * How many points lie in the closed disk of radius {@code radius} about
     * {@code (centreX, centreY)} - <b>on</b> the circle counts as in it.
     * <p>
     * One row of cells at a time. For a row, the disk covers an interval of x, and the run of cells
     * lying wholly within it is added with a single subtraction of running totals, however many
     * points that is. Only the cells at the two ends of the run - where the circle's edge cuts
     * through - are opened, and their points tested exactly. A row the disk misses entirely is
     * skipped without arithmetic.
     * <p>
     * So the cost is O(r/s) cells plus the points in them, for a cell size {@code s}: the
     * circumference, never the area. Counting a billion points takes the same work as counting a
     * thousand, as long as the circle's edge is the same length.
     *
     * @param centreX the circle's centre, x
     * @param centreY the circle's centre, y
     * @param radius  the radius, zero counting only points exactly at the centre
     * @return how many of the cloud's points lie in the disk - a point stored twice counts twice
     * @throws IllegalArgumentException if {@code radius} is negative
     */
    public long countInCircle(int centreX, int centreY, int radius) {
        requireRadius(radius);
        if (xs.length == 0) return 0;

        long cx = centreX, cy = centreY, r = radius;
        long size = 1L << shift;
        long total = 0;

        int lastRow = lastRow(cy + r);
        for (int row = firstRow(cy - r); row <= lastRow; row++) {
            long y0 = minY + (long) row * size;             // the row's band of y, inclusive
            long y1 = y0 + size - 1;
            long dyNear = nearDistance(y0, y1, cy);
            if (dyNear > r) continue;                       // the band misses the disk altogether
            long dyFar = farDistance(y0, y1, cy);

            // Where the disk reaches in x: at the band's nearest edge, and at its farthest.
            long reach = isqrt(r * r - dyNear * dyNear);
            int from = firstColumn(cx - reach);
            int to = lastColumn(cx + reach);
            if (from > to) continue;

            int base = row * cols;
            int insideFrom = to + 1, insideTo = to;         // an empty run, unless the next lines widen it
            if (dyFar <= r) {
                long safe = isqrt(r * r - dyFar * dyFar);   // every point of a cell within this x is in the disk
                long first = Math.max(from, Math.ceilDiv(cx - safe - minX, size));
                long last = Math.min(to, Math.floorDiv(cx + safe + 1 - minX, size) - 1);
                if (first <= last) {                        // now both are inside [from, to], so both are ints:
                    insideFrom = (int) first;               // the quotients themselves need not have been
                    insideTo = (int) last;
                }
            }

            if (insideFrom <= insideTo) {                   // the whole run, counted without being read
                total += cellStart[base + insideTo + 1] - cellStart[base + insideFrom];
                total += countPointsOf(base + from, base + insideFrom - 1, cx, cy, r);
                total += countPointsOf(base + insideTo + 1, base + to, cx, cy, r);
            } else {
                total += countPointsOf(base + from, base + to, cx, cy, r);
            }
        }
        return total;
    }

    /**
     * The same count, visiting every cell the circle's bounding box touches rather than reasoning a
     * row at a time - the straightforward way to use a grid.
     * <p>
     * A cell whose farthest corner is inside the disk contributes its count and is not read; a cell
     * whose nearest corner is outside is skipped; anything else has its points tested. That already
     * keeps the interior's <i>points</i> out of the query, which is most of the win, and it is
     * easier to be sure of than {@link #countInCircle(int, int, int)}.
     * <p>
     * What it does not avoid is the interior's <i>cells</i>: O((r/s)^2) of them, one visit each.
     * With 64 points to a cell that is one visit per 64 points of area - a 64-fold saving over the
     * scan, and still proportional to the area. Summing a row at a time removes that last
     * proportionality, which is the difference between the two methods and nothing else.
     *
     * @param centreX the circle's centre, x
     * @param centreY the circle's centre, y
     * @param radius  the radius, zero counting only points exactly at the centre
     * @return how many of the cloud's points lie in the disk
     * @throws IllegalArgumentException if {@code radius} is negative
     */
    public long countInCircleByCells(int centreX, int centreY, int radius) {
        requireRadius(radius);
        if (xs.length == 0) return 0;

        long cx = centreX, cy = centreY, r = radius;
        long size = 1L << shift;
        long total = 0;

        int fromColumn = firstColumn(cx - r);
        int toColumn = lastColumn(cx + r);
        int lastRow = lastRow(cy + r);
        for (int row = firstRow(cy - r); row <= lastRow; row++) {
            long y0 = minY + (long) row * size;
            long y1 = y0 + size - 1;
            int base = row * cols;

            for (int column = fromColumn; column <= toColumn; column++) {
                int cell = base + column;
                int begin = cellStart[cell], end = cellStart[cell + 1];
                if (begin == end) continue;                 // empty cell: nothing to count either way

                long x0 = minX + (long) column * size;
                long x1 = x0 + size - 1;
                if (!inDisk(nearDistance(x0, x1, cx), nearDistance(y0, y1, cy), r)) continue;

                if (inDisk(farDistance(x0, x1, cx), farDistance(y0, y1, cy), r)) {
                    total += end - begin;                   // wholly inside, so its points are not read
                } else {
                    total += countPoints(begin, end, cx, cy, r);
                }
            }
        }
        return total;
    }

    /**
     * The same count by testing every point of the cloud - O(n), no index used.
     * <p>
     * Here for two reasons. It is the right method when there is exactly one query, since building
     * the grid costs more than answering once. And it is the definition, which is what the other
     * two are tested against.
     *
     * @param centreX the circle's centre, x
     * @param centreY the circle's centre, y
     * @param radius  the radius, zero counting only points exactly at the centre
     * @return how many of the cloud's points lie in the disk
     * @throws IllegalArgumentException if {@code radius} is negative
     */
    public long countInCircleByScan(int centreX, int centreY, int radius) {
        requireRadius(radius);
        return countPoints(0, xs.length, centreX, centreY, radius);
    }

    /** How many points the cloud holds. */
    public int size() {
        return xs.length;
    }

    /** The cell size the constructor chose, a power of two, in units of the coordinates. */
    public long cellSize() {
        return 1L << shift;
    }

    /** How many cells the grid has - {@code columns * rows}, however empty most of them are. */
    public int cells() {
        return cols * rows;
    }

    // --- the index ------------------------------------------------------------------------------------

    /** How many cells are worth having for n points: enough for the target, within the cap. */
    private static int cellBudget(int n, int targetPointsPerCell) {
        if (n == 0) return 1;
        return (int) Math.min(MAX_CELLS, Math.max(1, n / (long) targetPointsPerCell));
    }

    /**
     * The smallest power-of-two cell size whose grid fits the budget.
     * <p>
     * The budget is compared by dividing rather than by multiplying, which is not fussiness: a
     * cloud spanning the whole of {@code int} has an extent of {@code 2^32 - 1}, so at a shift of
     * zero both sides are {@code 2^32} and their product is {@code 2^64} - which a {@code long}
     * reports as nought, making the widest possible grid look like the smallest.
     */
    private static int chooseShift(long extentX, long extentY, int budget) {
        for (int shift = 0; shift < 32; shift++) {
            long columns = (extentX >>> shift) + 1;
            long rows = (extentY >>> shift) + 1;
            if (columns <= budget / rows) return shift;      // i.e. columns * rows <= budget, without overflowing
        }
        return 32;                                          // one cell: the extent no longer divides
    }

    private static int columnsAt(long extent, int shift) {
        return shift == 32 ? 1 : (int) ((extent >>> shift) + 1);
    }

    /** Pass one: how many points fall in each cell, accumulated into the running totals. */
    private void countIntoCellStart() {
        for (int i = 0; i < xs.length; i++) {
            cellStart[cellOf(xs[i], ys[i]) + 1]++;
        }
        for (int cell = 0; cell < cellStart.length - 1; cell++) {
            cellStart[cell + 1] += cellStart[cell];
        }
    }

    /**
     * Pass two: put every point in its cell's run, by permuting the two arrays in place.
     * <p>
     * A counting sort would write into a second pair of arrays - another 16 GB at the size this
     * class is for - so the points are cycled into place instead: take the first slot of a cell
     * that is not yet filled, look at what is sitting there, and swap it with the first free slot
     * of the cell it belongs to. Every swap puts at least one point in its final place, so the
     * whole permutation is O(n) swaps and 4 bytes per <i>cell</i> of extra space, not per point.
     */
    private void movePointsIntoCellOrder() {
        int[] next = cellStart.clone();                     // where each cell's next point goes
        for (int cell = 0; cell < cells(); cell++) {
            while (next[cell] < cellStart[cell + 1]) {
                int here = next[cell];
                int home = cellOf(xs[here], ys[here]);
                if (home == cell) {
                    next[cell]++;                           // already where it belongs
                    continue;
                }
                int there = next[home]++;
                int x = xs[here];
                xs[here] = xs[there];
                xs[there] = x;
                int y = ys[here];
                ys[here] = ys[there];
                ys[there] = y;
            }
        }
    }

    private int cellOf(int x, int y) {
        int column = (int) ((x - (long) minX) >>> shift);
        int row = (int) ((y - (long) minY) >>> shift);
        return row * cols + column;
    }

    // --- geometry, in integers ------------------------------------------------------------------------

    /** Points of cells {@code fromCell .. toCell} that are in the disk, the cells being contiguous. */
    private long countPointsOf(int fromCell, int toCell, long cx, long cy, long r) {
        if (fromCell > toCell) return 0;
        return countPoints(cellStart[fromCell], cellStart[toCell + 1], cx, cy, r);
    }

    private long countPoints(int from, int to, long cx, long cy, long r) {
        long found = 0;
        for (int i = from; i < to; i++) {
            long dx = xs[i] - cx;
            if (dx > r || dx < -r) continue;                // and, just as importantly, dx*dx cannot overflow now
            long dy = ys[i] - cy;
            if (dy > r || dy < -r) continue;
            if (dx * dx + dy * dy <= r * r) found++;
        }
        return found;
    }

    /** Whether a corner at these distances is in the disk, guarded so that the squares cannot overflow. */
    private static boolean inDisk(long dx, long dy, long r) {
        return dx <= r && dy <= r && dx * dx + dy * dy <= r * r;
    }

    /** The distance from {@code c} to the nearest point of {@code [lo, hi]}, zero if it is inside. */
    private static long nearDistance(long lo, long hi, long c) {
        if (c < lo) return lo - c;
        if (c > hi) return c - hi;
        return 0;
    }

    /** The distance from {@code c} to the farthest point of {@code [lo, hi]}. */
    private static long farDistance(long lo, long hi, long c) {
        return Math.max(Math.abs(lo - c), Math.abs(hi - c));
    }

    /**
     * {@code floor(sqrt(v))}, exactly.
     * <p>
     * {@link Math#sqrt} is a guess: a double has 53 bits of mantissa and {@code v} has up to 62, so
     * its result can land either side of the true root. Two corrections in integers settle it, and
     * they are what keeps a point on the circle's edge from being counted by one method and not
     * another.
     */
    private static long isqrt(long v) {
        long root = (long) Math.sqrt((double) v);
        while (root > 0 && root * root > v) root--;
        while ((root + 1) * (root + 1) <= v) root++;
        return root;
    }

    // --- the grid's edges -----------------------------------------------------------------------------

    private int firstRow(long y) {
        return y <= minY ? 0 : (int) ((Math.min(y, maxY) - (long) minY) >>> shift);
    }

    private int lastRow(long y) {
        return y >= maxY ? rows - 1 : (y < minY ? -1 : (int) ((y - (long) minY) >>> shift));
    }

    private int firstColumn(long x) {
        return x <= minX ? 0 : (int) ((Math.min(x, maxX) - (long) minX) >>> shift);
    }

    private int lastColumn(long x) {
        return x >= maxX ? cols - 1 : (x < minX ? -1 : (int) ((x - (long) minX) >>> shift));
    }

    private static void requireRadius(int radius) {
        if (radius < 0) throw new IllegalArgumentException("radius must not be negative, but was " + radius);
    }
}
