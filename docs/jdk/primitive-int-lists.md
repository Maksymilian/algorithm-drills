# Listy typu prostego `int`: fastutil, Eclipse Collections i zwykłe `int[]`, zmierzone

Notatka do [`PrimitiveIntListsBenchmark`](../../test/java/jdk/PrimitiveIntListsBenchmark.java), benchmarku
JMH, i dwóch zestawów testów obok niego:
[`PrimitiveIntListsBenchmarkTest`](../../test/java/jdk/PrimitiveIntListsBenchmarkTest.java), który pilnuje
uczciwości benchmarku, i [`PrimitiveIntListsTest`](../../test/java/jdk/PrimitiveIntListsTest.java), który
przypina, jak trzy kontenery się _zachowują_: kto kopiuje, kto współdzieli, co znaczy `equals`.

Benchmark wykonuje te same siedem operacji na trzy sposoby: ręcznie na `int[]`, przez `IntArrayList`
z fastutil i przez `IntArrayList` z Eclipse Collections. Obok biegnie `ArrayList<Integer>` jako punkt
odniesienia z opakowywaniem, który obie biblioteki mają zastąpić.

Każdą liczbę tutaj zmierzono tym benchmarkiem na JDK 25.0.1 (Temurin), Intel i7-14650HX (AVX2, bez
AVX-512), przypiętym do rdzeni P, z fastutil 8.5.19, Eclipse Collections 13.0.0 i JMH 1.37: dwa forki
po 3 × 1 s rozgrzewki i 5 × 1 s pomiaru, `-Xms2g -Xmx2g`, G1. Błąd JMH jest poniżej 10% wyniku w każdym
wierszu poza dwoma, oba z opakowywaniem przy milionie elementów, gdzie zmienia się moment GC:
`append_boxed` (±28%) i `randomRead_boxed` (±11%). Te liczby są orientacyjne, z jednej maszyny i
jednego JDK. Sekcja „Uruchomienie” niżej pozwala je odtworzyć.

Dane to `size` losowych intów z `[0, size)`, ziarno 42, w dwóch rozmiarach: **1 000** (4 KB, mieści się
w L1) i **1 000 000** (4 MB na kontener, mieści się tylko w L3). Czasy są na element, więc oba rozmiary
można czytać obok siebie.

**Odpowiedź w jednej linii.** Tam, gdzie metoda biblioteki robi to samo co ręczna pętla, _jest_ tą
pętlą: ten sam kod, skompilowany tak samo, z tą samą szybkością. Każda różnica większa niż szum bierze
się z jednej z trzech rzeczy: opakowywania, algorytmu wybranego za ciebie za nazwą metody albo zbioru,
któremu nie nadano rozmiaru z góry.

| Operacja | `int[]` ręcznie | fastutil | Eclipse | `ArrayList<Integer>` |
|---|---|---|---|---|
| suma, contains, odczyt losowy | punkt odniesienia | tyle samo | tyle samo | 3–5 razy wolniej |
| dopisywanie (rośnie od pustej) | punkt odniesienia | 1.4× (2.7× przy 1 000) | 1.2× | 15× |
| sortowanie rosnąco | `Arrays.sort` | **4.7×**: `sort(null)` to sortowanie pozycyjne | tyle samo: `sortThis()` _to_ `Arrays.sort` | 22× |
| sortowanie malejąco | sortowanie, potem odwrócenie | 8.6×: dowolny komparator | tyle co tablica, jeśli odwracasz; 9.4× z komparatorem | 22× |
| liczba różnych wartości | sortowanie 1×, **mapa bitowa 0.07×** | 1.0× | **2.9×** z `toSet()`, 1.4× z rozmiarem z góry | 3.7× |

(Przy 1 000 000 elementów, chyba że zaznaczono inaczej; kolumna tablicy to punkt odniesienia.)

---

## Przejścia po danych: biblioteki to ta sama pętla

| Wariant | ns/element, 1 000 | wobec tablicy | ns/element, 1 000 000 | wobec tablicy |
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

Przeczytaj kod źródłowy bibliotek, a remis przestaje dziwić. `sum()` z Eclipse to

```java
long result = 0L;
for (int i = 0; i < this.size; i++) {
    result += this.items[i];
}
```

