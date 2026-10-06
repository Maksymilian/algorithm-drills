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

@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PointCloudTest {

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

    @Test
    void theEdgeCounts() {
        int[][] points = {{3, 4}, {-3, -4}, {5, 0}, {0, -5}, {4, 4}, {0, 0}};
        PointCloud cloud = cloudOf(points, 1);

        assertEveryMethodCounts(5, cloud, 0, 0, 5, "the four points at distance exactly 5, and the centre");
        assertEveryMethodCounts(1, cloud, 0, 0, 4, "only the centre, once the edge is pulled in");
        assertEveryMethodCounts(6, cloud, 0, 0, 6, "everything, (4,4) being at distance sqrt(32)");
    }

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

    @Test
    void countsNothingWhenTheCircleIsElsewhere() {
        int[][] points = {{0, 0}, {10, 10}, {5, 5}, {10, 0}, {0, 10}};
        PointCloud cloud = cloudOf(points, 1);

        for (int[] query : new int[][]{{-100, 5, 50}, {110, 5, 50}, {5, -100, 50}, {5, 110, 50}, {-100, -100, 100}}) {
            assertEveryMethodCounts(0, cloud, query[0], query[1], query[2],
                    "away at (" + query[0] + ", " + query[1] + ")");
        }
    }

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

    @Test
    void countsATightCloudAtOneEndOfThePlaneFromTheOther() {
        List<int[]> corner = new ArrayList<>();
        for (int x = 0; x <= 10; x++) {
            for (int y = 0; y <= 10; y++) {
                corner.add(new int[]{Integer.MIN_VALUE + x, y});
            }
        }
        int[][] points = corner.toArray(new int[0][]);
        PointCloud cloud = cloudOf(points, 1);

        assertEquals(1, cloud.cellSize(), "the point of this test is a cell size of one");
        for (int r : new int[]{0, 1, 1_000, Integer.MAX_VALUE}) {
            assertEveryMethodCounts(countExactly(points, Integer.MAX_VALUE, 5, r), cloud,
                    Integer.MAX_VALUE, 5, r, "from the far end of the plane, r=" + r);
            assertEveryMethodCounts(countExactly(points, Integer.MIN_VALUE + 5, 5, r), cloud,
                    Integer.MIN_VALUE + 5, 5, r, "and from inside it, r=" + r);
        }
    }

    @Test
    void theSquareRootHasToBeExact() {
        int r = 1 << 30;
        int[][] points = new int[10][];
        Arrays.setAll(points, i -> new int[]{-r + i, 1});

        PointCloud cloud = cloudOf(points, 1);
        assertEquals(1, cloud.cellSize(), "a cell per point, so a reach off by one moves the run");
        assertEquals(9, countExactly(points, 0, 0, r), "the leftmost point is sqrt(r^2 + 1) away, not sqrt(r^2)");

        assertEveryMethodCounts(9, cloud, 0, 0, r, "a circle reaching exactly sqrt(r^2 - 1) across the row");
    }

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
        PointCloud cloud = new PointCloud(xs, ys, 4);

        long total = 0;
        for (int query = 0; query < 20_000; query++) {
            int cx = random.nextInt(-spread, spread);
            int cy = random.nextInt(-spread, spread);
            long count = cloud.countInCircle(cx, cy, 800_000);

            if (query < 5) {
                assertEquals(cloud.countInCircleByScan(cx, cy, 800_000), count,
                        "at (" + cx + ", " + cy + ")");
            }
            total += count;
        }
        assertTrue(total > 0, "twenty thousand circles of radius 800 000 over ten million points found nothing");
    }

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

        PointCloud cloud = new PointCloud(xs, ys);

        assertEquals(Long.highestOneBit(cloud.cellSize()), cloud.cellSize(), "the cell size is a power of two");
        assertTrue(cloud.cells() <= n / 64, "the grid stays within its budget: " + cloud.cells());
        assertTrue(cloud.cells() * 4L > n / 64,
                "and is no coarser than the budget forces: " + cloud.cells());
    }
}
