# Three locks: `ReentrantLock`, `StampedLock`, and one built out of a `VarHandle`

Notes for [`src/java/jdk/VarHandleLock.java`](../../src/java/jdk/VarHandleLock.java) and
[`src/java/jdk/VarHandleVector.java`](../../src/java/jdk/VarHandleVector.java), and for the four
suites beside them: [`ReentrantLockTest`](../../test/java/jdk/ReentrantLockTest.java),
[`StampedLockTest`](../../test/java/jdk/StampedLockTest.java),
[`VarHandleLockTest`](../../test/java/jdk/VarHandleLockTest.java) and
[`VarHandleVectorTest`](../../test/java/jdk/VarHandleVectorTest.java).

Companion to [the `mapConcurrent` note](map-concurrent-and-equal-share-semaphore.md), which covers
`Semaphore`; the contrasts with it are marked where they come up.

Every number here was measured on JDK 25.0.1 (Temurin), 24 CPUs, Intel i7-14650HX, either by those
tests or by an ad-hoc benchmark that is **not in the tree** — per this package's convention,
throughput has no robust assertion, so it is measured here and quoted as indicative. Figures from
the benchmark are marked; everything else is asserted by a test. All ranges are the spread over
repeated runs, never a single reading.

**The one question.** All three of these exclude writers. What separates them is what a *reader*
has to do:

| | A reader must… | Reads/s, 8 threads |
|---|---|---|
| `ReentrantLock` | take the lock, excluding every other reader | ~40 M |
| `ReentrantReadWriteLock` | take a shared lock, and pay for the bookkeeping | ~7–9 M |
| `StampedLock` optimistic | **take nothing**, then check afterwards | ~3 100 M |

The last row is not a better lock. It is the absence of one, and the rest of this note is about
what that costs you.

---

## Part 1 — `ReentrantLock`: the things a permit is not

All of these are asserted in `ReentrantLockTest`. The right-hand column is what `Semaphore` does
instead, because the two classes are often reached for interchangeably and agree on almost nothing.

| Behaviour | `ReentrantLock` | `Semaphore` |
|---|---|---|
| Acquiring twice from one thread | re-enters; hold count 2 | takes **two permits**, and can self-deadlock |
| Releasing without holding | `IllegalMonitorStateException` | **creates a permit** — the pool silently grows |
| Releasing from another thread | `IllegalMonitorStateException` | legal, and idiomatic |
| `getHoldCount()` | per *current thread*; 0 when another thread holds it | no such concept |
| Conditions | `newCondition()`, as many as you like | none |

### `lock()` cannot be interrupted, and says so by not forgetting

A thread parked in `lock()` stays parked when interrupted — measured by interrupting it, watching it
remain in `hasQueuedThread` for 200 ms, and only then releasing the lock. What it does *not* do is
discard the interrupt: it re-asserts the flag once it has the lock, so the next blocking call sees
it. `lockInterruptibly()` is the one that gives up, and when it does it leaves the queue clean —
`getQueueLength()` back to 0, the owner undisturbed.

That pairing is the whole reason both methods exist. `lock()` is for critical sections that must not
be abandoned halfway; `lockInterruptibly()` is for anything a caller may be asked to cancel.

### `Condition.await()` releases *every* hold — and re-acquires before it throws

Two behaviours that surprise people, both asserted:

1. A thread that locked twice and then awaits releases **both** holds. The test proves it by
   taking the lock from the outside while the waiter is parked, and asserts `getHoldCount() == 2`
   again after the signal. A `Condition` that released only one hold would deadlock instantly, and
   nothing in the API hints that it releases all of them.
2. `await()` always returns holding the lock — *including when it is throwing
   `InterruptedException`*. The test holds the lock, interrupts the waiter, watches it stay parked
   for 200 ms, and only sees the exception after the lock is released. So an interrupt cannot pull a
   thread out of a critical section; it can only make it leave through the front door.

`awaitUninterruptibly()` is the third case: it ignores the interrupt entirely and hands the flag
back on return.

