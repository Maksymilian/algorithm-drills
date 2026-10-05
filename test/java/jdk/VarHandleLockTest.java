package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VarHandleLock} — the hand-rolled lock — against the same contract
 * {@link ReentrantLockTest} holds the JDK's to.
 *
 * <p>Two of these tests are the ones that matter, and they are the ones a lock written from scratch
 * usually fails:
 *
 * <ul>
 *   <li>{@code mutualExclusionHolds…} — a deliberately non-atomic critical section, so a lock that
 *       ever admits two threads loses a count and says so.</li>
 *   <li>{@code noWakeupIsLostWhenWaitersGiveUp} — every acquisition mode at once, with an
 *       interrupter running, because the <b>cancellation</b> paths are where a lost wakeup hides: a
 *       thread that abandons its wait may be carrying the signal that was meant for it. A lost
 *       wakeup shows up as a worker still alive at the end, which is asserted directly rather than
 *       inferred from the totals — a parked worker contributes nothing to either side of them.</li>
 * </ul>
 */
class VarHandleLockTest {

    private static final long JOIN_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final long OBSERVE_MILLIS = 200;

    // ---------- the contract it shares with ReentrantLock ----------

    @Test
    void holdsAreCountedAndEveryOneNeedsItsOwnUnlock() {
        VarHandleLock lock = new VarHandleLock();
        assertEquals(0, lock.getHoldCount());
        assertFalse(lock.isLocked());

        lock.lock();
        lock.lock();
        assertTrue(lock.tryLock(), "tryLock must re-enter rather than fail against its own holder");
        assertEquals(3, lock.getHoldCount());
        assertTrue(lock.isHeldByCurrentThread());
        assertTrue(lock.isLocked());

        lock.unlock();
        lock.unlock();
        assertEquals(1, lock.getHoldCount());
        assertTrue(lock.isLocked(), "one unlock per lock, not one per thread");

        lock.unlock();
        assertEquals(0, lock.getHoldCount());
        assertFalse(lock.isLocked());
        assertThrows(IllegalMonitorStateException.class, lock::unlock);
    }

    @Test
    @Timeout(30)
    void onlyTheOwnerMayUnlock() throws InterruptedException {
        VarHandleLock lock = new VarHandleLock();
        lock.lock();

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                lock.unlock();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        other.setDaemon(true);
        other.start();
        other.join(JOIN_MILLIS);

        assertInstanceOf(IllegalMonitorStateException.class, thrown.get());
        assertTrue(lock.isHeldByCurrentThread(), "a rejected unlock must not release anything");
        assertEquals(1, lock.getHoldCount());
        lock.unlock();
    }

