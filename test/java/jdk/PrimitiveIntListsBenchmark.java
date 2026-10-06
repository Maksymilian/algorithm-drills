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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"-Xms2g", "-Xmx2g"})
public class PrimitiveIntListsBenchmark {

    static final int ABSENT = -1;

    @Param({"1000", "1000000"})
    public int size;

    int[] array;
    it.unimi.dsi.fastutil.ints.IntArrayList fastutil;
    MutableIntList eclipse;
    List<Integer> boxed;
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
        fastutil = new it.unimi.dsi.fastutil.ints.IntArrayList(array);
        eclipse = IntArrayList.newListWith(array.clone());
        boxed = new ArrayList<>(size);
        for (int v : array) boxed.add(v);
    }

    @Benchmark
    public int[] append_array_presized() {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = array[i];
        return out;
    }

    @Benchmark
    public int[] append_array_grown() {
        int[] out = new int[10];
        int n = 0;
        for (int v : array) {
            if (n == out.length) out = Arrays.copyOf(out, n + (n >> 1) + 1);
            out[n++] = v;
        }
        return out;
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

    @Benchmark
    public List<Integer> append_boxed() {
        List<Integer> list = new ArrayList<>();
        for (int v : array) list.add(v);
        return list;
    }

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

    @Benchmark
    public long sum_fastutil_elements() {
        int[] elements = fastutil.elements();
        long sum = 0;
        for (int i = 0, n = fastutil.size(); i < n; i++) sum += elements[i];
        return sum;
    }

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

    @Benchmark
    public long randomRead_boxed() {
        long sum = 0;
        for (int i : indices) sum += boxed.get(i);
        return sum;
    }

    @Benchmark
    public boolean contains_array() {
        for (int v : array) if (v == ABSENT) return true;
        return false;
    }

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

    @Benchmark
    public boolean contains_boxed() {
        return boxed.contains(ABSENT);
    }

    @Benchmark
    public int[] sort_array() {
        int[] copy = array.clone();
        Arrays.sort(copy);
        return copy;
    }

    @Benchmark
    public int[] sort_fastutil_quickSort() {
        int[] copy = array.clone();
        IntArrays.quickSort(copy);
        return copy;
    }

    @Benchmark
    public it.unimi.dsi.fastutil.ints.IntArrayList sort_fastutil() {
        var copy = fastutil.clone();
        copy.sort((IntComparator) null);
        return copy;
    }

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

    @Benchmark
    public it.unimi.dsi.fastutil.ints.IntArrayList sortDescending_fastutil() {
        var copy = fastutil.clone();
        copy.sort(IntComparators.OPPOSITE_COMPARATOR);
        return copy;
    }

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

    @Benchmark
    public int distinct_array_sort() {
        int[] copy = array.clone();
        Arrays.sort(copy);
        int distinct = copy.length == 0 ? 0 : 1;
        for (int i = 1; i < copy.length; i++) if (copy[i] != copy[i - 1]) distinct++;
        return distinct;
    }

    @Benchmark
    public int distinct_array_bitmap() {
        long[] seen = new long[(size + 63) >>> 6];
        for (int v : array) seen[v >>> 6] |= 1L << v;
        int distinct = 0;
        for (long word : seen) distinct += Long.bitCount(word);
        return distinct;
    }

    @Benchmark
    public int distinct_fastutil() {
        return new IntOpenHashSet(fastutil).size();
    }

    @Benchmark
    public int distinct_eclipse() {
        return eclipse.toSet().size();
    }

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
