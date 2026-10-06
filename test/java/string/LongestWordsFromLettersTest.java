package string;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LongestWordsFromLettersTest {

    @Test
    void longestWordsFromTheExample() {
        List<String> words = List.of("toes", "toe", "abc", "stop", "baseball");
        assertEquals(List.of("toes", "stop"), LongestWordsFromLetters.longestWords("toestp", words));
    }

    @Test
    void eachLetterCanBeUsedOnlyAsOftenAsItAppears() {
        assertEquals(List.of("ab"), LongestWordsFromLetters.longestWords("abc", List.of("aab", "ab", "a")));
        assertEquals(List.of("aab"), LongestWordsFromLetters.longestWords("aabc", List.of("aab", "ab")));
    }

    @Test
    void ignoresCaseAndReturnsNothingWhenNoWordFits() {
        assertEquals(List.of("Stop"), LongestWordsFromLetters.longestWords("TOESTP", List.of("Stop", "xyz")));
        assertEquals(List.of(), LongestWordsFromLetters.longestWords("abc", List.of("xyz", "")));
        assertThrows(IllegalArgumentException.class, () -> LongestWordsFromLetters.longestWords("a b", List.of()));
    }
}
