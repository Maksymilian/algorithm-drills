package string;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordPrefixPositionsTest {

    @Test
    void prefixPositionsFromTheExample() {
        assertEquals(List.of(0, 3, 7), WordPrefixPositions.positions("aa aaa AaC a bb", "aa"));
    }

    @Test
    void prefixMustStartAWordAndFitInsideIt() {
        assertEquals(List.of(), WordPrefixPositions.positions("baa caa", "aa"), "aa w środku słowa");
        assertEquals(List.of(), WordPrefixPositions.positions("a a", "a a"), "prefiks nie przeskakuje odstępu");
        assertEquals(List.of(2, 10), WordPrefixPositions.positions("  Kot\tkto Kotlet", "kot"), "tabulator też dzieli");
        assertEquals(List.of(0, 4), WordPrefixPositions.positions("ala ma", ""), "pusty prefiks: każde słowo");
    }

    @Test
    void prefixIndexAnswersTheSameAsTheScan() {
        Random random = new Random(3);
        for (int round = 0; round < 100; round++) {
            String document = randomDocument(random);
            WordPrefixPositions.Index index = new WordPrefixPositions.Index(document);
            for (String prefix : new String[]{"a", "AB", "b", "ba", "abc", "x", ""}) {
                assertEquals(WordPrefixPositions.positions(document, prefix), index.positions(prefix),
                        document + " / " + prefix);
            }
        }
    }

    private static String randomDocument(Random random) {
        String[] pool = {"a", "aa", "Ab", "abc", "b", "BA", "bab", "c"};
        StringBuilder document = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            document.append(pool[random.nextInt(pool.length)]).append(" ".repeat(1 + random.nextInt(2)));
        }
        return document.toString();
    }
}
