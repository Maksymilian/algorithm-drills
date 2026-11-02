package string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsInvalidParameters() {
        assertThrows(NullPointerException.class, () -> ClosestWords.closest((String) null, "a", "b"));
        assertThrows(NullPointerException.class, () -> ClosestWords.closest("a b", null, "b"));
        assertThrows(NullPointerException.class, () -> ClosestWords.closest("a b", "a", null));
        assertThrows(IllegalArgumentException.class, () -> ClosestWords.closest("a b", "", "b"));
        assertThrows(IllegalArgumentException.class, () -> ClosestWords.closest("a b", "a", " "));
        assertThrows(IllegalArgumentException.class, () -> ClosestWords.closest("ala ma kota", "ala ma", "kota"));
        assertThrows(IllegalArgumentException.class, () -> ClosestWords.closest("ala ma kota", "ala", "kota!"));
    }

    @Test
    void emptyTextHasNoPair() {
        assertEquals(-1, ClosestWords.closest("", "a", "b"));
        assertEquals(-1, ClosestWords.closest(" ,.! ", "a", "b"));
    }

    @Test
    void acceptsLettersAndDigitsInAnyScript() {
        assertEquals(1, ClosestWords.closest("Żółw 42 śpi", "ŻÓŁW", "42"));
    }

    @Test
    void ignoresCaseAndPunctuation() {
        assertEquals(1, ClosestWords.closest("Ala, ma kota! KOTA ma ALA.", "ala", "MA"));
    }
}
