package greedy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import static greedy.LuckBalance.luckBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LuckBalanceTest {

    static Stream<Arguments> knownCases() {
        return Stream.of(
                Arguments.of("sample", 3, new int[][]{{5, 1}, {2, 1}, {1, 1}, {8, 1}, {10, 0}, {5, 0}}, 29),
                Arguments.of("statement example, k = 2", 2, new int[][]{{5, 1}, {1, 1}, {4, 0}}, 10),
                Arguments.of("statement example, k = 1", 1, new int[][]{{5, 1}, {1, 1}, {4, 0}}, 8),
                Arguments.of("no contests", 0, new int[][]{}, 0),
                Arguments.of("single unimportant", 0, new int[][]{{7, 0}}, 7),
                Arguments.of("single important, k = 0", 0, new int[][]{{7, 1}}, -7),
                Arguments.of("single important, k = 1", 1, new int[][]{{7, 1}}, 7),
                Arguments.of("k = 0 wins every important", 0, new int[][]{{3, 1}, {4, 1}, {10, 0}}, 3),
                Arguments.of("k above important count", 100, new int[][]{{3, 1}, {4, 1}}, 7),
                Arguments.of("only unimportant", 0, new int[][]{{1, 0}, {2, 0}, {3, 0}}, 6),
                Arguments.of("result may be negative", 0, new int[][]{{5, 1}, {6, 1}, {1, 0}}, -10),
                Arguments.of("duplicate luck values", 1, new int[][]{{4, 1}, {4, 1}, {4, 1}}, -4),
                Arguments.of("zero luck contests", 0, new int[][]{{0, 1}, {0, 0}}, 0),
                Arguments.of("input order does not matter", 2, new int[][]{{8, 1}, {1, 1}, {5, 1}, {2, 1}}, 10),
                Arguments.of("result exactly Integer.MAX_VALUE", 0,
                        new int[][]{{Integer.MAX_VALUE, 0}}, Integer.MAX_VALUE),
                Arguments.of("intermediate sum above int is fine", 0,
                        new int[][]{{Integer.MAX_VALUE, 0}, {Integer.MAX_VALUE, 1}}, 0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownCases")
    void findsTheMaximumLuck(String name, int k, int[][] contests, int expected) {
        assertEquals(expected, luckBalance(k, contests), name);
    }

    @Test
    void leavesTheInputAlone() {
        int[][] contests = {{5, 1}, {2, 1}, {1, 1}};
        String original = Arrays.deepToString(contests);

        luckBalance(1, contests);

        assertEquals(original, Arrays.deepToString(contests), "input must not be reordered");
    }

    @Test
    void matchesBruteForceOnRandomInputs() {
        Random random = new Random(42);
        for (int round = 0; round < 2_000; round++) {
            int[][] contests = randomContests(random, random.nextInt(11), 20);
            int k = random.nextInt(contests.length + 2);

            assertEquals(bruteForce(k, contests), luckBalance(k, contests),
                    "k=%d contests=%s".formatted(k, Arrays.deepToString(contests)));
        }
    }

    @Test
    void handlesLargeInputQuickly() {
        int[][] contests = randomContests(new Random(7), 1_000_000, 1_000);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> luckBalance(500_000, contests));
    }

    @Test
    void overflowingResultThrows() {
        int[][] contests = {{Integer.MAX_VALUE, 0}, {1, 0}};

        assertThrows(ArithmeticException.class, () -> luckBalance(0, contests));
    }

    @Test
    void underflowingResultThrows() {
        int[][] contests = {{Integer.MAX_VALUE, 1}, {Integer.MAX_VALUE, 1}};

        assertThrows(ArithmeticException.class, () -> luckBalance(0, contests));
    }

    @Test
    void rejectsNullContests() {
        assertThrows(NullPointerException.class, () -> luckBalance(1, null));
    }

    @Test
    void rejectsNullContestInside() {
        assertThrows(NullPointerException.class, () -> luckBalance(1, new int[][]{{1, 1}, null}));
    }

    @Test
    void rejectsNegativeK() {
        assertThrows(IllegalArgumentException.class, () -> luckBalance(-1, new int[][]{{1, 1}}));
    }

    @Test
    void rejectsWrongContestLength() {
        assertThrows(IllegalArgumentException.class, () -> luckBalance(1, new int[][]{{1}}));
        assertThrows(IllegalArgumentException.class, () -> luckBalance(1, new int[][]{{1, 1, 1}}));
    }

    @Test
    void rejectsNegativeLuck() {
        assertThrows(IllegalArgumentException.class, () -> luckBalance(1, new int[][]{{-1, 0}}));
    }

    @Test
    void rejectsUnknownImportance() {
        assertThrows(IllegalArgumentException.class, () -> luckBalance(1, new int[][]{{1, 2}}));
        assertThrows(IllegalArgumentException.class, () -> luckBalance(1, new int[][]{{1, -1}}));
    }

    private static int[][] randomContests(Random random, int count, int maxLuck) {
        int[][] contests = new int[count][];
        for (int i = 0; i < count; i++) {
            contests[i] = new int[]{random.nextInt(maxLuck + 1), random.nextInt(2)};
        }
        return contests;
    }

    private static int bruteForce(int k, int[][] contests) {
        int best = Integer.MIN_VALUE;
        for (int lostMask = 0; lostMask < 1 << contests.length; lostMask++) {
            if (lostImportant(lostMask, contests) <= k) {
                best = Math.max(best, balance(lostMask, contests));
            }
        }
        return best;
    }

    private static int lostImportant(int lostMask, int[][] contests) {
        int lost = 0;
        for (int i = 0; i < contests.length; i++) {
            if ((lostMask >> i & 1) == 1 && contests[i][1] == 1) {
                lost++;
            }
        }
        return lost;
    }

    private static int balance(int lostMask, int[][] contests) {
        int luck = 0;
        for (int i = 0; i < contests.length; i++) {
            luck += (lostMask >> i & 1) == 1 ? contests[i][0] : -contests[i][0];
        }
        return luck;
    }
}