a `contains` z fastutil to `indexOf`, czyli `for (int i = 0; i < size; i++) if (k == a[i])`. C2 je
inline'uje, wyciąga dwa odczyty pól przed pętlę i od tego miejsca kompiluje tę samą pętlę co
`sum_array`.

**Wektoryzuje je też tak samo.** `-XX:-UseSuperWord` wyłącza autowektoryzację C2, a z nią każdy wariant
`sum` na typach prostych zwalnia o ten sam czynnik: 3.4–3.5 razy przy 1 000, 2.6–2.8 razy przy
1 000 000. Dotyczy to pętli na tablicy, `sum()` i pętli z `get` z Eclipse oraz wszystkich czterech
pętli fastutil, łącznie z for-each z opakowywaniem. Suma rozszerzająca `int` do `long` jest
wektoryzowana niezależnie od tego, czy piszesz ją sam, czy biblioteka. `sum_boxed` i każde `contains`
w ogóle się nie zmieniają, bo nigdy nie były wektoryzowane.

**Sprawdzanie granic nic nie kosztuje.** `getInt(i)` i `get(i)` sprawdzają `index < size` przy każdym
wywołaniu, a pętle z indeksem i tak dorównują tablicy: w pętli liczonej C2 raz dowodzi, że sprawdzenie
jest zbędne, i je usuwa. `elements()` z fastutil, wyjście awaryjne dające dostęp do tablicy pod spodem,
z tego samego powodu niczego tu nie daje.

**Pętla for-each po liście fastutil opakowuje w kodzie źródłowym, a tu nic nie kosztuje.** Lista
fastutil to `Iterable<Integer>`, więc `for (int v : list)` kompiluje się do przestarzałego,
opakowującego `Integer next()`, a nie `nextInt()`. Kod bajtowy to `Iterator.next()`,
`checkcast Integer`, `intValue()`, a w źródle nic o tym nie mówi. Zmierzone: remis z `nextInt()` i
**0 bajtów alokacji na wywołanie** (`-prof gc`). Gdy `next()` zostanie zinline'owane, C2 widzi
`Integer.valueOf(x).intValue()` i usuwa opakowanie. To zależy od inline'owania: w pętli, której C2 tak
nie skompiluje, opakowania byłyby prawdziwe. `nextInt()` sprawia, że to nie ma znaczenia.

**`contains` się nie wektoryzuje, jakkolwiek je zapisać.** Wyszukiwanie z wczesnym wyjściem to nie
kształt pętli, który C2 wektoryzuje, więc `contains_array_noEarlyExit` zapisuje je jako redukcję bez
wyjścia, jak `sum`. I tak się nie wektoryzuje: 0.122 µs przy 1 000 z wyłączonym SuperWord wobec 0.123
z włączonym, i nie szybciej niż pętla z wczesnym wyjściem. Zostaje w benchmarku jako wynik negatywny,
bo to oczywista rzecz do wypróbowania. Każdy wariant na typach prostych przegląda około 8 elementów na
nanosekundę, mniej więcej 1.6 na cykl, skalarnie.

**Kosztuje opakowywanie: 3–5 razy.** Każdy element `ArrayList<Integer>` to referencja do 16-bajtowego
obiektu, więc każdy odczyt to dwa zależne odczyty. Losowe odczyty przy milionie elementów płacą za
drugie chybienie w pamięć podręczną (3.6 razy). To _najlepszy_ przypadek dla listy z opakowaniami: jej
`Integer`y alokowano jeden po drugim, w kolejności listy, więc przejście po kolei idzie po pamięci do
przodu. Opakowania alokowane przez cały czas życia programu, przemieszane ze wszystkim innym, nie leżą
tak wygodnie.

---

## Budowanie listy: wzrost

| Wariant | ns/element, 1 000 | wobec tablicy | ns/element, 1 000 000 | wobec tablicy | zaalokowane bajty / element |
|---|---:|---:|---:|---:|---:|
| `append_array_presized` | 0.24 | 0.2× | 0.37 | 0.3× | 4.0 |
| `append_array_grown` | 1.03 | 1.0× | 1.13 | 1.0× | 12.1 |
| `append_eclipse` | 1.15 | 1.1× | 1.40 | 1.2× | 12.1 |
| `append_fastutil` | **2.79** | **2.7×** | 1.55 | 1.4× | 14.6 |
| `append_boxed` | 3.57 | 3.5× | 16.9 | 14.9× | 30.6 |