The reason `Condition` exists at all rather than `wait`/`notify` is one wait set versus several. A
monitor has exactly one, so a bounded buffer's "not full" and "not empty" waiters share it and every
`put` wakes every blocked `put` as well. Two conditions wake exactly the threads that can make
progress — that is what `BoundedBuffer` in the test is for, and it is checked by summing 2 000 items
through a buffer of 4.

### Fairness is about order, and it costs two orders of magnitude

Deterministic first: with five threads queued **one at a time** (each verified into
`hasQueuedThread` before the next starts), a fair lock grants in exactly arrival order. An unfair
lock also serves all five — barging is about *newcomers*, not about scrambling a queue that has
already formed.

Where unfairness shows is the handoff. Eight threads in a tight lock/unlock loop for 300 ms, started
from a latch so the measurement window is identical:

| | Acquisitions | Self-succession |
|---|---:|---:|
| `ReentrantLock()` (barging) | 3.2–4.1 M | **97.9–98.4 %** |
| `ReentrantLock(true)` (fair) | 101–107 K | **0.00–1.28 %** |

Self-succession is the share of acquisitions where the lock went straight back to the thread that
just released it. At 98 % the unfair lock is barely shared at all: one thread runs a private
critical section while seven wait, because the releasing thread still holds the cache line and wins
the re-acquire before a parked thread can even be scheduled. Ordering removes that completely and
costs **30–40×** here.

> **How not to measure it.** Per-thread acquisition counts over a fixed window are dominated by
> startup skew and rank the *fair* lock as the less fair one. The same trap is documented at length
> in the `Semaphore` note; barging is a question about the handoff, so the handoff is what has to be
> counted.

### `synchronized` versus `ReentrantLock` on JDK 25

The usual advice — "use `ReentrantLock` on virtual threads, because `synchronized` pins the
carrier" — **is out of date on the JDK this project targets.** Measured directly, with a single
carrier thread (`-Djdk.virtualThreadScheduler.parallelism=1 -Djdk.virtualThreadScheduler.maxPoolSize=1`)
and a virtual thread blocking inside each construct:

| | JDK 21.0.8 | JDK 25.0.1 |
|---|---|---|
| blocked inside `synchronized` | **pinned** — the second virtual thread never ran (3 s) | not pinned |
| blocked inside `ReentrantLock` | not pinned | not pinned |

That is JEP 491, delivered in JDK 24. *(The `mapConcurrent` note gives carrier pinning as a reason
for preferring `ReentrantLock`; on JDK 25 that reason no longer applies, though the note's other
reasons — conditions and timed acquisition — do.)*

Pinning aside, the two are not interchangeable under contention (ad-hoc benchmark, tiny critical
section):

| Threads | `synchronized` | `ReentrantLock` |
|---:|---:|---:|
| 1 | 74–270 M ops/s | 85–90 M ops/s |
| 8 | 15.7–16.9 M | **43.2–44.4 M** |
| 24 | 16.5–28.8 M | **39.3–41.2 M** |

Uncontended, `synchronized` is the faster of the two and wildly variable: the 74 M and 270 M
readings come from the same run. Something the JIT does to an uncontended monitor stops applying
once a second thread appears — the exact optimisation was not investigated, so the honest summary is
the spread itself. Contended, `ReentrantLock` is 2.5× ahead. Neither figure is a reason to rewrite working code; both are a reason not to assume
`synchronized` is "the cheap one".

---

## Part 2 — `StampedLock`: the mode that is not a lock

`tryOptimisticRead()` acquires **nothing**. It returns a version number; you read what you like;
`validate(stamp)` afterwards answers one question — *did a writer get in while I was reading?*

The clearest proof that it holds nothing needs no second thread at all, and is the first test in
`StampedLockTest`:

```java
long stamp = lock.tryOptimisticRead();
int x = point.x;                  // reads 1

long write = lock.writeLock();    // the *same* thread takes the write lock. No wait: it holds nothing
try { point.moveTo(50); } finally { lock.unlockWrite(write); }

int y = point.y;                  // reads 100 — from the new state
assertNotEquals(2 * x, y);        // torn, across an invariant that always held under the lock
assertFalse(lock.validate(stamp));
```

