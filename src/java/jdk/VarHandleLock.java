package jdk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A reentrant exclusive {@link Lock} built from two {@link VarHandle}s, a queue and
 * {@link LockSupport} — the parts {@link ReentrantLock} hides inside {@code AbstractQueuedSynchronizer},
 * spelled out.
 *
 * <p>It exists to answer one question with code rather than prose: <em>what is actually in a lock?</em>
 * The answer is smaller than it looks.
 *
 * <ol>
 *   <li><b>One atomic state word.</b> {@code state} is the hold count: {@code 0} means free, and a
 *       successful {@code compareAndSet(0, 1)} <em>is</em> the acquisition. Nothing else about the
 *       fast path matters — an uncontended lock is one CAS and one plain store.</li>
 *   <li><b>An owner, so holds can be counted.</b> This is the whole of reentrancy, and the whole of
 *       why {@code unlock} can reject a thread that never acquired — the difference between a lock
 *       and a {@link java.util.concurrent.Semaphore} permit.</li>
 *   <li><b>A queue and a parking protocol,</b> for when the CAS fails. Everything hard lives here:
 *       a thread must enqueue, re-check, park, and be woken exactly often enough.</li>
 * </ol>
 *
 * <h2>Why a VarHandle rather than {@code volatile} and {@code synchronized}</h2>
 * A {@code volatile} field gives every access the same, strongest ordering. A {@code VarHandle}
 * makes the ordering a property of the <em>access</em>, so each line can say what it needs:
 *
 * <ul>
 *   <li>{@code STATE.compareAndSet(this, 0, 1)} — the acquisition. Atomic, and a full fence, because
 *       everything the previous holder did must become visible before anything this holder does.</li>
 *   <li>{@code STATE.set(this, n)} for the reentrant case — a <b>plain</b> store. Only the owner can
 *       reach this line, and no other thread may read the count, so ordering it would buy nothing.</li>
 *   <li>{@code STATE.setVolatile(this, 0)} — the release. This single store is what publishes the
 *       whole critical section to the next acquirer.</li>
 * </ul>
 *
 * Choosing plain access for the reentrant path is the point of the exercise, not a micro-optimisation:
 * it is only correct because of a stated invariant (the field is owner-confined while held), and
 * writing it down is what makes the invariant visible.
 *
 * <h2>Lost wakeups, and why this one does not have any</h2>
 * The dangerous interleaving is: a thread fails its CAS, and the holder releases and unparks the
 * queue <em>before</em> that thread has enqueued. It then parks with nobody left to wake it. The
 * guard is ordering, not luck — {@code acquireQueued} enqueues <b>first</b> and re-checks
 * <b>after</b>, so a release that missed the enqueue is always caught by the re-check, and a release
 * that saw the enqueue always unparks. {@link LockSupport} does the rest: its permit is sticky, so
 * an {@code unpark} that arrives before the {@code park} is not lost, it just makes the park return
 * at once.
 *
 * <h2>What it does not do</h2>
 * {@link #newCondition()} throws — a correct {@link Condition} needs a second queue and a transfer
 * protocol between the two, which is a larger exercise than this one, and {@link java.util.concurrent.locks.StampedLock}
 * makes the same refusal for the same reason. For anything real, use {@code ReentrantLock}: it is
 * better tested than this, it has conditions, and (as measured in the notes beside this file) it is
 * also faster under contention.
 *
 * @see java.util.concurrent.locks.ReentrantLock
 */
public final class VarHandleLock implements Lock {

    private static final VarHandle STATE;
    private static final VarHandle OWNER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(VarHandleLock.class, "state", int.class);
            OWNER = lookup.findVarHandle(VarHandleLock.class, "owner", Thread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);   // the class is unusable; fail at load
        }
    }

    /** Hold count: 0 when free. Written by CAS to take it, plainly to re-enter, volatile to release. */
    private int state;

    /** The holder, or null when free. Never read without {@code state != 0} having been established. */
    private Thread owner;

    /** FIFO among threads that have already queued; {@code fair} decides whether newcomers may skip it. */
    private final Queue<Thread> waiters = new ConcurrentLinkedQueue<>();

    private final boolean fair;

    /** A barging lock: a newcomer may take a free lock ahead of threads that are already waiting. */
    public VarHandleLock() {
        this(false);
    }

    /**
     * @param fair when true, {@link #lock()} declines the fast path while anyone is queued, so the
     *             lock is granted in arrival order. {@link #tryLock()} barges either way — the same
     *             documented exception {@code ReentrantLock} and {@code Semaphore} both make.
     */
    public VarHandleLock(boolean fair) {
        this.fair = fair;
    }

    // ---------- the fast path: one CAS ----------

    /**
     * The whole uncontended acquisition. Note the order of the two stores: the CAS claims the lock
     * and only then is {@code owner} written, so a thread that observes {@code state != 0} with
     * {@code owner == null} sees a lock that is held by someone who has not finished announcing
     * itself — which is why {@code owner} is only ever compared against the current thread, never
     * treated as "who holds it".
     */
    private boolean tryAcquire() {
        Thread me = Thread.currentThread();
        if (OWNER.getVolatile(this) == me) {              // reentrant: we already own it
            STATE.set(this, (int) STATE.get(this) + 1);   // plain — owner-confined while held
            return true;
        }
        if (STATE.compareAndSet(this, 0, 1)) {
            OWNER.setVolatile(this, me);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryLock() {
        // barges past the queue by design, exactly as ReentrantLock.tryLock() does even when fair
        return tryAcquire();
    }

    @Override
    public void lock() {
        if (canBarge() && tryAcquire()) return;
        try {
            acquireQueued(false, 0L);
        } catch (InterruptedException impossible) {
            throw new AssertionError("uninterruptible acquire threw", impossible);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        if (canBarge() && tryAcquire()) return;
        acquireQueued(true, 0L);
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        if (canBarge() && tryAcquire()) return true;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        if (deadline == 0L) deadline = 1L;                // 0 is the "no deadline" sentinel
        return acquireQueued(true, deadline);
    }

    /** A fair lock refuses the queue-jumping fast path while anybody is waiting. */
    private boolean canBarge() {
        return !fair || waiters.isEmpty() || OWNER.getVolatile(this) == Thread.currentThread();
    }

    // ---------- the slow path: enqueue, re-check, park ----------

    /**
     * Enqueue, then loop: only the head competes for the lock, so the queue is FIFO once entered.
     *
     * <p>The ordering here is the correctness argument. {@code waiters.add} happens <b>before</b> the
     * first {@code tryAcquire}, so a release that runs in between is guaranteed to see this thread
     * in the queue and unpark it; and the {@code tryAcquire} happens <b>before</b> the park, so a
     * release that ran before the enqueue is caught by the re-check instead. There is no third case.
     *
     * @param deadline absolute {@link System#nanoTime()} deadline, or 0 for none
     * @return true if the lock was acquired; false only when a deadline passed
     */
    private boolean acquireQueued(boolean interruptible, long deadline) throws InterruptedException {
        Thread me = Thread.currentThread();
        waiters.add(me);
        boolean acquired = false;
        boolean interrupted = false;
        try {
            for (;;) {
                if (waiters.peek() == me && tryAcquire()) {
                    acquired = true;
                    return true;
                }
                if (Thread.interrupted()) {
                    if (interruptible) throw new InterruptedException();
                    interrupted = true;            // lock() defers it rather than acting on it
                }
                if (deadline == 0L) {
                    LockSupport.park(this);
                } else {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) return false;
                    LockSupport.parkNanos(this, remaining);
                }
            }
        } finally {
            waiters.remove(me);
            if (!acquired) unparkHead();           // we may have been holding a signal meant for us
            if (interrupted) me.interrupt();       // ...and lock() hands the interrupt back
        }
    }

    // ---------- release ----------

    @Override
    public void unlock() {
        if (OWNER.getVolatile(this) != Thread.currentThread()) {
            throw new IllegalMonitorStateException("this thread does not hold " + this);
        }
        int remaining = (int) STATE.get(this) - 1;     // plain: nobody else may touch it while held
        if (remaining > 0) {
            STATE.set(this, remaining);
            return;
        }
        OWNER.setVolatile(this, null);                 // clear the owner *before* opening the gate
        STATE.setVolatile(this, 0);                    // the release: publishes the critical section
        unparkHead();
    }

    private void unparkHead() {
        Thread head = waiters.peek();
        if (head != null) LockSupport.unpark(head);    // sticky: safe even if it has not parked yet
    }

    // ---------- monitoring, for tests and for toString ----------

    /** True when any thread holds it. Not a basis for a decision — it can change as you read it. */
    public boolean isLocked() {
        return (int) STATE.getVolatile(this) != 0;
    }

    public boolean isHeldByCurrentThread() {
        return OWNER.getVolatile(this) == Thread.currentThread();
    }

    /** Holds by the <em>current</em> thread — the only thread that may read the count safely. */
    public int getHoldCount() {
        return isHeldByCurrentThread() ? (int) STATE.get(this) : 0;
    }

    public boolean isFair() {
        return fair;
    }

    public boolean hasQueuedThreads() {
        return !waiters.isEmpty();
    }

    public boolean hasQueuedThread(Thread thread) {
        return waiters.contains(thread);
    }

    public int getQueueLength() {
        return waiters.size();
    }

    /**
     * Always throws. A {@link Condition} has to release every hold, park on a second queue, and
     * transfer the waiter back onto this one before returning — a protocol of its own rather than a
     * few more lines here. {@code StampedLock} refuses for the same reason.
     */
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("VarHandleLock has no conditions — use ReentrantLock");
    }

    @Override
    public String toString() {
        Thread holder = (Thread) OWNER.getVolatile(this);
        return "VarHandleLock[" + (fair ? "fair, " : "barging, ")
                + (holder == null ? "unlocked" : "held by " + holder.getName())
                + ", queued=" + waiters.size() + "]";
    }
}
