# Ten procesor i SIMD: AVX2, `IntVector` i co z tego ma JVM

Notatka do [`src/java/jdk/IntVectors.java`](../../src/java/jdk/IntVectors.java), testy:
[`test/java/jdk/IntVectorsTest.java`](../../test/java/jdk/IntVectorsTest.java).

SIMD (Single Instruction, Multiple Data) to jedna instrukcja wykonywana naraz na kilku wartościach
upakowanych w szerokim rejestrze. Ile wartości, zależy od procesora, dlatego ta notatka zaczyna się od
niego.

## Procesor

| | |
|---|---|
| Model | Intel Core i7-14650HX (Raptor Lake Refresh, laptopowa seria HX) |
| Rdzenie | hybrydowe: 8 rdzeni P z Hyper-Threading (procesory 0–15) i 8 rdzeni E bez HT (procesory 16–23), razem 24 wątki |
| Zegar | rdzenie P do 5.2 GHz (procesory 8–11, pozostałe do 5.0), rdzenie E do 3.7 GHz, minimum 800 MHz |
| L1 danych | 640 KiB: 48 KiB na rdzeń P, 32 KiB na rdzeń E |
| L1 instrukcji | 768 KiB: 32 KiB na rdzeń P, 64 KiB na rdzeń E |
| L2 | 24 MiB: 2 MiB na rdzeń P i 4 MiB na każdą z dwóch czwórek rdzeni E |
| L3 | 30 MiB, wspólne |

Instrukcje SIMD (z `/proc/cpuinfo`):

| Zestaw | Szerokość rejestru | Jest? |
|---|---|---|
| SSE, SSE2, SSSE3, SSE4.1, SSE4.2 | 128 bitów | tak |
| AVX, AVX2, FMA | 256 bitów | tak |
| AVX-VNNI | 256 bitów; mnożenie liczb całkowitych z akumulacją, pod sieci neuronowe | tak |
| AVX-512 | 512 bitów | **nie** |

Rejestr 256-bitowy mieści 8 wartości `int`, 4 `long`, 8 `float`, 4 `double` albo 32 bajty.

**Dlaczego bez AVX-512.** Rdzenie E nie mają AVX-512, a w procesorze hybrydowym oba typy rdzeni muszą
mieć ten sam zestaw instrukcji, bo system przenosi wątki między nimi w dowolnej chwili. Intel wyłączył
więc AVX-512 także na rdzeniach P, od generacji Alder Lake.

## Co z tego bierze JVM

```sh
grep -o -wE "sse4_2|avx|avx2|fma|avx_vnni|avx512f" /proc/cpuinfo | sort -u
java -XX:+PrintFlagsFinal -version | grep -E " UseAVX | UseSSE | MaxVectorSize | UseSuperWord "
```

| Flaga | Wartość | Znaczenie |
|---|---|---|
| `UseAVX` | 2 | JIT generuje instrukcje AVX2 |
| `UseSSE` | 4 | i SSE do 4.2 |
| `MaxVectorSize` | 32 | wektory najwyżej 32 bajty, czyli 256 bitów |
| `UseSuperWord` | true | C2 sam wektoryzuje proste pętle |

`IntVector.SPECIES_PREFERRED` to tutaj 256 bitów, czyli **8 pasów `int`** (`IntVectors.main` to wypisuje).

## Trzy drogi do SIMD w Javie

1. **Autowektoryzacja C2 (SuperWord).** Prosta pętla bez rozgałęzień, np. `out[i] = a[i] + b[i]`, sama
   staje się kodem AVX2. Nic nie trzeba pisać, ale nie wiadomo z góry, która pętla się załapie.
2. **Intrinsics JVM.** Wybrane metody JDK mają ręcznie napisane wersje wektorowe, np. `Arrays.sort(int[])`,
   które na tym procesorze jest 7.5 razy szybsze niż bez intrinsica
   ([pomiar](primitive-int-lists.md)).
