package jdk;

import jdk.EqualShareSemaphore.Ticket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EqualShareSemaphoreTest {

    private static final long NOW = 0L;

    static Stream<Arguments> configurations() {
        return Stream.of(
                Arguments.of(10, 3, 3, 1),
                Arguments.of(12, 4, 3, 0),
                Arguments.of(7, 3, 2, 1),
                Arguments.of(5, 2, 2, 1),
                Arguments.of(1, 1, 1, 0),
                Arguments.of(100, 7, 14, 2),
                Arguments.of(1_000_000, 3, 333_333, 1));
    }

    @ParameterizedTest(name = "{0} permits / {1} parties -> share {2}, surplus {3}")
    @MethodSource("configurations")
    void splitsPermitsEqually(int total, int parties, int share, int surplus) {
        EqualShareSemaphore gate = new EqualShareSemaphore(total, parties);
        assertEquals(share, gate.guaranteedShare());
        assertEquals(surplus, gate.surplus());
        assertEquals(share + surplus, gate.maxPerParty());
        assertEquals(total, gate.totalPermits());
        assertEquals(parties, gate.parties());
        assertEquals(total, parties * gate.guaranteedShare() + gate.surplus());
        assertTrue(gate.surplus() < parties, "surplus must be the remainder, not a second share");
    }

    @Test
    void rejectsConfigurationsItCannotGuarantee() {
        assertThrows(IllegalArgumentException.class, () -> new EqualShareSemaphore(2, 3));
        assertThrows(IllegalArgumentException.class, () -> new EqualShareSemaphore(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EqualShareSemaphore(10, 0));
        assertThrows(IllegalArgumentException.class, () -> new EqualShareSemaphore(10, -1));
    }

    @Test
    void rejectsPartiesItDoesNotHave() throws InterruptedException {
        EqualShareSemaphore gate = new EqualShareSemaphore(10, 3);
        assertThrows(IndexOutOfBoundsException.class, () -> gate.acquire(3));
        assertThrows(IndexOutOfBoundsException.class, () -> gate.acquire(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> gate.tryAcquire(3, NOW, TimeUnit.MILLISECONDS));
        assertThrows(IndexOutOfBoundsException.class, () -> gate.held(3));
        assertEquals(0, gate.totalHeld(), "a rejected call must not have taken a permit");
    }

    static Stream<Arguments> smallConfigurations() {
        return Stream.of(
                Arguments.of(4, 2), Arguments.of(5, 2), Arguments.of(6, 3),
                Arguments.of(7, 3), Arguments.of(9, 4), Arguments.of(10, 3),
                Arguments.of(12, 5));
    }

    @ParameterizedTest(name = "{0} permits / {1} parties")
    @MethodSource("smallConfigurations")
    void everyReachableStateKeepsTheGuarantee(int total, int parties) throws InterruptedException {
        EqualShareSemaphore probe = new EqualShareSemaphore(total, parties);
        int share = probe.guaranteedShare();
        int surplus = probe.surplus();
        int states = 0;

        for (int[] holdings : legalHoldings(parties, share, surplus)) {
            for (boolean reverseOrder : new boolean[]{false, true}) {
                EqualShareSemaphore gate = new EqualShareSemaphore(total, parties);
                List<Ticket> held = new ArrayList<>();

                for (int step = 0; step < parties; step++) {
                    int party = reverseOrder ? parties - 1 - step : step;
                    for (int i = 0; i < holdings[party]; i++) {
                        Ticket t = gate.tryAcquire(party, NOW, TimeUnit.MILLISECONDS);
                        assertNotNull(t, () -> "refused while building a legal state "
                                + java.util.Arrays.toString(holdings));
                        held.add(t);
                    }
                }

                int sum = java.util.Arrays.stream(holdings).sum();
                assertEquals(sum, gate.totalHeld());
                assertTrue(sum <= total, "the total was exceeded");

                for (int p = 0; p < parties; p++) {
                    Ticket extra = gate.tryAcquire(p, NOW, TimeUnit.MILLISECONDS);
                    if (holdings[p] < share) {
                        assertNotNull(extra, "party below its share was refused in state "
                                + java.util.Arrays.toString(holdings));
                    } else if (holdings[p] == share + surplus || sum == total) {
                        assertNull(extra, "party at its cap was admitted in state "
                                + java.util.Arrays.toString(holdings));
                    }
                    if (extra != null) extra.close();
                }

                held.forEach(Ticket::close);
                assertEquals(0, gate.totalHeld(), "permits leaked out of state "
                        + java.util.Arrays.toString(holdings));
                assertEquals(0, gate.belowShareDenials());
                states++;
            }
        }
        assertTrue(states > 0, "the enumeration produced no states");
    }

    private static List<int[]> legalHoldings(int parties, int share, int surplus) {
        List<int[]> out = new ArrayList<>();
        build(new int[parties], 0, share, surplus, out);
        return out;
    }

    private static void build(int[] current, int index, int share, int surplus, List<int[]> out) {
        if (index == current.length) {
            int borrowed = 0;
            for (int h : current) borrowed += Math.max(0, h - share);
            if (borrowed <= surplus) out.add(current.clone());
            return;
        }
        for (int h = 0; h <= share + surplus; h++) {
            current[index] = h;
            build(current, index + 1, share, surplus, out);
        }
        current[index] = 0;
    }

    @Test
    void oneGreedyPartyCannotStarveTheOthers() throws InterruptedException {
        EqualShareSemaphore gate = new EqualShareSemaphore(10, 3);
        List<Ticket> greedy = drain(gate, 0);
        assertEquals(4, greedy.size(), "share 3 + the whole surplus of 1");

        List<Ticket> others = new ArrayList<>();
        others.addAll(drain(gate, 1));
        others.addAll(drain(gate, 2));
        assertEquals(6, others.size());
        assertEquals(10, gate.totalHeld());
        assertNull(gate.tryAcquire(1, NOW, TimeUnit.MILLISECONDS), "the 11th permit does not exist");
        assertEquals(0, gate.belowShareDenials());

        greedy.forEach(Ticket::close);
        others.forEach(Ticket::close);
        assertEquals(0, gate.totalHeld());
    }

    @Test
    void capacityIsFullyRestored() throws InterruptedException {
        EqualShareSemaphore gate = new EqualShareSemaphore(10, 3);
        for (int round = 0; round < 3; round++) {
            List<Ticket> tickets = drain(gate, round % 3);
            assertEquals(4, tickets.size(), "round " + round + " saw shrunken capacity");
            tickets.forEach(Ticket::close);
            assertEquals(0, gate.totalHeld());
        }
    }

    @Test
    void closingATicketTwiceDoesNotInflatePermits() throws InterruptedException {
        EqualShareSemaphore gate = new EqualShareSemaphore(3, 3);
        Ticket ticket = gate.acquire(0);
        ticket.close();
        ticket.close();
        assertEquals(0, gate.totalHeld());
        assertEquals(3, drainAll(gate).size(), "a double close must not create a permit");
    }

    @Test
    void aTicketCanBeReleasedByAnotherThread() throws Exception {
        EqualShareSemaphore gate = new EqualShareSemaphore(2, 2);
        Ticket ticket = gate.acquire(0);
        Thread worker = new Thread(ticket::close);
        worker.start();
        worker.join();
        assertEquals(0, gate.totalHeld());
    }

    @Test
    @Timeout(30)
    void aSurplusWaiterIsWokenWhenTheSurplusComesBack() throws Exception {
        EqualShareSemaphore gate = new EqualShareSemaphore(5, 2);
        List<Ticket> hog = drain(gate, 1);
        assertEquals(3, hog.size());
        List<Ticket> mine = drain(gate, 0);
        assertEquals(2, mine.size());
        assertEquals(5, gate.totalHeld());

        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Ticket> got = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                got.set(gate.acquire(0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        waiting.await();
        Thread.sleep(50);
        assertTrue(waiter.isAlive(), "the surplus is held elsewhere; this must wait");

        hog.get(2).close();
        waiter.join(TimeUnit.SECONDS.toMillis(10));
        assertNotNull(got.get(), "the returned surplus permit never reached the waiter");

        got.get().close();
        hog.get(0).close();
        hog.get(1).close();
        mine.forEach(Ticket::close);
        assertEquals(0, gate.totalHeld());
    }

    @Test
    @Timeout(30)
    void aPartyAtItsCapIsNotWokenByAnotherPartysRelease() throws Exception {
        EqualShareSemaphore gate = new EqualShareSemaphore(4, 2);
        List<Ticket> mine = drain(gate, 0);
        List<Ticket> theirs = drain(gate, 1);
        assertEquals(2, mine.size());
        assertEquals(2, theirs.size());

        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Ticket> got = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                got.set(gate.acquire(0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        waiting.await();

        theirs.forEach(Ticket::close);
        Thread.sleep(200);
        assertTrue(waiter.isAlive(), "a party over its cap must not take an idle party's share");
        assertNull(got.get());
        assertEquals(2, gate.totalHeld(), "only this party's own permits are out");

        mine.get(0).close();
        waiter.join(TimeUnit.SECONDS.toMillis(10));
        assertNotNull(got.get(), "its own release should have admitted it");
        got.get().close();
        mine.get(1).close();
        assertEquals(0, gate.totalHeld());
    }

    @Test
    @Timeout(30)
    void aBlockedAcquireIsInterruptibleAndTakesNothing() throws Exception {
        EqualShareSemaphore gate = new EqualShareSemaphore(2, 2);
        Ticket a = gate.acquire(0);
        Ticket b = gate.acquire(1);

        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                gate.acquire(0);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        waiting.await();
        Thread.sleep(50);
        waiter.interrupt();
        waiter.join(TimeUnit.SECONDS.toMillis(10));

        assertTrue(thrown.get() instanceof InterruptedException,
                "expected InterruptedException, got " + thrown.get());
        assertEquals(2, gate.totalHeld(), "an interrupted acquire must not hold a permit");
        a.close();
        b.close();
        assertEquals(0, gate.totalHeld());
    }

    @Test
    @Timeout(30)
    void tryAcquireWaitsForItsTimeoutAndThenGivesUp() throws Exception {
        EqualShareSemaphore gate = new EqualShareSemaphore(2, 2);
        Ticket a = gate.acquire(0);
        Ticket b = gate.acquire(1);

        long start = System.nanoTime();
        Ticket refused = gate.tryAcquire(0, 150, TimeUnit.MILLISECONDS);
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertNull(refused);
        assertTrue(waited >= 140, "gave up after only " + waited + " ms");
        assertEquals(2, gate.totalHeld());
        assertEquals(0, gate.belowShareDenials(), "the caller was at its cap, not below its share");
        a.close();
        b.close();
    }

    @Test
    @Timeout(30)
    void tryAcquireSucceedsWhenTheSurplusArrivesDuringTheWait() throws Exception {
        EqualShareSemaphore gate = new EqualShareSemaphore(5, 2);
        List<Ticket> hog = drain(gate, 1);
        List<Ticket> mine = drain(gate, 0);

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            hog.get(2).close();
        });
        releaser.setDaemon(true);
        releaser.start();

        Ticket granted = gate.tryAcquire(0, 10, TimeUnit.SECONDS);
        assertNotNull(granted, "the permit released mid-wait was never handed over");
        releaser.join();

        granted.close();
        hog.get(0).close();
        hog.get(1).close();
        mine.forEach(Ticket::close);
        assertEquals(0, gate.totalHeld());
    }

    @ParameterizedTest(name = "fairQueueing={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(60)
    void invariantsHoldUnderConcurrentLoad(boolean fairQueueing) throws Exception {
        int permits = 12;
        int parties = 4;
        int threadsPerParty = 6;
        EqualShareSemaphore gate = new EqualShareSemaphore(permits, parties, fairQueueing);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger overCap = new AtomicInteger();
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(parties * threadsPerParty, runnable -> {
            Thread t = new Thread(runnable);
            t.setDaemon(true);
            return t;
        });
        CountDownLatch go = new CountDownLatch(1);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);

        for (int p = 0; p < parties; p++) {
            final int party = p;
            for (int t = 0; t < threadsPerParty; t++) {
                pool.submit(() -> {
                    Random rnd = new Random(party * 31L + Thread.currentThread().threadId());
                    try {
                        go.await();
                        while (System.nanoTime() < deadline) {
                            Ticket attempt = gate.tryAcquire(party, NOW, TimeUnit.MILLISECONDS);
                            Ticket ticket = attempt != null ? attempt : gate.acquire(party);
                            try (ticket) {
                                acquisitions.incrementAndGet();
                                peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                                if (gate.held(party) > gate.maxPerParty()) overCap.incrementAndGet();
                                if (rnd.nextInt(4) == 0) Thread.yield();
                                inFlight.decrementAndGet();
                            }
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    }
                    return null;
                });
            }
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers did not finish");

        assertNull(failure.get(), () -> "a worker threw " + failure.get());
        assertTrue(acquisitions.get() > 1_000, "only " + acquisitions.get() + " acquisitions — too few to mean anything");
        assertTrue(peak.get() <= permits, "peak " + peak.get() + " exceeded " + permits);
        assertEquals(0, overCap.get(), "a party held more than share + surplus");
        assertEquals(0, gate.belowShareDenials(), "a party below its share was made to wait");
        assertEquals(0, gate.totalHeld(), "permits leaked");
        assertEquals(permits, drainAll(gate).size(), "the gate lost capacity under load");
    }

    private static List<Ticket> drain(EqualShareSemaphore gate, int party) throws InterruptedException {
        List<Ticket> out = new ArrayList<>();
        Ticket t;
        while ((t = gate.tryAcquire(party, NOW, TimeUnit.MILLISECONDS)) != null) {
            out.add(t);
            assertTrue(out.size() <= gate.maxPerParty(),
                    "party " + party + " was handed " + out.size() + " permits, over its cap of "
                            + gate.maxPerParty());
        }
        return out;
    }

    private static List<Ticket> drainAll(EqualShareSemaphore gate) throws InterruptedException {
        List<Ticket> out = new ArrayList<>();
        for (int party = 0; party < gate.parties(); party++) {
            out.addAll(drain(gate, party));
            assertTrue(out.size() <= gate.totalPermits(),
                    "the gate handed out " + out.size() + " of " + gate.totalPermits() + " permits");
        }
        out.forEach(Ticket::close);
        return out;
    }
}