Dnem jest tablica z rozmiarem z góry: gdy ostateczny rozmiar jest znany, nic nie rośnie, a 4 bajty
alokacji na element to 4 bajty, które zostają. Wszystko inne zaczyna od 10 pól i przy każdym
zapełnieniu rośnie o połowę, kopiując po drodze. To mniej więcej trzy razy więcej alokacji niż
ostateczna tablica (12.1 bajta na element) i trzy do czterech razy więcej czasu.

`append_array_grown` rośnie dokładnie jak Eclipse (10, 16, 25, 38…), oba alokują tyle samo i działają w
granicach 25% od siebie. fastutil rośnie z 10 do 15, 22, 33… (`length + length/2`, bez `+ 1` z
Eclipse), więc realokuje o raz więcej i alokuje 14.6 bajta na element.

**`add` z fastutil jest 2.7 razy wolniejsze przy 1 000 elementów.** Nie z powodu inline'owania:
`-XX:+PrintInlining` pokazuje, że `add` i `grow` są zinline'owane w obu bibliotekach. Nie z powodu
dodatkowego kopiowania, które daje 20% więcej alokacji, a nie 170% więcej czasu. Reszty nie
wyizolowano:

- Trzymanie `grow` z fastutil poza inline'owaniem
  (`-XX:CompileCommand=dontinline,it.unimi.dsi.fastutil.ints.IntArrayList::grow`) odzyskuje mniej
  więcej piątą część różnicy, 2.79 → 2.18 µs.
- Wymuszenie inline'owania ścieżki wzrostu w Eclipse _nie_ spowalnia Eclipse: 1.10 µs w trzech forkach.

Zinline'owany kod wzrostu to więc najwyżej część historii. Reszta wymaga liczników sprzętowych albo
wygenerowanego asemblera, a `perf` jest na tej maszynie zablokowany (`perf_event_paranoid` wynosi 3).
Przy milionie elementów, gdzie dominuje przenoszenie pamięci, różnica wobec Eclipse spada do 1.1 razy.

Pierwsza próba dla Eclipse dała 1.91 ± 1.30 µs w dwóch forkach: błąd prawie tak duży jak wynik, z
dziesięciu iteracji, które bardzo się różniły. Powtórzone z trzema forkami, wszystkie piętnaście
iteracji było zgodnych, na 1.10. Liczby nie warto cytować, dopóki nie przetrwa powtórzenia w świeżych
JVM-ach, a po to są forki JMH.

**Dopisywanie z opakowywaniem to 15 razy przy milionie elementów** i 30.6 bajta alokacji na element:
16-bajtowy `Integer` dla każdej wartości powyżej 127 plus tablica referencji, która sama rośnie przez
kopiowanie. To też najbardziej zaszumiony wiersz benchmarku (±28%), najpewniej dlatego, że najbardziej
zależy od tego, kiedy akurat wypadną odśmiecania młodego pokolenia.

---

## Sortowanie: gdzie biblioteka wybiera algorytm za ciebie

| Wariant | ns/element, 1 000 | wobec tablicy | ns/element, 1 000 000 | wobec tablicy | zaalokowane bajty / element |
|---|---:|---:|---:|---:|---:|
| `sort_array` (`Arrays.sort`) | 2.70 | 1.0× | 7.48 | 1.0× | 4.2 |
| `sort_eclipse` (`sortThis()`) | 2.78 | 1.0× | 7.46 | 1.0× | 4.2 |
| `sort_fastutil` (`sort(null)`) | 13.3 | **4.9×** | 35.4 | **4.7×** | 4.0 |
| `sort_fastutil_quickSort` | 13.2 | 4.9× | 72.5 | 9.7× | 4.0 |
| `sort_boxed` | 28.7 | 10.6× | 168 | 22.5× | 8.1 |

Każdy wariant sortuje świeżą kopię; kopia to te 4 bajty na element, które wszystkie alokują.

**`Arrays.sort(int[])` to nie sortowanie, które pamiętasz.** Na tym JDK i procesorze HotSpot zastępuje
wewnętrzne sortowanie `DualPivotQuicksort` wektorowym, intrinsicem używającym tu AVX2, bo ten procesor
nie ma AVX-512. Wyłączenie intrinsica (`-XX:DisableIntrinsic=_arraySort,_arrayPartition`) pokazuje, ile
jest wart:

