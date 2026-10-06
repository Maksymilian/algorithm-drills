package collection;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightedRandomCharTest {

    @Test
    void drawsFollowTheDistribution() {
        Map<Character, Double> distribution = Map.of('a', 0.5, 'b', 0.3, 'c', 0.15, 'd', 0.05);
        WeightedRandomChar generator = new WeightedRandomChar(distribution, new Random(42));
        int draws = 200_000;
        Map<Character, Integer> counts = new TreeMap<>();
        for (int i = 0; i < draws; i++) {
            counts.merge(generator.next(), 1, Integer::sum);
        }
        assertEquals(distribution.keySet(), counts.keySet());
        distribution.forEach((c, p) -> assertEquals(p, counts.get(c) / (double) draws, 0.01, "znak " + c));
    }

    @Test
    void singleCharacterIsAlwaysDrawn() {
        WeightedRandomChar generator = new WeightedRandomChar(Map.of('x', 1.0), new Random(1));
        for (int i = 0; i < 100; i++) {
            assertEquals('x', generator.next());
        }
    }

    @Test
    void rejectsInvalidDistributions() {
        Random random = new Random();
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandomChar(Map.of(), random));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandomChar(Map.of('a', 0.5, 'b', 0.2), random));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandomChar(Map.of('a', 1.5, 'b', -0.5), random));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandomChar(Map.of('a', 0.0, 'b', 1.0), random));
    }
}
