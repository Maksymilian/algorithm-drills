# Greedy Florist — the multipliers are forced, so only the pairing is a choice

Notes for [`src/java/greedy/GreedyFlorist.java`](../../src/java/greedy/GreedyFlorist.java),
tested by [`test/java/greedy/GreedyFloristTest.java`](../../test/java/greedy/GreedyFloristTest.java).

**Problem** `k` friends buy `n` flowers between them. A flower costs its listed price times one more
than the number of flowers *that same buyer* has already bought — a buyer's first flower costs `c`,
their second `2c`, their third `3c`. Buy all of them for as little as possible. Samples:
`k=3, [2,5,6] → 13`; `k=2, [2,5,6] → 15`; `k=3, [1,3,5,7,9] → 29`.

> The constraints block did not survive the paste. The usual bounds for this problem are
> `1 ≤ n, k ≤ 100` and `1 ≤ c[i] ≤ 10⁶`; nothing in the implementation depends on them, and the
> overflow section below is about what happens *inside* them.

## Two lemmas, and there is nothing else

### 1. The multipliers are not a choice

Each friend has exactly one *first* purchase, so across the whole group at most `k` flowers can
carry a multiplier of 1. At most `k` can carry 2, at most `k` can carry 3 — so at most `m · k`
flowers can carry a multiplier of `m` or less. That caps every possible scheme.

One scheme meets every one of those caps simultaneously: hand out `k` flowers at ×1, then `k` at ×2,
and so on. Since it is pointwise optimal and achievable, the multiset of multipliers is settled
before any price is looked at:

```
1, 1, ... 1,   2, 2, ... 2,   3, 3, ... 3,  ...        (k of each, until the flowers run out)
```

### 2. Given the multipliers, the pairing is the rearrangement inequality

Suppose a dearer flower carried a *larger* multiplier than a cheaper one — prices `c_a > c_b` with
multipliers `m_a > m_b`. Swap them. The cost changes by

```
(c_a·m_b + c_b·m_a) - (c_a·m_a + c_b·m_b)  =  (m_b - m_a)(c_a - c_b)  <  0
```

strictly cheaper, so no optimal purchase contains such a pair. The dearest flower takes the smallest
multiplier, and the answer is

```
cost = sum over i of   descending[i] * (i / k + 1)
```

Worked on sample 2, `k = 3`:

```
descending    9    7    5    3    1
multiplier   x1   x1   x1   x2   x2
cost          9  + 7  + 5  + 6  + 2   =  29
```

Both lemmas end to end on `[2, 5, 6, 3]` with `k = 2` — sort, read off the forced multipliers, pair
them largest-price-to-smallest-multiplier:

![Greedy Florist on prices 2, 5, 6, 3 with two friends: sorted descending, multipliers forced to 1, 1, 2, 2, total 21](img_1.png)

*The purchase plan in panel 5 is the one `purchasePlan(2, {2, 5, 6, 3})` returns — friend 0 takes
ranks 0 and 2 (`6`, then `3`), friend 1 takes ranks 1 and 3 (`5`, then `2`), each buying their
dearer flower while it is still cheapest to do so.*

## This one really is greedy

Worth saying, because its neighbour [`maxMin`](max-min.md) is not — and the difference is exactly
the thing the two files are useful for side by side.

| | `maxMin` | `GreedyFlorist` |
|---|---|---|
| Incremental rule? | none — it enumerates `n − k + 1` windows | **yes**: dearest remaining flower → cheapest remaining multiplier |
| Commits irrevocably? | nothing to commit | **yes**, one flower at a time |
| Exchange argument | proves the *search space* collapses | proves the *choice* is never wrong |
| Shape | sort, then exhaustive scan of a pruned space | greedy choice property + exchange proof |

`GreedyFlorist` is the textbook shape: a locally optimal decision, taken without lookahead, proved
globally optimal by showing any deviation can be exchanged back. `maxMin` only borrows the proof
technique.

## Five entry points

| Method | Time | Extra space | Array afterwards |
|---|---|---|---|
| `getMinimumCost(k, int[])` / `(k, List)` | O(n log n) | 4n bytes | untouched |
| `getMinimumCostAsLong(k, int[])` | O(n log n) | 4n bytes | untouched |
| `getMinimumCostInPlace(k, int[])` | O(n log n) | 0 – 4n bytes † | **sorted** |
| `getMinimumCostByCounting(k, int[])` | **O(n + maxPrice)** | 4·maxPrice bytes | untouched |
| `purchasePlan(k, int[])` | O(n log n) | 4n bytes | untouched |

† The same footnote as in [`max-min.md`](max-min.md): `Arrays.sort(int[])` allocates a full `int[n]`
merge buffer when it finds the input is several sorted runs, so "no copy of its own" is not the same
as "no allocation".

`purchasePlan` returns who buys what, in purchase order — `plan[f][j]` is bought at `j+1` times its
listed price. It is *an* optimal plan, not *the* one: equal prices and the boundary between
multiplier blocks both leave real ties.

### The counting variant never sorts

The argument needs the prices walked dearest-to-cheapest, which is weaker than needing them sorted.
A histogram gives exactly that: count how many flowers carry each price, then walk the counts
downwards handing out multipliers in blocks of `k`. No comparisons, no ordering — just a table.

## Measured

