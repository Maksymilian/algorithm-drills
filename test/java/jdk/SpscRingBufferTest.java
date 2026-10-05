package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpscRingBuffer}: layout first, then the queue's behaviour with one thread playing both
 * roles in a confined arena, then the real case — a producer and a consumer on a shared arena,
 * with the consumer checking that every value arrives exactly once and in order.
 */
class SpscRingBufferTest {

    // ---------- the layout ----------

    @Test
    void producerAndConsumerIndicesAreOnSeparateCacheLinePairs() {
        var header = SpscRingBuffer.HEADER;
        assertEquals(256, header.byteSize());
        assertEquals(0, header.byteOffset(groupElement("producer"), groupElement("tail")));
        assertEquals(8, header.byteOffset(groupElement("producer"), groupElement("headCache")));
        assertEquals(128, header.byteOffset(groupElement("consumer"), groupElement("head")));
        assertEquals(136, header.byteOffset(groupElement("consumer"), groupElement("tailCache")));
    }

    @Test
    void theHeaderIsAlignedToTheStride() {
        try (Arena arena = Arena.ofConfined()) {
            SpscRingBuffer queue = new SpscRingBuffer(arena, 8);
            assertEquals(0, queue.headerSegment().address() % SpscRingBuffer.STRIDE);
        }
    }

    @Test
    void capacityMustBeAPowerOfTwo() {
        try (Arena arena = Arena.ofConfined()) {
            for (int bad : new int[]{0, -8, 6, 100}) {
                assertThrows(IllegalArgumentException.class, () -> new SpscRingBuffer(arena, bad), "capacity " + bad);
            }
        }
    }

    // ---------- one thread in both roles, confined arena ----------

    @Test
    void offerFailsOnlyWhenFullAndDrainIsFifo() {
        try (Arena arena = Arena.ofConfined()) {
            SpscRingBuffer queue = new SpscRingBuffer(arena, 4);
            for (long v = 10; v < 14; v++) assertTrue(queue.offer(v), "offer " + v);
            assertFalse(queue.offer(99), "full at capacity");
            assertEquals(4, queue.size());

            List<Long> out = new ArrayList<>();
            assertEquals(4, queue.drain(out::add, 10));
            assertEquals(List.of(10L, 11L, 12L, 13L), out);
            assertEquals(0, queue.drain(out::add, 10), "empty");
        }
    }

    @Test
    void drainTakesNoMoreThanMax() {
        try (Arena arena = Arena.ofConfined()) {
            SpscRingBuffer queue = new SpscRingBuffer(arena, 8);
            for (long v = 0; v < 5; v++) queue.offer(v);

            List<Long> out = new ArrayList<>();
            assertEquals(2, queue.drain(out::add, 2));
            assertEquals(List.of(0L, 1L), out);
            assertEquals(3, queue.size());
            assertEquals(0, queue.drain(out::add, 0));
        }
    }

    /** Many laps of a small ring, at every fill level, so the masking of the indices is exercised. */
    @Test
    void wrapsAroundForManyLaps() {
        try (Arena arena = Arena.ofConfined()) {
            SpscRingBuffer queue = new SpscRingBuffer(arena, 4);
            long next = 0;
            long[] expected = {0};
            for (int round = 0; round < 1_000; round++) {
                int burst = round % 5;                     // 0..4: empty up to exactly full
                for (int i = 0; i < burst; i++) assertTrue(queue.offer(next++));
                int got = queue.drain(v -> assertEquals(expected[0]++, v), burst);
                assertEquals(burst, got, "round " + round);
            }
            assertEquals(next, expected[0]);
        }
    }

    @Test
    void aConfinedArenaRejectsTheSecondThread() throws InterruptedException {
        try (Arena arena = Arena.ofConfined()) {
            SpscRingBuffer queue = new SpscRingBuffer(arena, 8);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread consumer = Thread.ofPlatform().start(() -> {
                try {
                    queue.drain(v -> { }, 1);
                } catch (Throwable t) {
                    thrown.set(t);
                }
            });
            consumer.join();
            assertInstanceOf(WrongThreadException.class, thrown.get());
        }
    }

    @Test
    void closingTheArenaFreesTheQueue() {
        SpscRingBuffer queue;
        try (Arena arena = Arena.ofConfined()) {
            queue = new SpscRingBuffer(arena, 8);
            queue.offer(1);
        }
        assertThrows(IllegalStateException.class, () -> queue.offer(2));
    }

    // ---------- a real producer and consumer, shared arena ----------

    /**
     * Ten million values through a 1024-slot ring: every value must arrive once and in order, which
     * is what release/acquire on the indices promises. A missing barrier shows up here as a
     * duplicate, a gap or a stale slot.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void everyValueArrivesOnceAndInOrder() throws InterruptedException {
        long count = 10_000_000;
        try (Arena arena = Arena.ofShared()) {
            SpscRingBuffer queue = new SpscRingBuffer(arena, 1024);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread producer = Thread.ofPlatform().start(() -> {
                for (long v = 0; v < count; v++) {
                    while (!queue.offer(v)) Thread.onSpinWait();
                }
            });
            Thread consumer = Thread.ofPlatform().start(() -> {
                long[] expected = {0};
                try {
                    while (expected[0] < count) {
                        int got = queue.drain(v -> {
                            if (v != expected[0]) throw new AssertionError("expected " + expected[0] + ", got " + v);
                            expected[0]++;
                        }, 256);
                        if (got == 0) Thread.onSpinWait();
                    }
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            producer.join();
            consumer.join();                               // both, before the arena closes

            assertNull(failure.get());
            assertEquals(0, queue.size());
        }
    }
}
