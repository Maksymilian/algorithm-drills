package jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StampedLock}, whose selling point is the one mode that is <em>not a lock</em>.
 *
 * <p>A {@code tryOptimisticRead()} acquires nothing. It returns a version number, the reader reads
 * whatever it likes, and {@link StampedLock#validate} afterwards answers a single question: <em>did
 * a writer get in while I was reading?</em> Nothing was excluded, so the reader may well have seen
 * garbage — which is why the check comes last and why the values must be copied into locals before
 * it. Get that order wrong and the check is decoration.
 *
 * <p>The price of that speed is everything a {@link java.util.concurrent.locks.ReentrantLock} gives
 * away for free:
 *
 * <ol>
 *   <li>It is <b>not reentrant</b>. A second acquire from the holding thread deadlocks it against
 *       itself — demonstrated here, safely, on a thread this test can interrupt.</li>
 *   <li>A stamp is a <b>credential</b>, not a flag: unlocking with the wrong one throws, and a
 *       stamp is invalidated by the very conversion that seems to keep it.</li>
 *   <li>There are <b>no conditions</b> — {@code newCondition()} throws.</li>
 *   <li>Readers do not invalidate a stamp; a writer does, even one that writes nothing.</li>
 *   <li>The optimistic read can observe a <b>torn</b> pair of fields. That is not a bug in the
 *       lock, it is the contract, and the last test measures how often it happens under load.</li>
 * </ol>
 */
class StampedLockTest {

    private static final long JOIN_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final long OBSERVE_MILLIS = 200;

    /** Two fields with an invariant between them: {@code y == 2 * x}, always, under any lock. */
    private static final class Point {
        int x = 1;
        int y = 2;

        void moveTo(int newX) {
            x = newX;
            y = 2 * newX;
        }
    }

    // ---------- 1. an optimistic read excludes nobody ----------

    @Test
    void anOptimisticReadCanSeeATornPairAndValidateSaysSo() {
        // no second thread is needed to show this, which is the clearest proof that the stamp
        // holds nothing: the *same* thread takes the write lock in the middle of its own read
        StampedLock lock = new StampedLock();
        Point point = new Point();

        long stamp = lock.tryOptimisticRead();
        assertNotEquals(0L, stamp, "an unlocked StampedLock must issue an optimistic stamp");
        int x = point.x;                                  // reads 1

        long write = lock.writeLock();                    // no wait: the reader holds nothing
        try {
            point.moveTo(50);
        } finally {
            lock.unlockWrite(write);
        }

        int y = point.y;                                  // reads 100, from the *new* state
        assertNotEquals(2 * x, y, "the read should have torn across the write");
        assertFalse(lock.validate(stamp), "validate must report the write that happened mid-read");
    }

    @Test
    void theRetryLoopIsWhatMakesTheReadCorrect() {
        StampedLock lock = new StampedLock();
        Point point = new Point();
        point.moveTo(7);

        int[] seen = readPoint(lock, point);
        assertEquals(7, seen[0]);
        assertEquals(14, seen[1], "the accepted pair must satisfy the invariant");
    }

    /**
     * The documented idiom, and the order matters at every line: copy the fields into locals,
     * <em>then</em> validate, and only fall back to a real read lock when validation fails.
     * Validating before the reads, or using the fields before validating, checks nothing.
     */
    private static int[] readPoint(StampedLock lock, Point point) {
        long stamp = lock.tryOptimisticRead();
        int x = point.x;
        int y = point.y;                                  // locals, not fields — the copy is the point
        if (!lock.validate(stamp)) {                      // ...and the check comes after them
            stamp = lock.readLock();
            try {
                x = point.x;
                y = point.y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return new int[]{x, y};
    }

    @Test
    void aZeroStampNeverValidates() {
        StampedLock lock = new StampedLock();
        assertFalse(lock.validate(0L), "0 is the failure value, and it must never pass");

        long write = lock.writeLock();
        try {
            assertEquals(0L, lock.tryOptimisticRead(), "no optimistic stamp while a writer holds it");
            assertEquals(0L, lock.tryReadLock(), "and no reader either");
            assertEquals(0L, lock.tryWriteLock(), "not even a second writer — this is not reentrant");
        } finally {
            lock.unlockWrite(write);
        }
        assertNotEquals(0L, lock.tryOptimisticRead());
    }

    @Test
    void readersDoNotInvalidateAStampButAnEmptyWriteDoes() {
        StampedLock lock = new StampedLock();
        long stamp = lock.tryOptimisticRead();

        long read = lock.readLock();
        assertNotEquals(0L, lock.tryOptimisticRead(), "optimistic reads coexist with real readers");
        lock.unlockRead(read);
        assertTrue(lock.validate(stamp), "a reader changes nothing, so it must not invalidate");

        long write = lock.writeLock();
        lock.unlockWrite(write);                          // writes nothing at all
        assertFalse(lock.validate(stamp),
                "validate tracks exclusive acquisition, not mutation — it cannot know you wrote nothing");
    }

    // ---------- 2. exclusion, and the absence of reentrancy ----------

    @Test
    @Timeout(30)
    void readersShareAndWritersExclude() throws InterruptedException {
        StampedLock lock = new StampedLock();
        long r1 = lock.readLock();
        long r2 = lock.readLock();
        long r3 = lock.tryReadLock();

        assertNotEquals(0L, r3);
        assertEquals(3, lock.getReadLockCount());
        assertTrue(lock.isReadLocked());
        assertFalse(lock.isWriteLocked());
        assertEquals(0L, lock.tryWriteLock(), "a writer must wait for every reader");
        assertNotEquals(r1, r2, "each acquisition gets its own stamp");

        lock.unlockRead(r3);
        lock.unlockRead(r2);
        assertEquals(1, lock.getReadLockCount());
        assertEquals(0L, lock.tryWriteLock(), "one reader is still one too many");

        lock.unlockRead(r1);
        assertFalse(lock.isReadLocked());
        long w = lock.tryWriteLock();
        assertNotEquals(0L, w);
        assertTrue(lock.isWriteLocked());
        lock.unlockWrite(w);
    }

    @Test
    @Timeout(60)
    void aSecondWriteAcquireDeadlocksTheThreadAgainstItself() throws InterruptedException {
        // the hazard ReentrantLock's name exists to rule out. Demonstrated on a daemon using the
        // interruptible form, so the standoff can be observed and then escaped; writeLock() itself
        // would park the thread with no way out at all.
        StampedLock lock = new StampedLock();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean gotSecond = new AtomicBoolean();
        CountDownLatch holdsFirst = new CountDownLatch(1);

        Thread self = new Thread(() -> {
            long first = lock.writeLock();
            holdsFirst.countDown();
            try {
                lock.writeLockInterruptibly();            // waits for a lock it is already holding
                gotSecond.set(true);
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                lock.unlockWrite(first);
            }
        });
        self.setDaemon(true);
        self.start();
        holdsFirst.await();
        Thread.sleep(OBSERVE_MILLIS);

        assertFalse(gotSecond.get(), "the second acquire returned, so the lock is reentrant after all");
        assertTrue(self.isAlive());
        assertTrue(lock.isWriteLocked());

        self.interrupt();                                 // the only way out
        self.join(JOIN_MILLIS);
        assertInstanceOf(InterruptedException.class, thrown.get());
        assertFalse(lock.isWriteLocked(), "the finally block released the first stamp");
    }

    @Test
    @Timeout(60)
    void writeLockIgnoresInterruptsAndWriteLockInterruptiblyDoesNot() throws InterruptedException {
        StampedLock lock = new StampedLock();
        long held = lock.writeLock();                     // the test thread is the obstacle

        AtomicBoolean acquired = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        AtomicLong stamp = new AtomicLong();
        Thread waiter = new Thread(() -> {
            started.countDown();
            long s = lock.writeLock();                    // uninterruptible, like ReentrantLock.lock()
            stamp.set(s);
            acquired.set(true);
            lock.unlockWrite(s);
        });
        waiter.setDaemon(true);
        waiter.start();
        started.await();

        waiter.interrupt();
        Thread.sleep(OBSERVE_MILLIS);
        assertFalse(acquired.get(), "writeLock() must not give up because of an interrupt");
        assertTrue(waiter.isAlive());

        lock.unlockWrite(held);                           // only this frees it
        waiter.join(JOIN_MILLIS);
        assertTrue(acquired.get());
        assertNotEquals(0L, stamp.get());

        // ...and the interruptible form, in the same standoff, does give up
        held = lock.writeLock();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch started2 = new CountDownLatch(1);
        Thread interruptible = new Thread(() -> {
            started2.countDown();
            try {
                lock.readLockInterruptibly();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        interruptible.setDaemon(true);
        interruptible.start();
        started2.await();
        Thread.sleep(50);
        interruptible.interrupt();
        interruptible.join(JOIN_MILLIS);
        assertInstanceOf(InterruptedException.class, thrown.get());
        assertEquals(0, lock.getReadLockCount(), "the abandoned reader must not have counted itself");
        lock.unlockWrite(held);
    }

    @Test
    @Timeout(30)
    void aTryWithATimeoutReportsFailureRatherThanWaiting() throws InterruptedException {
        StampedLock lock = new StampedLock();
        long held = lock.writeLock();

        long start = System.nanoTime();
        assertEquals(0L, lock.tryReadLock(150, TimeUnit.MILLISECONDS));
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(waited >= 140, "gave up after only " + waited + " ms");
        assertEquals(0, lock.getReadLockCount(), "a failed try must hold nothing");

        lock.unlockWrite(held);
        long ok = lock.tryReadLock(1, TimeUnit.SECONDS);
        assertNotEquals(0L, ok);
        lock.unlockRead(ok);
    }

    // ---------- 3. a stamp is a credential ----------

    @Test
    void aStampIsCheckedForItsModeAndItsVersion() {
        StampedLock lock = new StampedLock();
        long read = lock.readLock();

        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockWrite(read), "wrong mode");
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockRead(0L));
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlock(0L));
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockRead(read - 1));
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockRead(read + 128), "other version");
        assertFalse(lock.tryUnlockWrite(), "tryUnlockWrite is the form that reports instead of throwing");
        assertEquals(1, lock.getReadLockCount(), "no failed unlock may release anything");

        lock.unlock(read);                                 // the mode-agnostic release
        assertFalse(lock.isReadLocked());
        assertFalse(lock.tryUnlockRead());
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockRead(read), "now stale");

        long write = lock.writeLock();
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockWrite(write + 1));
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockRead(write), "wrong mode");
        assertTrue(lock.isWriteLocked(), "neither rejection may have released it");
        lock.unlockWrite(write);
        assertThrows(IllegalMonitorStateException.class, () -> lock.unlockWrite(write), "spent");
    }

    @Test
    void aReadStampIsAVersionNumberRatherThanACredential() {
        // Unspecified territory, asserted so that a change gets noticed rather than assumed.
        // unlockRead checks the version bits and that *some* reader is counted -- not that this
        // stamp is the one you were handed. So the stamp is a weak guard, and only for readers.
        StampedLock lock = new StampedLock();
        long read = lock.readLock();
        lock.unlockRead(read + 1);                         // a stamp that was never issued
        assertFalse(lock.isReadLocked(), "...and it released the lock all the same");

        // the same hole at its most alarming: two locks in the same state accept each other stamps
        StampedLock a = new StampedLock();
        StampedLock b = new StampedLock();
        long fromB = b.readLock();
        long fromA = a.readLock();
        assertEquals(fromA, fromB, "identical history, identical stamp -- there is no lock identity in it");
        a.unlockRead(fromB);
        assertFalse(a.isReadLocked(), "a stamp belonging to another lock released this one");
        b.unlockRead(fromA);

        // what is actually checked: 7 bits of reader count, and above them the version
        StampedLock c = new StampedLock();
        long r = c.readLock();
        assertThrows(IllegalMonitorStateException.class, () -> c.unlockRead(r + 127), "count wraps to 0");
        assertThrows(IllegalMonitorStateException.class, () -> c.unlockRead(r + 128), "version differs");
        assertTrue(c.isReadLocked());
        c.unlockRead(r);

        // a write stamp, by contrast, is exact -- the write bit sits inside the checked region
        StampedLock d = new StampedLock();
        long w = d.writeLock();
        assertThrows(IllegalMonitorStateException.class, () -> d.unlockWrite(w + 1));
        d.unlockWrite(w);
    }

    @Test
    void anUpgradeInvalidatesTheStampItCameFrom() {
        StampedLock lock = new StampedLock();
        Point point = new Point();

        long read = lock.readLock();
        long write = lock.tryConvertToWriteLock(read);     // sole reader: the upgrade is available
        assertNotEquals(0L, write);
        assertTrue(lock.isWriteLocked());
        assertEquals(0, lock.getReadLockCount(), "the read lock was consumed, not kept alongside");
        assertFalse(lock.validate(read), "the old stamp is spent; releasing with it would throw");

        point.moveTo(9);
        long observation = lock.tryConvertToOptimisticRead(write);
        assertNotEquals(0L, observation, "downgrading to an observation releases the lock");
        assertFalse(lock.isWriteLocked());
        assertTrue(lock.validate(observation));
        assertEquals(18, point.y);
    }

    @Test
    @Timeout(30)
    void aFailedUpgradeLeavesTheReadLockExactlyWhereItWas() throws InterruptedException {
        // the bug this prevents: treating tryConvertToWriteLock's 0 as "nothing happened" and
        // dropping the stamp on the floor. It failed *and* you are still a reader.
        StampedLock lock = new StampedLock();
        CountDownLatch otherReaderIn = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread otherReader = new Thread(() -> {
            long s = lock.readLock();
            try {
                otherReaderIn.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlockRead(s);
            }
        });
        otherReader.setDaemon(true);
        otherReader.start();

        long read = lock.readLock();
        otherReaderIn.await();
        assertEquals(2, lock.getReadLockCount());

        assertEquals(0L, lock.tryConvertToWriteLock(read), "two readers: the upgrade cannot succeed");
        assertEquals(2, lock.getReadLockCount(), "...and our read lock is still held");
        assertTrue(lock.validate(read), "so the original stamp is still the one to release with");

        lock.unlockRead(read);                             // the stamp that the failed upgrade kept
        release.countDown();
        otherReader.join(JOIN_MILLIS);
        assertEquals(0, lock.getReadLockCount());
    }

    // ---------- 4. the Lock views, and what they cannot do ----------

    @Test
    void theViewsAreOrdinaryLocksWithoutConditions() {
        StampedLock lock = new StampedLock();
        Lock read = lock.asReadLock();
        Lock write = lock.asWriteLock();
        ReadWriteLock rw = lock.asReadWriteLock();

        // the views hide the stamp — which is what makes them usable with code that expects a Lock,
        // and what makes them unable to offer optimistic reading at all
        read.lock();
        assertTrue(lock.isReadLocked());
        assertFalse(write.tryLock());
        read.unlock();

        assertTrue(write.tryLock());
        assertTrue(lock.isWriteLocked());
        write.unlock();

        assertEquals(read.getClass(), rw.readLock().getClass());
        rw.writeLock().lock();
        assertTrue(lock.isWriteLocked());
        rw.writeLock().unlock();

        // no Condition anywhere: there is no owner to re-acquire for, so await/signal has no meaning
        assertThrows(UnsupportedOperationException.class, read::newCondition);
        assertThrows(UnsupportedOperationException.class, write::newCondition);
        assertThrows(UnsupportedOperationException.class, () -> rw.readLock().newCondition());
    }

    // ---------- 5. how often does an optimistic read actually have to retry? ----------

    /** What one optimistic-read measurement produced. */
    private record Optimism(long attempts, long validationFailures, long inconsistentPairs) {
        double failureRate() { return attempts == 0 ? 0 : (double) validationFailures / attempts; }
    }

    /**
     * Readers using the optimistic idiom against one writer that never stops. Every accepted pair
     * is checked against the invariant {@code y == 2 * x} — that is the oracle, and it must hold
     * whatever the machine does. The validation-failure rate beside it is a statistic, not a
     * contract: it moves with the writer's duty cycle and the number of readers.
     */
    private static Optimism optimisticReads(int readers, int millis) throws InterruptedException {
        StampedLock lock = new StampedLock();
        Point point = new Point();
        AtomicLong attempts = new AtomicLong();
        AtomicLong failures = new AtomicLong();
        AtomicLong inconsistent = new AtomicLong();
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch ready = new CountDownLatch(readers + 1);
        CountDownLatch go = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            ready.countDown();
            try {
                go.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int n = 0;
            while (!stop.get()) {
                long s = lock.writeLock();
                try {
                    point.moveTo(++n);                     // the invariant is broken between these
                } finally {                                // two field writes, every single time
                    lock.unlockWrite(s);
                }
            }
        });

        Thread[] readerThreads = new Thread[readers];
        for (int i = 0; i < readers; i++) {
            readerThreads[i] = new Thread(() -> {
                long mine = 0, failed = 0, bad = 0;
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!stop.get()) {
                    long stamp = lock.tryOptimisticRead();
                    int x = point.x;
                    int y = point.y;
                    mine++;
                    if (lock.validate(stamp)) {
                        if (y != 2 * x) bad++;             // must never happen: validate said clean
                    } else {
                        failed++;
                        long s = lock.readLock();
                        try {
                            x = point.x;
                            y = point.y;
                        } finally {
                            lock.unlockRead(s);
                        }
                        if (y != 2 * x) bad++;             // nor here: this one really was locked
                    }
                }
                attempts.addAndGet(mine);
                failures.addAndGet(failed);
                inconsistent.addAndGet(bad);
            });
            readerThreads[i].setDaemon(true);
        }

        writer.setDaemon(true);
        writer.start();
        for (Thread r : readerThreads) r.start();
        ready.await();
        go.countDown();
        Thread.sleep(millis);
        stop.set(true);
        writer.join(TimeUnit.SECONDS.toMillis(30));
        for (Thread r : readerThreads) r.join(TimeUnit.SECONDS.toMillis(30));
        return new Optimism(attempts.get(), failures.get(), inconsistent.get());
    }

    @ParameterizedTest(name = "{0} readers")
    @ValueSource(ints = {1, 8})
    @Timeout(120)
    void aValidatedOptimisticReadIsNeverTorn(int readers) throws InterruptedException {
        Optimism seen = optimisticReads(readers, 300);
        assertTrue(seen.attempts() > 1_000, "only " + seen.attempts() + " reads — too few to mean anything");
        assertEquals(0, seen.inconsistentPairs(),
                "a validated read returned a torn pair, which is the one thing validate rules out");
        // the writer never stops, so some reads must lose the race — a rate of exactly zero would
        // mean the measurement, not the lock, was broken
        assertTrue(seen.validationFailures() > 0,
                "no optimistic read ever failed against a writer that never pauses");
        assertTrue(seen.failureRate() < 1.0, "every single read failed; the fallback was the whole test");
    }
}