    @Test
    @Timeout(60)
    void lockDefersAnInterruptAndHandsItBack() throws InterruptedException {
        VarHandleLock lock = new VarHandleLock();
        lock.lock();

        AtomicBoolean acquired = new AtomicBoolean();
        AtomicBoolean interruptSurvived = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            started.countDown();
            lock.lock();
            try {
                acquired.set(true);
                interruptSurvived.set(Thread.currentThread().isInterrupted());
            } finally {
                lock.unlock();
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        started.await();
        while (!lock.hasQueuedThread(waiter)) Thread.onSpinWait();

        waiter.interrupt();
        Thread.sleep(OBSERVE_MILLIS);
        assertFalse(acquired.get(), "lock() must ignore the interrupt and keep waiting");
        assertTrue(lock.hasQueuedThread(waiter));

        lock.unlock();
        waiter.join(JOIN_MILLIS);
        assertTrue(acquired.get());
        assertTrue(interruptSurvived.get(), "the interrupt must be reasserted, not discarded");
        assertEquals(0, lock.getQueueLength());
    }

    @Test
    @Timeout(60)
    void lockInterruptiblyGivesUpAndLeavesTheQueueClean() throws InterruptedException {
        VarHandleLock lock = new VarHandleLock();
        lock.lock();

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                lock.lockInterruptibly();
                lock.unlock();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        started.await();
        while (!lock.hasQueuedThread(waiter)) Thread.onSpinWait();

        waiter.interrupt();
        waiter.join(JOIN_MILLIS);

        assertInstanceOf(InterruptedException.class, thrown.get());
        assertEquals(0, lock.getQueueLength(), "an abandoned waiter must take itself out of the queue");
        assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();

        // and the lock still works afterwards — a cancelled waiter must not have eaten the signal
        assertTrue(lock.tryLock());
        lock.unlock();
    }

    @Test
    @Timeout(30)
    void aTimedTryLockGivesUpAndHoldsNothing() throws InterruptedException {
        VarHandleLock lock = new VarHandleLock();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread owner = new Thread(() -> {
            lock.lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        owner.setDaemon(true);
        owner.start();
        held.await();

        long start = System.nanoTime();
        assertFalse(lock.tryLock(150, TimeUnit.MILLISECONDS));
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(waited >= 140, "gave up after only " + waited + " ms");
        assertEquals(0, lock.getHoldCount());
        assertEquals(0, lock.getQueueLength(), "the expired waiter must have dequeued itself");

        release.countDown();
        owner.join(JOIN_MILLIS);
        assertTrue(lock.tryLock(1, TimeUnit.SECONDS));
        lock.unlock();
    }

    @Test
    void itHasNoConditions() {
        VarHandleLock lock = new VarHandleLock();
        assertThrows(UnsupportedOperationException.class, lock::newCondition);
        assertFalse(lock.isFair());
        assertTrue(new VarHandleLock(true).isFair());
        assertTrue(lock.toString().contains("unlocked"), lock.toString());
        lock.lock();
        try {
            assertTrue(lock.toString().contains("held by"), lock.toString());
        } finally {
            lock.unlock();
        }
    }

    @Test
    @Timeout(60)
    void theFairVariantGrantsInArrivalOrder() throws InterruptedException {
        VarHandleLock lock = new VarHandleLock(true);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        lock.lock();

        Thread[] waiters = new Thread[5];
        for (int i = 0; i < waiters.length; i++) {
            final int id = i;
            waiters[i] = new Thread(() -> {
                lock.lock();
                try {
                    order.add(id);
                } finally {
                    lock.unlock();
                }
            });
            waiters[i].setDaemon(true);
            waiters[i].start();
            while (!lock.hasQueuedThread(waiters[i])) Thread.onSpinWait();
        }
        assertEquals(5, lock.getQueueLength());

        lock.unlock();
        for (Thread w : waiters) w.join(JOIN_MILLIS);
        assertEquals(List.of(0, 1, 2, 3, 4), order);
        assertFalse(lock.isLocked());
        assertEquals(0, lock.getQueueLength());
    }

    // ---------- the two that matter ----------

    @ParameterizedTest(name = "{0} threads")
    @ValueSource(ints = {2, 8, 24})
    @Timeout(120)
    void mutualExclusionHoldsUnderContention(int threads) throws InterruptedException {
        VarHandleLock lock = new VarHandleLock();
        int perThread = 20_000;
        long[] guarded = new long[1];                  // read, pause, write back: not atomic
        AtomicLong overlaps = new AtomicLong();
        CountDownLatch go = new CountDownLatch(1);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                if (!awaitQuietly(go)) return;
                for (int i = 0; i < perThread; i++) {
                    lock.lock();
                    try {
                        if (lock.getHoldCount() != 1) overlaps.incrementAndGet();
                        long seen = guarded[0];
                        Thread.onSpinWait();
                        guarded[0] = seen + 1;
                    } finally {
                        lock.unlock();
                    }
                }
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(60));

        for (Thread w : workers) assertFalse(w.isAlive(), "a worker never finished — a lost wakeup");
        assertEquals((long) threads * perThread, guarded[0], "a read-modify-write was lost");
        assertEquals(0, overlaps.get());
        assertFalse(lock.isLocked());
        assertEquals(0, lock.getQueueLength());
    }

    @ParameterizedTest(name = "fair={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(180)
    void noWakeupIsLostWhenWaitersGiveUp(boolean fair) throws InterruptedException {
        // every acquisition mode mixed together, with an interrupter running, so that waiters are
        // constantly abandoning their place in the queue. The count is the oracle for exclusion;
        // "every worker finished" is the oracle for the wakeups.
        VarHandleLock lock = new VarHandleLock(fair);
        int threads = 12;
        int rounds = 4_000;
        long[] guarded = new long[1];
        AtomicLong acquisitions = new AtomicLong();
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch pastTheStartLine = new CountDownLatch(threads);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            workers[t] = new Thread(() -> {
                Random rnd = new Random(id * 31L + 7);
                if (!awaitQuietly(go)) return;
                pastTheStartLine.countDown();
                long mine = 0;
                for (int i = 0; i < rounds; i++) {
                    boolean held = false;
                    try {
                        switch (rnd.nextInt(4)) {
                            case 0 -> {
                                lock.lock();                                   // uninterruptible
                                held = true;
                            }
                            case 1 -> held = lock.tryLock();                   // may simply fail
                            case 2 -> held = lock.tryLock(rnd.nextInt(2), TimeUnit.MILLISECONDS);
                            default -> {
                                lock.lockInterruptibly();                      // may be cancelled
                                held = true;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.interrupted();                                  // a miss, and carry on
                    }
                    if (held) {
                        long seen = guarded[0];
                        Thread.onSpinWait();
                        guarded[0] = seen + 1;
                        mine++;
                        lock.unlock();
                    }
                }
                acquisitions.addAndGet(mine);
            });
            workers[t].setDaemon(true);
        }

        Thread interrupter = new Thread(() -> {
            Random rnd = new Random(99);
            if (!awaitQuietly(go)) return;
            // and not one moment sooner: an interrupt delivered while a worker is still parked on
            // the start line takes it out of the run before it has competed for the lock even once,
            // which empties the measurement instead of stressing it
            if (!awaitQuietly(pastTheStartLine)) return;
            while (!stop.get()) {
                workers[rnd.nextInt(threads)].interrupt();
                Thread.onSpinWait();
            }
        });
        interrupter.setDaemon(true);

        for (Thread w : workers) w.start();
        interrupter.start();
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(120));
        stop.set(true);
        interrupter.join(JOIN_MILLIS);

        for (Thread w : workers) {
            assertFalse(w.isAlive(), "a worker is still parked — a wakeup was lost on a cancel path");
        }
        assertTrue(acquisitions.get() > 0, "nothing was ever acquired");
        assertEquals(acquisitions.get(), guarded[0], "a read-modify-write was lost: two holders at once");
        assertFalse(lock.isLocked(), "the lock was left held");
        assertEquals(0, lock.getQueueLength(), "the queue was left dirty");
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
