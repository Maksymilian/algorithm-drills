# Złożoność operacji na kolekcjach: czas i pamięć

Koszt pojedynczych operacji na kolekcjach, tablicach, napisach i strumieniach Javy. Którą kolekcję
wybrać do problemu, opisuje [ściąga](cheatsheet.md#ściąga-którą-kolekcję-wybrać).

**Oznaczenia:**

- n to rozmiar kolekcji;
- „zamort.” to koszt zamortyzowany: czasem drogie powiększenie tablicy, średnio tanio;
- „śr.” to koszt średni przy dobrym `hashCode`;
- pamięć przy operacji to dodatkowe miejsce, które ta operacja alokuje, a ile zajmuje cała struktura,
  pokazuje sekcja [Pamięć](#pamięć).

## W skrócie

| Struktura | Kluczowe koszty |
|---|---|
| `HashMap` / `HashSet` | O(1) śr., O(log n) w najgorszym przypadku (od Javy 8) |
| `TreeMap` / `TreeSet` | O(log n), ale posortowane i z nawigacją (`floor`, `ceiling`) |
| `PriorityQueue` | `add` i `poll` O(log n), `peek` O(1), budowa z kolekcji O(n) |
| `ArrayList` | O(1) na końcu i po indeksie, O(n) na początku i przy szukaniu |
| `ArrayDeque` | O(1) na obu końcach |
| widoki (`reversed`, `subList`, `asList`, `keySet`, `unmodifiable*`) | O(1) czasu i pamięci |
| kopie (`substring`, `toArray`, `sorted()`, `new ArrayList<>(x)`) | O(n) czasu i pamięci |
| sortowanie | O(n log n); obiekty TimSortem (stabilnie), typy proste Quicksortem |

## Jak wyznaczyć O()

1. **Nazwij rozmiary wejścia.** Niezależne rozmiary dostają osobne litery, np. n słów, z czego
   m różnych, i k elementów wyniku.
2. **Policz koszt każdego kroku.** Pętla kosztuje liczbę obrotów razy koszt ciała, a koszt wywołań
   bibliotecznych podają tabele niżej.
3. **Kroki po kolei się dodają, kroki zagnieżdżone się mnożą.** Jedno sortowanie m elementów to
   O(m log m), a sortowanie n elementów m razy to O(m · n log n).
4. **Uprość.** Pomiń stałe (O(2n) → O(n)) i zostaw składnik dominujący (O(n + k) → O(n), gdy
   k ≤ n). Składników z niezależnymi literami nie łączymy: O(n + m log k) zostaje tak, jak jest.
5. **Pamięć to to, co alokujesz i co żyje w tym samym czasie.** Wejścia nie liczymy. Liczymy
   największy rozmiar nowych kolekcji, kopie i głębokość rekurencji, bo każde wywołanie to ramka
   stosu.

**Sprawdzian:** czas nigdy nie jest mniejszy niż pamięć, bo k komórek nie da się zapełnić w mniej
niż k krokach.

| Wzorzec w kodzie | Czas |
|---|---|
| jedna pętla po n | O(n) |
| dwie zagnieżdżone pętle po n | O(n²) |
| pętla wewnętrzna od i do n | nadal O(n²), bo n²/2 traci stałą |
| połowienie w każdym kroku (wyszukiwanie binarne, `i *= 2`) | O(log n) |
| pętla po n z pracą O(log n) w środku | O(n log n) |
| rekurencja z dwoma wywołaniami na n − 1 (naiwny Fibonacci) | O(2ⁿ), dokładnie φⁿ |
| rekurencja na dwóch połówkach + liniowe scalanie (sortowanie przez scalanie) | O(n log n) |

## Listy i tablice

| Operacja | tablica | `ArrayList` | `LinkedList` |
|---|---|---|---|
| `get(i)` / `set(i, x)` | O(1) | O(1) | O(n), idzie od bliższego końca |
| `add(x)` na końcu | brak, stały rozmiar | O(1) zamort. | O(1) |
| `add(0, x)` / `remove(0)` | brak | O(n), przesuwa tablicę | O(1) |
| `add(i, x)` / `remove(i)` w środku | brak | O(n) | O(n) na znalezienie miejsca, potem O(1) |
| `iterator.remove()` | brak | O(n) za każde usunięcie | O(1) |
| `contains` / `indexOf` / `remove(Object)` | O(n) | O(n) | O(n) |
| `removeIf` | brak | O(n) | O(n) |
| kopia: `copyOf` / `clone` / `new ArrayList<>(x)` / `toArray` | O(n) | O(n) | O(n) |
| `Arrays.fill` | O(n) | brak | brak |

**Wybór:** prawie zawsze `ArrayList`. `LinkedList` wygrywa tylko przy usuwaniu przez iterator w
środku, a do obu końców lepszy jest [`ArrayDeque`](#kolejki-stosy-i-kopce).

### Widoki i odwracanie

| Operacja | Czas | Pamięć | Uwagi |
|---|---|---|---|
| `list.reversed()` (Java 21+) | O(1) | O(1) | widok, lista bez zmian |
| `Collections.reverse(list)` | O(n) | O(1) | w miejscu, zwraca `void` |
| `subList(a, b)` | O(1) | O(1) | widok; zmiana oryginału go unieważnia |
| `Arrays.asList(tablica)` | O(1) | O(1) | widok o stałym rozmiarze, `add` rzuca wyjątek |
| `Collections.unmodifiableList(x)` | O(1) | O(1) | widok tylko do odczytu, zmiany oryginału widać |
| `List.of(...)` / `List.copyOf(x)` | O(n) | O(n) | niezmienna kopia, bez `null` |

**Pułapki:**

- **`Collections.reverseOrder()`** to komparator, nie lista. Metody `Collections.reversed()` nie ma.
- **`addFirst` w pętli na `ArrayList`** kosztuje razem O(n²), bo każde wstawienie przesuwa całą
  tablicę. Lepiej `add` na końcu i na koniec `reversed()`.

## Kolejki, stosy i kopce

### `ArrayDeque`

| Operacja | Czas |
|---|---|
| `addFirst` / `addLast` / `push` / `offer` | O(1) zamort. |
| `pollFirst` / `pollLast` / `pop` / `poll` | O(1) |
| `peekFirst` / `peekLast` / `peek` | O(1) |
| `contains` / `remove(Object)` | O(n) |

Nie przyjmuje `null`. Lepszy niż `Stack` (synchronizowany, przestarzały) i niż `LinkedList`.

### `PriorityQueue`

| Operacja | Czas |
|---|---|
| `add` / `offer` | O(log n) |
| `poll` / `remove()` | O(log n) |
| `peek` | O(1) |
| `remove(Object)` / `contains` | O(n) |
| `new PriorityQueue<>(kolekcja)` | O(n), budowa kopca, nie n log n |
| iteracja | O(n), **nieposortowana** |
| opróżnienie przez `poll` | O(n log n) |

- **Kopiec minimalny w tablicy.** Gwarantowane jest tylko to, że korzeń (`peek()`) jest najmniejszy
  według komparatora.
- **Tylko kolejne `poll()` dają kolejność.** Wypisanie kopca albo `for`/`stream()` przechodzą
  tablicę w kolejności kopca.
- **Kopiec nie jest stabilny.** Remisy rozstrzyga tylko komparator.
- **Kopiec maksymalny:** `new PriorityQueue<>(Comparator.reverseOrder())` albo `cmp.reversed()`.
- **Ograniczenie rozmiaru do k zmienia koszt.** `poll()` po przekroczeniu k trzyma kopiec przy k + 1
  elementach, więc każda operacja kosztuje O(log k), a nie O(log n).

## Mapy i zbiory

`HashSet`, `LinkedHashSet` i `TreeSet` to mapy bez wartości (w środku siedzi `HashMap`,
`LinkedHashMap` albo `TreeMap`), więc kosztują tyle samo co odpowiednia mapa.

### Implementacje

| | `HashMap` | `LinkedHashMap` | `TreeMap` | `EnumMap` | `ConcurrentHashMap` | `Map.of` / `Map.copyOf` |
|---|---|---|---|---|---|---|
| Wnętrze | tablica kubełków (lista, od 8 wpisów drzewo) | `HashMap` + lista dwukierunkowa | drzewo czerwono-czarne | tablica po `ordinal()` | kubełki, blokada na kubełek | zwarta tablica, niezmienna |
| `get` / `containsKey` | O(1) śr. | O(1) śr. | O(log n) | O(1) | O(1) śr. | O(1) śr. |
| `put` / `remove` | O(1) śr., zamort. | O(1) śr., zamort. | O(log n) | O(1) | O(1) śr. | `UnsupportedOperationException` |
| Najgorszy przypadek | O(log n) | O(log n) | O(log n) | O(1) | O(log n) | O(n) |
| Kolejność iteracji | brak, może się zmienić po powiększeniu | wstawiania (albo użycia) | posortowana po kluczu | kolejność `ordinal` | brak | brak, różna między uruchomieniami |
| `null` jako klucz / wartość | 1 klucz, dowolnie wartości | jak `HashMap` | klucz nie przy porządku naturalnym | klucz nie | **żadne** | **żadne** |

### Operacje

Koszty dla `HashMap`; dla `TreeMap` O(1) zamień na O(log n).

| Operacja | Czas | Uwagi |
|---|---|---|
| `get(k)` / `getOrDefault(k, d)` / `containsKey(k)` | O(1) | `getOrDefault` niczego nie dodaje |
| `put(k, v)` / `putIfAbsent(k, v)` | O(1) zamort. | `put` zwraca starą wartość albo `null` |
| `remove(k)` / `remove(k, v)` | O(1) | |
| `merge(k, v, fn)` | O(1) + koszt `fn` | zliczanie: `merge(w, 1, Integer::sum)`; `null` z `fn` usuwa wpis |
| `computeIfAbsent(k, fn)` | O(1) + koszt `fn` | grupowanie: `computeIfAbsent(k, x -> new ArrayList<>()).add(v)` |
| `compute` / `computeIfPresent` | O(1) + koszt `fn` | |
| `containsValue(v)` | **O(n)** | wartości nie mają indeksu |
| `size` / `isEmpty` | O(1) | |
| `keySet()` / `values()` / `entrySet()` | O(1) | **widoki**: usunięcie z nich usuwa z mapy |
| iteracja po `entrySet` | O(n + pojemność) | dla `LinkedHashMap` i `TreeMap` O(n) |
| `replaceAll(fn)` / `forEach` / `equals` / `hashCode` | O(n) | |
| `putAll(inna)` / `new HashMap<>(inna)` | O(rozmiar innej) | tyle samo pamięci |
| `clear` | O(pojemność) | tablica kubełków zostaje |
| powiększenie (samo) | O(n), rzadko | gdy rozmiar > pojemność × 0,75; pojemność się podwaja |

Rozmiar ustawiamy z góry przez `HashMap.newHashMap(oczekiwana)` (Java 19+), żeby uniknąć
powiększeń. `new HashMap<>(oczekiwana)` ustawia pojemność, a nie liczbę elementów, więc nadal może
się powiększyć.

### Nawigacja i kolejność: `TreeMap`, `LinkedHashMap`

| Operacja | `TreeMap` / `TreeSet` | `LinkedHashMap` (Java 21+) |
|---|---|---|
| `firstEntry` / `lastEntry` (`first` / `last` w zbiorze) | O(log n) | O(1) |
| `pollFirstEntry` / `pollLastEntry` | O(log n) | O(1) |
| `floorKey(k)` ≤ k, `ceilingKey(k)` ≥ k | O(log n) | brak |
| `lowerKey(k)` < k, `higherKey(k)` > k | O(log n) | brak |
| `headMap` / `tailMap` / `subMap` (`headSet`, …) | O(log n) na utworzenie **widoku** | brak |
| `putFirst` / `putLast` | `UnsupportedOperationException` | O(1) |
| `reversed()` / `descendingMap()` | O(1), widok | O(1), widok |

**Typowe zastosowania `TreeMap`:**

- przedziały i osie czasu;
- „najbliższy mniejszy klucz” ([historia klucza](collection/time-map.md));
- przesuwane okno z duplikatami (`TreeMap<Integer, Integer>` jako multizbiór).

**LRU w trzech liniach:** `new LinkedHashMap<>(16, 0.75f, true)` (kolejność użycia) i nadpisane
`removeEldestEntry`, które zwraca `size() > capacity`. Każda operacja to O(1). Wersja z własną
listą jest w [LRU](collection/lru-cache.md).

### Wzorce z mapami

| Wzorzec | Czas | Pamięć |
|---|---|---|
| zliczanie: `merge(x, 1, Integer::sum)` w pętli | O(n) | O(m) różnych kluczy |
| grupowanie: `computeIfAbsent(k, ...).add(v)` albo `groupingBy(f)` | O(n) | O(n) |
| `groupingBy(f, TreeMap::new, ...)` | O(n log m) | O(n) |
| `toMap(k, v, merge)` | O(n) | O(m); bez `merge` duplikat rzuca `IllegalStateException` |
| wpisy po wartości: `entrySet().stream().sorted(comparingByValue())` | O(m log m) | O(m) |
| k największych po wartości przez kopiec ograniczony do k | O(m log k) | O(k) |
| dwie sumy / „czy już to widziałem” | O(n) | O(n) |
| `int[26]` zamiast `Map<Character, Integer>` dla a-z | O(n) | O(1), ok. 100 B zamiast kilobajtów |

### Pułapki, które zmieniają O()

- **Zły `hashCode`** (stały albo nienadpisany razem z `equals`) psuje `HashMap`. Każda operacja
  kosztuje wtedy O(log n), a O(n), gdy klucze nie są `Comparable`, i cała pętla może zrobić się
  O(n²).
- **Zmiana klucza po `put`** zmienia jego skrót. Wpis siedzi wtedy w złym kubełku i `get` go nie
  znajduje. Klucze powinny być niezmienne (`String`, `Integer`, rekordy).
- **Napisy jako klucze:** `hashCode` kosztuje O(L) przy pierwszym wywołaniu, a potem jest
  zapamiętany, ale `equals` zawsze kosztuje O(L). Ściśle biorąc, operacja na `HashMap<String, …>`
  to O(L), nie O(1).
- **`TreeMap` z komparatorem niezgodnym z `equals`:** dwa klucze porównane jako 0 to jeden klucz,
  więc jeden nadpisuje drugi.
- **Rzadka `HashMap`** (ogromna pojemność, mało wpisów, np. po wielu `remove`) iteruje się w
  O(pojemność), nie O(n).
- **Usuwanie podczas iteracji** w `for (k : map.keySet())` rzuca `ConcurrentModificationException`.
  Zamiast tego `map.entrySet().removeIf(...)` (O(n)) albo `iterator.remove()`.

## Sortowanie i wyszukiwanie

| | Obiekty: `List.sort`, `Collections.sort`, `Arrays.sort(Object[])` | Typy proste: `Arrays.sort(int[])` |
|---|---|---|
| Algorytm | TimSort | Dual-Pivot Quicksort |
| Najgorszy przypadek | O(n log n), ok. n·log₂n porównań | O(n log n) |
| Dane posortowane lub odwrócone | O(n), TimSort wykrywa serie | O(n log n) |
| Pamięć | do n/2 referencji | O(log n), w miejscu |
| Stabilne | tak | nie (dla liczb bez znaczenia) |

| Operacja | Czas | Pamięć |
|---|---|---|
| `Arrays.binarySearch` / `Collections.binarySearch` | O(log n), tylko na posortowanych danych | O(1) |
| `Collections.max` / `min` / `frequency` | O(n) | O(1) |
| `Arrays.parallelSort` | O(n log n), szybciej dopiero od setek tysięcy elementów | O(n) |

- **`ArrayList`** sortuje swoją wewnętrzną tablicę, a inne listy kopiują się do tablicy i z
  powrotem.
- **Listy niemodyfikowalne** (`List.of`, `toList()`) rzucają `UnsupportedOperationException`.
- **O realnym czasie decyduje koszt jednego porównania**, bo komparator wywołuje się ok. n log n
  razy. Dlatego `comparingDouble` zamiast `comparing` (bez opakowywania przy każdym porównaniu) i
  parsowanie raz, a nie w komparatorze.
- **`List<Integer>` sortuje się kilka razy wolniej niż `int[]`**, bo elementy to rozrzucone w
  pamięci obiekty.

## Napisy

| Operacja | Czas |
|---|---|
| `charAt` / `length` | O(1) |
| `substring` / `toCharArray` / `split` | O(n), robi kopię |
| `equals` / `compareTo` / `hashCode` | O(n), ale `hashCode` jest zapamiętywany po pierwszym wywołaniu |
| `indexOf` / `contains` | O(n·m) w najgorszym przypadku |
| `s1 + s2` | O(n + m), więc `+=` w pętli to razem O(n²) |
| `StringBuilder.append` | O(1) zamort. |
| `StringBuilder.insert(0, x)` / `deleteCharAt(0)` | O(n) |
| `StringBuilder.reverse` / `toString` | O(n) |

## Strumienie

| Operacja | Czas | Dodatkowa pamięć |
|---|---|---|
| `filter` / `map` / `forEach` / `count` / `sum` | O(n) | O(1) |
| `limit(k)` / `findFirst` / `anyMatch` | kończy wcześniej, O(k) | O(1) |
| `distinct()` | O(n) śr. | O(n), trzyma `HashSet` |
| `sorted()` | O(n log n) | O(n), buforuje wszystko |
| `toList()` / `collect(toList())` / `groupingBy` / `toMap` | O(n) śr. | O(n) |

## Pamięć

Wszystkie struktury zajmują O(n) pamięci, ale stałe są bardzo różne:

| Struktura | Koszt na element (JVM 64-bit, skompresowane referencje) |
|---|---|
| `int[]` | 4 B |
| `ArrayList<Integer>` | 4 B referencji + ~16 B `Integer`, plus do 50% zapasu pojemności |
| `ArrayDeque` | 4 B referencji + element, pojemność to potęga dwójki |
| `LinkedList` | ~24 B węzła + element |
| `HashMap` / `HashSet` | ~32 B węzła + 4 B w tablicy kubełków (współczynnik wypełnienia 0,75) |
| `LinkedHashMap` | ~40 B węzła |
| `TreeMap` / `TreeSet` | ~40 B węzła (lewy, prawy, rodzic, kolor) |
| `EnumMap` | 4 B (referencja w tablicy) |
| `Map.of` | ~8 B |

Dla porównania, przy milionie elementów:

- `HashSet<Integer>` zajmuje ok. 50 MB;
- `boolean[]` dla tego samego zakresu zajmuje 1 MB;
- `BitSet` zajmuje 125 KB.
