package string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirstUniqueLetterTest {

    @Test
    void firstUniqueLetter() {
        assertEquals(1, FirstUniqueLetter.firstUnique("swiss"));
        assertEquals(4, FirstUniqueLetter.firstUnique("Aabbc"), "A i a to ta sama litera");
        assertEquals(-1, FirstUniqueLetter.firstUnique("aabb"));
        assertEquals(-1, FirstUniqueLetter.firstUnique(""));
        assertEquals(0, FirstUniqueLetter.firstUnique("a"));
        assertEquals(-1, FirstUniqueLetter.firstUnique("bb"));
        assertEquals(-1, FirstUniqueLetter.firstUnique("123 !?"), "bez liter nie ma unikalnej");
        assertEquals(-1, FirstUniqueLetter.firstUnique("abc".repeat(1_000_000)));
        assertEquals(3, FirstUniqueLetter.firstUnique("a1 b a"), "cyfry i spacje pomijamy");
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> FirstUniqueLetter.firstUnique(null));
    }
}
