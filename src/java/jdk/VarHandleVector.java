package jdk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Vector arithmetic through {@link VarHandle}, in the two senses the API actually supports —
 * neither of which is SIMD, and both of which are worth knowing before reaching for
 * {@code jdk.incubator.vector}.
 *
 * <h2>1. A byte array read eight lanes at a time</h2>
 * {@link MethodHandles#byteArrayViewVarHandle} reinterprets a {@code byte[]} as an array of wider
 * values. Reading one {@code long} out of it pulls <b>eight byte lanes into one register</b>, and
 * ordinary integer arithmetic then operates on all eight at once. The technique is SWAR — SIMD
 * Within A Register — and it is how {@code String.indexOf} and friends are written.
 *
 * <p>The arithmetic has one rule: <b>a lane must not carry into its neighbour</b>. Every operation
 * below is built to respect it, and the masks are the proof rather than decoration.
 *
 * <p>The byte order argument is not a formality. {@link ByteOrder#LITTLE_ENDIAN} is fixed here so
 * that lane <i>i</i> is bits {@code 8i..8i+7}, which is what makes
 * {@link Long#numberOfTrailingZeros} a lane index in {@link #indexOf}. Passing
 * {@link ByteOrder#nativeOrder()} would be marginally faster on a little-endian machine and would
 * silently give {@link #indexOf} the wrong answer on a big-endian one.
 *
 * <p>A byte-array view offers <b>only {@code get} and {@code set}</b> — every atomic mode on it
 * throws {@link UnsupportedOperationException}. That is a feature here: the reads are unaligned by
 * construction (the loop walks by 8 from index 0 of an arbitrary array) and plain access is the
 * only kind that tolerates it. Wide <em>atomic</em> access needs
 * {@link MethodHandles#byteBufferViewVarHandle} over a <em>direct</em> buffer; on a heap buffer it
 * fails too.
 *
 * <h2>2. One array element at a time, atomically</h2>
 * {@link MethodHandles#arrayElementVarHandle} gives every element of an array the full set of
 * access modes — {@code getVolatile}, {@code compareAndSet}, {@code getAndAdd} — without a boxed
 * {@code AtomicLongArray} wrapper around it. That makes a plain {@code long[]} a lock-free
 * accumulator that several threads can add into, and {@code getAndAdd} works on {@code double[]}
 * and {@code float[]} too (only the <em>bitwise</em> modes are integral-only).
 *
 * <p>It buys <b>per-lane</b> atomicity and nothing more. A reader of the whole array can still
 * observe a mixture of before and after, because no two lanes are updated together. When the
 * vector has an invariant <em>across</em> lanes, that is not enough and a lock is the answer —
 * which is what {@link #addIntoUnderLock} is for, and what the tests beside this class measure.
 *
 * <p>What there is no access mode for is anything the hardware has no instruction for — a maximum,
 * for instance. {@link #maxInto} is the CAS retry loop that fills that gap, and it is where the
 * bitwise nature of {@code compareAndSet} on a {@code double} starts to matter: {@code -0.0} does
 * not match {@code 0.0} and a {@code NaN} does match itself, the same asymmetry
 * {@code ArrayComparisonTest} covers for {@code Arrays.equals}.
 */
public final class VarHandleVector {

    /** Eight byte lanes per access. Little-endian so that lane <i>i</i> is bits {@code 8i..8i+7}. */
    private static final VarHandle LONG_LANES =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final VarHandle LONG_ELEMENT = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle DOUBLE_ELEMENT = MethodHandles.arrayElementVarHandle(double[].class);

    private static final int LANES = Long.BYTES;
    private static final long ONES = 0x0101010101010101L;   // 1 in every lane
    private static final long HIGHS = 0x8080808080808080L;  // the top bit of every lane
    private static final long LOWS = 0x7F7F7F7F7F7F7F7FL;   // everything but the top bit

    private VarHandleVector() {
    }

    // ---------- 1. SWAR over a byte[] ----------

    /**
     * Marks the zero lanes of {@code v}: the result has bit 7 of lane <i>i</i> set exactly when
     * lane <i>i</i> of {@code v} is zero, and every other bit clear.
     *
     * <p>The obvious formula, {@code (v - ONES) & ~v & HIGHS}, is <em>wrong</em> for this: a zero
     * lane borrows from its neighbour and marks it too, so {@code 0x0100} reports two zero lanes
     * instead of one. It is fine for "is there <i>any</i> zero lane" and useless for counting. This
     * version cannot borrow, because {@code (lane & 0x7F) + 0x7F} is at most {@code 0xFE}:
     *
     * <pre>
     *   (lane &amp; 0x7F) + 0x7F   sets bit 7 iff the low seven bits are non-zero
     *   ... | lane             also sets it iff bit 7 itself is set
     *   ... | 0x7F             fills the low bits so the complement leaves only bit 7
     *   ~(...)                 bit 7 set iff the lane was zero
     * </pre>
     */
    private static long zeroLanes(long v) {
        return ~((((v & LOWS) + LOWS) | v) | LOWS);
    }

    /** Broadcasts one byte into all eight lanes: {@code 0xAB -> 0xABABABABABABABAB}. */
    private static long broadcast(byte value) {
        return (value & 0xFFL) * ONES;
    }

    /**
     * Counts occurrences of {@code value}, eight bytes per memory access.
     *
     * <p>XOR against the broadcast pattern turns "equals {@code value}" into "is zero", which is the
     * only lane-wise predicate that survives having no per-lane comparison instruction.
     */
    public static int count(byte[] data, byte value) {
        long pattern = broadcast(value);
        int i = 0, found = 0;
        int limit = data.length - LANES;
        for (; i <= limit; i += LANES) {
            found += Long.bitCount(zeroLanes((long) LONG_LANES.get(data, i) ^ pattern));
        }
        for (; i < data.length; i++) {                      // the tail, one lane wide
            if (data[i] == value) found++;
        }
        return found;
    }

    /**
     * The index of the first {@code value}, or {@code -1}. Same eight-lane step; the answer comes
     * out of the mark word rather than out of a second pass, since with little-endian lanes the
     * lowest set bit is the earliest matching byte.
     */
    public static int indexOf(byte[] data, byte value) {
        long pattern = broadcast(value);
        int i = 0;
        int limit = data.length - LANES;
        for (; i <= limit; i += LANES) {
            long marks = zeroLanes((long) LONG_LANES.get(data, i) ^ pattern);
            if (marks != 0) return i + (Long.numberOfTrailingZeros(marks) >>> 3);
        }
        for (; i < data.length; i++) {
            if (data[i] == value) return i;
        }
        return -1;
    }

    /**
     * Lane-wise {@code a[i] + b[i]}, wrapping exactly as {@code (byte) (a[i] + b[i])} does, eight
     * lanes per step.
     *
     * <p>Adding the two longs directly would let lane <i>i</i>'s carry corrupt lane <i>i+1</i>. So
     * the low seven bits are added — which cannot carry out of a lane — and the top bit is supplied
     * separately: {@code bit7(sum) = bit7(a) ^ bit7(b) ^ carry}, and the carry is already sitting in
     * bit 7 of the partial sum.
     */
    public static byte[] add(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("length " + a.length + " != " + b.length);
        }
        byte[] out = new byte[a.length];
        int i = 0;
        int limit = a.length - LANES;
        for (; i <= limit; i += LANES) {
            long x = (long) LONG_LANES.get(a, i);
            long y = (long) LONG_LANES.get(b, i);
            LONG_LANES.set(out, i, ((x & LOWS) + (y & LOWS)) ^ ((x ^ y) & HIGHS));
        }
        for (; i < a.length; i++) {
            out[i] = (byte) (a[i] + b[i]);
        }
        return out;
    }

    // ---------- 2. per-element atomics on a plain array ----------

    /**
     * Adds {@code delta} into {@code accumulator} lane by lane, lock-free.
     *
     * <p>Every lane is atomic, so no update is lost however many threads run this at once. The
     * <em>vector</em> is not atomic: a concurrent reader can see some lanes updated and some not,
     * and so can observe a vector that no thread ever wrote. That is usually fine for a counter
     * array and never fine for anything with a cross-lane invariant.
     */
    public static void addInto(long[] accumulator, long[] delta) {
        requireSameLength(accumulator.length, delta.length);
        for (int i = 0; i < accumulator.length; i++) {
            LONG_ELEMENT.getAndAdd(accumulator, i, delta[i]);
        }
    }

    /**
     * The same for doubles. {@code getAndAdd} is available here — the numeric access modes cover
     * {@code float} and {@code double}, and only the bitwise ones are integral-only — so there is
     * no reason to hand-write a CAS loop for an addition.
     *
     * <p>It is atomic per lane, not associative across threads: floating-point addition is not,
     * so the total depends on the order the threads happened to run in. With values that are exact
     * in binary the results agree anyway, which is what the tests use.
     */
    public static void addInto(double[] accumulator, double[] delta) {
        requireSameLength(accumulator.length, delta.length);
        for (int i = 0; i < accumulator.length; i++) {
            DOUBLE_ELEMENT.getAndAdd(accumulator, i, delta[i]);
        }
    }

    /**
     * Lane-wise {@code accumulator[i] = max(accumulator[i], candidate[i])}, lock-free.
     *
     * <p>This is the operation that has no access mode: there is a {@code getAndAdd} and a
     * {@code getAndBitwiseOr}, and there is no {@code getAndMax}, because no processor offers one.
     * Anything outside the fixed menu is written as a read-compare-swap retry loop, and this is
     * what one looks like.
     *
     * <p>Two details are load-bearing. The witness passed to {@code compareAndSet} must come from a
     * read of <em>that element</em>, because the comparison is over raw bits: a {@code -0.0} in the
     * array will never match a {@code 0.0} computed elsewhere, and the loop would spin forever.
     * And the comparison uses {@link Double#compare} rather than {@code >}, which is what makes the
     * result agree with {@link Math#max} on {@code NaN} and on {@code -0.0}.
     */
    public static void maxInto(double[] accumulator, double[] candidate) {
        requireSameLength(accumulator.length, candidate.length);
        for (int i = 0; i < accumulator.length; i++) {
            double seen;
            do {
                seen = (double) DOUBLE_ELEMENT.getVolatile(accumulator, i);
                if (Double.compare(candidate[i], seen) <= 0) break;   // already at least as large
            } while (!DOUBLE_ELEMENT.compareAndSet(accumulator, i, seen, candidate[i]));
        }
    }

    /**
     * The whole-vector alternative: one lock, one update, and readers that hold the same lock see
     * every lane move together. Slower per element than {@link #addInto(long[], long[])} and the
     * only correct choice when the lanes mean something jointly.
     */
    public static void addIntoUnderLock(VarHandleLock lock, long[] accumulator, long[] delta) {
        requireSameLength(accumulator.length, delta.length);
        lock.lock();
        try {
            for (int i = 0; i < accumulator.length; i++) {
                accumulator[i] += delta[i];       // plain: the lock is what publishes them
            }
        } finally {
            lock.unlock();
        }
    }

    /** A consistent copy of {@code vector}, for readers of {@link #addIntoUnderLock}. */
    public static long[] snapshot(VarHandleLock lock, long[] vector) {
        lock.lock();
        try {
            return Arrays.copyOf(vector, vector.length);
        } finally {
            lock.unlock();
        }
    }

    private static void requireSameLength(int a, int b) {
        if (a != b) throw new IllegalArgumentException("length " + a + " != " + b);
    }
}
