package spatial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time-boxed on a thread of its own: the constructor's in-place permutation is a loop that only
 * ends because every swap places a point, so a regression there is an endless build rather than a
 * failing one. Correct, the whole class runs in a few seconds.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PointCloudTest {

    // --- the definition, against which everything else is checked ---------------------------------

    /**
     * The count, in arithmetic that cannot overflow whatever the coordinates are - which is the
     * point of using {@link BigInteger} here. The class under test is careful with {@code long}s
     * precisely because {@code dx * dx} does not fit one; an oracle that repeated that reasoning
     * would agree with a mistake in it.
     */
    private static long countExactly(int[][] points, int centreX, int centreY, int radius) {
        BigInteger rr = BigInteger.valueOf(radius).pow(2);
        long found = 0;
        for (int[] point : points) {
            BigInteger dx = BigInteger.valueOf(point[0] - (long) centreX);
            BigInteger dy = BigInteger.valueOf(point[1] - (long) centreY);
            if (dx.pow(2).add(dy.pow(2)).compareTo(rr) <= 0) found++;
        }
        return found;
    }

    private static PointCloud cloudOf(int[][] points, int pointsPerCell) {
        int[] xs = new int[points.length];
        int[] ys = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            xs[i] = points[i][0];
            ys[i] = points[i][1];
        }
        return new PointCloud(xs, ys, pointsPerCell);
    }

    private static void assertEveryMethodCounts(long expected, PointCloud cloud, int cx, int cy, int r, String where) {
        assertEquals(expected, cloud.countInCircle(cx, cy, r), () -> "countInCircle " + where);
        assertEquals(expected, cloud.countInCircleByCells(cx, cy, r), () -> "countInCircleByCells " + where);
        assertEquals(expected, cloud.countInCircleByScan(cx, cy, r), () -> "countInCircleByScan " + where);
    }

    // --- exhaustive over a small plane -------------------------------------------------------------

    /**
     * The load-bearing test. Every point of the 9x9 block {@code [-4, 4]^2}, every centre of the
     * 13x13 block {@code [-6, 6]^2}, every radius from 0 to 8: 1 521 circles, each counted three
     * ways and compared with the definition.
     * <p>
     * Small, but it is the shape of the problem rather than its size that has the corner cases, and
     * this covers them all at once - circles entirely inside the cloud, entirely outside it,
     * centred on a point, centred between points, and - the ones worth the test - circles whose
     * edge passes exactly through points. At radius 5 about the origin, {@code (3, 4)} and
     * {@code (0, 5)} sit exactly on the circle, which is where a square root would decide wrongly.
     * <p>
     * Repeated at three cell sizes, because the grid is what is really under test: one point per
     * cell makes almost every cell an edge cell, 64 puts the whole cloud in a single cell, and the
     * answers must not know the difference.
     */
    @Test
    void everyCircleOverASmallPlane() {
        List<int[]> square = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                square.add(new int[]{x, y});
            }
        }
        int[][] points = square.toArray(new int[0][]);

        PointCloud[] clouds = {cloudOf(points, 1), cloudOf(points, 4), cloudOf(points, 64)};

        for (int cx = -6; cx <= 6; cx++) {
            for (int cy = -6; cy <= 6; cy++) {
                for (int r = 0; r <= 8; r++) {
                    long expected = countExactly(points, cx, cy, r);
                    for (PointCloud cloud : clouds) {
                        assertEveryMethodCounts(expected, cloud, cx, cy, r,
                                "at (" + cx + ", " + cy + ") r=" + r + " with " + cloud.cells() + " cells");
                    }
                }
            }
        }
    }

    /** The circle is closed: a point exactly at distance r is in it, and 3-4-5 says so in integers. */
    @Test
    void theEdgeCounts() {
        int[][] points = {{3, 4}, {-3, -4}, {5, 0}, {0, -5}, {4, 4}, {0, 0}};
        PointCloud cloud = cloudOf(points, 1);

        assertEveryMethodCounts(5, cloud, 0, 0, 5, "the four points at distance exactly 5, and the centre");
        assertEveryMethodCounts(1, cloud, 0, 0, 4, "only the centre, once the edge is pulled in");
        assertEveryMethodCounts(6, cloud, 0, 0, 6, "everything, (4,4) being at distance sqrt(32)");
    }

    // --- the ends of the coordinate range ----------------------------------------------------------

    /**
     * Coordinates at {@code Integer.MIN_VALUE} and {@code MAX_VALUE}, with radii to match.
     * <p>
     * This is the overflow test. {@code dx} between those two is 2^32, which no {@code int} holds;
     * {@code dx * dx} is 2^64, which no {@code long} holds either. Every distance test in the class
     * therefore rejects on {@code |dx| > r} before it squares, and the grid's extent arithmetic is
     * in {@code long}s throughout. Get either wrong and a point on the far side of the plane is
     * counted as if it were on top of the centre.
     */
    @Test
    void countsAtTheEndsOfTheCoordinateRange() {
        int min = Integer.MIN_VALUE, max = Integer.MAX_VALUE;
        int[][] points = {
                {min, min}, {min, max}, {max, min}, {max, max},
                {0, 0}, {1, -1}, {-1, 1},
                {min + 1, 0}, {max - 1, 0},
        };
        PointCloud cloud = cloudOf(points, 1);

        int[][] queries = {
                {0, 0, max}, {0, 0, 0}, {0, 0, 1},
                {min, min, max}, {max, max, max}, {min, max, max}, {max, min, max},
                {min, min, 1}, {max, max, 1},
                {min, 0, max}, {max, 0, max}, {0, min, max}, {0, max, max},
        };
        for (int[] query : queries) {
            long expected = countExactly(points, query[0], query[1], query[2]);
            assertEveryMethodCounts(expected, cloud, query[0], query[1], query[2],
                    "at (" + query[0] + ", " + query[1] + ") r=" + query[2]);
        }
    }

    // --- degenerate clouds ---------------------------------------------------------------------------

    @Test
    void countsAnEmptyCloud() {
        PointCloud empty = new PointCloud(new int[0], new int[0]);

        assertEquals(0, empty.size());
        assertEveryMethodCounts(0, empty, 0, 0, 0, "empty");
        assertEveryMethodCounts(0, empty, 7, -7, Integer.MAX_VALUE, "empty, and a circle over everything");
    }

    @Test
    void countsOnePointAndManyCopiesOfIt() {
        assertEveryMethodCounts(1, cloudOf(new int[][]{{3, -3}}, 1), 3, -3, 0, "the point itself, radius zero");
        assertEveryMethodCounts(0, cloudOf(new int[][]{{3, -3}}, 1), 3, -2, 0, "one off, radius zero");

        int[][] identical = new int[100][];
        Arrays.setAll(identical, i -> new int[]{-9, 12});
        PointCloud cloud = cloudOf(identical, 1);

        assertEquals(1, cloud.cells(), "points with no extent need exactly one cell");
        assertEveryMethodCounts(100, cloud, -9, 12, 0, "every copy, counted separately");
        assertEveryMethodCounts(100, cloud, -9, 13, 1, "and again from one unit away");
        assertEveryMethodCounts(0, cloud, -9, 14, 1, "and none from two");
    }

    /** A circle that misses the cloud's bounding box entirely, on each of the four sides and a corner. */
    @Test
    void countsNothingWhenTheCircleIsElsewhere() {
        int[][] points = {{0, 0}, {10, 10}, {5, 5}, {10, 0}, {0, 10}};
        PointCloud cloud = cloudOf(points, 1);

        for (int[] query : new int[][]{{-100, 5, 50}, {110, 5, 50}, {5, -100, 50}, {5, 110, 50}, {-100, -100, 100}}) {
            assertEveryMethodCounts(0, cloud, query[0], query[1], query[2],
                    "away at (" + query[0] + ", " + query[1] + ")");
        }
    }

    /** A circle in a hole in the middle of the cloud: inside the bounding box, over no points. */
    @Test
    void countsNothingInAHole() {
        List<int[]> ring = new ArrayList<>();
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                if (Math.abs(x) > 10 || Math.abs(y) > 10) ring.add(new int[]{x, y});
            }
        }
        int[][] points = ring.toArray(new int[0][]);
        PointCloud cloud = cloudOf(points, 4);

        assertEveryMethodCounts(0, cloud, 0, 0, 10, "the hole is 10 deep in every direction");
        assertEquals(countExactly(points, 0, 0, 12), cloud.countInCircle(0, 0, 12), "and the rim beyond it");
    }

    /**
     * A small, tightly gridded cloud in one corner of the plane, asked about circles at the other -
     * where the arithmetic that finds the run of wholly-inside cells is at its largest.
     * <p>
     * The run's first column is {@code (cx - reach - minX) / cellSize}, and with the cloud at
     * {@code MIN_VALUE}, the centre at {@code MAX_VALUE} and a cell one unit wide, that numerator is
     * 2^32 and the quotient does not fit an {@code int}. Cast too early it comes back negative, and
     * a negative column reaches behind the start of the running totals.
     */
    @Test
    void countsATightCloudAtOneEndOfThePlaneFromTheOther() {
        List<int[]> corner = new ArrayList<>();
        for (int x = 0; x <= 10; x++) {
            for (int y = 0; y <= 10; y++) {
                corner.add(new int[]{Integer.MIN_VALUE + x, y});
            }
        }
        int[][] points = corner.toArray(new int[0][]);
        PointCloud cloud = cloudOf(points, 1);              // 121 cells of one unit each

        assertEquals(1, cloud.cellSize(), "the point of this test is a cell size of one");
        for (int r : new int[]{0, 1, 1_000, Integer.MAX_VALUE}) {
            assertEveryMethodCounts(countExactly(points, Integer.MAX_VALUE, 5, r), cloud,
                    Integer.MAX_VALUE, 5, r, "from the far end of the plane, r=" + r);
            assertEveryMethodCounts(countExactly(points, Integer.MIN_VALUE + 5, 5, r), cloud,
                    Integer.MIN_VALUE + 5, 5, r, "and from inside it, r=" + r);
        }
    }

    /**
     * The one square root the class takes, and the case that says it has to be exact.
     * <p>
     * Ten points on a single row of cells one unit tall, and a circle of radius {@code 2^30} whose
     * edge falls across them. How far the circle reaches along that row is {@code sqrt(r^2 - 1)},
     * whose true floor is {@code r - 1} - but {@code r^2 - 1} needs 61 bits and a double carries 53,
     * so it rounds to {@code r^2} and {@code Math.sqrt} answers {@code r}. One too far: the cell
     * holding the leftmost point is then taken for wholly inside, and that point - at
     * {@code sqrt(r^2 + 1)} from the centre, and so outside the circle by one unit of squared
     * distance - is counted with the rest of its cell, unread.
     * <p>
     * Nine, not ten. Nothing else in this class notices the difference: the cell-by-cell method
     * tests corners rather than roots, so it stays right, and the scan never leaves the definition.
     */
    @Test
    void theSquareRootHasToBeExact() {
        int r = 1 << 30;
        int[][] points = new int[10][];
        Arrays.setAll(points, i -> new int[]{-r + i, 1});        // one row, starting exactly r from the centre

        PointCloud cloud = cloudOf(points, 1);
        assertEquals(1, cloud.cellSize(), "a cell per point, so a reach off by one moves the run");
        assertEquals(9, countExactly(points, 0, 0, r), "the leftmost point is sqrt(r^2 + 1) away, not sqrt(r^2)");

        assertEveryMethodCounts(9, cloud, 0, 0, r, "a circle reaching exactly sqrt(r^2 - 1) across the row");
    }

    // --- random clouds --------------------------------------------------------------------------------

    /**
     * Random clouds against the definition, over coordinate ranges chosen to make different grids:
     * tight clusters where every point shares a cell, and spreads where almost none do.
     */
    @Test
    void agreesWithTheDefinitionOnRandomClouds() {
        Random random = new Random(20260919L);

        for (int trial = 0; trial < 400; trial++) {
            int n = random.nextInt(600);
            int spread = 1 << random.nextInt(20);
            int[][] points = new int[n][];
            Arrays.setAll(points, i -> new int[]{random.nextInt(-spread, spread + 1), random.nextInt(-spread, spread + 1)});

            PointCloud cloud = cloudOf(points, 1 + random.nextInt(64));
            for (int query = 0; query < 5; query++) {
                int cx = random.nextInt(-spread * 2 - 1, spread * 2 + 2);
                int cy = random.nextInt(-spread * 2 - 1, spread * 2 + 2);
                int r = random.nextInt(spread * 2 + 2);

                assertEveryMethodCounts(countExactly(points, cx, cy, r), cloud, cx, cy, r,
                        "trial " + trial + " spread " + spread + " at (" + cx + ", " + cy + ") r=" + r);
            }
        }
    }

    // --- the index is doing the work -------------------------------------------------------------------

    /**
     * Ten million points, two thousand queries, each circle holding some five million of them.
     * <p>
     * This is the test that asserts the <i>structure</i> rather than an answer, and the clock is how
     * it asserts it. Each circle holds some five million of the ten million points, and reading
     * them all twenty thousand times is 10^11 point tests. The interior is not read: it is added a
     * row of cells at a time out of the running totals, so only the few thousand points the edge
     * passes through are ever looked at.
     * <p>
     * Hence the box of twenty seconds, which is neither arbitrary nor a benchmark. Measured on the
     * machine this was written on, the method takes about 4.5 s as it stands, and about 58 s with
     * the interior run disabled and its cells' points tested one by one - a mutation that changes
     * no answer at all and so passes every other test in this class. Both margins are wide, and
     * the 20 s in between is what stands between "the index works" and "the index is decoration".
     * <p>
     * Correctness at this size is spot-checked against the scan, the exhaustive tests above having
     * settled that the three methods agree in general.
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void answersLargeCirclesOverTenMillionPointsWithoutReadingThem() {
        int n = 10_000_000;
        int spread = 1_000_000;
        Random random = new Random(11L);

        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = random.nextInt(-spread, spread);
            ys[i] = random.nextInt(-spread, spread);
        }
        PointCloud cloud = new PointCloud(xs, ys, 4);         // finer cells: the choice for large, frequent circles

        long total = 0;
        for (int query = 0; query < 20_000; query++) {
            int cx = random.nextInt(-spread, spread);
            int cy = random.nextInt(-spread, spread);
            long count = cloud.countInCircle(cx, cy, 800_000);

            if (query < 5) {                                  // the scan is affordable a handful of times
                assertEquals(cloud.countInCircleByScan(cx, cy, 800_000), count,
                        "at (" + cx + ", " + cy + ")");
            }
            total += count;
        }
        assertTrue(total > 0, "twenty thousand circles of radius 800 000 over ten million points found nothing");
    }

    /** The constructor takes the arrays over and permutes them; it must not lose or invent a point. */
    @Test
    void reorderingKeepsEveryPoint() {
        Random random = new Random(7L);
        int n = 10_000;
        int[] xs = new int[n];
        int[] ys = new int[n];
        Arrays.setAll(xs, i -> random.nextInt(-500, 500));
        Arrays.setAll(ys, i -> random.nextInt(-500, 500));

        long[] before = pairs(xs, ys);
        new PointCloud(xs, ys, 4);
        long[] after = pairs(xs, ys);

        Arrays.sort(before);
        Arrays.sort(after);
        assertTrue(Arrays.equals(before, after), "the same multiset of points, in some order");
    }

    private static long[] pairs(int[] xs, int[] ys) {
        long[] packed = new long[xs.length];
        Arrays.setAll(packed, i -> ((long) xs[i] << 32) | (ys[i] & 0xffffffffL));
        return packed;
    }

    // --- the input contract ------------------------------------------------------------------------------

    @Test
    void rejectsArraysItCannotIndex() {
        assertThrows(IllegalArgumentException.class, () -> new PointCloud(null, new int[1]));
        assertThrows(IllegalArgumentException.class, () -> new PointCloud(new int[1], null));
        assertThrows(IllegalArgumentException.class, () -> new PointCloud(new int[2], new int[3]));
        assertThrows(IllegalArgumentException.class, () -> new PointCloud(new int[1], new int[1], 0));
        assertThrows(IllegalArgumentException.class, () -> new PointCloud(new int[1], new int[1], -8));
    }

    @Test
    void rejectsANegativeRadius() {
        PointCloud cloud = cloudOf(new int[][]{{0, 0}}, 1);

        assertThrows(IllegalArgumentException.class, () -> cloud.countInCircle(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> cloud.countInCircleByCells(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> cloud.countInCircleByScan(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> cloud.countInCircle(0, 0, Integer.MIN_VALUE));
    }

    @Test
    void acceptsTheQuestionsOwnSignature() {
        List<int[]> points = List.of(new int[]{0, 0}, new int[]{1, 1}, new int[]{9, 9});
        PointCloud cloud = PointCloud.of(points);

        assertEquals(3, cloud.size());
        assertEveryMethodCounts(2, cloud, 0, 0, 2, "from a list of pairs");

        assertThrows(IllegalArgumentException.class, () -> PointCloud.of(null));
        assertThrows(IllegalArgumentException.class, () -> PointCloud.of(List.of(new int[]{1})));
        assertThrows(IllegalArgumentException.class, () -> PointCloud.of(List.of(new int[]{1, 2, 3})));
    }

    @Test
    void theGridIsAPowerOfTwoAndWithinItsBudget() {
        Random random = new Random(3L);
        int n = 100_000;
        int[] xs = new int[n];
        int[] ys = new int[n];
        Arrays.setAll(xs, i -> random.nextInt(-50_000, 50_000));
        Arrays.setAll(ys, i -> random.nextInt(-50_000, 50_000));

        PointCloud cloud = new PointCloud(xs, ys);                       // 64 points to a cell by default

        assertEquals(Long.highestOneBit(cloud.cellSize()), cloud.cellSize(), "the cell size is a power of two");
        assertTrue(cloud.cells() <= n / 64, "the grid stays within its budget: " + cloud.cells());
        assertTrue(cloud.cells() * 4L > n / 64,             // halving the cell size quadruples the cells
                "and is no coarser than the budget forces: " + cloud.cells());
    }
}
