# Obserwowanie testów i benchmarków przez `taskset`, `perf`, `jstack` i `jcmd`

Skrypty, które uruchamiają jedną klasę testów JUnit (albo jedną metodę) albo benchmark JMH z drzewa
testów w JVM przypiętej przez `taskset`, z `perf`, `jstack` albo `jcmd` dookoła. Nie dodają własnego
kodu Java.

Każdy skrypt najpierw buduje projekt, jeśli zmieniły się źródła (`mvn test-compile`), a potem uruchamia
bezpośrednio konsolowy launcher JUnit, więc JVM samego Mavena nie wlicza się do wyników.

| Skrypt | Co robi |
|---|---|
| `run-test.sh` | przypięty przebieg, tylko czas zegarowy; nie wymaga uprawnień do perf |
| `stat.sh` | `perf stat -r N`: liczniki sprzętowe plus przełączenia kontekstu i migracje |
| `record.sh` | `perf record -g` z czytelnymi ramkami JIT, potem `perf report` |
| `watch.sh` | na żywo: liczniki co sekundę (`-m stat`) albo `perf top` (`-m top`), uruchomione przez skrypt albo podpięte do działającej JVM |
| `compare-pinning.sh` | ten sam test na parze SMT, dwóch rdzeniach, wszystkich rdzeniach P, rdzeniach E… w jednej tabeli |
| `jmh.sh` | benchmark JMH z drzewa testów (`*Benchmark.java`), przypięty; własne flagi JMH przechodzą dalej |
| `snapshot.sh` | zrzuty `jstack` i `jcmd` w trakcie testu albo benchmarku: wątki i blokady, sterta, JIT, histogram |

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

Każdy skrypt przyjmuje `-h`. Wspólne argumenty:

- `TestClass` albo `TestClass#method`; jeśli pominiesz pakiet, zostanie dodany `jdk.`.
- `-c CPUS`: lista dla `taskset` (`0-3`, `0,2`) albo `p` (wszystkie rdzenie P, domyślnie), `e`
  (wszystkie rdzenie E), `all`.
- `-- JVM_OPTS...`: przekazywane do JVM testu, np. `-XX:-UseSuperWord`, `-XX:ActiveProcessorCount=8`.
  Dla testów zawsze ustawiane jest `-Xmx512m`, zgodnie z `argLine` surefire; benchmark ustawia własną
  stertę.
- `PERF_JAVA_HOME`: JDK, na którym uruchamiać. Domyślnie `25.0.1-tem` z sdkman, bo `JAVA_HOME`
  eksportowane przez sdkman to 17.

Wyniki takie jak `perf.data` trafiają do `target/perf/`, tak samo jak classpath zależności z pom,
wyznaczany raz przez `mvn dependency:build-classpath` i ponownie przy każdej zmianie `pom.xml`.

## JMH

`jmh.sh` przyjmuje wyrażenie regularne benchmarku zamiast klasy testów, a wszystko inne przekazuje do
JMH: `-p size=1000`, `-f 2`, `-prof gc`, `-rf json -rff out.json` (względem `target/perf/`). Opcje JVM po
`--` trafiają do forkowanych JVM, czyli tych, które są mierzone. Przypinanie działa jak dla testów:
forki dziedziczą maskę powinowactwa. Zostaw domyślne `-c p` zamiast jednego rdzenia, żeby wątki JIT i
GC nie walczyły o niego z wątkiem benchmarku.

Opcja JVM podana w wierszu poleceń **zastępuje** pasujący atrybut `@Fork`, zamiast do niego dopisywać.
`jmh.sh -- ...` wysyła swoje opcje jako `-jvmArgsAppend`, więc benchmark, który umieścił stertę w
`@Fork(jvmArgsAppend = ...)`, po cichu działał na domyślnej stercie, gdy tylko podano jakieś opcje.
Złapał to `snapshot.sh` (niżej), czytając `VM.command_line` forka. Benchmarki tutaj umieszczają własne
flagi w `jvmArgsPrepend`, którego skrypt nigdy nie ustawia.

## `jstack` i `jcmd`: co robi JVM, a nie jak szybko

`perf` liczy, co zrobił procesor. `jstack` i `jcmd` pytają samą JVM: jakie są wątki, na czym każdy jest
zablokowany i jaką blokadę trzyma, jak pełna jest sterta, co JIT jeszcze kompiluje, które obiekty
zajmują pamięć, z jakimi flagami JVM naprawdę wystartowała. Żadne z nich nie wymaga uprawnień do perf.
Oba podłączają się przez mechanizm attach JVM, który działa dla każdej JVM uruchomionej przez twojego
użytkownika, z `bin/` tego samego JDK.

