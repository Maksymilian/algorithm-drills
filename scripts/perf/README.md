# Watching the tests and benchmarks with `taskset`, `perf`, `jstack` and `jcmd`

Wrappers that run one of the JUnit test classes (or one method), or a JMH benchmark from the test
tree, in a JVM pinned with `taskset`, with `perf`, `jstack` or `jcmd` around it. They add no Java
code of their own.

Each script builds the tree first if the sources changed (`mvn test-compile`), then launches the
JUnit console launcher directly, so Maven's own JVM is not counted in the numbers.

| Script | What it does |
|---|---|
| `run-test.sh` | pinned run, wall clock only; needs no perf permissions |
| `stat.sh` | `perf stat -r N`: hardware counters plus context switches and migrations |
| `record.sh` | `perf record -g` with readable JIT frames, then `perf report` |
| `watch.sh` | live: counters every second (`-m stat`) or `perf top` (`-m top`), started by the script or attached to a running JVM |
| `compare-pinning.sh` | the same test on an SMT pair, two cores, all P-cores, E-cores… as one table |
| `jmh.sh` | a JMH benchmark from the test tree (`*Benchmark.java`), pinned; JMH's own flags pass through |
| `snapshot.sh` | `jstack` and `jcmd` snapshots while a test or benchmark runs: threads and locks, heap, JIT, histogram |

```sh
scripts/perf/run-test.sh -c 0,1 StampedLockTest
scripts/perf/stat.sh -r 5 VarHandleLockTest
scripts/perf/stat.sh -c e VarHandleVectorTest -- -XX:-UseSuperWord
scripts/perf/record.sh 'VarHandleVectorTest#countMatchesTheScalarVersionAtEveryLength'
scripts/perf/watch.sh -m top -c 0-3 ReentrantLockTest
scripts/perf/compare-pinning.sh VarHandleLockTest
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sum_' -p size=1000 -prof gc
scripts/perf/snapshot.sh ReentrantLockTest
scripts/perf/snapshot.sh --take histo --count 1 'PrimitiveIntListsBenchmark.sum_boxed$' -p size=1000000
```

Every script takes `-h`. Common arguments:

- `TestClass` or `TestClass#method`; the `jdk.` package is added if you leave it out.
- `-c CPUS`: a `taskset` list (`0-3`, `0,2`) or `p` (all P-cores, the default), `e` (all
  E-cores), `all`.
- `-- JVM_OPTS...`: passed to the test JVM, e.g. `-XX:-UseSuperWord`, `-XX:ActiveProcessorCount=8`.
  `-Xmx512m` is always set for tests, matching surefire's `argLine`; a benchmark sets its own heap.
- `PERF_JAVA_HOME`: the JDK to run on. Defaults to sdkman's `25.0.1-tem`, because the `JAVA_HOME`
  sdkman exports is 17.

Output such as `perf.data` goes to `target/perf/`, and so does the pom's dependency classpath,
resolved once with `mvn dependency:build-classpath` and again whenever `pom.xml` changes.

## JMH

`jmh.sh` takes a benchmark regex instead of a test class, and passes everything else to JMH:
`-p size=1000`, `-f 2`, `-prof gc`, `-rf json -rff out.json` (relative to `target/perf/`). JVM
options after `--` go to the forked JVMs, which are the ones measured. Pinning works as for the
tests: the forks inherit the affinity mask. Keep the default `-c p` rather than a single core, so
the JIT and GC threads are not competing with the benchmark thread for it.

A JVM option given on the command line **replaces** the matching `@Fork` attribute rather than
adding to it. `jmh.sh -- ...` sends its options as `-jvmArgsAppend`, so a benchmark that put its
heap in `@Fork(jvmArgsAppend = ...)` silently ran on the default heap whenever options were given.
That was caught by `snapshot.sh`, below, reading the fork's `VM.command_line`. Benchmarks here put
their own flags in `jvmArgsPrepend`, which the script never sets.

## `jstack` and `jcmd`: what the JVM is doing, rather than how fast

`perf` counts what the CPU did. `jstack` and `jcmd` ask the JVM itself: which threads exist, what
each is blocked on and which lock it holds, how full the heap is, what the JIT is still compiling,
which objects take the memory, which flags it actually started with. Neither needs perf
permissions. Both attach through the JVM's attach mechanism, which works for any JVM run by your
user, from the same JDK's `bin/`.

`snapshot.sh` starts a test or benchmark, or attaches to a running JVM, and takes snapshots until it
exits. Each snapshot is a set of files under `target/perf/snapshots/<target>-<time>/`, plus one line
on the terminal:

```
$ scripts/perf/snapshot.sh ReentrantLockTest
>>    0.5s  pid 3268487  ReentrantLockTest.awaitUninterruptiblyKeepsWaitingAndHandsBackTheFlag:369  threads 23 (RUNNABLE 11, TIMED_WAITING 3, WAITING 2)  heap 50M  jit queue 0
>>    1.3s  pid 3268487  ReentrantLockTest.handoff:651  threads 41 (RUNNABLE 11, TIMED_WAITING 3, WAITING 9)  heap 19M  jit queue 0
```

The second column is what the JVM was running at that moment: the test method on `main`, with its
line, or under JMH the benchmark method. `--take` picks what each snapshot collects:

