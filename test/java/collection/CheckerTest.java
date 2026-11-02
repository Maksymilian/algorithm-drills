package collection;

import collection.Checker.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckerTest {

    private final Checker checker = new Checker();

    @Test
    void sampleInput() {
        Player[] players = {
                new Player("amy", 100),
                new Player("david", 100),
                new Player("heraldo", 50),
                new Player("aakansha", 75),
                new Player("aleksa", 150),
        };
        Arrays.sort(players, checker);
        assertEquals("[aleksa 150, amy 100, david 100, aakansha 75, heraldo 50]", Arrays.toString(players));
    }

    @Test
    void higherScoreComesFirst() {
        assertTrue(checker.compare(new Player("z", 10), new Player("a", 5)) < 0);
        assertTrue(checker.compare(new Player("a", 5), new Player("z", 10)) > 0);
    }

    @Test
    void equalScoresAreOrderedByNameAscending() {
        assertTrue(checker.compare(new Player("amy", 100), new Player("david", 100)) < 0);
        assertTrue(checker.compare(new Player("david", 100), new Player("amy", 100)) > 0);
        assertTrue(checker.compare(new Player("ab", 1), new Player("abc", 1)) < 0, "prefix sorts first");
    }

    @Test
    void samePlayerDataComparesEqual() {
        assertEquals(0, checker.compare(new Player("amy", 100), new Player("amy", 100)));
    }

    @Test
    void duplicatesKeepInputOrderBecauseSortIsStable() {
        Player first = new Player("amy", 100);
        Player second = new Player("amy", 100);
        Player[] players = {second, new Player("bob", 200), first};
        Arrays.sort(players, checker);
        assertSame(second, players[1]);
        assertSame(first, players[2]);
    }

    @Test
    void extremeScoresDoNotOverflow() {
        Player max = new Player("a", Integer.MAX_VALUE);
        Player min = new Player("b", Integer.MIN_VALUE);
        Player negative = new Player("c", -1);
        assertTrue(checker.compare(max, negative) < 0, "subtraction would overflow here");
        assertTrue(checker.compare(min, max) > 0);
        Player[] players = {negative, min, max};
        Arrays.sort(players, checker);
        assertArrayEquals(new Player[]{max, negative, min}, players);
    }

    @Test
    void negativeAndZeroScores() {
        Player[] players = {new Player("x", -5), new Player("y", 0), new Player("a", -5)};
        Arrays.sort(players, checker);
        assertEquals("[y 0, a -5, x -5]", Arrays.toString(players));
    }

    @Test
    void matchesComparatorChainOnRandomPlayers() {
        Comparator<Player> reference = Comparator.comparingInt(Player::score).reversed()
                .thenComparing(Player::name);
        Random random = new Random(20261102L);
        for (int trial = 0; trial < 1000; trial++) {
            List<Player> players = randomPlayers(random, random.nextInt(30));
            List<Player> expected = new ArrayList<>(players);
            expected.sort(reference);
            players.sort(checker);
            assertEquals(expected, players);
        }
    }

    @Test
    void signIsAntisymmetric() {
        Random random = new Random(7L);
        for (int trial = 0; trial < 10_000; trial++) {
            Player a = randomPlayers(random, 1).get(0);
            Player b = randomPlayers(random, 1).get(0);
            assertEquals(Integer.signum(checker.compare(a, b)), -Integer.signum(checker.compare(b, a)), a + " vs " + b);
        }
    }

    @Test
    void rejectsPlayerWithoutName() {
        assertThrows(NullPointerException.class, () -> new Player(null, 1));
    }

    private static List<Player> randomPlayers(Random random, int count) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = String.valueOf((char) ('a' + random.nextInt(3))).repeat(1 + random.nextInt(2));
            players.add(new Player(name, random.nextInt(7) - 3));
        }
        return players;
    }
}