So the reader really can see garbage, and the whole contract is that it finds out afterwards. The
idiom is therefore three ordered steps, and every one of them is load-bearing:

```java
long stamp = lock.tryOptimisticRead();
int x = point.x;
int y = point.y;                  // 1. copy into LOCALS — the fields may change under you
if (!lock.validate(stamp)) {      // 2. validate AFTER the reads, never before
    stamp = lock.readLock();      // 3. fall back to a real lock; do not spin on the optimistic path
    try { x = point.x; y = point.y; } finally { lock.unlockRead(stamp); }
}
return x + y;                     // only the locals are ever used
```

Validating before the reads checks nothing. Using the *fields* after validating checks nothing
either, because they can change between the check and the use. The copy is the point.

### How often does it actually have to retry?

Readers running that idiom against one writer that never pauses, 300 ms (asserted: zero torn pairs
ever accepted; the rates are measured):

| Readers | Optimistic attempts | Validation failures | Torn pairs accepted |
|---:|---:|---:|---:|
| 1 | 9.6–22.4 M | 3.1–7.8 % | **0** |
| 8 | 546–757 M | 0.05–0.06 % | **0** |
| 24 | 3.67–3.72 G | ~0.00 % | **0** |

The failure rate *falls* as readers are added, which looks backwards until you notice what is being
starved: 24 spinning readers leave the single writer almost no opportunity to acquire, so there are
fewer writes to lose a race against. The rate is a property of the writer's duty cycle, not a
constant of the lock — which is exactly why it is reported here and only bounded in the test.

### What it costs

`StampedLock` is a bag of sharp edges, and the tests pin each one down:

| | Behaviour |
|---|---|
| Reentrancy | **none**. A second `writeLock()` from the holder deadlocks it against itself — demonstrated on a daemon using `writeLockInterruptibly()`, so the standoff can be observed and then escaped |
| Conditions | **none**. `asReadLock().newCondition()` throws `UnsupportedOperationException` |
| Interruption | `readLock()`/`writeLock()` ignore interrupts; only the `…Interruptibly` forms respond |
| Ownership | none — nothing tracks who holds what; the stamp is all there is |
| Upgrade | `tryConvertToWriteLock` succeeds only for the sole reader; on failure **you still hold the read lock** and must release it with the original stamp |
| Downgrade | `tryConvertToOptimisticRead` releases the lock and hands back an observation stamp |
| Readers | do not invalidate a stamp; an *empty* write section does — `validate` tracks acquisition, not mutation |

The failed-upgrade row is the one that bites in production. `tryConvertToWriteLock` returning `0`
reads like "nothing happened", and what it actually means is "the upgrade failed **and** you are
still a reader". Dropping the stamp there leaks a read lock, which is a deadlock later and
somewhere else.

### A read stamp is a version number, not a credential

Probed rather than assumed, and then asserted so that a change gets noticed:

| | Result |
|---|---|
| `unlockRead(stamp + 1)` … `+ 126` | **succeeds** — and releases the lock |
| `unlockRead(stamp + 127)`, `+ 128`, `- 1` | `IllegalMonitorStateException` |
| `unlockRead(stampFromAnotherStampedLock)` | **succeeds**, when both locks are in the same state |
| `unlockWrite(writeStamp + 1)` | `IllegalMonitorStateException` |
| `unlockRead(writeStamp)`, `unlockWrite(readStamp)` | `IllegalMonitorStateException` |

`unlockRead` checks the version bits above the 7-bit reader count, and that *some* reader is
counted — not that this stamp is the one you were handed. Two fresh locks each with one reader have
byte-identical stamps, so one will release the other. A **write** stamp is checked exactly, because
the write bit sits inside the checked region.

None of this is specified, and none of it should be relied on. The practical reading: the stamp will
catch some of your mistakes on the write path and roughly none on the read path, so a `StampedLock`
wants its `unlockRead` in a `finally` next to its `readLock` far more than a `ReentrantLock` does.

