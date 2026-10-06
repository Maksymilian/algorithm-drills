package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaddedCountersTest {

    @Test
    void eachSlotIsTwoCacheLinesWithTheValueFirst() {
        assertEquals(128, PaddedCounters.SLOT.byteSize());
        assertEquals(128, PaddedCounters.SLOT.byteAlignment());
        assertEquals(0, PaddedCounters.SLOT.byteOffset(groupElement("value")));
    }

    @Test
    void theStripesAreAlignedToTheStrideAndOneStrideApart() {
        try (Arena arena = Arena.ofConfined()) {
            PaddedCounters counters = new PaddedCounters(arena, 16);
            MemorySegment segment = counters.segment();

            assertEquals(0, segment.address() % PaddedCounters.STRIDE, "address " + segment.address());
            assertEquals(16 * PaddedCounters.STRIDE, segment.byteSize());
        }
    }

    @Test
    void stripesMustBeAPowerOfTwo() {
        try (Arena arena = Arena.ofConfined()) {
            for (int bad : new int[]{0, -4, 3, 12}) {
                assertThrows(IllegalArgumentException.class, () -> new PaddedCounters(arena, bad), "stripes " + bad);
            }
        }
    }

    @Test
    void oneThreadsIncrementsAllLandInItsOwnStripe() {
        try (Arena arena = Arena.ofConfined()) {
            PaddedCounters counters = new PaddedCounters(arena, 8);
            for (int i = 0; i < 1_000; i++) counters.increment();
            counters.add(-10);

            long mine = Thread.currentThread().threadId() & 7;
            for (long s = 0; s < 8; s++) {
                assertEquals(s == mine ? 990 : 0, counters.stripe(s), "stripe " + s);
            }
            assertEquals(990, counters.sum());
        }
    }

    @Test
    void aConfinedArenaRejectsEveryOtherThread() throws InterruptedException {
        try (Arena arena = Arena.ofConfined()) {
            PaddedCounters counters = new PaddedCounters(arena, 8);
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            Thread other = Thread.ofPlatform().start(() -> {
                try {
                    counters.increment();
                } catch (Throwable t) {
                    thrown.set(t);
                }
            });
            other.join();

            assertInstanceOf(WrongThreadException.class, thrown.get());
            assertEquals(0, counters.sum(), "the owner can still use it, and nothing was added");
        }
    }

    @Test
    void closingTheArenaFreesTheStripes() {
        PaddedCounters counters;
        try (Arena arena = Arena.ofConfined()) {
            counters = new PaddedCounters(arena, 8);
            counters.increment();
        }
        assertThrows(IllegalStateException.class, counters::increment);
        assertThrows(IllegalStateException.class, counters::sum);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void noIncrementIsLostAcrossThreads() throws InterruptedException {
        int threads = 8;
        int perThread = 200_000;
        try (Arena arena = Arena.ofShared()) {
            PaddedCounters counters = new PaddedCounters(arena, 16);
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                workers.add(Thread.ofPlatform().start(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    for (int i = 0; i < perThread; i++) counters.increment();
                }));
            }
            start.countDown();
            for (Thread w : workers) w.join();

            assertEquals((long) threads * perThread, counters.sum());
        }
    }
}
