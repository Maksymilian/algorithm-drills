package dynamic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Random;
import java.util.stream.Stream;

import static dynamic.Abbreviation.abbreviation;
import static dynamic.Abbreviation.canAbbreviate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbbreviationTest {

    static Stream<Arguments> knownCases() {
        return Stream.of(
                Arguments.of("sample query", "daBcd", "ABC", true),
                Arguments.of("capitalize and drop", "AbcDE", "ABDE", true),
                Arguments.of("letters cannot be changed", "AbcDE", "AFDE", false),
                Arguments.of("greedy trap: drop, do not capitalize", "aA", "A", true),
                Arguments.of("greedy trap: capitalize as well", "aA", "AA", true),
                Arguments.of("uppercase cannot be dropped", "AB", "B", false),
                Arguments.of("trailing uppercase left unmatched", "beFgH", "EFG", false),
                Arguments.of("drops around the uppercase", "beFgH", "EFH", true),
                Arguments.of("capitalize everything needed", "beFgH", "EFGH", true),
                Arguments.of("uppercase left over", "KXzQ", "K", false),
                Arguments.of("drop the whole string", "abc", "", true),
                Arguments.of("uppercase blocks an empty target", "aBc", "", false),
                Arguments.of("both empty", "", "", true),
                Arguments.of("empty a, non-empty b", "", "A", false),
                Arguments.of("a shorter than b", "A", "AA", false),
                Arguments.of("already equal", "ABC", "ABC", true),
                Arguments.of("uppercase out of order", "ACB", "ABC", false),
                Arguments.of("lowercase in b never matches", "abc", "aBC", false),
                Arguments.of("single letter capitalized", "Pi", "PI", true),
                Arguments.of("single letter dropped", "Pi", "P", true),
                Arguments.of("repeated letters", "aab", "AB", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownCases")
    void decidesKnownQueries(String name, String a, String b, boolean expected) {
        assertEquals(expected, canAbbreviate(a, b));
        assertEquals(expected, tableDp(a, b), "the O(n*m)-memory table disagrees");
        assertEquals(expected, bruteForce(a, b), "brute force disagrees");
    }

    @Test
    void returnsTheAnswerAsYesOrNo() {
        assertEquals("YES", abbreviation("daBcd", "ABC"));
        assertEquals("NO", abbreviation("AbcDE", "AFDE"));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> canAbbreviate(null, "A"));
        assertThrows(IllegalArgumentException.class, () -> canAbbreviate("a", null));
        assertThrows(IllegalArgumentException.class, () -> abbreviation(null, null));
    }

    @Test
    void matchesBruteForceOnRandomQueries() {
        Random random = new Random(20260908L);
        int yesCount = 0;

        for (int trial = 0; trial < 5_000; trial++) {
            String a = randomWord(random, random.nextInt(11), 8);
            String b = randomWord(random, random.nextInt(6), 0);
            boolean expected = bruteForce(a, b);

            assertEquals(expected, canAbbreviate(a, b), a + " -> " + b);
            if (expected) yesCount++;
        }

        assertTrue(yesCount > 500, "too few reachable cases to be a meaningful cross-check: " + yesCount);
    }

    @Test
    void matchesTableDpOnLargeReachableQueries() {
        Random random = new Random(20260908L);

        for (int trial = 0; trial < 5; trial++) {
            String a = randomWord(random, 2_000, 8);
            String reachable = someTargetOf(random, a);

            assertTrue(canAbbreviate(a, reachable), "should be reachable by construction");
            assertEquals(tableDp(a, reachable), canAbbreviate(a, reachable));

            assertFalse(canAbbreviate(a, reachable + "Z"));
            assertEquals(tableDp(a, reachable + "Z"), canAbbreviate(a, reachable + "Z"));
        }
    }

    @Test
    void solvesTenThousandByTenThousand() {
        String a = "ab".repeat(5_000);
        String b = "AB".repeat(5_000);

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            assertTrue(canAbbreviate(a, b));
            assertFalse(canAbbreviate(a + "Z", b));
            assertTrue(canAbbreviate(a + "z", b));
        });
    }

    @Test
    void handlesAVeryLongStringAgainstAShortTarget() {
        String a = "ab".repeat(500_000);

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            assertTrue(canAbbreviate(a, ""));
            assertTrue(canAbbreviate(a, "AB"));
            assertTrue(canAbbreviate(a, "BA"));
            assertFalse(canAbbreviate(a, "ABZ"));
        });
    }

    private static boolean tableDp(String a, String b) {
        int n = a.length();
        int m = b.length();
        boolean[][] reachable = new boolean[n + 1][m + 1];
        reachable[0][0] = true;

        for (int i = 1; i <= n; i++) {
            char c = a.charAt(i - 1);
            boolean droppable = Character.isLowerCase(c);
            reachable[i][0] = reachable[i - 1][0] && droppable;

            for (int j = 1; j <= m; j++) {
                boolean spendOnMatch = Character.toUpperCase(c) == b.charAt(j - 1) && reachable[i - 1][j - 1];
                reachable[i][j] = spendOnMatch || (droppable && reachable[i - 1][j]);
            }
        }

        return reachable[n][m];
    }

    private static boolean bruteForce(String a, String b) {
        int lowercase = (int) a.chars().filter(Character::isLowerCase).count();

        for (int mask = 0; mask < (1 << lowercase); mask++) {
            StringBuilder built = new StringBuilder(a.length());
            int bit = 0;

            for (int i = 0; i < a.length(); i++) {
                char c = a.charAt(i);
                if (Character.isUpperCase(c)) {
                    built.append(c);
                } else if ((mask & (1 << bit++)) != 0) {
                    built.append(Character.toUpperCase(c));
                }
            }

            if (built.toString().contentEquals(b)) return true;
        }

        return false;
    }

    private static String randomWord(Random random, int length, int lowercaseOutOfTen) {
        StringBuilder word = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            char base = random.nextInt(10) < lowercaseOutOfTen ? 'a' : 'A';
            word.append((char) (base + random.nextInt(2)));
        }

        return word.toString();
    }

    private static String someTargetOf(Random random, String a) {
        StringBuilder target = new StringBuilder(a.length());

        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);
            if (Character.isUpperCase(c) || random.nextBoolean()) {
                target.append(Character.toUpperCase(c));
            }
        }

        return target.toString();
    }
}