Ad hoc, JDK 25, `k = 1024`, one JVM per cell, 4 warm-up reps then the median of 9, input clone
outside the timer — a throwaway harness, not reproduced by the build.

| | sort a copy | sort in place | **counting** |
|---|---:|---:|---:|
| `n = 10⁷`, prices `0..10⁶` — **`n` ≫ range** | 102 ms | 99 ms | **32 ms** |
| `n = 10⁷`, prices `0..10⁷` — **range = `n`** | 97 ms | 94 ms | 97 ms |
| `n = 10⁴`, prices `0..10⁶` — **`n` ≪ range** | **0.5 ms** | **0.5 ms** | 0.9 ms |

Textbook counting-sort behaviour, and the crossover lands where the arithmetic says it should: at
`maxPrice ≈ n`. Above that the histogram is the cheaper way to order 10 million flowers by a factor
of **3.2×**; at parity it is a wash to within noise; below it you pay for a million buckets to sort
ten thousand flowers and lose.

That is why the method has a *checked* precondition rather than a documented one — the edge is hard,
not gentle. A price above 10⁷ is rejected instead of quietly allocating a 40 MB table for a handful
of flowers.

## The `int` return overflows inside the stated bounds

This is not a "beyond the constraints" caveat. Take the problem's own worst case — one friend,
a hundred flowers, a million each:

```
10⁶ × (1 + 2 + ... + 100)  =  5 050 000 000
Integer.MAX_VALUE          =  2 147 483 647
```

More than twice over, and an `int` would wrap it to a *negative* total. `getMinimumCost` keeps the
platform's signature and narrows with `Math.toIntExact`, so it throws rather than lies;
`getMinimumCostAsLong` is the one to actually call. The multiplication inside the scan is in `long`
too — at `n = 10⁷` the totals in the benchmark above reach 1.6 × 10¹⁶.

## Negative prices break the problem, not just the proof

Lemma 1 assumes a small multiplier is desirable. Below zero it is not — a negative flower gets
*cheaper* the later it is bought, so you would want a single friend to hoard flowers and run their
multiplier up:

```
c = {-10, 1},  k = 2

blocks of k (what the greedy rule says):   1×1 + (-10)×1  =  -9
one friend buys both:                      1×1 + (-10)×2  =  -19    <- better
```

So the failure is not marginal: the whole forced-multiplier argument collapses. Every method rejects a negative price rather than returning the wrong one. Zero is fine —
a free flower costs nothing at any multiplier, and lemma 1 only needs prices to be non-negative.

## Test oracles

| Oracle | Scale | Catches |
|---|---|---|
| **Brute force over every flower→friend assignment** | all `kⁿ`, `n ≤ 7`, `k ≤ 4` | both lemmas being false |
| Hand-written expected costs | 13 cases incl. all three samples | a wrong block size or multiplier |
| The block formula over an independently sorted copy | 400 random arrays, `n ≤ 400` | a wrong scan direction |
| `purchasePlan` contract | 400 random plans | a plan that is not a partition, or not priced as claimed |
| The `{-10, 1}` counterexample | one array | the negative-price rejection silently going away |
| 3 000 flowers at 10⁶ with `k = 1` | one array | a product that wraps before it is accumulated |
| Closed form at 10⁶, methods against each other at 5 × 10⁶ | | overflow and scale |

The brute force is the load-bearing one. It enumerates every way to hand flowers to friends and
assumes neither lemma — only that *within* one friend the multipliers are forced to `1..t`, which is
true by definition rather than by argument. Everything else in the file would agree with a
confidently wrong block size.

### Mutation check

Seventeen deliberate breaks. **Fifteen were caught on the first pass, and the two survivors were a
real gap rather than equivalent mutants** — worth recording, because that is the case mutation
testing exists for.

Both survivors dropped the `(long)` cast from the multiplication:

```java
total += (long) price * (bought / k + 1);     // as written
total +=        price * (bought / k + 1);     // the mutant - still compiles, total is a long
```

`total` being a `long` widens the *result*, so the product still wraps in `int` arithmetic first.
The existing overflow test did not notice, because it only overflowed the **total** (100 flowers ×
10⁶, one friend: 5.05 × 10⁹) while every individual product — at most 10⁶ × 100 — stayed comfortably
inside an `int`. Adding one array of 3 000 flowers at 10⁶ with `k = 1`, where the 2148th purchase
alone passes `Integer.MAX_VALUE`, kills both. The score is 17 of 17 with that test in place.

| Mutation | Caught by |
|---|---|
| the multiplier starts at 0 | 19 failures |
| the counting walk's multiplier off by one | 17 |
| row length of a plan floors instead of ceiling | 16 errors |
| blocks of `k + 1` instead of `k` | 13 |
| the plan buys cheapest flowers first | 12 |
| the scan reads cheapest-first | 11 |
| the counting walk goes upwards | 9 |
| plan's friend and purchase-slot indices swapped | 12 errors, 2 failures |
| the sort dropped, from `AsLong` and from `InPlace` | 5 each |
| `k < 1` accepted; `(int)` cast instead of `toIntExact`; `InPlace` validates after sorting | 2 each |
| negative prices accepted | 3 |
| the histogram's price limit removed | 1 |
| **the `(long)` cast dropped, in both scans** | **1 each** |

```
mvn test -Dtest=GreedyFloristTest
```
