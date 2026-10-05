package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReentrantLock}'s contract, and the places where it is <em>not</em> an object-shaped
 * {@code synchronized}. Like {@link SemaphoreTest} these tests pin down the platform rather than
 * this repository's code; several of them are the exact counterpart of a {@code Semaphore}
 * behaviour, and the contrast is the point.
 *
 * <ol>
 *   <li>A lock has an owner, and holds are <b>counted</b>: one {@code unlock} per {@code lock},
 *       and nobody else may unlock it — where a permit has no owner at all.</li>
 *   <li>{@code lock()} cannot be interrupted. It swallows the interrupt, parks anyway, and
 *       reasserts the flag on the way out; {@code lockInterruptibly()} is the one that gives up.</li>
 *   <li>{@code Condition.await()} releases <b>every</b> hold and restores them all — and it
 *       re-acquires the lock before it will even throw {@link InterruptedException}.</li>
 *   <li>Fairness orders the queue and costs an order of magnitude; {@code tryLock()} barges
 *       regardless, exactly as {@code Semaphore.tryAcquire()} does.</li>
 *   <li>The monitoring methods ({@code getHoldCount}, {@code hasQueuedThread}, {@code hasWaiters})
 *       are what make all of the above testable without a stopwatch.</li>
 * </ol>
 */
class ReentrantLockTest {

    private static final long JOIN_MILLIS = TimeUnit.SECONDS.toMillis(10);

    /** Settle time for "and then nothing happened" observations. */
    private static final long OBSERVE_MILLIS = 200;

    // ---------- 1. holds are counted, and the lock has an owner ----------

    @Test
    void holdsAreCountedAndEveryOneNeedsItsOwnUnlock() {
        ReentrantLock lock = new ReentrantLock();
        assertEquals(0, lock.getHoldCount());
        assertFalse(lock.isLocked());

        lock.lock();
        lock.lock();
        assertTrue(lock.tryLock(), "tryLock is reentrant too — it re-enters rather than failing");
        assertEquals(3, lock.getHoldCount());
        assertTrue(lock.isHeldByCurrentThread());

        lock.unlock();
        lock.unlock();
        assertEquals(1, lock.getHoldCount());
        assertTrue(lock.isLocked(), "one unlock per lock, not one per thread");

        lock.unlock();
        assertEquals(0, lock.getHoldCount());
        assertFalse(lock.isLocked());

        // and unlike Semaphore.release(), the extra unlock is an error rather than a free permit
        assertThrows(IllegalMonitorStateException.class, lock::unlock);
        assertEquals(0, lock.getHoldCount(), "the failed unlock must not have moved anything");
    }

    @Test
    @Timeout(30)
    void onlyTheOwnerMayUnlock() throws InterruptedException {
        // the sharpest difference from Semaphore, where any thread may release a permit it never
        // acquired. A lock knows who holds it, so a foreign unlock is rejected outright.
        ReentrantLock lock = new ReentrantLock();
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
        assertTrue(lock.isHeldByCurrentThread(), "the foreign unlock must not have taken the lock away");
        assertEquals(1, lock.getHoldCount());
        lock.unlock();
    }

    @Test
    @Timeout(30)
    void isLockedAndIsHeldByCurrentThreadAnswerDifferentQuestions() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
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

        assertTrue(lock.isLocked(), "somebody holds it");
        assertFalse(lock.isHeldByCurrentThread(), "...but not us");
        assertEquals(0, lock.getHoldCount(), "getHoldCount is per-thread, so ours is zero");
        assertFalse(lock.tryLock(), "and we cannot take it");