---

## Part 3 — Choosing between them

Ad-hoc benchmark, readers taking a consistent two-field pair, M reads/s, range over runs:

**No writer at all**

| Readers | `synchronized` | `ReentrantLock` | `ReentrantRWLock` | `StampedLock.readLock` | optimistic |
|---:|---:|---:|---:|---:|---:|
| 1 | 73 | 78 | 67–70 | 72–73 | **456–462** |
| 8 | 4.6–5.0 | 39.9–41.6 | 7.0–8.8 | 16.9–19.1 | **3 045–3 147** |
| 24 | 4.5–4.9 | 34.7–36.5 | 6.4–7.4 | 10.9–12.3 | **4 323–4 436** |

**One writer running flat out**

| Readers | `synchronized` | `ReentrantLock` | `ReentrantRWLock` | `StampedLock.readLock` | optimistic |
|---:|---:|---:|---:|---:|---:|
| 1 | 13.2–13.7 | 6.0–7.6 | 1.4–3.2 | 6.6–7.7 | **35–53** |
| 8 | 3.9–4.6 | 36.9–38.8 | 6.6–8.6 | 10.6–11.8 | **791–1 044** |
| 24 | 4.5 | 33.5–35.0 | 6.0–6.9 | 11.6–14.8 | **3 940–4 108** |

Three things fall out of this, and the second is the one worth remembering:

1. **The optimistic read is 100–400× a read lock**, because it is not a lock: no cache line is
   written, so eight readers do not contend at all. That is where the super-linear scaling comes
   from — 456 M at one reader, 3 100 M at eight.

2. **`ReentrantReadWriteLock` is 4–5× *slower* than a plain exclusive `ReentrantLock`** here
   (7–9 M against 40 M at 8 threads). A read-write lock is not free concurrency: every reader still
   writes a shared counter to announce itself, so readers contend with each other on exactly the
   cache line they were supposed to be avoiding. It only pays when the read section is long enough
   that the parallelism it buys outweighs that bookkeeping — and the critical section here is two
   field reads, which is far below that line. Reaching for a read-write lock because "reads
   dominate" and never measuring is a way to make code slower and more complicated at once.

3. **`StampedLock.readLock` is also slower than exclusive `ReentrantLock`** at 8+ readers, for the
   same reason. If you take a `StampedLock` and use its read lock, you have paid for the complexity
   and bought nothing. The optimistic path is the entire reason to be there.

Exclusive locking, for completeness (ad-hoc benchmark, M ops/s):

| Threads | `synchronized` | `ReentrantLock` | `ReentrantLock(fair)` | `StampedLock.writeLock` | `VarHandleLock` | `VarHandleLock(fair)` |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 74–270 | 85–90 | 82–88 | 79–81 | 64–66 | 63–65 |
| 8 | 15.7–16.9 | 43.2–44.4 | 0.36–0.44 | **45.5–47.4** | 9.2–9.8 | 0.37–0.40 |
| 24 | 16.5–28.8 | 39.3–41.2 | 0.29–0.33 | **39.9–41.9** | 7.7–7.9 | 0.30–0.33 |

`StampedLock`'s write lock is the fastest exclusive lock in the set — it has no ownership or hold
count to maintain. That is the same missing bookkeeping that makes it non-reentrant.

**A decision procedure, in order.** Use `synchronized` until it is a measured problem. Then
`ReentrantLock` — for a timeout, an interruptible acquire, a condition, or contention. Reach for
`StampedLock` only when reads dominate, the read section is short, *and* you are willing to write
the validate-and-retry idiom; anything less and it is a slower `ReentrantLock` with sharp edges. And
a read-write lock needs a read section long enough to pay for its own bookkeeping — measure before,
not after.

---

## Part 4 — `VarHandleLock`: what is actually inside a lock

`src/java/jdk/VarHandleLock.java` is a reentrant exclusive `Lock` in about 120 lines of logic:
two `VarHandle`s, a `ConcurrentLinkedQueue` and `LockSupport`. It exists to make the answer to
"what is in a lock?" concrete, and the answer has three parts.

