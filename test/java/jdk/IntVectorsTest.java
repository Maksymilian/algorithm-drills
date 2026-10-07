package jdk;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntVectorsTest {

    static IntStream lengths() {
        return IntStream.rangeClosed(0, 40);
    }

    private static int[] random(int length, int bound, long seed) {
        int[] a = new int[length];
        Random random = new Random(seed);
        Arrays.setAll(a, i -> random.nextInt(bound));
        return a;
    }

    @ParameterizedTest(name = "length {0}")
    @MethodSource("lengths")
    void addMatchesTheScalarLoopAtEveryLength(int length) {
        int[] a = random(length, 1_000, 1), b = random(length, 1_000, 2);
        int[] expected = new int[length];
        Arrays.setAll(expected, i -> a[i] + b[i]);
        assertArrayEquals(expected, IntVectors.add(a, b));
    }

    @ParameterizedTest(name = "length {0}")
    @MethodSource("lengths")
    void sumMatchesTheScalarLoopAtEveryLength(int length) {
        int[] a = random(length, 1_000, 3);
        assertEquals(Arrays.stream(a).sum(), IntVectors.sum(a));
    }

    @ParameterizedTest(name = "length {0}")
    @MethodSource("lengths")
    void countMatchesTheScalarLoopAtEveryLength(int length) {
        int[] a = random(length, 4, 4);
        assertEquals(Arrays.stream(a).filter(v -> v == 2).count(), IntVectors.count(a, 2));
    }

    @ParameterizedTest(name = "length {0}")
    @MethodSource("lengths")
    void indexOfFindsTheFirstOccurrenceAtEveryPosition(int length) {
        for (int at = 0; at < length; at++) {
            int[] a = new int[length];
            a[at] = 7;
            a[length - 1] = 7;
            assertEquals(at, IntVectors.indexOf(a, 7), "length " + length + ", first at " + at);
        }
        assertEquals(-1, IntVectors.indexOf(new int[length], 7));
    }

    @Test
    void sumWrapsExactlyLikeTheScalarLoop() {
        int[] a = new int[1_003];
        Arrays.fill(a, Integer.MAX_VALUE);
        assertEquals(Arrays.stream(a).sum(), IntVectors.sum(a));
    }

    @Test
    void largeRandomArraysAgreeWithTheScalarLoops() {
        int[] a = random(1_000_003, Integer.MAX_VALUE, 5), b = random(1_000_003, 16, 6);
        assertEquals(Arrays.stream(a).sum(), IntVectors.sum(a));
        assertEquals(Arrays.stream(b).filter(v -> v == 3).count(), IntVectors.count(b, 3));
        assertEquals(IntStream.range(0, b.length).filter(i -> b[i] == 15).findFirst().orElse(-1),
                IntVectors.indexOf(b, 15));
    }

    @Test
    void theVectorIsAPowerOfTwoNumberOfLanes() {
        assertTrue(IntVectors.lanes() >= 1 && Integer.bitCount(IntVectors.lanes()) == 1, "lanes " + IntVectors.lanes());
    }

    @Test
    void addRejectsArraysOfDifferentLengths() {
        assertThrows(IllegalArgumentException.class, () -> IntVectors.add(new int[3], new int[4]));
    }
}
