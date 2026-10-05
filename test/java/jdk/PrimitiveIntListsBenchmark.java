package jdk;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * The same operations written by hand on a plain {@code int[]}, and done through fastutil's and
 * Eclipse Collections' {@code IntArrayList}, with {@code ArrayList<Integer>} alongside as the boxed
 * baseline both libraries exist to replace. {@link PrimitiveIntListsTest} pins down how the three
 * behave; this measures only how fast.
 *
 * <p>Methods are named {@code <operation>_<container>[_<how>]}, so JMH's alphabetical report keeps
 * each operation's variants together. Within an operation every variant does the same work on the
 * same values and returns the same answer, and none of them changes the state the next invocation
 * reads: {@link PrimitiveIntListsBenchmarkTest} checks both, because neither mistake shows in a
 * timing.
 *
 * <p>The data is {@code size} random ints in {@code [0, size)}. That leaves about 63% of them
 * distinct, so {@code distinct} has duplicates to find, and it gives the bitmap variant the known,
 * small range it relies on. The fastutil class shares its simple name with Eclipse's, so it is
 * written out in full, as in the test.
 *
 * <p>Not run by the build: {@code scripts/perf/jmh.sh PrimitiveIntListsBenchmark} runs it pinned to
 * the P-cores. What it measured is in {@code docs/jdk/primitive-int-lists.md}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
// Prepend, not append: a -jvmArgsAppend on the command line (jmh.sh -- ...) replaces the annotation's
// jvmArgsAppend instead of adding to it, and the heap would silently go back to the default.
@Fork(value = 1, jvmArgsPrepend = {"-Xms2g", "-Xmx2g"})
public class PrimitiveIntListsBenchmark {

    /** Not in the data, so {@code contains} always scans to the end. */
    static final int ABSENT = -1;

    /** 4 KB of ints, which fits in L1, and 4 MB, which fits only in L3. */
    @Param({"1000", "1000000"})
    public int size;

    int[] array;
    it.unimi.dsi.fastutil.ints.IntArrayList fastutil;
    MutableIntList eclipse;
    List<Integer> boxed;
    /** Positions for {@code randomRead}, drawn separately from the values. */
    int[] indices;

