# Primitive `int` lists: fastutil, Eclipse Collections, and a plain `int[]`, measured

Notes for [`PrimitiveIntListsBenchmark`](../../test/java/jdk/PrimitiveIntListsBenchmark.java), a JMH
benchmark, and the two suites beside it:
[`PrimitiveIntListsBenchmarkTest`](../../test/java/jdk/PrimitiveIntListsBenchmarkTest.java), which
keeps the benchmark honest, and [`PrimitiveIntListsTest`](../../test/java/jdk/PrimitiveIntListsTest.java),
which pins down how the three containers *behave*: who copies, who shares, what `equals` means.

The benchmark does the same seven operations three ways: by hand on an `int[]`, through fastutil's
`IntArrayList`, and through Eclipse Collections' `IntArrayList`. `ArrayList<Integer>` runs
alongside as the boxed baseline both libraries exist to replace.

Every number here was measured with that benchmark on JDK 25.0.1 (Temurin), Intel i7-14650HX (AVX2,
no AVX-512), pinned to the P-cores, with fastutil 8.5.19, Eclipse Collections 13.0.0 and JMH 1.37:
two forks of 3 × 1 s warm-up and 5 × 1 s measurement, `-Xms2g -Xmx2g`, G1. The JMH error is under
10% of the score on every row except two, both boxed at a million elements, where GC timing varies:
`append_boxed` (±28%) and `randomRead_boxed` (±11%). These numbers are indicative, from one machine
and one JDK. [Running it](#running-it) reproduces them.

The data is `size` random ints in `[0, size)`, seed 42, at two sizes: **1 000** (4 KB, fits in L1)
and **1 000 000** (4 MB per container, fits only in L3). Times are per element, so the two sizes
can be read side by side.

**The one-line answer.** Where a library method does what a hand-written loop does, it *is* that
loop: the same code, compiled the same way, at the same speed. Every difference bigger than noise
comes from one of three things: boxing, an algorithm chosen for you behind a method name, or a set
that is not sized up front.

| Operation | `int[]` by hand | fastutil | Eclipse | `ArrayList<Integer>` |
|---|---|---|---|---|
| sum, contains, random read | baseline | same | same | 3–5× slower |
| append (grow from empty) | baseline | 1.4× (2.7× at 1 000) | 1.2× | 15× |
| sort ascending | `Arrays.sort` | **4.7×**: `sort(null)` is a radix sort | same: `sortThis()` *is* `Arrays.sort` | 22× |
| sort descending | sort, then reverse | 8.6×: any comparator | same as the array if you reverse; 9.4× with a comparator | 22× |
| count distinct | sort 1×, **bitmap 0.07×** | 1.0× | **2.9×** with `toSet()`, 1.4× presized | 3.7× |

(At 1 000 000 elements unless marked; the array column is the reference.)

---

## Passes over the data: the libraries are the loop

| Variant | ns/element, 1 000 | vs array | ns/element, 1 000 000 | vs array |
|---|---:|---:|---:|---:|
| `sum_array` | 0.05 | 1.0× | 0.08 | 1.0× |
| `sum_eclipse_sum` | 0.05 | 1.0× | 0.07 | 1.0× |
| `sum_eclipse_get` | 0.06 | 1.0× | 0.07 | 1.0× |
| `sum_fastutil_getInt` | 0.06 | 1.0× | 0.07 | 1.0× |
| `sum_fastutil_elements` | 0.06 | 1.1× | 0.08 | 1.0× |
| `sum_fastutil_nextInt` | 0.06 | 1.0× | 0.07 | 1.0× |
| `sum_fastutil_forEachLoop` | 0.06 | 1.1× | 0.07 | 1.0× |
| `sum_boxed` | 0.29 | **5.3×** | 0.37 | **4.8×** |
| `contains_array` | 0.13 | 1.0× | 0.12 | 1.0× |
| `contains_array_noEarlyExit` | 0.12 | 1.0× | 0.13 | 1.0× |
| `contains_fastutil` | 0.13 | 1.0× | 0.12 | 1.0× |
| `contains_eclipse` | 0.13 | 1.0× | 0.12 | 1.0× |
| `contains_boxed` | 0.36 | 2.8× | 0.37 | 3.1× |
| `randomRead_array` | 0.23 | 1.0× | 0.82 | 1.0× |
| `randomRead_fastutil` | 0.26 | 1.1× | 0.82 | 1.0× |
| `randomRead_eclipse` | 0.26 | 1.1× | 0.83 | 1.0× |
| `randomRead_boxed` | 0.70 | 3.0× | 2.94 | 3.6× |

Read the libraries' source and the tie stops being surprising. Eclipse's `sum()` is

```java
long result = 0L;
for (int i = 0; i < this.size; i++) result += this.items[i];
```

and fastutil's `contains` is `indexOf`, which is `for (int i = 0; i < size; i++) if (k == a[i])`.
C2 inlines them, hoists the two field loads out of the loop, and from there it is compiling the same
loop as `sum_array`.

**It vectorizes them the same way, too.** `-XX:-UseSuperWord` turns off C2's auto-vectorizer, and
with it every primitive `sum` variant slows down by the same factor: 3.4–3.5× at 1 000, 2.6–2.8× at
1 000 000. That covers the array loop, Eclipse's `sum()` and `get` loop, and all four fastutil loops,
the boxing for-each among them. The `int`-to-`long` widening sum is vectorized whether you write it
or a library does. `sum_boxed` and every `contains` do not move at all, because they never were
vectorized.

**Bounds checks cost nothing.** `getInt(i)` and `get(i)` each check `index < size` on every call,
yet the indexed loops match the array: in a counted loop C2 proves the check redundant once and
drops it. fastutil's `elements()`, the escape hatch that hands you the backing array, buys nothing
here for the same reason.

**The for-each loop over a fastutil list boxes in the source, and costs nothing here.** A fastutil
list is an `Iterable<Integer>`, so `for (int v : list)` compiles to the deprecated, boxing
`Integer next()`, not `nextInt()`. The bytecode is `Iterator.next()`, `checkcast Integer`,
`intValue()`, and nothing in the source says so. Measured, it ties `nextInt()` and
allocates **0 bytes per call** (`-prof gc`). Once `next()` is inlined, C2 sees
`Integer.valueOf(x).intValue()` and removes the box. That depends on the inlining: in a loop C2 does
not compile that way, the boxes would be real. `nextInt()` makes it not matter.

**`contains` does not vectorize, however it is written.** A search with an early exit is not a
loop shape C2 vectorizes, so `contains_array_noEarlyExit` rewrites it as a reduction with no exit,
like `sum`. It still does not vectorize: 0.122 µs at 1 000 with SuperWord off, against 0.123 with it
on, and no faster than the early-exit loop. It stays in the benchmark as the negative result, because it is the obvious
thing to try. Every primitive variant scans at about 8 elements per nanosecond, roughly 1.6 per
cycle, scalar.

**Boxing is what costs: 3–5×.** Each element of `ArrayList<Integer>` is a reference to a 16-byte
object, so every read is two dependent loads. Random reads at a million elements pay a second cache
miss each (3.6×). This is the *best* case for the boxed list: its `Integer`s were allocated one after
another, in list order, so a sequential pass walks memory forwards. Boxes allocated over a program's
lifetime, interleaved with everything else, are not laid out so conveniently.

---

## Building a list: growth

| Variant | ns/element, 1 000 | vs array | ns/element, 1 000 000 | vs array | bytes allocated / element |
|---|---:|---:|---:|---:|---:|
| `append_array_presized` | 0.24 | 0.2× | 0.37 | 0.3× | 4.0 |
| `append_array_grown` | 1.03 | 1.0× | 1.13 | 1.0× | 12.1 |
| `append_eclipse` | 1.15 | 1.1× | 1.40 | 1.2× | 12.1 |
| `append_fastutil` | **2.79** | **2.7×** | 1.55 | 1.4× | 14.6 |
| `append_boxed` | 3.57 | 3.5× | 16.9 | 14.9× | 30.6 |

The floor is the presized array: when the final size is known, nothing grows, and the 4 bytes per
element allocated are the 4 bytes kept. Everything else starts at 10 slots and grows by half again
each time it fills, copying as it goes. That is about three times the final array in allocation
(12.1 bytes per element), and three to four times the time.

`append_array_grown` grows exactly as Eclipse does (10, 16, 25, 38…), and the two allocate
identically and run within 25% of each other. fastutil grows from 10 to 15, 22, 33… (`length +
length/2`, without Eclipse's `+ 1`), so it reallocates once more and allocates 14.6 bytes per
element.

**fastutil's `add` is 2.7× slower at 1 000 elements.** Not because of inlining: `-XX:+PrintInlining`
shows `add` and `grow` both inlined, for both libraries. Not because of the extra copying either,
which is 20% more allocation, not 170% more time. What is left was not isolated:

- Keeping fastutil's `grow` out of line
  (`-XX:CompileCommand=dontinline,it.unimi.dsi.fastutil.ints.IntArrayList::grow`) recovers about a
  fifth of it, 2.79 → 2.18 µs.
- Forcing Eclipse's growth path inline does *not* slow Eclipse down: 1.10 µs over three forks.

So the inlined growth code is at most part of the story. The rest needs hardware counters or the
generated assembly, and `perf` is locked on this machine (`perf_event_paranoid` is 3). At a million
elements, where moving memory dominates, the gap shrinks to 1.1× against Eclipse.

A first attempt at the Eclipse half came out at 1.91 ± 1.30 µs over two forks: an error almost as
large as the score, from ten iterations that disagreed wildly. Rerun with three forks, all fifteen
iterations agreed, at 1.10. A number is not worth quoting until it has survived a rerun in fresh
JVMs, and that is what JMH's forks are for.

**Boxed append is 15× at a million elements**, and 30.6 bytes allocated per element: a 16-byte
`Integer` for every value above 127, plus a reference array that itself grows by copying. It is also
the noisiest row in the benchmark (±28%), most likely because it depends most on when the young
collections happen to land.

---

## Sorting: where the library picks the algorithm for you

| Variant | ns/element, 1 000 | vs array | ns/element, 1 000 000 | vs array | bytes allocated / element |
|---|---:|---:|---:|---:|---:|
| `sort_array` (`Arrays.sort`) | 2.70 | 1.0× | 7.48 | 1.0× | 4.2 |
| `sort_eclipse` (`sortThis()`) | 2.78 | 1.0× | 7.46 | 1.0× | 4.2 |
| `sort_fastutil` (`sort(null)`) | 13.3 | **4.9×** | 35.4 | **4.7×** | 4.0 |
| `sort_fastutil_quickSort` | 13.2 | 4.9× | 72.5 | 9.7× | 4.0 |
| `sort_boxed` | 28.7 | 10.6× | 168 | 22.5× | 8.1 |

Every variant sorts a fresh copy; the copy is the 4 bytes per element they all allocate.

**`Arrays.sort(int[])` is not the sort you remember.** On this JDK and CPU, HotSpot replaces
`DualPivotQuicksort`'s inner sort with a vectorized one, an intrinsic that uses AVX2 here, since
this CPU has no AVX-512. Turning the intrinsic off
(`-XX:DisableIntrinsic=_arraySort,_arrayPartition`) shows what it is worth:

| ns/element | intrinsic on | intrinsic off | |
|---|---:|---:|---:|
| `sort_array`, 1 000 | 2.70 | 10.5 | 3.9× |
| `sort_array`, 1 000 000 | 7.48 | 56.0 | **7.5×** |
| `sort_fastutil` (radix sort), 1 000 000 | 35.4 | 35.4 | unchanged |

Everything built on `Arrays.sort` moves with it: `sort_eclipse`, `sortDescending_array` and
`distinct_array_sort` slow down 3.5–3.9× at 1 000 and 7.1–7.5× at a million. fastutil's sorts do
not move at all, because they never call it. Without the intrinsic, fastutil's radix sort is 1.6×
*faster* than `Arrays.sort` at a million elements.

**fastutil never calls `Arrays.sort`.** `IntArrayList.sort(null)` goes to `IntArrays.stableSort`,
which goes to `unstableSort`: a stable sort of `int`s is indistinguishable from an unstable one.
`unstableSort` picks

```java
if (to - from >= RADIX_SORT_MIN_THRESHOLD)   // 2 000, "determined on an Intel i7 8700K"
    radixSort(a, from, to);
else
    quickSort(a, from, to);
```

Against the scalar `Arrays.sort` that threshold was right, and the radix sort still beats it, as
measured above. Against the SIMD sort, the radix sort is 4.7× slower at a million elements, and
fastutil's quicksort is 4.9× slower at a thousand. The library made a sound choice for the JDK it was tuned on, and the JDK moved
underneath it. Nothing in the method name tells you which algorithm you are getting.

**Eclipse inherits the speed-up for free**, because `sortThis()` is a one-liner:
`Arrays.sort(this.items, 0, this.size)`. The hand-written array and Eclipse are the same call.

### Descending

`Arrays.sort(int[])` takes no comparator, so "descending" means either a comparator, through a
library, or sorting ascending and reversing in place, which is one more pass.

| Variant | ns/element, 1 000 | vs array | ns/element, 1 000 000 | vs array | bytes allocated / element |
|---|---:|---:|---:|---:|---:|
| `sortDescending_array` (sort, reverse) | 3.02 | 1.0× | 7.80 | 1.0× | 4.2 |
| `sortDescending_eclipse` (`sortThis().reverseThis()`) | 3.14 | 1.0× | 8.24 | 1.1× | 4.2 |
| `sortDescending_fastutil` (`sort(OPPOSITE)`) | 8.61 | 2.9× | 67.2 | 8.6× | 8.0 |
| `sortDescending_fastutil_unstable` (`unstableSort(OPPOSITE)`) | 14.2 | 4.7× | 70.2 | 9.0× | 4.0 |
| `sortDescending_eclipse_comparator` (`sortThis(comparator)`) | 10.4 | 3.4× | 73.7 | 9.4× | 4.0 |
| `sortDescending_boxed` (`reverseOrder()`) | 37.0 | 12.3× | 174 | 22.3× | 8.1 |

The reverse adds 0.3 ns per element. A comparator costs 9×, whichever library supplies it: every
comparator route is a scalar comparison sort, with no SIMD path.
fastutil's `sort(comparator)` is a stable **merge sort**, which needs a second buffer as large as
the list (the 8.0 bytes per element); `unstableSort(comparator)` is its quicksort, in place. At a
thousand elements the merge sort is the faster of the two.

So the descending sort to write is the ascending one, followed by a reverse.

---

## Counting distinct values: hash sets, and what an array can know

| Variant | ns/element, 1 000 | vs array | ns/element, 1 000 000 | vs array | bytes allocated / element |
|---|---:|---:|---:|---:|---:|
| `distinct_array_sort` (sort a copy, count changes) | 3.16 | 1.0× | 8.09 | 1.0× | 4.2 |
| `distinct_array_bitmap` | 0.56 | **0.2×** | 0.57 | **0.07×** | 0.1 |
| `distinct_fastutil` (`IntOpenHashSet`) | 1.45 | 0.5× | 8.09 | 1.0× | 8.4 |
| `distinct_eclipse` (`toSet()`) | 5.01 | 1.6× | 23.7 | **2.9×** | 16.8 |
| `distinct_eclipse_presized` | 1.91 | 0.6× | 11.4 | 1.4× | 8.4 |
| `distinct_boxed` (`HashSet<Integer>`) | 7.95 | 2.5× | 30.0 | 3.7× | 28.6 |

**The hash set wins small and ties large.** At a thousand elements fastutil's set takes half the time
of sort-and-scan. At a million its table is 2²¹ slots, 8 MB, past this core's L2, and nearly every
insert is a cache miss. The sort streams through memory instead, and the two tie.

**Eclipse's `toSet()` does not size the set.** It calls `new IntHashSet(IntIterable)`, which starts
at the default capacity and rehashes its way up as `addAll` fills it. You can see it in the
allocation: 16.8 bytes per element, twice the final table. fastutil's
`new IntOpenHashSet(IntCollection)` sizes for the collection first. Presizing the Eclipse set by hand
(`new IntHashSet(size)`, then `addAll`) halves its allocation to exactly fastutil's and makes it 2.1×
faster at a million elements (2.6× at a thousand). The remaining 1.4× gap to fastutil was not
isolated: the two hash and probe differently.

**The bitmap is 14× faster than anything else at a million elements**, because it is not a general
solution. The values are known to lie in `[0, size)`, so one bit per possible value is a complete,
collision-free set in 125 KB, and counting is a `bitCount` per word. No general-purpose set can
assume that. This is the one place in the benchmark where hand-written array code wins outright, and
it wins by knowing something about the data, not by being a better loop.

---

## What a fork's heap holds: one class histogram

`scripts/perf/snapshot.sh` takes `jstack` and `jcmd` snapshots of the JVM a benchmark runs in (the
JMH fork, not the host). One `jcmd GC.class_histogram`, taken during a `size = 1 000 000` fork:

```
$ scripts/perf/snapshot.sh --take histo --count 1 'PrimitiveIntListsBenchmark.sum_boxed$' -p size=1000000
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:          1601       16386880  [I (java.base@25.0.1)
   2:       1000169       16002704  java.lang.Integer (java.base@25.0.1)
   3:         31493        5199984  [B (java.base@25.0.1)
   4:          1945        4184136  [Ljava.lang.Object; (java.base@25.0.1)
```

The four containers, side by side:

- **`[I`, 16 MB**: four `int[]`s of 4 MB each, namely the source array, the random indices,
  fastutil's backing array and Eclipse's. A million primitive ints is one 4 MB array, whichever
  library holds it.
- **`java.lang.Integer` and `[Ljava.lang.Object;`, 20 MB**: the boxed list alone. That is a million
  16-byte objects plus a 4 MB array of compressed references to them, five times the footprint for
  the same values.

The `@State` builds all four containers for every benchmark, so every fork holds all of them,
`sum_array`'s as much as `sum_boxed`'s. That costs the allocation-heavy benchmarks a few tens of MB
of extra live data for the GC to step around. Separate states per container would remove it, at the
price of a benchmark that is harder to check for equal work.

The same script caught a mistake that no score would have shown. Its `jcmd VM.command_line` of a
fork started with `jmh.sh ... -- -XX:-UseSuperWord` showed `MaxHeapSize` at the 16 GB default:
JMH's `-jvmArgsAppend` *replaces* `@Fork(jvmArgsAppend = ...)` rather than adding to it. The heap
flags now sit in `jvmArgsPrepend`, and every number on this page comes from forks with
`-Xms2g -Xmx2g`.

---

## What to take from it

1. **For passes over the data, pick either library for its API, not its speed.** `sum`, `contains`,
   indexed and iterated reads all compile to the hand-written loop, vectorization included.
2. **For sorting, know which algorithm the method is.** Eclipse's `sortThis()` is `Arrays.sort`.
   fastutil's `sort(null)` is a radix sort, 4.7× slower on a JDK whose `Arrays.sort` is vectorized.
   With fastutil, call `Arrays.sort(list.elements(), 0, list.size())` instead, which is what
   Eclipse does.
3. **Never sort descending with a comparator**: sort ascending and reverse, which is about 9× faster.
4. **Size Eclipse sets yourself.** `toSet()` does not, and it costs 2–3×.
5. **Boxing is the expensive part**, at 3–22× and 5× the memory. Either library removes it.
6. **The array wins outright only where you know more than the library does**: a known size (the
   presized append, 3–4× faster) or a known range (the bitmap, 14× faster).

---

## Running it

```sh
scripts/perf/jmh.sh PrimitiveIntListsBenchmark -f 2 -prof gc              # everything, ~25 min
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sort' -p size=1000000      # one group, one size
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sum_' -- -XX:-UseSuperWord                 # the vectorization control
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sort_array$' \
    -- -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_arraySort,_arrayPartition        # the SIMD-sort control
scripts/perf/snapshot.sh --take histo --count 1 'PrimitiveIntListsBenchmark.sum_boxed$' -p size=1000000
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest='PrimitiveIntLists*'      # the two suites
```

The build never runs the benchmark. `mvn test-compile` generates its harness (the JMH annotation
processor is configured for the tests in the `pom.xml`), and `PrimitiveIntListsBenchmarkTest` runs
in the normal test phase. That test drives every `@Benchmark` method directly and checks two things.
First, that each variant of an operation returns the same answer as the others, at sizes either side
of fastutil's 2 000-element radix threshold. Second, that no variant changes the shared input. A
benchmark that sorted its input in place would time sorted data from the second invocation on, and
nothing in the scores would say so. Planting exactly that bug fails the test.