3. **Vector API, jawnie.** Kod mówi wprost, że chce wektorów; to jest `IntVectors`.

SWAR (osiem bajtów w zwykłym 64-bitowym rejestrze, `VarHandleVector`) to nie SIMD, tylko jego imitacja na
zwykłych instrukcjach ([notatka](locks-reentrant-stamped-and-varhandle.md)).

## `IntVectors`: cztery operacje, cztery wnioski

Każda metoda ma ten sam kształt: pętla po pełnych wektorach do `SPECIES.loopBound(n)`, potem zwykła pętla po
końcówce, której nie da się wypełnić całym wektorem:

```java
int count = 0, i = 0;
for (; i < SPECIES.loopBound(data.length); i += SPECIES.length()) {
    count += IntVector.fromArray(SPECIES, data, i).compare(VectorOperators.EQ, value).trueCount();
}
for (; i < data.length; i++) {
    count += data[i] == value ? 1 : 0;
}
```

`compare` porównuje 8 pasów naraz i daje maskę, `trueCount()` liczy trafienia. `indexOf` używa tej samej
maski z `anyTrue()` i `firstTrue()`, `sum` trzyma akumulator wektorowy i robi jedną redukcję
(`reduceLanes(ADD)`) na końcu.

## Pomiary

Doraźnie, jednorazowym programem poza repozytorium. JDK 25.0.1, przypięte do rdzeni P, dane losowe
`0..15`, najlepszy z bloków po 20 powtórzeń w 1.5 s. Obie wersje `add` alokują tablicę wynikową. W
nanosekundach na element:

| Operacja | n = 4 096 (16 KB, L1): pętla | Vector API | | n = 1 048 576 (4 MB): pętla | Vector API | |
|---|---:|---:|---:|---:|---:|---:|
| `add` | **0.109** | 0.235 | 0.5× | 0.377 | 0.389 | 1.0× |
| `sum` | 0.191 | **0.024** | **7.9×** | 0.193 | **0.041** | **4.7×** |
| `count` | 0.142 | **0.041** | **3.5×** | 0.549 | **0.050** | **11×** |
| `indexOf` (brak trafienia, całe przejście) | 0.111 | **0.034** | **3.2×** | 0.112 | **0.043** | **2.6×** |

Ta sama pętla z wyłączoną autowektoryzacją (`-XX:-UseSuperWord`): `add` spada do 0.232 i 0.445, a `sum`,
`count` i `indexOf` **nie zmieniają się wcale**, czyli C2 ich nie wektoryzował.

**1. Vector API wygrywa dokładnie tam, gdzie C2 nie wektoryzuje sam.** `count` (warunek w pętli),
`indexOf` (wczesne wyjście) i, co zaskakuje, zwykła suma `int` w postaci `for (int v : a) s += v`. Ta
ostatnia na JDK 25.0.1 nie została zwektoryzowana (z SuperWord i bez: 0.19 ns), choć suma rozszerzająca do
`long` w [notatce o listach](primitive-int-lists.md) była; dlaczego, nie badałem. Wniosek ogólny: nie
zgaduj, czy pętla się zwektoryzuje, tylko porównaj z `-XX:-UseSuperWord`.

**2. Tam, gdzie C2 wektoryzuje sam, Vector API nic nie daje.** Przy dużej tablicy `add` remisuje, bo i tak
decyduje przepustowość pamięci. Przy małej pętla jest **2 razy szybsza** od wersji wektorowej; przyczyny
nie wyizolowałem (możliwe, że przy pętli JIT pomija zerowanie świeżo zaalokowanej tablicy, którą pętla i
tak całą zapisuje).

**3. `count` na dużej tablicy: 11 razy.** Koszt pętli na element rośnie czterokrotnie między 16 KB a 4 MB
(0.14 → 0.55 ns), a wersji wektorowej prawie wcale (0.04 → 0.05). Prawdopodobnie to błędne przewidywanie
rozgałęzień: przy małej tablicy przechodzonej wiele razy predyktor może nauczyć się wzorca, przy dużej
nie. Wersja wektorowa nie ma rozgałęzienia, które dałoby się źle przewidzieć.

