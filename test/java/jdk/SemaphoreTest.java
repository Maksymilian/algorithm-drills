package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemaphoreTest {

    @ParameterizedTest(name = "{0} bare releases")
    @ValueSource(ints = {1, 3, 10})
    void releaseCreatesPermitsItWasNeverGiven(int bareReleases) {
        assertEquals(2 + bareReleases, permitsAfterBareReleases(bareReleases));
    }

    @Test
    @Timeout(30)
    void aPermitMayBeReleasedByAnotherThread() throws InterruptedException {
        assertTrue(releasedByAnotherThread());

        Semaphore mutex = new Semaphore(1);
        mutex.acquire();
        Thread other = new Thread(mutex::release);
        other.setDaemon(true);
        other.start();
        other.join(TimeUnit.SECONDS.toMillis(10));
        assertEquals(1, mutex.availablePermits());
        assertFalse(mutex.isFair());
    }

    @Test
    @Timeout(60)
    void twoCallersHoldingHalfEachDeadlock() throws InterruptedException {
        assertTrue(bothCallersStuckHoldingHalf(300),
                "the standoff resolved itself, which should be impossible");
    }

    @ParameterizedTest(name = "timeout {0} ms")
    @ValueSource(longs = {50, 300})
    @Timeout(60)
    void aTimeoutCannotLetBothCallersThrough(long timeoutMillis) throws InterruptedException {
        int through = callersThatGotTheirSecondPair(timeoutMillis);
        assertTrue(through <= 1, "both callers got their second pair, which the arithmetic forbids");
    }

    @Test
    @Timeout(60)
    void takingEverythingInOneCallCannotDeadlock() throws InterruptedException {
        assertTrue(takenInOneCall());
    }

    @Test
    @Timeout(30)
    void acquireIsAllOrNothing() throws InterruptedException {
        Semaphore pool = new Semaphore(3);
        assertFalse(pool.tryAcquire(5));
        assertEquals(3, pool.availablePermits());
        assertFalse(pool.tryAcquire(5, 50, TimeUnit.MILLISECONDS));
        assertEquals(3, pool.availablePermits());
    }

    @Test
    @Timeout(30)
    void anInterruptedAcquireTakesNothing() throws InterruptedException {
        Semaphore pool = new Semaphore(3);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Thread greedy = new Thread(() -> {
            started.countDown();
            try {
                pool.acquire(5);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        greedy.setDaemon(true);
        greedy.start();
        started.await();
        while (!pool.hasQueuedThreads()) Thread.onSpinWait();

        greedy.interrupt();
        greedy.join(TimeUnit.SECONDS.toMillis(10));

        assertInstanceOf(InterruptedException.class, thrown.get());
        assertEquals(3, pool.availablePermits(), "an interrupted acquire must not hold anything");
    }

    @ParameterizedTest(name = "fair={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(30)
    void tryAcquireWithoutATimeoutIgnoresFairness(boolean fair) throws InterruptedException {
        Barge barge = smallRequestMeetsAParkedBigOne(fair);
        assertTrue(barge.withoutTimeout(),
                "tryAcquire() is specified to barge whatever the fairness setting says");
        assertEquals(3, barge.available(), "the probe must leave the semaphore as it found it");
    }

    @ParameterizedTest(name = "fair={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(30)
    void tryAcquireWithATimeoutHonoursFairness(boolean fair) throws InterruptedException {
        Barge barge = smallRequestMeetsAParkedBigOne(fair);
        assertEquals(!fair, barge.withTimeout(),
                fair ? "a fair semaphore must queue this behind the parked request"
                     : "an unfair semaphore should hand it over immediately");
    }

    @Test
    @Timeout(30)
    void drainPermitsTakesOnlyWhatIsFree() throws InterruptedException {
        Drain drain = closeAndReopen(5, 2);
        assertEquals(3, drain.taken(), "2 of the 5 were held, so only 3 were there to drain");
        assertEquals(0, drain.leftAvailable());
        assertFalse(drain.stillOpen(), "a drained semaphore admits nobody");
        assertEquals(3, drain.afterReopen(), "releasing what was drained restores exactly that");
    }

    @Test
    @Timeout(30)
    void drainingAnEmptySemaphoreTakesNothing() {
        Semaphore gate = new Semaphore(0);
        assertEquals(0, gate.drainPermits());
        assertEquals(0, gate.availablePermits());
    }

    @ParameterizedTest(name = "{0} workers")
    @ValueSource(ints = {1, 4, 32})
    @Timeout(60)
    void acquireOfNWaitsForNReleases(int workers) throws InterruptedException {
        long millis = millisToAwait(workers);
        assertTrue(millis < TimeUnit.SECONDS.toMillis(30), "waited " + millis + " ms for " + workers);
    }

    @Test
    @Timeout(30)
    void aSemaphoreCanStartInDebt() throws InterruptedException {
        assertEquals(-2, permitsOfADebtOf(2));
        Semaphore owing = new Semaphore(-1);
        assertFalse(owing.tryAcquire());
        owing.release();
        assertFalse(owing.tryAcquire(), "one release only brought it back to zero");
        owing.release();
        owing.release();
        assertTrue(owing.tryAcquire(), "the second release is the first real permit");
        assertTrue(owing.tryAcquire(), "the second release is the first real permit");
        assertFalse(owing.tryAcquire(), "the second release is the first real permit");
    }

    @Test
    @Timeout(30)
    void permitsSurviveBeingCountedBackUp() throws InterruptedException {
        Semaphore finished = new Semaphore(0);
        finished.release(3);
        finished.acquire(3);
        assertEquals(0, finished.availablePermits());
        finished.release(3);
        assertTrue(finished.tryAcquire(3, 1, TimeUnit.SECONDS),
                "the same semaphore should be usable for a second round");
    }

    private record Barging(long acquisitions, long selfSuccessions) {
        double selfRate() { return acquisitions == 0 ? 0 : (double) selfSuccessions / acquisitions; }
    }

    private static Barging barging(int threads, int millis, boolean fair) throws InterruptedException {
        Semaphore gate = new Semaphore(1, fair);
        AtomicInteger lastHolder = new AtomicInteger(-1);
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger selfSuccessions = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        long[] deadline = new long[1];

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            workers[t] = new Thread(() -> {
                int mine = 0, selfs = 0;
                ready.countDown();
                try {
                    go.await();
                    while (System.nanoTime() < deadline[0]) {
                        for (int i = 0; i < 64; i++) {
                            gate.acquire();
                            if (lastHolder.getAndSet(id) == id) selfs++;
                            mine++;
                            Thread.onSpinWait();
                            gate.release();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                acquisitions.addAndGet(mine);
                selfSuccessions.addAndGet(selfs);
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        ready.await();
        deadline[0] = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(30));
        return new Barging(acquisitions.get(), selfSuccessions.get());
    }

    @Test
    @Timeout(60)
    void anUnfairSemaphoreHandsThePermitBackToItsLastHolder() throws InterruptedException {
        Barging unfair = barging(8, 300, false);
        assertTrue(unfair.acquisitions() > 1_000, "too few acquisitions to mean anything");
        assertTrue(unfair.selfRate() > 0.5,
                "only " + Math.round(100 * unfair.selfRate()) + "% self-succession; barging should dominate");
    }

    @Test
    @Timeout(60)
    void aFairSemaphoreHandsItOnInstead() throws InterruptedException {
        Barging fair = barging(8, 300, true);
        assertTrue(fair.acquisitions() > 100, "too few acquisitions to mean anything");
        assertTrue(fair.selfRate() < 0.1,
                Math.round(100 * fair.selfRate()) + "% self-succession on a fair semaphore");
    }

    private static int permitsAfterBareReleases(int bareReleases) {
        Semaphore pool = new Semaphore(2);
        for (int i = 0; i < bareReleases; i++) pool.release();
        return pool.availablePermits();
    }

    private static boolean releasedByAnotherThread() throws InterruptedException {
        Semaphore mutex = new Semaphore(1);
        mutex.acquire();
        Thread other = new Thread(mutex::release);
        other.start();
        other.join();
        return mutex.availablePermits() == 1;
    }

    private static boolean bothCallersStuckHoldingHalf(long observeMillis) throws InterruptedException {
        Semaphore pool = new Semaphore(4);
        CountDownLatch bothHoldTwo = new CountDownLatch(2);
        AtomicInteger gotSecondPair = new AtomicInteger();

        Runnable caller = () -> {
            try {
                pool.acquire(2);
                bothHoldTwo.countDown();
                bothHoldTwo.await();
                pool.acquire(2);
                gotSecondPair.incrementAndGet();
                pool.release(4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread a = new Thread(caller);
        Thread b = new Thread(caller);
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();

        bothHoldTwo.await();
        Thread.sleep(observeMillis);
        boolean stuck = gotSecondPair.get() == 0
                && pool.availablePermits() == 0
                && a.isAlive() && b.isAlive();

        a.interrupt();
        b.interrupt();
        a.join(TimeUnit.SECONDS.toMillis(10));
        b.join(TimeUnit.SECONDS.toMillis(10));
        return stuck;
    }

    private static int callersThatGotTheirSecondPair(long timeoutMillis) throws InterruptedException {
        Semaphore pool = new Semaphore(4);
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch bothHoldTwo = new CountDownLatch(2);

        Runnable caller = () -> {
            try {
                pool.acquire(2);
                bothHoldTwo.countDown();
                bothHoldTwo.await();
                if (pool.tryAcquire(2, timeoutMillis, TimeUnit.MILLISECONDS)) {
                    succeeded.incrementAndGet();
                    pool.release(2);
                }
                pool.release(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread a = new Thread(caller);
        Thread b = new Thread(caller);
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();
        a.join();
        b.join();
        return succeeded.get();
    }

    private static boolean takenInOneCall() throws InterruptedException {
        Semaphore pool = new Semaphore(4);
        AtomicInteger completed = new AtomicInteger();
        Runnable caller = () -> {
            try {
                pool.acquire(4);
                try {
                    completed.incrementAndGet();
                } finally {
                    pool.release(4);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread a = new Thread(caller);
        Thread b = new Thread(caller);
        a.start();
        b.start();
        a.join(TimeUnit.SECONDS.toMillis(10));
        b.join(TimeUnit.SECONDS.toMillis(10));
        return completed.get() == 2 && pool.availablePermits() == 4;
    }

    private record Barge(boolean withoutTimeout, boolean withTimeout, int available) {}

    private static Barge smallRequestMeetsAParkedBigOne(boolean fair) throws InterruptedException {
        Semaphore pool = new Semaphore(3, fair);
        Thread big = new Thread(() -> {
            try {
                pool.acquire(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        big.setDaemon(true);
        big.start();
        while (!pool.hasQueuedThreads()) Thread.onSpinWait();

        boolean barged = pool.tryAcquire();
        if (barged) pool.release();
        boolean queued = pool.tryAcquire(1, 50, TimeUnit.MILLISECONDS);
        if (queued) pool.release();

        return new Barge(barged, queued, pool.availablePermits());
    }

    private record Drain(int taken, int leftAvailable, boolean stillOpen, int afterReopen) {}

    private static Drain closeAndReopen(int permits, int alreadyHeld) throws InterruptedException {
        Semaphore gate = new Semaphore(permits);
        gate.acquire(alreadyHeld);

        int taken = gate.drainPermits();
        boolean stillOpen = gate.tryAcquire();
        gate.release(taken);
        return new Drain(taken, permits - alreadyHeld - taken, stillOpen, gate.availablePermits());
    }

    private static long millisToAwait(int workers) throws InterruptedException {
        Semaphore finished = new Semaphore(0);
        for (int i = 0; i < workers; i++) {
            Thread worker = new Thread(finished::release);
            worker.setDaemon(true);
            worker.start();
        }
        long start = System.nanoTime();
        finished.acquire(workers);
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static int permitsOfADebtOf(int debt) {
        return new Semaphore(-debt).availablePermits();
    }

}
