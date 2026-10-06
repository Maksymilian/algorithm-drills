package jdk;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class EqualShareSemaphore {

    private final int total;
    private final int parties;
    private final int share;
    private final int surplus;

    private final ReentrantLock lock;
    private final Condition released;

    private final int[] held;
    private int totalHeld;
    private int surplusHeld;
    private long belowShareDenials;

    public EqualShareSemaphore(int totalPermits, int parties) {
        this(totalPermits, parties, false);
    }

    public EqualShareSemaphore(int totalPermits, int parties, boolean fairQueueing) {
        if (parties <= 0) throw new IllegalArgumentException("parties must be > 0");
        if (totalPermits < parties) {
            throw new IllegalArgumentException(
                    "need at least one permit per party: " + totalPermits + " < " + parties);
        }

        this.total = totalPermits;
        this.parties = parties;
        this.share = totalPermits / parties;
        this.surplus = totalPermits - parties * share;
        this.held = new int[parties];
        this.lock = new ReentrantLock(fairQueueing);
        this.released = lock.newCondition();
    }

    public int totalPermits()   { return total; }
    public int parties()        { return parties; }
    public int guaranteedShare(){ return share; }
    public int surplus()        { return surplus; }
    public int maxPerParty()    { return share + surplus; }

    public Ticket acquire(int party) throws InterruptedException {
        checkParty(party);
        lock.lockInterruptibly();
        try {
            while (!admissible(party)) {
                released.await();
            }
            requireCapacity(party);
            take(party);
            return new Ticket(party);
        } finally {
            lock.unlock();
        }
    }

    public Ticket tryAcquire(int party, long timeout, TimeUnit unit) throws InterruptedException {
        checkParty(party);
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (!admissible(party)) {
                if (nanos <= 0) {
                    if (held[party] < share) belowShareDenials++;
                    return null;
                }
                nanos = released.awaitNanos(nanos);
            }
            requireCapacity(party);
            take(party);
            return new Ticket(party);
        } finally {
            lock.unlock();
        }
    }

    public int held(int party) {
        checkParty(party);
        lock.lock();
        try {
            return held[party];
        } finally {
            lock.unlock();
        }
    }

    public long belowShareDenials() {
        lock.lock();
        try {
            return belowShareDenials;
        } finally {
            lock.unlock();
        }
    }

    public int totalHeld() {
        lock.lock();
        try {
            return totalHeld;
        } finally {
            lock.unlock();
        }
    }

    public final class Ticket implements AutoCloseable {
        private final int party;
        private boolean open = true;

        private Ticket(int party) { this.party = party; }

        @Override public void close() {
            lock.lock();
            try {
                if (!open) return;
                open = false;
                if (held[party] > share) surplusHeld--;
                held[party]--;
                totalHeld--;
                released.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean admissible(int party) {
        return held[party] < share
                || surplusHeld < surplus;
    }

    private void requireCapacity(int party) {
        if (held[party] < share && totalHeld >= total) {
            throw new IllegalStateException("invariant broken: party " + party + " holds "
                    + held[party] + " < share " + share + " but all " + total + " permits are out");
        }
    }

    private void take(int party) {
        if (held[party] >= share) surplusHeld++;
        held[party]++;
        totalHeld++;
    }

    private void checkParty(int party) {
        if (party < 0 || party >= parties) {
            throw new IndexOutOfBoundsException("party " + party + " of " + parties);
        }
    }
}