**4. Wektor 512-bitowy na procesorze bez AVX-512 jest około 60 razy wolniejszy.** To samo `count` z
`IntVector.SPECIES_512`:

| | ns/element, n = 4 096 | n = 1 048 576 |
|---|---:|---:|
| `SPECIES_PREFERRED` (256 bitów) | 0.041 | 0.050 |
| `SPECIES_512` | **2.437** | **3.095** |

Kod się kompiluje i daje poprawny wynik, ale skoro sprzęt nie ma takich rejestrów, JVM nie zamienia
operacji na instrukcje, tylko wykonuje ich zwykłą implementację w Javie, pas po pasie, z obiektami.
**Nigdy nie wpisuj szerokości na sztywno**: `SPECIES_PREFERRED` wybierze 256 bitów tutaj, a 512 na
serwerze z AVX-512.

**5. Przed kompilacją przez C2 Vector API jest 15–50 razy wolniejsze od zwykłej pętli.** Z
`-XX:TieredStopAtLevel=1` (tylko kompilator C1), n = 4 096:

| | pętla | Vector API |
|---|---:|---:|
| `add` | 0.549 | 6.631 |
| `sum` | 0.294 | 4.530 |
| `count` | 0.538 | 14.346 |
| `indexOf` | 0.283 | 15.190 |

Zamiana operacji wektorowych na instrukcje AVX2 to intrinsics wyłącznie w C2. W interpreterze i C1 każdy
`IntVector` to zwykły obiekt, a każda operacja to wywołanie metody i alokacja. Dla krótko żyjącego
programu, rzadko wykonywanej ścieżki albo kodu tuż po deoptymalizacji (np. po włączeniu pomiaru JFR,
zob. [ściągę](../cheatsheet.md)) Vector API przegrywa z kretesem, dopóki C2 go nie skompiluje.

## Pułapki

- **To wciąż moduł inkubatora** (w JDK 25 dziesiąta odsłona, JEP 508). Wymaga `--add-modules
  jdk.incubator.vector` przy `javac` i przy `java`: w tym repozytorium w `pom.xml` (kompilator i
  `argLine` surefire) oraz w `scripts/perf/common.sh`. JVM wypisuje ostrzeżenie
  `WARNING: Using incubator modules`, a API może się jeszcze zmienić.
- **Końcówka tablicy.** `loopBound` zaokrągla w dół do wielokrotności liczby pasów, a resztę obsługuje
  zwykła pętla. Alternatywa to maska `SPECIES.indexInRange(i, n)` w ostatnim obiegu. Błąd prawie zawsze
  siedzi w końcówce, dlatego `IntVectorsTest` sprawdza każdą długość od 0 do 40, a nie tylko wielokrotności 8.
- **Redukcje zmieniają kolejność działań.** Dla `int` to bez znaczenia: dodawanie jest łączne modulo 2³²,
  więc wynik jest identyczny z pętlą także przy przepełnieniu (test `sumWrapsExactlyLikeTheScalarLoop`).
  Dla `float` i `double` wynik może się różnić w ostatnich bitach.
- **Mierz na docelowym sprzęcie.** Te same liczby na serwerze z AVX-512 albo na ARM (NEON, SVE) będą inne,
  a `SPECIES_PREFERRED` dopasuje szerokość sam.

## Uruchomienie

```sh
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest=IntVectorsTest
java --add-modules jdk.incubator.vector -cp target/classes jdk.IntVectors
```

```
wektor: 256 bitów, 8 pasów int
add:     [6, 2, 8, 2, 10, 18, 4, 12, 10, 6, 10, 16, 18, 14, 18, 6]
sum:     80
count 9: 3
indexOf 9: 5
```
