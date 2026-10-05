package jdk;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * An array of {@code long}s indexed by a {@code long}, so it can hold more than
 * {@link Integer#MAX_VALUE} elements — which no Java array can.
 *
 * <h2>The limit it removes</h2>
 * An array's length is an {@code int}, so no array has more than {@code 2^31 - 1} elements, and
 * HotSpot caps it a few elements below that: {@code new byte[Integer.MAX_VALUE]} fails with
 * {@code OutOfMemoryError: Requested array size exceeds VM limit} however large the heap is. For
 * {@code long[]} that is a ceiling of 16 GiB, and for {@code byte[]} only 2 GiB. The usual workaround
 * is an array of arrays and a hand-written split of every index.
 *
 * <p>A {@link MemorySegment} is sized and addressed in {@code long} bytes, so the only limits are the
 * address space and the memory behind it. This class is one segment and one long-indexed
 * {@link VarHandle}, with no chunking.
 *
 * <h2>Two places the memory can come from</h2>
 * <ul>
 *   <li>{@link #allocate}: native memory from an {@link Arena}. It is outside the Java heap, so
 *       {@code -Xmx} does not limit it, and it is zeroed. It does use RAM.</li>
 *   <li>{@link #map}: a memory-mapped file. The file is sized with {@code setLength}, which leaves it
 *       sparse on ext4 and tmpfs, so a 16 GiB array costs disk and RAM only for the pages that are
 *       actually written. This is how the tests reach past the {@code int} limit cheaply.</li>
 * </ul>
 *
 * In both cases the arena owns the memory, and closing it — at the end of a
 * {@code try (Arena arena = Arena.ofConfined())} — frees the memory or unmaps the file. Any later
 * access throws {@link IllegalStateException} rather than reading freed memory.
 *
 * <h2>The {@code int} limit has not gone away everywhere</h2>
 * Anything that copies the segment back <em>into</em> an array still runs into it:
 * {@link MemorySegment#toArray} throws once the result would need more than an {@code int} of
 * elements. Long-indexed data has to stay long-indexed all the way through.
 */
public final class BigLongArray {

    /** {@code (MemorySegment, long baseOffset, long index) -> long}; every coordinate is a {@code long}. */
    private static final VarHandle ELEMENT = JAVA_LONG.arrayElementVarHandle().withInvokeExactBehavior();

    private final MemorySegment segment;
    private final long length;

    private BigLongArray(MemorySegment segment) {
        this.segment = segment;
        this.length = segment.byteSize() / JAVA_LONG.byteSize();
    }

    /** {@code length} zeroed elements of native memory, owned by {@code arena}. */
    public static BigLongArray allocate(Arena arena, long length) {
        requireNonNegative(length);
        return new BigLongArray(arena.allocate(JAVA_LONG, length));
    }

    /**
     * {@code length} elements backed by {@code file}, created or resized to fit. Pages are read and
     * written lazily, so the parts of the file that are never touched stay sparse. The mapping lasts
     * until {@code arena} closes; the channel can be closed straight away.
     */
    public static BigLongArray map(Arena arena, Path file, long length) throws IOException {
        requireNonNegative(length);
        long bytes = Math.multiplyExact(length, JAVA_LONG.byteSize());
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(bytes);                          // ftruncate: sparse, no data written
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            return new BigLongArray(channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes, arena));
        }
    }

    public long length() {
        return length;
    }

    /**
     * The segment checks the bounds. An index past the end throws {@link IndexOutOfBoundsException}
     * as an array would, but a negative one throws {@link IllegalArgumentException}, and one whose
     * byte offset overflows a {@code long} throws {@link ArithmeticException}.
     */
    public long get(long index) {
        return (long) ELEMENT.get(segment, 0L, index);
    }

    public void set(long index, long value) {
        ELEMENT.set(segment, 0L, index, value);
    }

    /** Sets {@code [from, to)} to {@code value}: {@link MemorySegment#fill} for zeros, otherwise a loop. */
    public void fill(long from, long to, long value) {
        if (value == 0) {
            segment.asSlice(from * JAVA_LONG.byteSize(), (to - from) * JAVA_LONG.byteSize()).fill((byte) 0);
            return;
        }
        for (long i = from; i < to; i++) {
            ELEMENT.set(segment, 0L, i, value);
        }
    }

    /** The sum of {@code [from, to)}. The index is a {@code long} all the way through. */
    public long sum(long from, long to) {
        long total = 0;
        for (long i = from; i < to; i++) {
            total += (long) ELEMENT.get(segment, 0L, i);
        }
        return total;
    }

    /** The backing memory, read-only. */
    public MemorySegment segment() {
        return segment.asReadOnly();
    }

    private static void requireNonNegative(long length) {
        if (length < 0) throw new IllegalArgumentException("negative length: " + length);
    }
}