**1. One atomic state word.** `state` is the hold count; `0` means free; a successful
`compareAndSet(0, 1)` *is* the acquisition. An uncontended lock is one CAS and one store — which is
why the uncontended column above is so flat across every implementation.

**2. An owner, so holds can be counted.** This is the whole of reentrancy and the whole of why
`unlock` can reject a thread that never acquired. It is the single field that separates a lock from
a semaphore permit.

**3. A queue and a parking protocol**, for when the CAS fails. Everything hard lives here.

### The memory-ordering modes, which are the reason to use a `VarHandle` at all

A `volatile` field gives every access the same, strongest ordering. A `VarHandle` makes ordering a
property of the *access*, so each line can say what it needs:

| Line | Mode | Why |
|---|---|---|
| `STATE.compareAndSet(this, 0, 1)` | CAS | the acquisition; a full fence, so the previous holder's writes are visible |
| `STATE.set(this, n)` (re-entry) | **plain** | owner-confined while held — no other thread may read it, so ordering buys nothing |
| `STATE.setVolatile(this, 0)` | volatile store | the release; this one store publishes the whole critical section |

The plain store is the point of the exercise rather than a micro-optimisation: it is correct only
because of a stated invariant, and writing the mode down is what makes the invariant visible. This
is what `volatile` costs you — it cannot express "plain here, releasing there".

### Lost wakeups, and the ordering argument that prevents them

The dangerous interleaving: a thread fails its CAS, and the holder releases and unparks the queue
*before* that thread has enqueued. It then parks with nobody left to wake it.

The guard is ordering, not luck. `acquireQueued` **enqueues first and re-checks after**, so a
release that missed the enqueue is caught by the re-check, and a release that saw the enqueue
unparks. There is no third case. `LockSupport` supplies the rest: its permit is sticky, so an
`unpark` arriving before the `park` is not lost — it just makes the park return at once.

That argument is why `VarHandleLockTest`'s main test looks the way it does. It mixes all four
acquisition modes with an interrupter running, because the **cancellation** paths are where a lost
wakeup hides: a thread that abandons its wait may be carrying the signal meant for somebody else, so
it must dequeue itself *and* unpark the new head. And it asserts "every worker finished" directly
rather than inferring it from the totals — a permanently parked worker contributes nothing to either
side of a count, so a totals-only check would pass through a hang.

### What it costs

From the table above: correct, and **4–5× slower than `ReentrantLock` under contention**
(9.2–9.8 M against 43.2–44.4 M at 8 threads), 1.4× slower uncontended. AQS earns that with a
spin-before-park path, a smarter intrusive queue, and cancellation that does not walk a
`ConcurrentLinkedQueue`. The fair variant lands within noise of `ReentrantLock(true)`
(0.37–0.40 M against 0.36–0.44 M), because at that point both are paying for a park/unpark per
handoff and nothing else matters.

`newCondition()` throws. A correct `Condition` needs a second queue and a transfer protocol between
the two — a larger exercise than this one, and `StampedLock` makes the same refusal for the same
reason.

---

## Part 5 — Vector calculation with a `VarHandle`

`VarHandle` gives two things that look like vectors. Neither is SIMD, and the difference between
them matters.

### Eight lanes per memory access (SWAR)

`MethodHandles.byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN)` reinterprets a `byte[]` as
`long`s. One read pulls **eight byte lanes into one register**, and ordinary integer arithmetic then
works on all eight at once — SIMD Within A Register. The single rule is that **a lane must not carry
into its neighbour**, and every mask in `VarHandleVector` is there to enforce it.

The detail worth stealing is the zero-lane mask. The famous one-liner

```java
(v - 0x0101010101010101L) & ~v & 0x8080808080808080L
```

is exact for *"does `v` contain a zero lane"* and **wrong for counting them**: a zero lane borrows
from its neighbour and marks it too. For `v = 0xFFFFFFFFFFFF0100` it reports two zero lanes where
there is one. The version that cannot borrow, because `(lane & 0x7F) + 0x7F` never exceeds `0xFE`:

```java
~((((v & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL) | v) | 0x7F7F7F7F7F7F7F7FL)
```

Measured over 1 MiB, single-threaded (ad-hoc benchmark). The right-hand pair repeats the run with
C2's auto-vectoriser disabled (`-XX:-UseSuperWord`), which is what turns the explanation below from
a guess into a measurement:

| Operation | Scalar | SWAR | | Scalar, no SuperWord | SWAR, no SuperWord |
|---|---:|---:|---|---:|---:|
| `count` a byte value | 5.7–6.5 | **18.4–23.1** | 3.5× | 6.4 *(unchanged)* | 14.3–15.7 |
| `indexOf`, full scan | 7.6–8.2 | **14.9–19.5** | 2.2× | 8.2 *(unchanged)* | 16.0–19.3 |
| lane-wise `add` | **5.9–8.9** | 4.2–5.3 | **0.6×** | **3.7–3.9** | **4.6–5.2** |

All figures GiB/s. Three things are now measured rather than assumed:

1. **The `add` row inverts when auto-vectorisation is switched off** — scalar halves, from 7.4–8.0
   to 3.7–3.9, and SWAR then wins. So SWAR does not compete with scalar code; it competes with
   **C2's auto-vectoriser**, which turns the naive `out[i] = (byte)(a[i] + b[i])` loop into real
   SIMD — wider than 8 lanes and with no masking. Hand-written SWAR blocks the optimisation it was
   imitating and loses to it.

2. **The scalar `count` loop is not auto-vectorised at all** — 6.4 GiB/s with SuperWord on or off.
   Its data-dependent branch is what stops it, and that is precisely why SWAR wins there: the SWAR
   form replaces the branch with arithmetic.

3. **The SWAR `count` loop is itself auto-vectorised** — 22.8 drops to 14.3 with SuperWord off. So
   SWAR's 3.5× is a partnership rather than a substitution: the hand-written masking removes the
   branch, and C2 then widens the branch-free loop that results. That is worth knowing before
   writing SWAR "because the JIT will not vectorise this" — sometimes the JIT will, once you have
   removed what was stopping it.

So: SWAR earns its place on the shapes the auto-vectoriser cannot reach on its own — searching,
matching, counting, anything with a data-dependent branch. For straight-line arithmetic it is a
pessimisation. Measure in that order: plain loop first, SWAR only if the plain loop loses, and check
with `-XX:-UseSuperWord` which of the two you are actually competing against.

### One element at a time, atomically

`MethodHandles.arrayElementVarHandle` gives every element of a plain array the full access-mode set,
with no `AtomicLongArray` wrapper. Access-mode rules, probed and then asserted:

| | |
|---|---|
| byte-array view | **`get`/`set` only** — every atomic mode throws `UnsupportedOperationException` |
| ByteBuffer view, heap buffer | `IllegalStateException: Atomic access not supported for heap buffer` |
| ByteBuffer view, direct buffer | atomic modes work, and require alignment |
| `getAndAdd` on `double[]`/`float[]` | **supported** — the numeric modes are not integral-only |
| `getAndBitwiseOr` on `double[]` | `UnsupportedOperationException` — the *bitwise* modes are |
| `compareAndSet` on `double[]` | compares **raw bits**: `-0.0` does not match `0.0`; `NaN` matches itself |

Two of those are worth stating plainly. The byte-array view being plain-access-only is a feature for
SWAR — the reads are unaligned by construction, and plain access is the only kind that tolerates it.
And the bit comparison is a *liveness* bug waiting to happen: a CAS loop whose witness came from
anywhere but a read of that element can spin forever against a value that `==` says is equal. That
is the same `-0.0`/`NaN` asymmetry `ArrayComparisonTest` documents for `Arrays.equals`, arriving as
a hang instead of a wrong answer.