    @Setup
    public void setUp() {
        Random random = new Random(42);
        array = new int[size];
        indices = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = random.nextInt(size);
            indices[i] = random.nextInt(size);
        }
        fastutil = new it.unimi.dsi.fastutil.ints.IntArrayList(array);   // copies
        eclipse = IntArrayList.newListWith(array.clone());               // adopts, hence the clone
        // The Integers are allocated one after another, in list order, so iterating them walks memory
        // forwards: the best case for a boxed list.
        boxed = new ArrayList<>(size);
        for (int v : array) boxed.add(v);
    }

    // ---------- append: build a list of size values, starting empty ----------

    /** The floor: the final size is known, so nothing grows. */
    @Benchmark
    public int[] append_array_presized() {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = array[i];
        return out;
    }

    /** By hand, growing the way both libraries do: 10 slots first, then half as many again. */
    @Benchmark
    public int[] append_array_grown() {
        int[] out = new int[10];
        int n = 0;
        for (int v : array) {
            if (n == out.length) out = Arrays.copyOf(out, n + (n >> 1) + 1);
            out[n++] = v;
        }
        return out;   // spare capacity included, as in the lists
    }

    @Benchmark
    public it.unimi.dsi.fastutil.ints.IntArrayList append_fastutil() {
        var list = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (int v : array) list.add(v);
        return list;
    }

    @Benchmark
    public MutableIntList append_eclipse() {
        MutableIntList list = new IntArrayList();
        for (int v : array) list.add(v);
        return list;
    }

    /** Every value above 127 is a new {@code Integer}. */
    @Benchmark
    public List<Integer> append_boxed() {
        List<Integer> list = new ArrayList<>();
        for (int v : array) list.add(v);
        return list;
    }

    // ---------- sum: one sequential pass ----------
    // Into a long throughout: Eclipse's sum() returns one, and an int would overflow at the larger size.

    @Benchmark
    public long sum_array() {
        long sum = 0;
        for (int v : array) sum += v;
        return sum;
    }

    @Benchmark
    public long sum_fastutil_getInt() {
        long sum = 0;
        for (int i = 0; i < fastutil.size(); i++) sum += fastutil.getInt(i);
        return sum;
    }

    /** fastutil hands out its backing array, which turns this back into the array loop. */
    @Benchmark
    public long sum_fastutil_elements() {
        int[] elements = fastutil.elements();
        long sum = 0;
        for (int i = 0, n = fastutil.size(); i < n; i++) sum += elements[i];
        return sum;
    }

    /**
     * The loop everyone writes. A fastutil list is an {@code Iterable<Integer>}, so this compiles to
     * the boxing {@code next()}, not {@code nextInt()}, and nothing in the source says so.
     */
    @Benchmark
    public long sum_fastutil_forEachLoop() {
        long sum = 0;
        for (int v : fastutil) sum += v;
        return sum;
    }

    @Benchmark
    public long sum_fastutil_nextInt() {
        long sum = 0;
        for (IntIterator it = fastutil.iterator(); it.hasNext(); ) sum += it.nextInt();
        return sum;
    }

    @Benchmark
    public long sum_eclipse_sum() {
        return eclipse.sum();
    }

    @Benchmark
    public long sum_eclipse_get() {
        long sum = 0;
        for (int i = 0; i < eclipse.size(); i++) sum += eclipse.get(i);
        return sum;
    }

    @Benchmark
    public long sum_boxed() {
        long sum = 0;
        for (int v : boxed) sum += v;
        return sum;
    }

    // ---------- randomRead: size reads at random positions ----------

    @Benchmark
    public long randomRead_array() {
        long sum = 0;
        for (int i : indices) sum += array[i];
        return sum;
    }

    @Benchmark
    public long randomRead_fastutil() {
        long sum = 0;
        for (int i : indices) sum += fastutil.getInt(i);
        return sum;
    }

    @Benchmark
    public long randomRead_eclipse() {
        long sum = 0;
        for (int i : indices) sum += eclipse.get(i);
        return sum;
    }

    /** Two dependent loads per read: the reference, then the {@code Integer} it points to. */
    @Benchmark
    public long randomRead_boxed() {
        long sum = 0;
        for (int i : indices) sum += boxed.get(i);
        return sum;
    }

    // ---------- contains: a linear search for a value that is not there ----------

    @Benchmark
    public boolean contains_array() {
        for (int v : array) if (v == ABSENT) return true;
        return false;
    }

    /**
     * No early exit, so the loop is a reduction like {@code sum_array}, the shape C2 vectorizes. It
     * does not vectorize this one: kept as the negative result, since it is the obvious thing to try.
     */
    @Benchmark
    public boolean contains_array_noEarlyExit() {
        int hits = 0;
        for (int v : array) hits += v == ABSENT ? 1 : 0;
        return hits != 0;
    }

    @Benchmark
    public boolean contains_fastutil() {
        return fastutil.contains(ABSENT);
    }

    @Benchmark
    public boolean contains_eclipse() {
        return eclipse.contains(ABSENT);
    }

    /** {@code Integer.equals} per element, against a key boxed once. */
    @Benchmark
    public boolean contains_boxed() {
        return boxed.contains(ABSENT);
    }

    // ---------- sort: ascending, on a fresh copy (the copy is in every variant) ----------

    @Benchmark
    public int[] sort_array() {
        int[] copy = array.clone();
        Arrays.sort(copy);
        return copy;
    }

    /** fastutil's comparison sort, on the same raw array. */
    @Benchmark
    public int[] sort_fastutil_quickSort() {
        int[] copy = array.clone();
        IntArrays.quickSort(copy);
        return copy;
    }

    /** The list's own sort: radix sort from 2 000 elements, quicksort below. Never Arrays.sort. */
    @Benchmark
    public it.unimi.dsi.fastutil.ints.IntArrayList sort_fastutil() {
        var copy = fastutil.clone();
        copy.sort((IntComparator) null);
        return copy;
    }

    /** {@code sortThis()} is {@code Arrays.sort} on the backing array. */
    @Benchmark
    public MutableIntList sort_eclipse() {
        return IntArrayList.newList(eclipse).sortThis();
    }

    @Benchmark
    public List<Integer> sort_boxed() {
        List<Integer> copy = new ArrayList<>(boxed);
        copy.sort(null);
        return copy;
    }

    // ---------- sortDescending: the order Arrays.sort(int[]) cannot be asked for ----------

    /** Ascending, then reversed in place: one more pass instead of a comparator. */
    @Benchmark
    public int[] sortDescending_array() {
        int[] copy = array.clone();
        Arrays.sort(copy);
        for (int i = 0, j = copy.length - 1; i < j; i++, j--) {
            int t = copy[i];
            copy[i] = copy[j];
            copy[j] = t;
        }
        return copy;
    }

    /** {@code sort} with a comparator is a stable merge sort, with a second array to merge through. */
    @Benchmark
    public it.unimi.dsi.fastutil.ints.IntArrayList sortDescending_fastutil() {
        var copy = fastutil.clone();
        copy.sort(IntComparators.OPPOSITE_COMPARATOR);
        return copy;
    }

    /** {@code unstableSort} with the same comparator is quicksort, in place. */
    @Benchmark
    public it.unimi.dsi.fastutil.ints.IntArrayList sortDescending_fastutil_unstable() {
        var copy = fastutil.clone();
        copy.unstableSort(IntComparators.OPPOSITE_COMPARATOR);
        return copy;
    }

    @Benchmark
    public MutableIntList sortDescending_eclipse() {
        return IntArrayList.newList(eclipse).sortThis().reverseThis();
    }

    /** With a comparator Eclipse leaves Arrays.sort for its own quicksort. */
    @Benchmark
    public MutableIntList sortDescending_eclipse_comparator() {
        return IntArrayList.newList(eclipse).sortThis((a, b) -> Integer.compare(b, a));
    }

    @Benchmark
    public List<Integer> sortDescending_boxed() {
        List<Integer> copy = new ArrayList<>(boxed);
        copy.sort(Comparator.reverseOrder());
        return copy;
    }

    // ---------- distinct: how many different values there are ----------

    /** Sort a copy, then count where the value changes. */
    @Benchmark
    public int distinct_array_sort() {
        int[] copy = array.clone();
        Arrays.sort(copy);
        int distinct = copy.length == 0 ? 0 : 1;
        for (int i = 1; i < copy.length; i++) if (copy[i] != copy[i - 1]) distinct++;
        return distinct;
    }

    /**
     * One bit per possible value. Only possible because the values are known to lie in
     * {@code [0, size)}: hand-written code can use what you know about the data, and a general
     * purpose set cannot.
     */
    @Benchmark
    public int distinct_array_bitmap() {
        long[] seen = new long[(size + 63) >>> 6];
        for (int v : array) seen[v >>> 6] |= 1L << v;
        int distinct = 0;
        for (long word : seen) distinct += Long.bitCount(word);
        return distinct;
    }

    /** Open addressing with linear probing, presized from the list. */
    @Benchmark
    public int distinct_fastutil() {
        return new IntOpenHashSet(fastutil).size();
    }

    /** {@code toSet()} starts from the default capacity and rehashes its way up. */
    @Benchmark
    public int distinct_eclipse() {
        return eclipse.toSet().size();
    }

    /** The same set, sized for the list before the first add, as fastutil's constructor does. */
    @Benchmark
    public int distinct_eclipse_presized() {
        MutableIntSet set = new IntHashSet(eclipse.size());
        set.addAll(eclipse);
        return set.size();
    }

    @Benchmark
    public int distinct_boxed() {
        return new HashSet<>(boxed).size();
    }
}