| `--take` | Tool | What it shows | Use it for |
|---|---|---|---|
| `threads` | `jstack -l` | every platform thread's stack and state; the lock or condition it is parked on; under "Locked ownable synchronizers", the `ReentrantLock`s it holds; deadlocks | the lock and semaphore tests: who waits for whom |
| `vthreads` | `jcmd Thread.dump_to_file -format=json` | the same, including **virtual threads**, which `jstack` leaves out | `MapConcurrentTest`: `mapConcurrent` runs each mapper on a virtual thread |
| `heap` | `jcmd GC.heap_info` | heap committed and used, per region type | how much a test or benchmark keeps live |
| `jit` | `jcmd Compiler.queue`, `Compiler.codecache` | methods waiting to be compiled, code cache use | is a benchmark still warming up? A queue that has not drained to 0 means it is |
| `histo` | `jcmd GC.class_histogram` | instances and bytes per class, top 20; runs a full GC first | where the memory goes, e.g. boxed against primitive |
| `nmt` | `jcmd VM.native_memory summary` | memory outside the heap: code cache, metaspace, thread stacks, GC structures | starts the target with `-XX:NativeMemoryTracking=summary`, which it requires |

`VM.command_line` and `VM.flags` are saved once per JVM whatever you pick, as `<pid>-vm.txt`: the
flags the JVM really ended up with, after JMH, the scripts and ergonomics all had their say.

What it does around those tools:

- **Tests are short.** Every test class here finishes within about 4 s, so for a test the snapshots
  run back to back (`--every 0`), about 0.3 s apart: each `jstack` or `jcmd` call takes about 90 ms.
  The target starts with `-XX:+StartAttachListener`, because otherwise the first attach has to start
  the listener and takes about 190 ms.
- **Under JMH it snapshots the fork.** The JVM `jmh.sh` starts only coordinates. Each benchmark runs
  in a fresh child JVM (`org.openjdk.jmh.runner.ForkedMain`), and the script follows the newest one,
  so a long run is sampled benchmark by benchmark.
- **Attaching.** Given a pid or a `jps -lm` pattern, it attaches instead of starting anything.
  That is the way in for a JVM you started yourself, or one IntelliJ's test runner started.

```sh
scripts/perf/snapshot.sh --take threads,heap StampedLockTest
scripts/perf/snapshot.sh --take vthreads MapConcurrentTest
scripts/perf/snapshot.sh --take jit --every 500 'PrimitiveIntListsBenchmark.sum_array$' -p size=1000
scripts/perf/snapshot.sh --take nmt --count 3 --every 2000 'PrimitiveIntListsBenchmark.append_boxed$'
scripts/perf/snapshot.sh --every 5000 12345
```

The same tools by hand, for a JVM that is already running (`jcmd` with no arguments lists the
JVMs, and `jcmd <pid> help` lists everything that JVM accepts):

```sh
JDK=~/.sdkman/candidates/java/25.0.1-tem/bin
$JDK/jcmd                                                   # pid and main class of every JVM you own
$JDK/jstack -l <pid>                                        # same as: jcmd <pid> Thread.print -l
$JDK/jcmd <pid> Thread.dump_to_file -format=json /tmp/t.json  # with virtual threads
$JDK/jcmd <pid> GC.heap_info
$JDK/jcmd <pid> GC.class_histogram | head -25
$JDK/jcmd <pid> VM.flags                                    # what ergonomics and the scripts settled on
$JDK/jcmd <pid> Compiler.queue
$JDK/jcmd <pid> JFR.start duration=30s filename=/tmp/run.jfr settings=profile   # then open in JMC
```

For a benchmark, JMH can also record a flight recording itself, around the measured iterations
only: `jmh.sh <regex> -prof jfr`.

## One-time setup: let perf read counters

Debian ships `kernel.perf_event_paranoid=3`, which blocks every unprivileged `perf` call. The perf
scripts need `1` (user and kernel counting of your own processes; the kernel side is where a parked
lock waiter's futex time shows up):

```sh
sudo sysctl -w kernel.perf_event_paranoid=1                                  # until reboot
echo 'kernel.perf_event_paranoid = 1' | sudo tee /etc/sysctl.d/99-perf.conf  # permanent
```

`run-test.sh` works without this.

## This CPU

The i7-14650HX is hybrid, and the scripts read the core lists from sysfs rather than hardcoding them:

- **P-cores**: CPUs 0–15, eight physical cores with two SMT threads each (`0,1` share a core).
  CPUs 8–11 boost to 5.2 GHz, the rest to 5.0.
- **E-cores**: CPUs 16–23, one thread each, 3.7 GHz.

On a hybrid CPU, `perf` reports each hardware event twice, as `cpu_core/cycles/` and
`cpu_atom/cycles/`. When the run is pinned to one core type, the other type's row reads
`<not counted>`; that is expected. `compare-pinning.sh` adds the two rows together.

## Things that skew what you see

- **Pinning changes the thread count.** The JVM sets `Runtime.availableProcessors()` from the
  affinity mask, so under `-c 0,1` a test that sizes its pool from it runs with two threads. To vary
  only the CPUs, pass `-- -XX:ActiveProcessorCount=N`.
- **JIT warm-up.** Most of these tests finish in well under a second, so a large share of the time
  goes to JVM startup and C1/C2 compilation. `stat.sh -r` repeats smooth the noise but do not remove
  it. Pick a single long method (`Class#method`) if you want steady-state behaviour.
- **`watch.sh` needs a long run.** A one-second test is over before the second sample. Use it on the
  heavier contention tests, or on a JVM you started yourself (pass its pid).
- **A snapshot stops the JVM.** A thread dump, `GC.heap_info` and `GC.class_histogram` run at a
  safepoint, which pauses every Java thread for as long as they take, and `histo` adds a full GC on
  top. A benchmark that is being snapshotted is not being measured: do not keep its scores. In a
  test, a pause can widen a timing window or close one, so a timing-sensitive test may behave
  differently while it is observed.
- **Frequency.** The P-cores boost to different clocks and the laptop throttles under sustained
  load. Compare IPC (instructions ÷ cycles), not only wall clock.
