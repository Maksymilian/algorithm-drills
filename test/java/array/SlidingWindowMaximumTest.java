package array;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlidingWindowMaximumTest {

    @Test
    void windowMaximaFromTheExample() {
        int[] a = {1, 3, -1, -3, 5, 3, 6, 7};
        assertArrayEquals(new int[]{3, 3, 5, 5, 6, 7}, SlidingWindowMaximum.maxInWindows(a, 3));
        assertArrayEquals(a, SlidingWindowMaximum.maxInWindows(a, 1));
        assertArrayEquals(new int[]{7}, SlidingWindowMaximum.maxInWindows(a, a.length));
    }

    @Test
    void rejectsWindowsThatDoNotFit() {
        assertThrows(IllegalArgumentException.class, () -> SlidingWindowMaximum.maxInWindows(new int[]{1, 2}, 3));
        assertThrows(IllegalArgumentException.class, () -> SlidingWindowMaximum.maxInWindows(new int[]{1, 2}, 0));
    }
}