`snapshot.sh` uruchamia test albo benchmark, albo podłącza się do działającej JVM, i robi zrzuty, aż się
zakończy. Każdy zrzut to zestaw plików w `target/perf/snapshots/<target>-<time>/` plus jedna linia w
terminalu:

```
$ scripts/perf/snapshot.sh ReentrantLockTest
>>    0.5s  pid 3268487  ReentrantLockTest.awaitUninterruptiblyKeepsWaitingAndHandsBackTheFlag:369  threads 23 (RUNNABLE 11, TIMED_WAITING 3, WAITING 2)  heap 50M  jit queue 0
>>    1.3s  pid 3268487  ReentrantLockTest.handoff:651  threads 41 (RUNNABLE 11, TIMED_WAITING 3, WAITING 9)  heap 19M  jit queue 0
```

Druga kolumna to to, co JVM w tej chwili wykonywała: metoda testowa na `main`, z numerem linii, albo
pod JMH metoda benchmarku. `--take` wybiera, co zbiera każdy zrzut:

| `--take` | Narzędzie | Co pokazuje | Do czego |
|---|---|---|---|
| `threads` | `jstack -l` | stos i stan każdego wątku platformowego; blokadę albo warunek, na którym jest zaparkowany; w „Locked ownable synchronizers” trzymane `ReentrantLock`; zakleszczenia | testy blokad i semaforów: kto na kogo czeka |
| `vthreads` | `jcmd Thread.dump_to_file -format=json` | to samo, łącznie z **wątkami wirtualnymi**, które `jstack` pomija | `MapConcurrentTest`: `mapConcurrent` uruchamia każdy mapper na wątku wirtualnym |
| `heap` | `jcmd GC.heap_info` | zarezerwowana i użyta sterta, według rodzaju regionu | ile żywych danych trzyma test albo benchmark |
| `jit` | `jcmd Compiler.queue`, `Compiler.codecache` | metody czekające na kompilację, użycie pamięci kodu | czy benchmark wciąż się rozgrzewa? Kolejka, która nie spadła do 0, mówi, że tak |
| `histo` | `jcmd GC.class_histogram` | instancje i bajty na klasę, 20 największych; najpierw pełne GC | gdzie idzie pamięć, np. opakowania wobec typów prostych |
| `nmt` | `jcmd VM.native_memory summary` | pamięć poza stertą: pamięć kodu, metaspace, stosy wątków, struktury GC | uruchamia cel z `-XX:NativeMemoryTracking=summary`, którego wymaga |

`VM.command_line` i `VM.flags` są zapisywane raz na JVM niezależnie od wyboru, jako `<pid>-vm.txt`:
flagi, z którymi JVM naprawdę skończyła, po tym, jak swoje powiedziały JMH, skrypty i ergonomia.

Co skrypt robi wokół tych narzędzi:

- **Testy są krótkie.** Każda klasa testów tutaj kończy się w około 4 s, więc dla testu zrzuty idą jeden
  po drugim (`--every 0`), co około 0.3 s: każde wywołanie `jstack` albo `jcmd` trwa około 90 ms. Cel
  startuje z `-XX:+StartAttachListener`, bo inaczej pierwsze podłączenie musi uruchomić nasłuch i trwa
  około 190 ms.
- **Pod JMH zrzuca fork.** JVM uruchomiona przez `jmh.sh` tylko koordynuje. Każdy benchmark działa w
  świeżej JVM potomnej (`org.openjdk.jmh.runner.ForkedMain`), a skrypt śledzi najnowszą, więc długi
  przebieg jest próbkowany benchmark po benchmarku.
- **Podłączanie.** Dla pid albo wzorca z `jps -lm` podłącza się, zamiast cokolwiek uruchamiać. Tak
  wchodzi się do JVM uruchomionej samodzielnie albo przez runner testów IntelliJ.

```sh
scripts/perf/snapshot.sh --take threads,heap StampedLockTest
scripts/perf/snapshot.sh --take vthreads MapConcurrentTest
scripts/perf/snapshot.sh --take jit --every 500 'PrimitiveIntListsBenchmark.sum_array$' -p size=1000
scripts/perf/snapshot.sh --take nmt --count 3 --every 2000 'PrimitiveIntListsBenchmark.append_boxed$'
scripts/perf/snapshot.sh --every 5000 12345
```

