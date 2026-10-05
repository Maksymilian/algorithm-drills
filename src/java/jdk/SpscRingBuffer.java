package jdk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;
import java.util.function.LongConsumer;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * A bounded single-producer, single-consumer queue of {@code long}s, off-heap, with its indices laid
 * out so that the two threads never write to the same cache line.
 *
 * <h2>Why there are no locks and no CAS</h2>
 * Each index has exactly one writer: only the producer moves {@code tail}, only the consumer moves
 * {@code head}. A single writer never needs an atomic read-modify-write, so every update is a plain
 * read followed by a {@code setRelease} — an ordinary store on x86. The release/acquire pair is the
 * whole synchronisation argument:
 *
 * <ul>
 *   <li>the producer stores the element, then {@code TAIL.setRelease(tail + 1)}; a consumer that
 *       {@code getAcquire}s that tail is guaranteed to see the element.</li>
 *   <li>the consumer reads the element, then {@code HEAD.setRelease(head + 1)}; a producer that
 *       {@code getAcquire}s that head knows the slot is free to overwrite.</li>
 * </ul>
 *
 * The indices increase forever and are masked only when they address a slot, so {@code tail - head}
 * is the size and there is no full/empty ambiguity to spend a slot on. A {@code long} at one
 * increment per nanosecond wraps after 292 years.
 *
 * <h2>The layout</h2>
 * <pre>
 *   offset   0  producer: tail, headCache, padding to 128   (written only by the producer)
 *   offset 128  consumer: head, tailCache, padding to 128   (written only by the consumer)
 *   separate    data:     capacity × long
 * </pre>
 *
 * The caches are what make it fast. Without them the producer would read the consumer's
 * {@code head} on every offer, pulling the consumer's line across the interconnect each time. With
 * them it reads {@code head} only when its cached copy says the queue is full, which in a running
 * pipeline is once per lap rather than once per element. The caches have to live <em>inside</em>
 * the padded groups: as two ordinary fields of this object they would sit next to each other, and
 * the producer's cache and the consumer's cache would false-share with each other.
 *
 * <h2>Who owns the memory</h2>
 * The caller, through the {@link Arena} the constructor takes. Two threads use this queue, so in
 * real use that arena must be {@linkplain Arena#ofShared() shared}, and must not be closed while
 * either thread can still reach the queue. A {@linkplain Arena#ofConfined() confined} arena works
 * only when one thread plays both roles — which is what the single-threaded tests do.
 *
 * <p>Nothing checks that there is only one producer and one consumer. Two producers would both read
 * the same tail and overwrite each other's element; that is the price of having no CAS.
 */
public final class SpscRingBuffer {

    /** Bytes per index group: two cache lines, as in {@link PaddedCounters#STRIDE}. */
    public static final long STRIDE = 128;

    private static StructLayout side(String index, String cache) {
        return MemoryLayout.structLayout(
                JAVA_LONG.withName(index),
                JAVA_LONG.withName(cache),
                MemoryLayout.paddingLayout(STRIDE - 2 * JAVA_LONG.byteSize())
        ).withByteAlignment(STRIDE);
    }

    /** The two index groups, one per thread, each alone on its own pair of cache lines. */
    public static final StructLayout HEADER = MemoryLayout.structLayout(
            side("tail", "headCache").withName("producer"),
            side("head", "tailCache").withName("consumer"));

    // (MemorySegment header, long baseOffset) -> long
    private static final VarHandle TAIL = field("producer", "tail");
    private static final VarHandle HEAD_CACHE = field("producer", "headCache");
    private static final VarHandle HEAD = field("consumer", "head");
    private static final VarHandle TAIL_CACHE = field("consumer", "tailCache");

    private static VarHandle field(String group, String name) {
        return HEADER.varHandle(groupElement(group), groupElement(name)).withInvokeExactBehavior();
    }

    private final MemorySegment header;
    private final MemorySegment data;
    private final long mask;

    /**
     * @param arena    owns the header and the slots; shared if two threads will use the queue
     * @param capacity a power of two, so that an index becomes a slot with one mask
     */
    public SpscRingBuffer(Arena arena, int capacity) {
        if (capacity <= 0 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a positive power of two: " + capacity);
        }
        this.mask = capacity - 1;
        this.header = arena.allocate(HEADER);
        this.data = arena.allocate(JAVA_LONG, capacity);
    }

    // ---------- producer side ----------

    /**
     * Appends {@code value}, or returns false if the queue is full. Producer thread only.
     *
     * <p>The fast path touches only the producer's own line: a plain read of its tail, a plain read
     * of its cached head, the element store, and the release.
     */
    public boolean offer(long value) {
        long tail = (long) TAIL.get(header, 0L);                  // plain: we are its only writer
        if (tail - (long) HEAD_CACHE.get(header, 0L) > mask) {    // looks full by the cache
            long head = (long) HEAD.getAcquire(header, 0L);       // so look at the real thing
            HEAD_CACHE.set(header, 0L, head);
            if (tail - head > mask) return false;
        }
        data.setAtIndex(JAVA_LONG, tail & mask, value);
        TAIL.setRelease(header, 0L, tail + 1);                    // publishes the element
        return true;
    }

    // ---------- consumer side ----------

    /**
     * Hands up to {@code max} elements to {@code sink}, in order, and returns how many it handed
     * over. Consumer thread only.
     *
     * <p>Draining in batches is the other half of the performance: however many elements are taken,
     * the head is released once, so the producer sees one store from this side per batch rather
     * than one per element.
     */
    public int drain(LongConsumer sink, int max) {
        long head = (long) HEAD.get(header, 0L);                  // plain: we are its only writer
        long available = (long) TAIL_CACHE.get(header, 0L) - head;
        if (available < max) {                                    // the cache may be stale; refresh
            long tail = (long) TAIL.getAcquire(header, 0L);
            TAIL_CACHE.set(header, 0L, tail);
            available = tail - head;
        }
        int n = (int) Math.min(available, max);
        if (n <= 0) return 0;
        for (int i = 0; i < n; i++) {
            sink.accept(data.getAtIndex(JAVA_LONG, (head + i) & mask));
        }
        HEAD.setRelease(header, 0L, head + n);                    // frees all n slots at once
        return n;
    }

    // ---------- either side, approximately ----------

    /**
     * How many elements are queued. Exact from a thread that is neither producing nor consuming at
     * the time; from anywhere else it is out of date as soon as it returns.
     */
    public long size() {
        long head = (long) HEAD.getAcquire(header, 0L);
        long tail = (long) TAIL.getAcquire(header, 0L);
        return tail - head;
    }

    public int capacity() {
        return (int) (mask + 1);
    }

    /** The index groups, read-only — for checking their layout and alignment. */
    public MemorySegment headerSegment() {
        return header.asReadOnly();
    }
}
