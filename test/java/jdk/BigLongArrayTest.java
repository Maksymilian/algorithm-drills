package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BigLongArray} against the {@code int} limit on Java arrays: first the ways an array refuses
 * to grow past it, then a segment that does.
 *
 * <p>The large tests use a sparse memory-mapped file. A 16 GiB array costs only the few pages it
 * writes, so it runs on any machine and under surefire's {@code -Xmx512m}. Every arena is confined
 * and opened with try-with-resources: these tests have one thread, and closing the arena frees the
 * memory or unmaps the file.
 */
class BigLongArrayTest {

    /** Two to the 31st plus a little: the first index no array can reach. */
    private static final long PAST_INT = Integer.MAX_VALUE + 16L;

    // ---------- an array will not go past an int ----------

    @Test
    void anArrayLengthIsAnIntAndOverflowsToNegative() {
        int length = Integer.MAX_VALUE;
        // one more element than an int can count: the length wraps to -2^31 before the array sees it
        assertThrows(NegativeArraySizeException.class, () -> { byte[] a = new byte[length + 1]; });
    }

    /** HotSpot refuses even {@code Integer.MAX_VALUE} itself, whatever the heap size. */
    @Test
    void theLargestIntLengthIsStillOverTheVmLimit() {
        OutOfMemoryError e = assertThrows(OutOfMemoryError.class, () -> { byte[] a = new byte[Integer.MAX_VALUE]; });
        assertTrue(e.getMessage().contains("exceeds VM limit"), e.getMessage());
    }

    /** {@code long[]} has the same element limit: 2^31 longs would be 16 GiB of heap, and the index cannot express it. */
    @Test
    void aLongArrayCannotBeIndexedPastAnIntEither() {
        long[] small = new long[16];
        long index = PAST_INT;
        // the only way to use a long index on an array is to narrow it, and the narrowing wraps
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> { long v = small[(int) index]; });
        assertEquals(-2147483633, (int) index, "the cast wraps rather than failing");
    }

    // ---------- a segment will ----------

    @Test
    void aMappedArrayHoldsMoreElementsThanAnyArrayCan(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("big.bin");
        try (Arena arena = Arena.ofConfined()) {
            BigLongArray big = BigLongArray.map(arena, file, PAST_INT);

            assertEquals(PAST_INT, big.length());
            assertTrue(big.length() > Integer.MAX_VALUE);
            assertEquals(PAST_INT * Long.BYTES, Files.size(file), "16 GiB, almost all of it sparse");

            big.set(0, 1);
            big.set(Integer.MAX_VALUE, 2);                  // the last index an int can name
            big.set(Integer.MAX_VALUE + 1L, 3);             // the first one it cannot
            big.set(PAST_INT - 1, 4);                       // the last element

            assertEquals(1, big.get(0));
            assertEquals(2, big.get(Integer.MAX_VALUE));
            assertEquals(3, big.get(Integer.MAX_VALUE + 1L));
            assertEquals(4, big.get(PAST_INT - 1));
            assertEquals(0, big.get(Integer.MAX_VALUE + 2L), "unwritten pages read as zero");
        }
    }

    @Test
    void loopsRunAcrossTheIntBoundaryWithoutWrapping(@TempDir Path dir) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            BigLongArray big = BigLongArray.map(arena, dir.resolve("big.bin"), PAST_INT);
            long from = Integer.MAX_VALUE - 4L, to = Integer.MAX_VALUE + 4L;

            big.fill(from, to, 3);
            assertEquals(8 * 3, big.sum(from, to));
            assertEquals(0, big.get(from - 1));
            assertEquals(0, big.get(to));

            big.fill(from, to, 0);
            assertEquals(0, big.sum(from, to));
        }
    }

    /**
     * Past the end of a segment is still an error — it is long-indexed, not unbounded. The exception
     * depends on how the index is wrong, and only the first case matches what an array throws.
     */
    @Test
    void indicesOutsideTheSegmentAreRejected(@TempDir Path dir) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            BigLongArray big = BigLongArray.map(arena, dir.resolve("big.bin"), PAST_INT);

            assertThrows(IndexOutOfBoundsException.class, () -> big.get(PAST_INT));
            assertThrows(IndexOutOfBoundsException.class, () -> big.set(PAST_INT, 1));
            // a negative index is refused before the bounds check, when the layout scales it to bytes
            assertThrows(IllegalArgumentException.class, () -> big.get(-1));
            // and an index whose byte offset does not fit in a long overflows during the same scaling
            assertThrows(ArithmeticException.class, () -> big.get(Long.MAX_VALUE));
        }
    }

    /** Copying back into an array brings the {@code int} limit straight back. */
    @Test
    void copyingIntoAnArrayHitsTheIntLimitAgain(@TempDir Path dir) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            BigLongArray big = BigLongArray.map(arena, dir.resolve("big.bin"), PAST_INT);

            assertThrows(IllegalStateException.class, () -> big.segment().toArray(JAVA_LONG));
            // even as bytes it is far too large: the limit is on the element count of the result
            assertThrows(IllegalStateException.class, () -> big.segment().toArray(JAVA_BYTE));
            // a slice that fits is fine
            assertEquals(4, big.segment().asSlice(0, 4 * Long.BYTES).toArray(JAVA_LONG).length);
        }
    }

    @Test
    void theFileOutlivesTheArenaAndMapsBackIn(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("big.bin");
        try (Arena arena = Arena.ofConfined()) {
            BigLongArray.map(arena, file, PAST_INT).set(Integer.MAX_VALUE + 5L, 42);
        }                                                    // unmapped here
        try (Arena arena = Arena.ofConfined()) {
            assertEquals(42, BigLongArray.map(arena, file, PAST_INT).get(Integer.MAX_VALUE + 5L));
        }
    }

    // ---------- native memory is not heap ----------

    /** More than the whole heap, allocated natively: {@code -Xmx} does not limit an arena. */
    @Test
    void nativeMemoryIsNotLimitedByTheHeap() {
        long heap = Runtime.getRuntime().maxMemory();
        long length = (heap + (64L << 20)) / Long.BYTES;   // the heap's size plus 64 MiB
        try (Arena arena = Arena.ofConfined()) {
            BigLongArray big = BigLongArray.allocate(arena, length);
            assertTrue(big.length() * Long.BYTES > heap);

            big.set(length - 1, 7);
            assertEquals(7, big.get(length - 1));
            assertEquals(0, big.get(length / 2), "allocate zeroes the memory");
        }
    }

    @Test
    void closingTheArenaInvalidatesTheArray(@TempDir Path dir) throws IOException {
        BigLongArray big;
        try (Arena arena = Arena.ofConfined()) {
            big = BigLongArray.map(arena, dir.resolve("big.bin"), 1024);
        }
        assertThrows(IllegalStateException.class, () -> big.get(0));
    }

    @Test
    void aNegativeLengthIsRejected(@TempDir Path dir) {
        try (Arena arena = Arena.ofConfined()) {
            assertThrows(IllegalArgumentException.class, () -> BigLongArray.allocate(arena, -1));
            assertThrows(IllegalArgumentException.class, () -> BigLongArray.map(arena, dir.resolve("x.bin"), -1));
        }
    }
}