Te same narzędzia ręcznie, dla JVM, która już działa (`jcmd` bez argumentów wypisuje JVM-y, a
`jcmd <pid> help` wypisuje wszystko, co dana JVM przyjmuje):

```sh
JDK=~/.sdkman/candidates/java/25.0.1-tem/bin
$JDK/jcmd                                                   # pid i główna klasa każdej twojej JVM
$JDK/jstack -l <pid>                                        # to samo co: jcmd <pid> Thread.print -l
$JDK/jcmd <pid> Thread.dump_to_file -format=json /tmp/t.json  # z wątkami wirtualnymi
$JDK/jcmd <pid> GC.heap_info
$JDK/jcmd <pid> GC.class_histogram | head -25
$JDK/jcmd <pid> VM.flags                                    # na czym stanęły ergonomia i skrypty
$JDK/jcmd <pid> Compiler.queue
$JDK/jcmd <pid> JFR.start duration=30s filename=/tmp/run.jfr settings=profile   # potem otwórz w JMC
```

Dla benchmarku JMH może też sam nagrać flight recording, tylko wokół mierzonych iteracji:
`jmh.sh <regex> -prof jfr`.

## Jednorazowa konfiguracja: pozwól perf czytać liczniki

Debian ustawia `kernel.perf_event_paranoid=3`, co blokuje każde nieuprzywilejowane wywołanie `perf`.
Skrypty perf potrzebują `1` (liczenie w przestrzeni użytkownika i jądra dla własnych procesów; po
stronie jądra widać czas futexa zaparkowanego czekającego na blokadę):

```sh
sudo sysctl -w kernel.perf_event_paranoid=1                                  # do restartu
echo 'kernel.perf_event_paranoid = 1' | sudo tee /etc/sysctl.d/99-perf.conf  # na stałe
```

`run-test.sh` działa i bez tego.

## Ten procesor

i7-14650HX jest hybrydowy, a skrypty czytają listy rdzeni z sysfs, zamiast mieć je na sztywno:

- **Rdzenie P**: procesory 0–15, osiem fizycznych rdzeni po dwa wątki SMT (`0,1` dzielą rdzeń).
  Procesory 8–11 przyspieszają do 5.2 GHz, pozostałe do 5.0.
- **Rdzenie E**: procesory 16–23, po jednym wątku, 3.7 GHz.

Na procesorze hybrydowym `perf` zgłasza każde zdarzenie sprzętowe dwa razy, jako `cpu_core/cycles/` i
`cpu_atom/cycles/`. Gdy przebieg jest przypięty do jednego typu rdzeni, wiersz drugiego typu pokazuje
`<not counted>`; tak ma być. `compare-pinning.sh` dodaje oba wiersze.

## Co zniekształca to, co widzisz

- **Przypinanie zmienia liczbę wątków.** JVM ustawia `Runtime.availableProcessors()` na podstawie maski
  powinowactwa, więc przy `-c 0,1` test, który dobiera rozmiar puli według niej, działa na dwóch
  wątkach. Żeby zmieniać tylko procesory, przekaż `-- -XX:ActiveProcessorCount=N`.
- **Rozgrzewanie JIT.** Większość tych testów kończy się dużo poniżej sekundy, więc znaczna część czasu
  idzie na start JVM i kompilację C1/C2. Powtórzenia `stat.sh -r` wygładzają szum, ale go nie usuwają.
  Wybierz jedną długą metodę (`Class#method`), jeśli interesuje cię stan ustalony.
- **`watch.sh` potrzebuje długiego przebiegu.** Test trwający sekundę kończy się przed drugą próbką.
  Używaj go na cięższych testach rywalizacji albo na JVM uruchomionej samodzielnie (podaj jej pid).
- **Zrzut zatrzymuje JVM.** Zrzut wątków, `GC.heap_info` i `GC.class_histogram` działają w safepoincie,
  który wstrzymuje każdy wątek Javy na czas ich trwania, a `histo` dokłada jeszcze pełne GC. Benchmark,
  z którego robi się zrzuty, nie jest mierzony: nie zachowuj jego wyników. W teście przerwa może
  poszerzyć albo zamknąć okno czasowe, więc test wrażliwy na czas może zachowywać się inaczej, gdy jest
  obserwowany.
- **Częstotliwość.** Rdzenie P przyspieszają do różnych zegarów, a laptop zwalnia pod długotrwałym
  obciążeniem. Porównuj IPC (instrukcje ÷ cykle), a nie tylko czas zegarowy.
