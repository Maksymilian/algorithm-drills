package jdk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.LongAdder;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * A striped counter — a hand-built {@link LongAdder} — whose memory layout is declared rather than
 * left to the JVM, using the Foreign Function &amp; Memory API.
 *
 * <h2>The problem padding solves</h2>
 * Two threads incrementing two <em>different</em> {@code long}s still contend if the two values
 * share a cache line: every write takes the line exclusive, so it bounces between cores
 * exactly as if they shared the value. That is false sharing, and an array of eight {@code long}s
 * is the worst case — all eight fit in one 64-byte line.
 *
 * <p>Here each stripe is a {@link #SLOT} of {@value #STRIDE} bytes: the counter, then a
 * {@link MemoryLayout#paddingLayout padding layout} filling the rest. 128 rather than 64 because
 * Intel's adjacent-line prefetcher fetches 64-byte lines in pairs, so neighbours one line apart
 * still interfere; {@code @jdk.internal.vm.annotation.Contended} pads by 128 for the same reason.
 * {@code @Contended} would need {@code -XX:-RestrictContended} and leaves the layout to the JVM;
 * a layout puts it in the source, where a test can check it.
 *
 * <h2>Why the VarHandle is static and exact</h2>
 * {@link #VALUE} is derived from the layout, so its coordinates are
 * {@code (MemorySegment, long baseOffset, long index)}. Because it is {@code static final}, C2
 * constant-folds it and {@code getAndAdd} compiles to a single {@code lock xadd}.
 * {@link VarHandle#withInvokeExactBehavior()} makes a call with the wrong coordinate types — an
 * {@code int} index, a {@code 0} for {@code 0L} — throw, where it would otherwise be quietly adapted
 * on every call.
 *
 * <h2>Who owns the memory</h2>
 * The caller does: the constructor takes an {@link Arena}, and closing that arena frees the
 * stripes. That arena must be {@linkplain Arena#ofShared() shared} for the counter to be useful,
 * because a {@linkplain Arena#ofConfined() confined} segment throws {@link WrongThreadException}
 * from every thread but the one that created it. A confined arena is still right for
 * single-threaded use, and it is cheaper to close: closing a shared arena has to handshake with
 * every thread that might be mid-access.
 *
 * @see LongAdder
 */
public final class PaddedCounters {

    /** Bytes per stripe: two 64-byte cache lines, because the prefetcher pairs them. */
    public static final long STRIDE = 128;

    /** One stripe: the counter at offset 0, and padding up to {@link #STRIDE}. */
    public static final StructLayout SLOT = MemoryLayout.structLayout(
            JAVA_LONG.withName("value"),
            MemoryLayout.paddingLayout(STRIDE - JAVA_LONG.byteSize())
    ).withByteAlignment(STRIDE);

    /** {@code (MemorySegment segment, long baseOffset, long index) -> long}. */
    private static final VarHandle VALUE =
            SLOT.arrayElementVarHandle(groupElement("value")).withInvokeExactBehavior();

    private final MemorySegment slots;
    private final long mask;

    /**
     * @param arena   owns the stripes; closing it frees them and makes this counter unusable
     * @param stripes a power of two — a thread picks its stripe by masking its id
     */
    public PaddedCounters(Arena arena, int stripes) {
        if (stripes <= 0 || Integer.bitCount(stripes) != 1) {
            throw new IllegalArgumentException("stripes must be a positive power of two: " + stripes);
        }
        this.mask = stripes - 1;
        this.slots = arena.allocate(SLOT, stripes);   // zeroed, and honours the 128-byte alignment
    }

    /**
     * Adds one to the calling thread's stripe. Still an atomic read-modify-write, since two threads
     * can hash to one stripe; what the padding removes is contention between <em>different</em> stripes.
     */
    public void increment() {
        add(1L);
    }

    public void add(long delta) {
        long stripe = Thread.currentThread().threadId() & mask;
        long unused = (long) VALUE.getAndAdd(slots, 0L, stripe, delta);
    }

    /**
     * The total. Like {@link LongAdder#sum()} it is not a snapshot: each stripe is read atomically,
     * but increments that land during the loop may or may not be counted.
     */
    public long sum() {
        long total = 0;
        for (long i = 0; i <= mask; i++) {
            total += (long) VALUE.getVolatile(slots, 0L, i);
        }
        return total;
    }

    /** One stripe's value, so that tests can see where increments landed. */
    public long stripe(long index) {
        return (long) VALUE.getVolatile(slots, 0L, index);
    }

    public int stripes() {
        return (int) (mask + 1);
    }

    /** The backing memory, read-only — for checking its size and alignment, not for writing. */
    public MemorySegment segment() {
        return slots.asReadOnly();
    }
}
