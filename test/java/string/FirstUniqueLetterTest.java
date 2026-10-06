package string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstUniqueLetterTest {

    @Test
    void firstUniqueLetter() {
        assertEquals(1, FirstUniqueLetter.firstUnique("swiss"));
        assertEquals(4, FirstUniqueLetter.firstUnique("Aabbc"), "A i a to ta sama litera");
        assertEquals(-1, FirstUniqueLetter.firstUnique("aabb"));
        assertEquals(-1, FirstUniqueLetter.firstUnique(""));
        assertEquals(3, FirstUniqueLetter.firstUnique("a1 b a"), "cyfry i spacje pomijamy");
    }
}
