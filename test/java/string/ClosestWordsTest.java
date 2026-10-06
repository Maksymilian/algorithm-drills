package string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClosestWordsTest {

    @Test
    void closestDistanceInWords() {
        assertEquals(1, ClosestWords.closest("a b c a d b", "a", "b"));
        assertEquals(2, ClosestWords.closest("a x b y y y a", "b", "a"));
        assertEquals(-1, ClosestWords.closest("a b c", "a", "z"));
    }

    @Test
    void sameWordMeansConsecutiveOccurrences() {
        assertEquals(2, ClosestWords.closest("a b a c c c a", "a", "a"));
        assertEquals(-1, ClosestWords.closest("a b c", "a", "a"));
    }

    @Test
    void ignoresCaseAndPunctuation() {
        assertEquals(1, ClosestWords.closest("Ala, ma kota! KOTA ma ALA.", "ala", "MA"));
    }
}