| ns/element | intrinsic włączony | intrinsic wyłączony | |
|---|---:|---:|---:|
| `sort_array`, 1 000 | 2.70 | 10.5 | 3.9× |
| `sort_array`, 1 000 000 | 7.48 | 56.0 | **7.5×** |
| `sort_fastutil` (sortowanie pozycyjne), 1 000 000 | 35.4 | 35.4 | bez zmian |

Wszystko zbudowane na `Arrays.sort` zmienia się razem z nim: `sort_eclipse`, `sortDescending_array` i
`distinct_array_sort` zwalniają 3.5–3.9 razy przy 1 000 i 7.1–7.5 razy przy milionie. Sortowania
fastutil w ogóle się nie zmieniają, bo nigdy go nie wołają. Bez intrinsica sortowanie pozycyjne z
fastutil jest 1.6 razy _szybsze_ niż `Arrays.sort` przy milionie elementów.

**fastutil nigdy nie woła `Arrays.sort`.** `IntArrayList.sort(null)` prowadzi do
`IntArrays.stableSort`, które prowadzi do `unstableSort`: stabilnego sortowania `int`ów nie da się
odróżnić od niestabilnego. `unstableSort` wybiera

```java
if (to - from >= RADIX_SORT_MIN_THRESHOLD) {   // 2 000, "determined on an Intel i7 8700K"
    radixSort(a, from, to);
} else {
    quickSort(a, from, to);
}
```

Wobec skalarnego `Arrays.sort` ten próg był dobry i sortowanie pozycyjne nadal je bije, jak zmierzono
wyżej. Wobec sortowania SIMD sortowanie pozycyjne jest 4.7 razy wolniejsze przy milionie elementów, a
quicksort z fastutil 4.9 razy wolniejszy przy tysiącu. Biblioteka dokonała rozsądnego wyboru dla JDK,
na którym ją strojono, a JDK zmieniło się pod nią. Nic w nazwie metody nie mówi, jaki algorytm
dostajesz.

**Eclipse dostaje przyspieszenie za darmo**, bo `sortThis()` to jedna linia:
`Arrays.sort(this.items, 0, this.size)`. Ręczna tablica i Eclipse to to samo wywołanie.

### Malejąco

`Arrays.sort(int[])` nie przyjmuje komparatora, więc „malejąco” znaczy albo komparator przez
bibliotekę, albo sortowanie rosnące i odwrócenie w miejscu, czyli jedno przejście więcej.

| Wariant | ns/element, 1 000 | wobec tablicy | ns/element, 1 000 000 | wobec tablicy | zaalokowane bajty / element |
|---|---:|---:|---:|---:|---:|
| `sortDescending_array` (sortowanie, odwrócenie) | 3.02 | 1.0× | 7.80 | 1.0× | 4.2 |
| `sortDescending_eclipse` (`sortThis().reverseThis()`) | 3.14 | 1.0× | 8.24 | 1.1× | 4.2 |
| `sortDescending_fastutil` (`sort(OPPOSITE)`) | 8.61 | 2.9× | 67.2 | 8.6× | 8.0 |
| `sortDescending_fastutil_unstable` (`unstableSort(OPPOSITE)`) | 14.2 | 4.7× | 70.2 | 9.0× | 4.0 |
| `sortDescending_eclipse_comparator` (`sortThis(comparator)`) | 10.4 | 3.4× | 73.7 | 9.4× | 4.0 |
| `sortDescending_boxed` (`reverseOrder()`) | 37.0 | 12.3× | 174 | 22.3× | 8.1 |

Odwrócenie dodaje 0.3 ns na element. Komparator kosztuje 9 razy, niezależnie od biblioteki: każda
droga przez komparator to skalarne sortowanie przez porównania, bez ścieżki SIMD. `sort(comparator)` z
fastutil to stabilne **sortowanie przez scalanie**, które potrzebuje drugiego bufora wielkości listy
(te 8.0 bajta na element); `unstableSort(comparator)` to jego quicksort, w miejscu. Przy tysiącu
elementów szybsze z tych dwóch jest sortowanie przez scalanie.