        release.countDown();
        owner.join(JOIN_MILLIS);
        assertFalse(lock.isLocked());
    }

    // ---------- 2. lock() is not interruptible; lockInterruptibly() is ----------

    @Test
    @Timeout(60)
    void lockSwallowsAnInterruptAndReassertsItOnTheWayOut() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();                                   // the test thread is the obstacle

        AtomicBoolean acquired = new AtomicBoolean();
        AtomicBoolean interruptSurvived = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            started.countDown();
            lock.lock();                               // parks; an interrupt will not free it
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

        assertFalse(acquired.get(), "lock() must not return while another thread holds the lock");
        assertTrue(lock.hasQueuedThread(waiter), "the interrupt must not have cancelled the wait");
        assertTrue(waiter.isAlive());

        lock.unlock();                                 // only this frees it
        waiter.join(JOIN_MILLIS);
        assertTrue(acquired.get());
        assertTrue(interruptSurvived.get(),
                "lock() must reassert the interrupt it refused to act on");
    }

    @Test
    @Timeout(60)
    void lockInterruptiblyGivesUpAndLeavesNothingBehind() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
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
        assertFalse(lock.hasQueuedThread(waiter), "a cancelled wait must leave the queue");
        assertEquals(0, lock.getQueueLength());
        assertTrue(lock.isHeldByCurrentThread(), "and must not have disturbed the owner");
        lock.unlock();
    }

    @Test
    @Timeout(30)
    void anAlreadyInterruptedThreadFailsLockInterruptiblyImmediately() {
        ReentrantLock lock = new ReentrantLock();       // free: nothing to wait for
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, lock::lockInterruptibly);
            assertFalse(lock.isLocked(), "the throw must not have taken the lock");
            assertFalse(Thread.currentThread().isInterrupted(), "throwing clears the flag");
        } finally {
            Thread.interrupted();                        // never leak an interrupt into the next test
        }
    }

    @Test
    @Timeout(30)
    void tryLockWithATimeoutReportsFailureInsteadOfWaitingForever() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread owner = holderOf(lock, held, release);
        held.await();

        long start = System.nanoTime();
        assertFalse(lock.tryLock(150, TimeUnit.MILLISECONDS));
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(waited >= 140, "gave up after only " + waited + " ms");
        assertEquals(0, lock.getHoldCount(), "a failed tryLock must hold nothing");

        release.countDown();
        owner.join(JOIN_MILLIS);
        assertTrue(lock.tryLock(1, TimeUnit.SECONDS));
        lock.unlock();
    }

    // ---------- 3. Condition: await releases every hold, and re-acquires before returning ----------

    @Test
    @Timeout(60)
    void awaitReleasesEveryHoldAndRestoresThemAll() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition ready = lock.newCondition();
        AtomicInteger holdsBefore = new AtomicInteger(-1);
        AtomicInteger holdsAfter = new AtomicInteger(-1);

        Thread waiter = new Thread(() -> {
            lock.lock();
            lock.lock();                                 // held twice — and it is not obvious
            try {                                        // that await gives back both
                holdsBefore.set(lock.getHoldCount());
                ready.await();
                holdsAfter.set(lock.getHoldCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
                lock.unlock();
            }
        });
        waiter.setDaemon(true);
        waiter.start();

        // taking the lock ourselves *is* the assertion: a doubly-held lock is free while its
        // owner is inside await(). hasWaiters requires the lock, hence the take-and-check loop.
        while (true) {
            if (lock.tryLock()) {
                if (lock.hasWaiters(ready)) break;
                lock.unlock();
            }
            Thread.onSpinWait();
        }
        assertEquals(2, holdsBefore.get());
        assertEquals(1, lock.getHoldCount(), "we hold it once; the waiter's two holds are gone");
        assertEquals(1, lock.getWaitQueueLength(ready));

        ready.signal();
        lock.unlock();
        waiter.join(JOIN_MILLIS);
        assertEquals(2, holdsAfter.get(), "await must restore every hold it released, not just one");
        assertFalse(lock.isLocked());
    }

    @Test
    @Timeout(60)
    void anInterruptedAwaitStillWaitsForTheLockBeforeItThrows() throws InterruptedException {
        // await's contract: it always returns holding the lock — including when it is throwing
        // InterruptedException. So an interrupt cannot pull a thread out of a critical section.
        ReentrantLock lock = new ReentrantLock();
        Condition ready = lock.newCondition();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean heldWhenThrown = new AtomicBoolean();

        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                ready.await();
            } catch (Throwable t) {
                heldWhenThrown.set(lock.isHeldByCurrentThread());
                thrown.set(t);
            } finally {
                lock.unlock();
            }
        });
        waiter.setDaemon(true);
        waiter.start();

        while (true) {
            if (lock.tryLock()) {
                if (lock.hasWaiters(ready)) break;
                lock.unlock();
            }
            Thread.onSpinWait();
        }
        // we hold the lock; interrupting now cannot let the waiter out, because it must re-acquire
        waiter.interrupt();
        Thread.sleep(OBSERVE_MILLIS);
        assertNull(thrown.get(), "await returned without the lock, which its contract forbids");
        assertTrue(waiter.isAlive());

        lock.unlock();
        waiter.join(JOIN_MILLIS);
        assertInstanceOf(InterruptedException.class, thrown.get());
        assertTrue(heldWhenThrown.get(), "await must re-acquire the lock even to throw");
    }

    @Test
    @Timeout(60)
    void awaitUninterruptiblyKeepsWaitingAndHandsBackTheFlag() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition ready = lock.newCondition();
        AtomicBoolean returned = new AtomicBoolean();
        AtomicBoolean flagSurvived = new AtomicBoolean();

        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                ready.awaitUninterruptibly();
                returned.set(true);
                flagSurvived.set(Thread.currentThread().isInterrupted());
            } finally {
                lock.unlock();
            }
        });
        waiter.setDaemon(true);
        waiter.start();

        while (true) {
            if (lock.tryLock()) {
                if (lock.hasWaiters(ready)) break;
                lock.unlock();
            }
            Thread.onSpinWait();
        }
        lock.unlock();

        waiter.interrupt();
        Thread.sleep(OBSERVE_MILLIS);
        assertFalse(returned.get(), "awaitUninterruptibly must ignore the interrupt");

        lock.lock();
        try {
            ready.signal();                              // only a signal gets it out
        } finally {
            lock.unlock();
        }
        waiter.join(JOIN_MILLIS);
        assertTrue(returned.get());
        assertTrue(flagSurvived.get(), "the interrupt is deferred, not discarded");
    }

    @Test
    void aConditionIsUselessWithoutItsLock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition ready = lock.newCondition();

        // every Condition method requires the lock, and says so rather than corrupting anything
        assertThrows(IllegalMonitorStateException.class, ready::signal);
        assertThrows(IllegalMonitorStateException.class, ready::signalAll);
        assertThrows(IllegalMonitorStateException.class, ready::await);
        assertThrows(IllegalMonitorStateException.class, () -> ready.await(1, TimeUnit.MILLISECONDS));
        assertThrows(IllegalMonitorStateException.class, () -> lock.hasWaiters(ready));

        // and a condition belongs to exactly one lock
        ReentrantLock other = new ReentrantLock();
        other.lock();
        try {
            assertThrows(IllegalMonitorStateException.class, ready::signal);
            assertThrows(IllegalArgumentException.class, () -> other.hasWaiters(ready));
        } finally {
            other.unlock();
        }
    }

    @Test
    @Timeout(60)
    void signalWakesOneWaiterAndSignalAllWakesTheRest() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition ready = lock.newCondition();
        AtomicInteger woken = new AtomicInteger();
        int waiters = 4;

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < waiters; i++) {
            Thread t = new Thread(() -> {
                lock.lock();
                try {
                    ready.await();
                    woken.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            });
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }

        lock.lock();
        try {
            while (lock.getWaitQueueLength(ready) < waiters) {
                lock.unlock();
                Thread.onSpinWait();
                lock.lock();
            }
            ready.signal();                              // exactly one is transferred
        } finally {
            lock.unlock();
        }
        Thread.sleep(OBSERVE_MILLIS);
        assertEquals(1, woken.get(), "signal() must move exactly one waiter, not all of them");

        lock.lock();
        try {
            assertEquals(waiters - 1, lock.getWaitQueueLength(ready));
            ready.signalAll();
        } finally {
            lock.unlock();
        }
        for (Thread t : threads) t.join(JOIN_MILLIS);
        assertEquals(waiters, woken.get());
    }

    @Test
    @Timeout(60)
    void twoConditionsOnOneLockAreABoundedBuffer() throws InterruptedException {
        // the reason ReentrantLock has newCondition() at all: a monitor has one wait set, so
        // "not full" and "not empty" share it and every put wakes every take. Two conditions
        // signal exactly the threads that can make progress.
        BoundedBuffer buffer = new BoundedBuffer(4);
        int items = 2_000;
        AtomicLong sum = new AtomicLong();
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < items; i++) sum.addAndGet(buffer.take());
                done.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.setDaemon(true);
        consumer.start();

        for (int i = 1; i <= items; i++) buffer.put(i);
        assertTrue(done.await(30, TimeUnit.SECONDS), "producer and consumer did not meet");
        consumer.join(JOIN_MILLIS);

        assertEquals((long) items * (items + 1) / 2, sum.get(), "an item was lost or duplicated");
        assertEquals(0, buffer.size());
        assertTrue(buffer.maxObservedSize() <= 4,
                "the buffer held " + buffer.maxObservedSize() + " items behind a bound of 4");
    }

    /** A textbook two-condition bounded buffer — the shape {@code Condition} exists for. */
    private static final class BoundedBuffer {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();
        private final int[] items;
        private int count, head, tail, maxObserved;

        BoundedBuffer(int capacity) { this.items = new int[capacity]; }

        void put(int value) throws InterruptedException {
            lock.lock();
            try {
                while (count == items.length) notFull.await();   // always a loop, never an if
                items[tail] = value;
                tail = (tail + 1) % items.length;
                count++;
                maxObserved = Math.max(maxObserved, count);
                notEmpty.signal();                               // wake a taker, not every putter
            } finally {
                lock.unlock();
            }
        }

        int take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) notEmpty.await();
                int value = items[head];
                head = (head + 1) % items.length;
                count--;
                notFull.signal();
                return value;
            } finally {
                lock.unlock();
            }
        }

        int size() { lock.lock(); try { return count; } finally { lock.unlock(); } }

        int maxObservedSize() { lock.lock(); try { return maxObserved; } finally { lock.unlock(); } }
    }

    // ---------- 4. fairness is ordering, and it is not free ----------

    @Test
    @Timeout(60)
    void aFairLockGrantsInArrivalOrder() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock(true);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        lock.lock();                                     // hold everyone up while they queue

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
            // enqueue them one at a time, so "arrival order" is a fact rather than a hope
            while (!lock.hasQueuedThread(waiters[i])) Thread.onSpinWait();
        }
        assertEquals(5, lock.getQueueLength());
        assertTrue(lock.hasQueuedThreads());

        lock.unlock();
        for (Thread w : waiters) w.join(JOIN_MILLIS);
        assertEquals(List.of(0, 1, 2, 3, 4), order, "a fair lock must grant to the longest waiter");
    }

    @Test
    @Timeout(60)
    void anUnfairLockStillServesEveryoneWhoIsAlreadyQueued() throws InterruptedException {
        // barging is about newcomers, not about scrambling the queue: with every thread already
        // parked there is nobody to barge, so this asserts only that nobody is dropped. Where
        // unfairness actually shows is the handoff, measured below.
        ReentrantLock lock = new ReentrantLock(false);
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
        lock.unlock();
        for (Thread w : waiters) w.join(JOIN_MILLIS);

        assertEquals(5, order.size());
        assertEquals(List.of(0, 1, 2, 3, 4), order.stream().sorted().toList(), "a waiter was lost");
    }

    /** What one handoff measurement produced. */
    private record Handoff(long acquisitions, long selfSuccessions) {
        /** Share of acquisitions where the lock went straight back to the thread that released it. */
        double selfRate() { return acquisitions == 0 ? 0 : (double) selfSuccessions / acquisitions; }
    }

    /**
     * Counts the handoff rather than the winners, for the reason spelled out in
     * {@code SemaphoreTest}: per-thread counts over a fixed window are dominated by startup skew
     * and rank a fair lock as the less fair one. Barging is a question about who gets the lock
     * next, so that is what this counts.
     */
    private static Handoff handoff(int threads, int millis, boolean fair) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock(fair);
        AtomicInteger lastHolder = new AtomicInteger(-1);
        AtomicLong acquisitions = new AtomicLong();
        AtomicLong selfSuccessions = new AtomicLong();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        long[] deadline = new long[1];

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            workers[t] = new Thread(() -> {
                long mine = 0, selfs = 0;
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (System.nanoTime() < deadline[0]) {
                    for (int i = 0; i < 64; i++) {           // amortise the clock read
                        lock.lock();
                        try {
                            if (lastHolder.getAndSet(id) == id) selfs++;
                            mine++;
                            Thread.onSpinWait();             // a tiny critical section
                        } finally {
                            lock.unlock();                   // ...then grab it straight back
                        }
                    }
                }
                acquisitions.addAndGet(mine);
                selfSuccessions.addAndGet(selfs);
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        ready.await();                                       // no startup skew inside the window
        deadline[0] = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(30));
        return new Handoff(acquisitions.get(), selfSuccessions.get());
    }

    @Test
    @Timeout(60)
    void anUnfairLockHandsItselfBackToItsLastHolder() throws InterruptedException {
        Handoff unfair = handoff(8, 300, false);
        assertTrue(unfair.acquisitions() > 1_000, "too few acquisitions to mean anything");
        assertTrue(unfair.selfRate() > 0.5,
                "only " + Math.round(100 * unfair.selfRate()) + "% self-succession; barging should dominate");
    }

    @Test
    @Timeout(60)
    void aFairLockHandsItOnInstead() throws InterruptedException {
        Handoff fair = handoff(8, 300, true);
        assertTrue(fair.acquisitions() > 100, "too few acquisitions to mean anything");
        assertTrue(fair.selfRate() < 0.1,
                Math.round(100 * fair.selfRate()) + "% self-succession on a fair lock");
    }

    // ---------- 5. mutual exclusion actually excludes ----------

    @ParameterizedTest(name = "{0} threads")
    @ValueSource(ints = {2, 8, 24})
    @Timeout(60)
    void theLockIsTheOnlyThingMakingTheIncrementSafe(int threads) throws InterruptedException {
        // a deliberately non-atomic critical section: read, spin, write back
        ReentrantLock lock = new ReentrantLock();
        int perThread = 5_000;
        long[] counter = new long[1];
        AtomicInteger seenOverlap = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    lock.lock();
                    try {
                        if (lock.getHoldCount() != 1) seenOverlap.incrementAndGet();
                        long seen = counter[0];
                        Thread.onSpinWait();
                        counter[0] = seen + 1;
                    } finally {
                        lock.unlock();
                    }
                }
            });
            workers[t].setDaemon(true);
            workers[t].start();
        }
        go.countDown();
        for (Thread w : workers) w.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals((long) threads * perThread, counter[0], "a read-modify-write was lost");
        assertEquals(0, seenOverlap.get());
        assertFalse(lock.isLocked(), "every lock was matched by an unlock");
    }

    // ---------- helpers ----------

    /** Starts a daemon that holds {@code lock} from {@code held} until {@code release}. */
    private static Thread holderOf(ReentrantLock lock, CountDownLatch held, CountDownLatch release) {
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
        return owner;
    }
}
