# Fibonacci — the recurrence, and the price of writing it down literally

Notes for [`src/java/recursion/Fibonacci.java`](../../src/java/recursion/Fibonacci.java),
tested by [`test/java/recursion/FibonacciTest.java`](../../test/java/recursion/FibonacciTest.java).

**Problem** `F(0) = 0`, `F(1) = 1`, `F(n) = F(n-1) + F(n-2)`. Write it as a recursion; write it
again with memoization; then account for what each one costs in processor and in memory.

## The recursion is the definition, and that is precisely what is wrong with it

```java
return n <= 1 ? n : naive(n - 1) + naive(n - 2);
```

Nothing about that line is wrong except that it has no memory. Every call explores its whole subtree
afresh, and the two subtrees overlap almost entirely:

```
                        F(6)
              F(5)                    F(4)
        F(4)        F(3)        F(3)        F(2)
     F(3)  F(2)   F(2) F(1)   F(2) F(1)   F(1) F(0)
   F(2) F(1) ...
```

`F(4)` is computed twice, `F(3)` three times, `F(2)` five times — and those are Fibonacci numbers
themselves: **`F(k)` is recomputed `F(n-k+1)` times**. Counting every call,

```
C(0) = C(1) = 1,    C(n) = C(n-1) + C(n-2) + 1        =>    C(n) = 2F(n+1) - 1
```

which is the same recurrence with an extra 1, and the closed form is exact — not asymptotic. The
reason is worth saying plainly: the only arithmetic this algorithm has is adding 1s at the leaves,
so reaching `F(n)` takes `F(n)` of them. **The running time is the answer's own magnitude**, and
`F(n)` grows by a factor of `φ = 1.618...` per step.

Measured, JDK 25.0.1 (Temurin), Intel i7-14650HX — ad hoc, not reproduced by the build:

| n | calls | time | vs `n-5` |
|---:|---:|---:|---:|
| 25 | 242 785 | 1.0 ms | — |
| 30 | 2 692 537 | 2.8 ms | 2.7× |
| 35 | 29 860 703 | 32.9 ms | 11.7× |
| 40 | 331 160 281 | 360.1 ms | 11.0× |
| 45 | 3 672 623 805 | 4 000.6 ms | 11.1× |

That last column is the whole story, and it is not a coincidence: **φ⁵ = 11.09**. Five more, eleven
times the work, for ever. The machine ran 912 million calls a second and still needed four seconds
for `F(45)`.

**And its memory cost is nothing at all.** No allocation, and the stack never goes deeper than `n`
frames — 45 of them here, a couple of kilobytes. This is the part that surprises people: the naive
Fibonacci is not a memory problem in any way. It is purely a processor catastrophe.

## Memoization: the same tree, with every repeat struck out

```java
if (n <= 1) return n;
if (known[n] != 0) return known[n];
return known[n] = memoized(n - 1, known) + memoized(n - 2, known);
```

One lookup and one store, and the shape of the recursion is otherwise untouched. What changes is
that a subtree already computed is never entered a second time, so the tree — two branches wide at
every node — collapses into a path down and a path back: **`2n - 1` calls and `n - 1` additions**,
where there were `2F(n+1) - 1` and `F(n+1) - 1`.

`0` means "not computed yet", which saves a filling pass and is safe for one specific reason:
`F(k)` is zero only at `k = 0`, and that is a base case that is never stored. (The same trick as the
step stamps in [`array.NextIndexCycle`](../array/next-index-cycle.md), for the same reason —
an unwritten `int[]`/`long[]` is already all zeroes, so a value that cannot legitimately be zero can
mean "empty" for free.)

The table is allocated per call, so nothing is shared between calls or between threads. Hoisting it
to a `static` field to keep it warm is the obvious next idea, and it is how this class would get a
concurrency bug: `long` writes are not guaranteed atomic by the memory model, so two threads filling
one table can, in principle, read a half-written value.

## The iterative version, and what memoization still costs

```java
for (int i = 0; i < n; i++) { long next = previous + current; previous = current; current = next; }
```

The recurrence looks back exactly two places, so two variables suffice — **no table, no recursion,
no call**. Computing in increasing order means a value is needed only while it is still one of the
last two, which is the whole reason the memo can be thrown away.

| at `n = 92` | per call |
|---|---:|
| memoized recursion | 207.3 ns |
| iterative | **9.7 ns** |

**21× apart**, for the same 92 additions. The difference is everything memoization did not remove:
a 744-byte array to allocate and zero, 183 calls to make and return from, and a table to index
instead of two values in registers. Memoization takes the exponent away; iteration takes away what
is left.

## Processor and memory, side by side

At `n = 92`, the largest a `long` allows:

| | Additions | Calls | Time | Heap | Stack |
|---|---:|---:|---:|---:|---:|
| naive recursion | `F(n+1) - 1` = 1.2·10¹⁹ | 2.44·10¹⁹ | **~848 years** | none | 92 frames |
| memoized recursion | 91 | 183 | 207 ns | 744 bytes | 92 frames |
| iterative | 92 | 1 | **9.7 ns** | **none** | **1 frame** |

The 848 years is the measured call rate — 912 million a second — divided into the exact call count.
It is not a bound or an estimate of an asymptote; it is what the machine that took four seconds over
`F(45)` would take over `F(92)`.

