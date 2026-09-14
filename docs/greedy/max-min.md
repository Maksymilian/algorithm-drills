# Minimum unfairness — sort, then slide a window of `k`

Notes for [`src/java/greedy/MaxMin.java`](../../src/java/greedy/MaxMin.java),
tested by [`test/java/greedy/MaxMinTest.java`](../../test/java/greedy/MaxMinTest.java).

**Problem** ("Angry Children") Given `arr[n]` and `k`, choose `k` elements so that
`max − min` over the chosen ones — the *unfairness* — is as small as possible. Samples:
`k=3, [10,100,300,200,1000,20,30] → 20`; `k=4, [1,2,3,4,10,20,30,40,100,200] → 3`;
`k=2, [1,2,1,2,1] → 0`. Values need not be unique.

## `C(n, k)` selections, and the `n − k + 1` that matter

At the problem's bounds `C(n, k)` is a number with tens of thousands of digits. One exchange
argument throws all but a linear number of them away.

**An optimal selection is `k` consecutive elements of the sorted array.** Take any selection; let
`m` be its smallest element, `M` its largest, and `i` the index of `m` once the array is sorted.
Every chosen element is `≥ m`, so all `k` of them sit at sorted index `i` or later — which puts the
largest of them at index `i + k − 1` or later. Therefore

```
M - m  >=  sorted[i + k - 1] - sorted[i]
```

and the right-hand side is itself a legal selection: the window starting at `i`. So no selection
beats the best window, and every window is achievable. The problem is

```
answer = min over i in [0, n - k] of   sorted[i + k - 1] - sorted[i]
```

Worked on sample 0, `k = 3`:

```
sorted     10   20   30   100   200   300   1000
windows   [10   20   30]                            ->  20   <- best
               [20   30   100]                      ->  80
                    [30   100  200]                 ->  170
                         [100  200  300]            ->  200
                              [200  300  1000]      ->  800
```

The same walk end to end — sort, slide, take the narrowest — on sample 0's array with the `30`
removed, so the answer moves to `90`:

![Sorting 10, 100, 300, 200, 1000, 20 and sliding a window of three over it, the narrowest being 10 to 100](img.png)

*Four windows over six elements: `n - k + 1` of them, against `C(6, 3) = 20` selections the exchange
argument never has to look at.*

## What "greedy" means here

Not an incremental take-the-best-element loop — there is none, and the obvious ones are all wrong.
On `[0, 10, 20, 30, 31, 32]` with `k = 3`:

| Greedy-looking rule | Picks | Unfairness |
|---|---|---:|
| the `k` smallest values | 0, 10, 20 | 20 |
| the `k` values closest to the mean (20.5) | 20, 30, 31 | 11 |
| **the tightest window** | **30, 31, 32** | **2** |

The greedy step is the *exchange*: any selection can be slid inward onto consecutive elements
without getting worse. That is what collapses the search to one linear scan — and it is the whole
algorithm. Everything left to pay for is the sort.

## The sort is not incidental

It cannot be avoided by being cleverer with comparisons. At `k = 2` the answer is the smallest gap
between any two elements, and it is `0` **exactly when the array holds a duplicate**. Deciding that
is element distinctness, which needs `Ω(n log n)` comparisons — so no comparison-based method beats
sorting at this problem, for any `k`.

`maxMinByRadixSort` is faster only because it leaves the model. It never compares two values; it
indexes buckets by their bits, which is information a comparison does not yield. The lower bound is
untouched — it just does not apply.

## Six methods, five cost profiles

| Method | Time | Extra space | Array afterwards |
|---|---|---|---|
| `maxMin(k, int[])` / `maxMin(k, List)` | O(n log n) | 4n bytes | untouched |
| `maxMinAsLong(k, int[])` | O(n log n) | 4n bytes | untouched |
| `maxMinInPlace(k, int[])` | O(n log n) | 0 – 4n bytes † | **sorted** |
| `maxMinByRadixSort(k, int[])` | **O(n)** | 8n bytes | untouched |
| `fairestSelection(k, int[])` | O(n log n) | 4n bytes | untouched |

`fairestSelection` returns the `k` elements themselves rather than their spread — the witness to the
number the others report, and a sub-multiset of the input.

† **`maxMinInPlace` allocates no copy of its own, which is not the same as allocating nothing.**
`Arrays.sort(int[])` scans for ascending runs before it partitions, and on an array built of several
of them it merges instead — into a freshly allocated `int[n]`. Measured with
`getCurrentThreadAllocatedBytes` at `n = 10⁷`:

| input to `Arrays.sort(int[])` | it allocates |
|---|---:|
| already sorted (one run) | 0 bytes |
| random | ~2 MB (the run index, before it gives up) |
| two sorted halves concatenated | **40 MB — exactly `n` ints** |
| ten sorted runs | **40 MB** |

So the copy this method avoids comes back precisely when the input is partly ordered — which is
common in real data and is exactly when people reach for the in-place variant. Radix sort's `8n` is
the only space figure in the file that does not depend on the shape of the data.

## Measured

Ad hoc, `n = 10⁷`, `k = 5 × 10⁶`, JDK 25, one JVM per cell, 4 warm-up reps then the median of 9,
with the input clone kept outside the timer — a throwaway harness, not reproduced by the build.