Malejąco należy więc sortować rosnąco, a potem odwrócić.

---

## Liczenie różnych wartości: zbiory haszujące i co może wiedzieć tablica

| Wariant | ns/element, 1 000 | wobec tablicy | ns/element, 1 000 000 | wobec tablicy | zaalokowane bajty / element |
|---|---:|---:|---:|---:|---:|
| `distinct_array_sort` (sortowanie kopii, liczenie zmian) | 3.16 | 1.0× | 8.09 | 1.0× | 4.2 |
| `distinct_array_bitmap` | 0.56 | **0.2×** | 0.57 | **0.07×** | 0.1 |
| `distinct_fastutil` (`IntOpenHashSet`) | 1.45 | 0.5× | 8.09 | 1.0× | 8.4 |
| `distinct_eclipse` (`toSet()`) | 5.01 | 1.6× | 23.7 | **2.9×** | 16.8 |
| `distinct_eclipse_presized` | 1.91 | 0.6× | 11.4 | 1.4× | 8.4 |
| `distinct_boxed` (`HashSet<Integer>`) | 7.95 | 2.5× | 30.0 | 3.7× | 28.6 |

**Zbiór haszujący wygrywa w małej skali i remisuje w dużej.** Przy tysiącu elementów zbiór z fastutil
zajmuje połowę czasu sortowania z przejściem. Przy milionie jego tablica ma 2²¹ pól, 8 MB, ponad L2
tego rdzenia, i prawie każde wstawienie to chybienie w pamięć podręczną. Sortowanie zamiast tego idzie
strumieniowo po pamięci i oba remisują.

**`toSet()` z Eclipse nie nadaje zbiorowi rozmiaru.** Woła `new IntHashSet(IntIterable)`, który zaczyna
od domyślnej pojemności i przehaszowuje się w górę, gdy `addAll` go wypełnia. Widać to w alokacji: 16.8
bajta na element, dwa razy więcej niż ostateczna tablica. `new IntOpenHashSet(IntCollection)` z fastutil
najpierw dobiera rozmiar do kolekcji. Nadanie rozmiaru zbiorowi Eclipse ręcznie (`new IntHashSet(size)`,
potem `addAll`) zmniejsza o połowę jego alokację, dokładnie do poziomu fastutil, i czyni go 2.1 razy
szybszym przy milionie elementów (2.6 razy przy tysiącu). Pozostałej różnicy 1.4 razy wobec fastutil
nie wyizolowano: oba inaczej haszują i inaczej szukają wolnego pola.

**Mapa bitowa jest 14 razy szybsza od wszystkiego innego przy milionie elementów**, bo nie jest
rozwiązaniem ogólnym. Wiadomo, że wartości leżą w `[0, size)`, więc jeden bit na możliwą wartość to
pełny zbiór bez kolizji w 125 KB, a liczenie to `bitCount` na słowo. Żaden zbiór ogólnego przeznaczenia
nie może tego założyć. To jedyne miejsce w benchmarku, gdzie ręczny kod na tablicy wygrywa bez
dyskusji, a wygrywa dzięki wiedzy o danych, a nie dzięki lepszej pętli.

---

## Co trzyma sterta forka: jeden histogram klas

`scripts/perf/snapshot.sh` robi zrzuty `jstack` i `jcmd` z JVM, w której działa benchmark (fork JMH, a
nie host). Jeden `jcmd GC.class_histogram`, zrobiony w trakcie forka z `size = 1 000 000`:

```
$ scripts/perf/snapshot.sh --take histo --count 1 'PrimitiveIntListsBenchmark.sum_boxed$' -p size=1000000
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:          1601       16386880  [I (java.base@25.0.1)
   2:       1000169       16002704  java.lang.Integer (java.base@25.0.1)
   3:         31493        5199984  [B (java.base@25.0.1)
   4:          1945        4184136  [Ljava.lang.Object; (java.base@25.0.1)
```

Cztery kontenery obok siebie:

- **`[I`, 16 MB**: cztery tablice `int[]` po 4 MB, czyli tablica źródłowa, losowe indeksy, tablica pod
  spodem fastutil i ta z Eclipse. Milion intów typu prostego to jedna tablica 4 MB, niezależnie od
  biblioteki.