Read the table the other way and the lesson is sharper than "memoization is faster". **Memory is not
the axis anything here varies on** — the difference between the best and worst is 744 bytes. The
gap in time is seventeen orders of magnitude (848 years against 207 nanoseconds), and every one of
them was bought with a table small enough to lose in a cache line.

## Why the table stops at 92

`F(92) = 7540113804746346429` fits a `long` with room to spare. `F(93) = 12200160415121876738` does
not fit at all — it is larger than `Long.MAX_VALUE = 9223372036854775807`. Every method here rejects
anything past 92 rather than returning a silently wrapped answer, and the test asserts the boundary
against `BigInteger` so the constant cannot drift from the arithmetic it claims to describe.

This also caps the memory question before it starts: the entire memo table, `F(0)` to `F(92)`, is
93 longs — **744 bytes**. For Fibonacci in `long`s there is no memory problem to discuss.

## Past 92: `BigInteger`, where the memory becomes the story

Change the type and both costs change character. `F(n)` has about `0.694n` bits (`n·log₂φ`), so
addition is no longer constant time and a stored table is no longer a row of registers:

| n | bits in `F(n)` | whole table | two variables | time |
|---:|---:|---:|---:|---:|
| 1 000 | 694 | 59 807 B | 206 B | 0.7 ms |
| 10 000 | 6 942 | 4.5 MB | 1 768 B | 2.8 ms |
| 100 000 | 69 424 | **415 MB** | **17 KB** | 117.3 ms |
| 1 000 000 | 694 242 | *~43 GB* | ~174 KB | — |

The last row was not run, for the reason its middle column gives. Keeping every value costs
`0.347n²` bits — **quadratic memory for a linear answer** — while the loop keeps two numbers and
pays `O(n)`. At `n = 100 000` that is a factor of 25 000, and it widens with every step.

And the recursion acquires a second limit, one the loop does not have at all:

| recursion carrying | frames before `StackOverflowError` |
|---|---:|
| a `long` | 21 571 |
| a `BigInteger` | 12 268 |

So a memoized `BigInteger` Fibonacci — the natural extension of the code above — stops working
somewhere around `n = 12 000`, on default stack settings, no matter how much heap is available. The
iterative version has no such number in it. **This is the real argument against top-down memoization
at scale**, and it has nothing to do with the exponential the memo was introduced to fix.

## Memoization and dynamic programming are the same graph

The memoized recursion and the loop visit exactly the same `n+1` subproblems and perform exactly the
same `n` additions. They differ only in direction: top-down on demand, versus bottom-up in an order
worked out in advance. That is the entire distinction between memoization and dynamic programming,
and Fibonacci is the smallest problem that shows it — see
[Max Subset Sum](../dynamic/max-subset-sum.md) for the same argument where the order is not
obvious and the choice actually costs something to make.

## Faster than linear

`n` additions is not the floor. The identities

```
F(2k)   = F(k) · (2F(k+1) - F(k))
F(2k+1) = F(k)² + F(k+1)²
```

reach `F(n)` in `O(log n)` steps — fast doubling, which is matrix exponentiation of
`[[1,1],[1,0]]` with the redundancy removed. For `long`s it is pointless: 92 additions are already
free, and the seven doublings that replace them are multiplications. For `BigInteger` at
`n = 1 000 000` it is the right answer and the loop is not, because it turns a million big additions
into twenty big multiplications.

Neither is in this class. The drill asked for the recursion and its memoized form; this is where
someone who needs `F(10⁶)` should look next.

## Test

Eight tests, a quarter of a second, and a `BigInteger` oracle that builds the sequence independently
rather than checking one implementation against another: every `n` from 0 to 92 for the memoized and
iterative versions, and up to 35 for the naive one — 35 being where its own table above says to
stop.

The one that carries the argument is **the memoization has to be real**: a memo that never hits is
still *correct*, and returns exactly the same numbers. What it cannot do is return them at all, so
asking for `F(92)` inside the class's 30-second box is what separates the two — microseconds
against centuries. The rest: the recurrence checked against itself, the 92 boundary checked against
`BigInteger` in both directions, every method rejecting negatives and 93, and 64 concurrent calls
agreeing with the iterative answer, because the obvious "improvement" of hoisting the table to a
static field would break that intermittently.

### Mutation check

Ten deliberate breaks, ten caught:

| Mutation | Caught by |
|---|---|
| the memoized base case returns 1 for `F(0)` | 4 failures |
| the loop runs one step too many | 4 failures |
| the loop returns the value one past the one asked for | 4 failures |
| the naive base case returns 1 for `F(0)` | 3 failures |
| the naive recursion looks back one place, twice | 3 failures |
| the claimed limit becomes 93 | 3 failures, 2 timeouts |
| nothing stops a negative `n` | 1 failure |
| **nothing stops `n` past 92** | **1 timeout** — the naive method, asked for `F(1 000)`, never comes back to fail the assertion |
| **the memo is read but never written** | **5 timeouts**, 2½ minutes |
| **the memo is written but never read** | **5 timeouts**, 2½ minutes |

The last two rows are the drill in one line. Neither mutation changes a single returned value —
both are *correct* implementations of Fibonacci — and neither is detectable by any assertion in the
file. What they change is whether the answers arrive, and the only instrument that reports that is
the clock.

```
mvn test -Dtest=FibonacciTest
```
