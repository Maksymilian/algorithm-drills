package array;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrappingWaterTest {

    private static int[] randomArray(Random random, int length, int bound) {
        int[] a = new int[length];
        Arrays.setAll(a, i -> random.nextInt(bound));
        return a;
    }

    @Test
    void snowBetweenHillsFromTheExample() {
        int[] hills = {0, 1, 2, 1, 0, 3, 1, 2};
        assertEquals(4, TrappingWater.trap(hills));
        assertEquals(4, TrappingWater.trapWithArrays(hills));
        assertEquals(4, TrappingWater.trapWithDeque(hills));
    }

    @Test
    void snowBetweenHillsMyExample() {
        int[] hills = {1, 3, 1, 4, 1, 5, 3};
        assertEquals(5, TrappingWater.trap(hills));
        assertEquals(5, TrappingWater.trapWithArrays(hills));
        assertEquals(5, TrappingWater.trapWithDeque(hills));
    }

    @Test
    void classicRainWaterExamples() {
        assertEquals(6, TrappingWater.trap(new int[]{0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1}));
        assertEquals(9, TrappingWater.trap(new int[]{4, 2, 0, 3, 2, 5}));
        assertEquals(0, TrappingWater.trap(new int[]{1, 2, 3, 4}), "same zbocze, nic się nie trzyma");
        assertEquals(0, TrappingWater.trap(new int[]{}));
        assertEquals(0, TrappingWater.trap(new int[]{5}));
        assertEquals(6, TrappingWater.trapWithDeque(new int[]{0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1}));
        assertEquals(9, TrappingWater.trapWithDeque(new int[]{4, 2, 0, 3, 2, 5}));
        assertEquals(0, TrappingWater.trapWithDeque(new int[]{}));
        assertEquals(4, TrappingWater.trapWithDeque(new int[]{3, 3, 1, 1, 3}), "równe wysokości: dno o szerokości 2");
    }

    @Test
    void twoPointersAndDequeAgreeWithPrefixMaxima() {
        Random random = new Random(1);
        for (int round = 0; round < 1000; round++) {
            int[] h = randomArray(random, random.nextInt(15), 6);
            long expected = TrappingWater.trapWithArrays(h);
            assertEquals(expected, TrappingWater.trap(h), Arrays.toString(h));
            assertEquals(expected, TrappingWater.trapWithDeque(h), Arrays.toString(h));
        }
    }
}