- **`java.lang.Integer` i `[Ljava.lang.Object;`, 20 MB**: sama lista z opakowaniami. To milion
  16-bajtowych obiektów plus tablica 4 MB skompresowanych referencji do nich, pięć razy więcej pamięci
  na te same wartości.

`@State` buduje wszystkie cztery kontenery dla każdego benchmarku, więc każdy fork trzyma je wszystkie,
`sum_array` tak samo jak `sum_boxed`. Kosztuje to benchmarki z dużą alokacją kilkadziesiąt MB
dodatkowych żywych danych, które GC musi omijać. Osobne stany na kontener by to usunęły, za cenę
benchmarku, w którym trudniej sprawdzić, że praca jest równa.

Ten sam skrypt złapał błąd, którego nie pokazałby żaden wynik. Jego `jcmd VM.command_line` dla forka
uruchomionego przez `jmh.sh ... -- -XX:-UseSuperWord` pokazał `MaxHeapSize` na domyślnych 16 GB:
`-jvmArgsAppend` w JMH _zastępuje_ `@Fork(jvmArgsAppend = ...)`, zamiast do niego dopisywać. Flagi
sterty są teraz w `jvmArgsPrepend`, a każda liczba na tej stronie pochodzi z forków z
`-Xms2g -Xmx2g`.

---

## Wnioski

1. **Do przejść po danych wybierz którąkolwiek bibliotekę dla jej API, a nie szybkości.** `sum`,
   `contains`, odczyty z indeksem i przez iterator kompilują się do ręcznej pętli, łącznie z
   wektoryzacją.
2. **Przy sortowaniu wiedz, jakim algorytmem jest metoda.** `sortThis()` z Eclipse to `Arrays.sort`.
   `sort(null)` z fastutil to sortowanie pozycyjne, 4.7 razy wolniejsze na JDK, którego `Arrays.sort`
   jest wektorowe. Z fastutil wołaj zamiast tego `Arrays.sort(list.elements(), 0, list.size())`, tak
   jak robi Eclipse.
3. **Nigdy nie sortuj malejąco komparatorem**: sortuj rosnąco i odwracaj, to około 9 razy szybciej.
4. **Nadawaj zbiorom Eclipse rozmiar sam.** `toSet()` tego nie robi, a kosztuje to 2–3 razy.
5. **Drogie jest opakowywanie**: 3–22 razy i 5 razy więcej pamięci. Każda z bibliotek je usuwa.
6. **Tablica wygrywa bez dyskusji tylko tam, gdzie wiesz więcej niż biblioteka**: znany rozmiar
   (dopisywanie z rozmiarem z góry, 3–4 razy szybciej) albo znany zakres (mapa bitowa, 14 razy
   szybciej).

---

## Uruchomienie

```sh
scripts/perf/jmh.sh PrimitiveIntListsBenchmark -f 2 -prof gc              # wszystko, ~25 min
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sort' -p size=1000000      # jedna grupa, jeden rozmiar
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sum_' -- -XX:-UseSuperWord                 # kontrola wektoryzacji
scripts/perf/jmh.sh 'PrimitiveIntListsBenchmark.sort_array$' \
    -- -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_arraySort,_arrayPartition        # kontrola sortowania SIMD
scripts/perf/snapshot.sh --take histo --count 1 'PrimitiveIntListsBenchmark.sum_boxed$' -p size=1000000
JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem mvn test -Dtest='PrimitiveIntLists*'      # oba zestawy testów
```

Build nigdy nie uruchamia benchmarku. `mvn test-compile` generuje jego szkielet (procesor adnotacji JMH
jest skonfigurowany dla testów w `pom.xml`), a `PrimitiveIntListsBenchmarkTest` działa w zwykłej fazie
testów. Ten test woła bezpośrednio każdą metodę `@Benchmark` i sprawdza dwie rzeczy. Po pierwsze, że
każdy wariant operacji zwraca tę samą odpowiedź co pozostałe, przy rozmiarach po obu stronach progu
2 000 elementów dla sortowania pozycyjnego z fastutil. Po drugie, że żaden wariant nie zmienia
wspólnego wejścia. Benchmark, który sortowałby swoje wejście w miejscu, od drugiego wywołania mierzyłby
posortowane dane, a w wynikach nic by tego nie zdradziło. Podłożenie dokładnie tego błędu oblewa test.