What there is *no* access mode for is anything the hardware lacks an instruction for — a maximum,
for instance. `maxInto` is the CAS retry loop that fills the gap, and it uses `Double.compare`
rather than `>` so that its result agrees with `Math.max` on `NaN` and on `-0.0`.

### Atomic per lane is not atomic per vector

This is the trap. Four writers adding the same value to all 256 lanes, one reader checking whether
the lanes it sees agree with each other, 300 ms:

| | Reads | Reader saw a partial update |
|---|---:|---:|
| per-lane `getAndAdd` (lock-free) | 558–682 K | **97.8–99.6 %** |
| whole vector under one lock | 159–190 K | **0** |

Per-lane atomicity means no *update* is lost. It says nothing about what a reader sees, because no
two lanes move together — so a reader observes a vector that no thread ever wrote, almost every
time. For a bag of independent counters that is fine. For anything with a cross-lane invariant it is
silently wrong, and the failure is invisible in testing because each lane is individually perfect.

And it is not even faster (256-lane vector, 8 threads, vector updates/s):

| | |
|---|---:|
| per-lane `getAndAdd` | 2.19–2.55 M |
| whole vector under `VarHandleLock` | 5.83–6.22 M |
| whole vector under `ReentrantLock` | **8.70–14.53 M** |

256 contended CAS operations cost more than one lock acquisition and 256 plain stores — by 4–6×.
"Lock-free is faster" is a statement about *uncontended* single words, and it inverts as soon as one
logical update spans many of them.

---

## What this is not

- **`StampedLock` is not a drop-in `ReentrantReadWriteLock`.** No reentrancy, no conditions, no
  ownership, and an upgrade that can fail while leaving you holding the read lock.
- **A fair lock is not a fair share.** Fairness orders the threads already waiting. It says nothing
  about how much any caller may hold — that is the `EqualShareSemaphore` problem in
  [the other note](map-concurrent-and-equal-share-semaphore.md).
- **`VarHandleLock` is not for use.** It is the mechanism written out. `ReentrantLock` is better
  tested, has conditions, and is 4–5× faster under contention.
- **SWAR is not SIMD** — and as measured above it does not even replace SIMD, it cooperates with
  it. The Vector API (`jdk.incubator.vector`) is a different thing again: still incubating, and
  requiring an `--add-modules` this project does not use.
- **None of the throughput figures is a benchmark result.** They are one machine, one JVM, one
  afternoon, with tiny critical sections chosen to maximise contention. The invariants are asserted;
  the numbers are indicative.

## Test oracles

`test/java/jdk/` — 94 tests across four suites, ~4 s:

| Suite | Tests | Oracle |
|---|---:|---|
| `ReentrantLockTest` | 20 | JDK semantics; a fair lock's grant order asserted exactly; the handoff measured |
| `StampedLockTest` | 15 | JDK semantics; a deterministic torn read; zero torn pairs accepted under load |
| `VarHandleLockTest` | 12 | a non-atomic critical section (exclusion), and every worker finishing (wakeups) |
| `VarHandleVectorTest` | 47 | SWAR against a scalar reference at **every** length 0–40, not just multiples of 8 |

Two choices in there are deliberate and transferable:

**Latches, not stopwatches, wherever the claim allows it.** "The waiter is definitely parked" is
`while (!lock.hasQueuedThread(t)) Thread.onSpinWait()`, not a sleep. Sleeps appear only where the
assertion genuinely is "and then nothing happened for 200 ms".

**The tail is where SWAR breaks.** Every SWAR routine is checked at lengths 0, 1, 2, 7, 8, 9, 15,
16, 17, 23, 31, 33, 40. A suite that only uses multiples of 8 tests the wide loop and never the
scalar remainder, which is where the bug always is.

## Running it

```
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test         # needs JDK 25
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest='ReentrantLockTest,StampedLockTest,VarHandleLockTest,VarHandleVectorTest'
```

The throughput tables came from a single-file program run as
`java -cp target/classes Bench.java`, kept outside the tree deliberately: it has no assertions, so
it is not a test, and this package does not ship measurement harnesses as production source.