| shape | `Arrays.sort` alone | sort a copy | sort in place | **radix sort** |
|---|---:|---:|---:|---:|
| random full-range `int` | 76 ms | 82 ms | 77 ms | **57 ms** |
| `0 .. 10⁹` (the problem's range) | 78 ms | 84 ms | 79 ms | **58 ms** |
| `0 .. 999` (low cardinality) | 40 ms | 41 ms | **34 ms** | 72 ms |
| already sorted | 2 ms | 8 ms | **4 ms** | 227 ms |

**The asymptotically better algorithm wins by 1.4×, and loses by 27×.** That is the whole lesson of
the table. `O(n)` against `O(n log n)` at `n = 10⁷` promises roughly a 23× gap; what shows up is
57 ms against 82 ms, because `log n` is multiplied by a very small constant in a well-tuned
quicksort while each radix pass reads and scatter-writes 40 MB.

**And radix is oblivious to order, which is where it loses.** The JDK's sort detects existing runs
and nearly exits on already-sorted input — 8 ms including the copy, 2 ms without it — while radix
pays its full three passes regardless (the fourth is skipped: every value here is below 2²⁴, so the
top byte is uniform). 227 ms against 8 ms is a **27× loss** on the input a comparison sort finds
easiest. Low-cardinality data is a milder version of the same story: the comparison sort gets
*faster* as duplicates pile up (40 ms against 76 ms), while radix's cost depends only on how many
byte positions vary — so the gap closes, and then reverses.

**The copy costs about 4 ms**, which is the whole difference between `sort a copy` and
`sort in place` in every row. `maxMinInPlace` earns its place on memory rather than time — but only
on input the JDK's sort does not decide to merge, per the footnote above. None of the four shapes
measured here triggers that, which is exactly why the timing table cannot be read as a memory
table.

## Beyond the stated bounds

The constraints promise `0 ≤ arr[i] ≤ 10⁹`, so the spread always fits in an `int`. Nothing here
relies on that, because the failure is silent when it comes:

```
{Integer.MIN_VALUE, -1, Integer.MAX_VALUE}, k = 2

 in int:   MAX_VALUE - (-1)        wraps to -2147483648   <- "the narrowest window"
 in long:  2147483648              is simply wider than the other pair
```

An `int` subtraction does not merely lose precision here, it inverts the comparison and returns a
*negative* unfairness. Every subtraction is done in `long`. `maxMin` keeps the platform's `int`
return and narrows with `Math.toIntExact`, so a genuinely oversized answer throws rather than lies;
`maxMinAsLong` is the one to call when the values are unconstrained.

The other edges: `k = 1` is always `0`, `k = n` leaves no choice, and `k < 1` or `k > n` is an
`IllegalArgumentException` — checked *before* `maxMinInPlace` sorts, so a rejected call cannot
reorder the caller's array.

## Test oracles

| Oracle | Scale | Catches |
|---|---|---|
| **Brute force over all `C(n,k)` selections** | **every** `k`, every array of `n ≤ 10` | the exchange argument being false |
| Hand-written expected answers | 16 cases incl. all three samples | a wrong window or off-by-one |
| `Arrays.sort` + a second hand-written scan | 400 random arrays, `n ≤ 300` | a wrong scan bound |
| Agreement across every `k` on sign-crossing input | `MIN_VALUE`, `MAX_VALUE`, negatives | radix ordering the sign byte unsigned |
| `fairestSelection` contract | 400 random arrays | a witness that is not a sub-multiset, or not `k` long |
| Fixed answers at 10⁷ | shuffled identity, and 10⁴ copies of each value | scale |

The brute force is the load-bearing one. Every method here *assumes* the answer lies in a window of
consecutive sorted elements; enumerating selections by bitmask assumes nothing, so it is the only
test that would survive the exchange argument turning out to be wrong. Everything else would agree
with a confidently incorrect implementation.

### Mutation check

Seventeen deliberate breaks, sixteen caught:

| Mutation | Caught by |
|---|---|
| the window's far end read at `k` instead of `k − 1` | 26 errors |
| the scan keeps the *widest* window | 23 failures |
| the prefix sum over the radix buckets removed | 19 |
| the sort dropped from `maxMinAsLong` | 15 |
| every radix pass skipped as "uniform" | 13 |
| the scan stops one window early | 11 |
| `maxMinInPlace` does not sort | 11 |
| the radix sign byte read unsigned, and every byte flipped instead of one | 8 each |
| the top radix pass dropped | 6 |
| `fairestSelection` returns the first window rather than the best | 6 |
| the spread subtracted in `int` | 3 |
| `k < 1` accepted; `k > n` accepted; `(int)` cast instead of `toIntExact` | 2 each |
| `maxMinInPlace` validates *after* sorting | 1 |

The `spread subtracted in int` row is the thin one, and honestly so: the wrap needs an array
spanning more than half the `int` range, which the problem's own constraints make impossible. Three
tests reach it because three were written to.

The single survivor is an equivalent mutant, not a gap: deleting the "this pass cannot reorder
anything" check leaves the radix sort running all four passes unconditionally — slower, and
identical in what it produces.

```
mvn test -Dtest=MaxMinTest
```
