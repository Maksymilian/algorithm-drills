package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarHandleVectorTest {

    private static final VarHandle LONG_LANES =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_ELEMENT = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle DOUBLE_ELEMENT = MethodHandles.arrayElementVarHandle(double[].class);

    @ParameterizedTest(name = "length {0}")
    @ValueSource(ints = {0, 1, 2, 7, 8, 9, 15, 16, 17, 23, 31, 33, 40})
    void countMatchesTheScalarVersionAtEveryLength(int length) {
        Random rnd = new Random(length * 1_000L + 1);
        byte[] data = new byte[length];
        rnd.nextBytes(data);
        for (int i = 0; i < length; i += 3) data[i] = 7;

        for (byte value : new byte[]{0, 7, (byte) -1, (byte) 0x80, (byte) 0xFF}) {
            int expected = 0;
            for (byte b : data) if (b == value) expected++;
            assertEquals(expected, VarHandleVector.count(data, value),
                    "value " + value + " in " + Arrays.toString(data));
        }
    }

    @ParameterizedTest(name = "length {0}")
    @ValueSource(ints = {0, 1, 2, 7, 8, 9, 15, 16, 17, 23, 31, 33, 40})
    void indexOfMatchesTheScalarVersionAtEveryLength(int length) {
        Random rnd = new Random(length * 7_919L + 3);
        byte[] data = new byte[length];
        rnd.nextBytes(data);

        for (byte value : new byte[]{0, 42, (byte) -1, (byte) 0x80}) {
            int expected = -1;
            for (int i = 0; i < length; i++) {
                if (data[i] == value) { expected = i; break; }
            }
            assertEquals(expected, VarHandleVector.indexOf(data, value),
                    "value " + value + " in " + Arrays.toString(data));
        }
    }

    @ParameterizedTest(name = "length {0}")
    @ValueSource(ints = {0, 1, 7, 8, 9, 16, 17, 33})
    void laneWiseAddMatchesByteAdditionIncludingItsWrapping(int length) {
        Random rnd = new Random(length * 104_729L + 5);
        byte[] a = new byte[length];
        byte[] b = new byte[length];
        rnd.nextBytes(a);
        rnd.nextBytes(b);
        if (length > 0) {
            a[0] = (byte) 0xFF; b[0] = 1;
            a[length - 1] = (byte) 0x80; b[length - 1] = (byte) 0x80;
        }

        byte[] expected = new byte[length];
        for (int i = 0; i < length; i++) expected[i] = (byte) (a[i] + b[i]);
        assertArrayEquals(expected, VarHandleVector.add(a, b));
    }

    @Test
    void addRejectsMismatchedLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> VarHandleVector.add(new byte[8], new byte[9]));
    }

    @Test
    void theTextbookZeroByteTrickOvercountsAndIsNotWhatIsUsed() {
        long ones = 0x0101010101010101L;
        long highs = 0x8080808080808080L;
        long lows = 0x7F7F7F7F7F7F7F7FL;
        long v = 0xFFFFFFFFFFFF0100L;
        assertEquals(1, Long.bitCount((v - ones) & ~v & highs) - 1,
                "the naive mask marks lane 1 as well, because lane 0 borrowed from it");
        assertEquals(1, Long.bitCount(~((((v & lows) + lows) | v) | lows)),
                "the formula the class uses cannot borrow, so it marks exactly the one zero lane");

        byte[] data = {0, 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(1, VarHandleVector.count(data, (byte) 0), "exactly one byte is zero");
    }

    @Test
    void aByteArrayViewIsPlainAccessOnlyAndThatIsWhatTheSwarLoopNeeds() {
        byte[] data = new byte[32];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;

        assertEquals(0x0706050403020100L, (long) LONG_LANES.get(data, 0));
        assertEquals(0x0807060504030201L, (long) LONG_LANES.get(data, 1), "unaligned, and still fine");

        assertTrue(LONG_LANES.isAccessModeSupported(VarHandle.AccessMode.GET));
        assertTrue(LONG_LANES.isAccessModeSupported(VarHandle.AccessMode.SET));
        assertFalse(LONG_LANES.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE));
        assertFalse(LONG_LANES.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_SET));
        assertThrows(UnsupportedOperationException.class, () -> LONG_LANES.getVolatile(data, 0));

        VarHandle bufferView = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
        assertTrue(bufferView.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE));
        assertThrows(IllegalStateException.class,
                () -> bufferView.getVolatile(ByteBuffer.allocate(32), 0), "heap buffer");
        assertEquals(0L, (long) bufferView.getVolatile(ByteBuffer.allocateDirect(32), 0));
        assertThrows(IllegalStateException.class,
                () -> bufferView.getVolatile(ByteBuffer.allocateDirect(32), 1), "misaligned");

        assertEquals(0x1F1E1D1C1B1A1918L, (long) LONG_LANES.get(data, 24));
        assertThrows(IndexOutOfBoundsException.class, () -> LONG_LANES.get(data, 25));
    }

    @Test
    void getAndAddCoversFloatingPointButTheBitwiseModesDoNot() {
        long[] longs = {5L};
        assertEquals(5L, (long) LONG_ELEMENT.getAndAdd(longs, 0, 3L), "getAndAdd returns the old value");
        assertEquals(8L, longs[0]);
        assertEquals(8L, (long) LONG_ELEMENT.getAndBitwiseOr(longs, 0, 1L));
        assertEquals(9L, longs[0]);

        double[] doubles = {5.0};
        assertTrue(DOUBLE_ELEMENT.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertEquals(5.0, (double) DOUBLE_ELEMENT.getAndAdd(doubles, 0, 3.0));
        assertEquals(8.0, doubles[0]);

        assertFalse(DOUBLE_ELEMENT.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR),
                "bitwise operations are the integral-only ones");
        assertThrows(UnsupportedOperationException.class,
                () -> DOUBLE_ELEMENT.getAndBitwiseOr(doubles, 0, 1.0));
    }

    @Test
    void casOnADoubleComparesBitsRatherThanValues() {
        double[] cell = {0.0};
        assertTrue(0.0 == -0.0, "the values are equal...");
        assertFalse(DOUBLE_ELEMENT.compareAndSet(cell, 0, -0.0, 1.0), "...and their bits are not");
        assertEquals(0.0, cell[0], "so the swap did not happen");

        double[] nan = {Double.NaN};
        assertTrue(Double.NaN != Double.NaN, "NaN is not equal to itself...");
        assertTrue(DOUBLE_ELEMENT.compareAndSet(nan, 0, Double.NaN, 1.0), "...but its bits match");
        assertEquals(1.0, nan[0]);
    }

    @Test
    void maxIntoAgreesWithMathMaxIncludingItsEdgeCases() {
        double[] accumulator = {1.0, 5.0, -0.0, Double.NaN, 3.0, Double.NEGATIVE_INFINITY};
        double[] candidate = {2.0, 4.0, 0.0, 7.0, Double.NaN, Double.NEGATIVE_INFINITY};

        double[] expected = new double[accumulator.length];
        for (int i = 0; i < expected.length; i++) expected[i] = Math.max(accumulator[i], candidate[i]);

        VarHandleVector.maxInto(accumulator, candidate);
        assertArrayEquals(expected, accumulator,
                "Double.compare is what makes NaN win and -0.0 lose, exactly as Math.max does");
        assertEquals(0.0, accumulator[2]);
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(accumulator[2]),
                "+0.0, not -0.0 — a > comparison would have left the -0.0 in place");
    }

    @Test
    @Timeout(60)
    void maxIntoLosesNothingUnderContention() throws InterruptedException {
        int threads = 8;
        int lanes = 32;
        double[] accumulator = new double[lanes];
        Arrays.fill(accumulator, Double.NEGATIVE_INFINITY);
        CountDownLatch go = new CountDownLatch(1);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            workers[t] = new Thread(() -> {
                if (!awaitQuietly(go)) return;
                double[] mine = new double[lanes];
                for (int round = 0; round < 2_000; round++) {
                    for (int i = 0; i < lanes; i++) mine[i] = id * 1_000 + round;
                    VarHandleVector.maxInto(accumulator, mine);
                }
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(30));

        double[] expected = new double[lanes];
        Arrays.fill(expected, (threads - 1) * 1_000 + 1_999);
        assertArrayEquals(expected, accumulator, "the largest value offered must survive every race");
    }

    @ParameterizedTest(name = "{0} threads")
    @ValueSource(ints = {2, 8, 24})
    @Timeout(60)
    void everyLaneGetsEveryAdditionHoweverManyThreadsRun(int threads) throws InterruptedException {
        int lanes = 64;
        int rounds = 2_000;
        long[] accumulator = new long[lanes];
        long[] delta = new long[lanes];
        for (int i = 0; i < lanes; i++) delta[i] = i + 1;
        CountDownLatch go = new CountDownLatch(1);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                if (!awaitQuietly(go)) return;
                for (int i = 0; i < rounds; i++) VarHandleVector.addInto(accumulator, delta);
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(30));

        long[] expected = new long[lanes];
        for (int i = 0; i < lanes; i++) expected[i] = (long) delta[i] * threads * rounds;
        assertArrayEquals(expected, accumulator, "an addition was lost — the per-lane CAS is the point");
    }

    @Test
    @Timeout(60)
    void doublesAccumulateLaneWiseToo() throws InterruptedException {
        int threads = 8;
        int rounds = 2_000;
        int lanes = 16;
        double[] accumulator = new double[lanes];
        double[] delta = new double[lanes];
        Arrays.fill(delta, 0.5);
        CountDownLatch go = new CountDownLatch(1);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                if (!awaitQuietly(go)) return;
                for (int i = 0; i < rounds; i++) VarHandleVector.addInto(accumulator, delta);
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(30));

        double[] expected = new double[lanes];
        Arrays.fill(expected, 0.5 * threads * rounds);
        assertArrayEquals(expected, accumulator);
    }

    private record Tearing(long reads, long inconsistent) {
        double rate() { return reads == 0 ? 0 : (double) inconsistent / reads; }
    }

    private static Tearing tearing(boolean locked, int writers, int millis) throws InterruptedException {
        int lanes = 256;
        long[] accumulator = new long[lanes];
        long[] delta = new long[lanes];
        Arrays.fill(delta, 1L);
        VarHandleLock lock = new VarHandleLock();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong reads = new AtomicLong();
        AtomicLong inconsistent = new AtomicLong();
        CountDownLatch go = new CountDownLatch(1);

        Thread[] threads = new Thread[writers + 1];
        for (int t = 0; t < writers; t++) {
            threads[t] = new Thread(() -> {
                if (!awaitQuietly(go)) return;
                while (!stop.get()) {
                    if (locked) VarHandleVector.addIntoUnderLock(lock, accumulator, delta);
                    else VarHandleVector.addInto(accumulator, delta);
                }
            });
        }
        threads[writers] = new Thread(() -> {
            if (!awaitQuietly(go)) return;
            long seen = 0, bad = 0;
            while (!stop.get()) {
                long[] copy = locked
                        ? VarHandleVector.snapshot(lock, accumulator)
                        : readLanes(accumulator);
                seen++;
                for (int i = 1; i < copy.length; i++) {
                    if (copy[i] != copy[0]) { bad++; break; }
                }
            }
            reads.set(seen);
            inconsistent.set(bad);
        });
        for (Thread t : threads) {
            t.setDaemon(true);
            t.start();
        }
        go.countDown();
        Thread.sleep(millis);
        stop.set(true);
        for (Thread t : threads) t.join(TimeUnit.SECONDS.toMillis(30));
        return new Tearing(reads.get(), inconsistent.get());
    }

    private static long[] readLanes(long[] source) {
        long[] copy = new long[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = (long) LONG_ELEMENT.getVolatile(source, i);
        return copy;
    }

    @Test
    @Timeout(120)
    void perLaneAtomicityDoesNotGiveTheReaderAConsistentVector() throws InterruptedException {
        Tearing seen = tearing(false, 4, 300);
        assertTrue(seen.reads() > 100, "only " + seen.reads() + " reads — too few to mean anything");
        assertTrue(seen.inconsistent() > 0,
                "no torn vector in " + seen.reads() + " reads; every lane being atomic is not"
                        + " supposed to make the vector atomic");
    }

    @Test
    @Timeout(120)
    void oneLockOverTheWholeVectorRemovesTheTearing() throws InterruptedException {
        Tearing seen = tearing(true, 4, 300);
        assertTrue(seen.reads() > 100, "only " + seen.reads() + " reads — too few to mean anything");
        assertEquals(0, seen.inconsistent(),
                "a snapshot taken under the same lock as the writers must never disagree with itself");
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
