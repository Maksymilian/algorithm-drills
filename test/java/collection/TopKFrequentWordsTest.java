package collection;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopKFrequentWordsTest {

    @Test
    void mostFrequentFirstThenAlphabetical() {
        List<String> words = List.of("kot", "pies", "kot", "mysz", "pies", "kot", "ryba", "mysz");
        assertEquals(List.of("kot", "mysz"), TopKFrequentWords.topK(words, 2));
        assertEquals(List.of("kot", "mysz", "pies", "ryba"), TopKFrequentWords.topK(words, 10));
        assertThrows(IllegalArgumentException.class, () -> TopKFrequentWords.topK(words, 0));
    }

    @Test
    void heapAgreesWithSortingEverything() {
        Random random = new Random(6);
        for (int round = 0; round < 200; round++) {
            List<String> words = random.ints(40, 0, 8).mapToObj(i -> String.valueOf((char) ('a' + i))).toList();
            int k = 1 + random.nextInt(8);
            assertEquals(topKBySortingEverything(words, k), TopKFrequentWords.topK(words, k));
        }
    }

    private static List<String> topKBySortingEverything(List<String> words, int k) {
        Map<String, Long> counts = words.stream().collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(k)
                .map(Map.Entry::getKey)
                .toList();
    }
}
