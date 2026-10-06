# Ściąga: wnioski i reguły kciuka

Wnioski z zadań w tym repozytorium, zebrane w jednym miejscu. Szczegóły, kod i testy są w notatkach,
do których prowadzą linki.

## Jak prowadzić rozwiązanie (ok. 30 minut)

1. **Dopytaj o założenia** (2 min): rozmiar danych, zakres wartości, przypadki brzegowe, co zwrócić,
   gdy nie ma odpowiedzi. Zapisz je jako tabelkę „Założenia”.
2. **Najprostsze poprawne rozwiązanie** (3 min): powiedz je i podaj złożoność, nawet jeśli jest
   wolne. To punkt odniesienia i późniejszy test.
3. **Kluczowa obserwacja** (5 min): co pozwala zrobić to szybciej. Właśnie to rekruter chce usłyszeć.
4. **Kod** (15 min): najpierw sygnatura i przykład, potem pętla główna, na końcu przypadki brzegowe.
5. **Sprawdzenie** (3 min): przejdź ręcznie przez przykład i jeden przypadek brzegowy.
6. **Złożoność i dopytania** (2 min): czas, pamięć, co się zmieni przy 1000 razy większych danych.

## Rozpoznaj wzorzec po treści

| W treści pada… | Technika | Koszt | Przykład |
|---|---|---|---|
| „najlepsza droga”, ruch tylko w jedną stronę, podproblemy się powtarzają | programowanie dynamiczne | O(liczba stanów) | [kamienie na mapie](array/max-stones-path.md) |
| „każdy element zależy od maksimum po lewej i po prawej” | maksima prefiksowe, potem dwa wskaźniki | O(n), O(1) pamięci | [śnieg między wzgórzami](array/trapping-water.md) |
| „kup, potem sprzedaj”, „najlepsza para i < j” | minimum dotąd + najlepsza różnica | O(n) | [zysk z akcji](array/stock-max-profit.md) |
| „maksimum / minimum w każdym oknie” | kolejka monotoniczna | O(n) | [maksimum w oknie](array/sliding-window-maximum.md) |
| „k największych / najczęstszych” | kopiec minimalny o rozmiarze k | O(n log k) | [top-k słów](collection/top-k-frequent-words.md) |
| „ile przedziałów naraz”, „ile sal” | sortowanie + kopiec końców albo zamiatanie +1 / −1 | O(n log n) | [sale na spotkania](collection/meeting-rooms.md) |
| „wartość w chwili t”, „najbliższy w dół / w górę” | `TreeMap.floorEntry` / `ceilingEntry` | O(log n) | [historia klucza](collection/time-map.md) |
| „losuj z wagami” | sumy prefiksowe + wyszukiwanie binarne | O(log k) na losowanie | [losowy znak](collection/weighted-random-char.md) |
| „get i put w O(1), wyrzucaj najstarszy” | `HashMap` + lista dwukierunkowa | O(1) | [LRU](collection/lru-cache.md) |
| „najkrótsza droga”, wagi ≥ 0 | Dijkstra na kopcu | O((V + E) log V) | [Dijkstra](graph/shortest-path.md) |
| „najkrótsza droga”, wszystkie wagi równe | BFS na `ArrayDeque` | O(V + E) | [Dijkstra](graph/shortest-path.md), dopytania |
| „czy da się ułożyć z liter”, mały alfabet | licznik `int[26]` | O(długość) | [słowa z liter](string/longest-words-from-letters.md) |
| „bez pamięci dynamicznej”, mały alfabet | maski bitowe w `int` / `long` | O(n), O(1) | [pierwsza unikalna](string/first-unique-letter.md) |
| „wiele zapytań o ten sam tekst” | zbuduj indeks raz, odpowiadaj z indeksu | O(log n + wynik) | [prefiksy](string/word-prefix-positions.md), [najbliższe słowa](string/closest-words.md) |
| „obsłuż żądania po drodze” | LOOK: sortowany zbiór, jedź, dopóki coś jest przed tobą | O(log n) na krok | [windy](greedy/simple-elevators.md) |
| „indeks następnego elementu”, „cykl” | żółw i zając (Floyd) | O(n), O(1) | [cykl w tablicy](array/next-index-cycle.md) |
| „miliardy punktów, wiele zapytań” | siatka / podział przestrzeni zamiast przeglądania wszystkiego | zależnie od danych | [punkty w kole](spatial/points-in-a-circle.md) |

## Wnioski z zadań

**Algorytmy**

- **Zachłanność trzeba udowodnić albo obalić kontrprzykładem.** Bogatszy sąsiad w każdym kroku nie
  daje najlepszej drogi, bo nie wolno się cofać. Mały kontrprzykład kończy dyskusję.
  ([kamienie](array/max-stones-path.md))
- **„Najbliższy” to nie „dojedzie pierwszy”.** Koszt musi uwzględniać kierunek i obietnice już
  złożone: winda jedno piętro dalej, która właśnie minęła wzywającego, jest najdalej.
  ([windy](greedy/elevator-interview.md))
- **Kolejka to zły kształt dla żądań, które da się obsłużyć po drodze.** Kolejność przyjścia to nie
  kolejność obsługi. Zbiór posortowany usuwa też duplikaty za darmo. ([windy](greedy/two-elevators.md))
- **Najpierw tanie filtry.** Porównanie długości kosztuje O(1), liczenie liter O(długość słowa).
  Słowo dłuższe niż wszystkie litery odpada od razu. ([słowa z liter](string/longest-words-from-letters.md))
- **„Jedno przejście” ma dwa znaczenia.** Dwa wskaźniki robią jedno przejście w O(1) pamięci, ale
  czytają z obu końców. Na strumieniu tylko do przodu potrzebny jest stos, czyli O(n).
  ([śnieg](array/trapping-water.md))
- **Pętla w pętli to nie zawsze O(n²).** Jeśli każdy element raz wchodzi do struktury i raz z niej
  wychodzi, łącznie jest O(n) (analiza zamortyzowana). ([kolejka monotoniczna](array/sliding-window-maximum.md))
- **Wiele zapytań o te same dane: przygotuj je raz.** Indeks słów, sumy prefiksowe, posortowane
  pozycje. Koszt budowy dzieli się na wszystkie zapytania.
- **Pierwsza wersja to tablice, docelowa to mniej pamięci.** Tablice maksimów łatwo udowodnić,
  dwa wskaźniki to optymalizacja tej samej obserwacji. Pokaż obie.
- **Testuj przeciw wersji naiwnej na losowych danych.** Każda szybka wersja w repozytorium ma test
  porównujący ją z wolną i oczywistą. To łapie błędy, których przykłady z treści nie pokażą.

**Struktury danych**

- **O(1) to nie wszystko.** `ArrayDeque` i `LinkedList` mają te same złożoności, ale tablica jest
  ciągła w pamięci i nie alokuje węzła na każdy element.
- **Kopiec minimalny o rozmiarze k, nie maksymalny ze wszystkim.** Na szczycie jest najsłabszy z
  najlepszych, więc łatwo go wymienić. Pamięć O(k), a nie O(n).
- **`PriorityQueue` nie ma decrease-key.** Wkładaj nowy wpis, a nieaktualny pomijaj przy zdjęciu
  (leniwe usuwanie). ([Dijkstra](graph/shortest-path.md))
- **W `TreeMap` i `TreeSet` o równości decyduje komparator, nie `equals`.** Remis w komparatorze
  znaczy „ten sam element” i drugi znika. Rozstrzygaj remisy po unikalnym polu.
  ([top-k](collection/top-k-frequent-words.md))
- **Klucz mapy nie może się powtarzać.** `TreeMap<liczba, słowo>` po cichu nadpisze słowa z tą samą
  liczbą.
- **Węzły-strażnicy usuwają przypadki brzegowe.** Lista nigdy nie jest pusta, więc nie ma `null`
  ani „pierwszego” i „ostatniego” elementu. ([LRU](collection/lru-cache.md))
- **Kolejność zmian wskaźników ma znaczenie.** Pole, które jeszcze czytasz (`head.next`), zmieniasz
  na końcu, albo zapisz je wcześniej w zmiennej.

## Pułapki Javy

| Pułapka | Zamiast tego |
|---|---|
| `Stack` (synchronizowany, przestarzały) | `ArrayDeque` jako stos i jako kolejka |
| `LinkedList.get(i)` to O(n) | `ArrayList`, albo iterator, który już stoi w miejscu |
| `PriorityQueue.remove(x)` to O(n) | leniwe usuwanie albo `TreeSet` z pełnym komparatorem |
| `Collections.reverse` odwraca w miejscu, O(n) | `list.reversed()` to widok w O(1) (Java 21+) |
| `new BigDecimal(0.1)` przenosi błąd `double` | `new BigDecimal("0.1")` |
| `BigDecimal.equals`: `5.0 ≠ 5.00` | `compareTo`, `min`, `max`; uwaga też na `equals` rekordów |
| suma wielu `int` się przepełnia | `long` na sumy i odległości |
| `String.split` używa wyrażeń regularnych | skan znak po znaku, gdy regex jest zakazany |
| `toLowerCase()` na całym tekście kopiuje go | `Character.toLowerCase` znak po znaku |
| kolejność iteracji `HashMap` / `Map.of` nie jest określona | `TreeMap`, gdy wynik ma być powtarzalny |
| `Arrays.binarySearch` szuka dokładnej wartości | własna pętla dla „pierwszy większy niż x” |
| `new int[26]` to też obiekt na stercie | zmienne typów prostych, np. maski bitowe |
| `Integer` dla indeksów > 127 to nowy obiekt | tablica `int[]` zamiast kolekcji, gdy liczy się wydajność |

## Ściąga: którą kolekcję wybrać

| Potrzebuję… | Kolekcja | Koszt | Uwaga |
|---|---|---|---|
| szukać po kluczu | `HashMap` / `HashSet` | O(1) | brak kolejności |
| kolejności wstawiania albo użycia | `LinkedHashMap` | O(1) | `accessOrder = true` daje LRU |
| zbioru z kolejnością i usuwaniem w O(1) | `LinkedHashSet` | O(1) | grupy w LFU |
| najbliższego klucza w dół / w górę, przedziału | `TreeMap` / `TreeSet` | O(log n) | `floor`, `ceiling`, `higher`, `lower`, `subMap` |
| zawsze najmniejszego / największego | `PriorityQueue` | O(log n), `peek` O(1) | nie ma decrease-key ani szybkiego `remove(x)` |
| stosu albo kolejki, dwóch końców | `ArrayDeque` | O(1) na obu końcach | nie `Stack`, nie `LinkedList` |
| dostępu po indeksie | `ArrayList` / tablica | O(1) | tablica typów prostych bez opakowywania |
| małego zbioru z małym alfabetem | maska bitowa / `int[26]` | O(1) | bez obiektów |
| dostępu z wielu wątków | `ConcurrentHashMap`, `ConcurrentSkipListMap` | jak wyżej | skip-lista to współbieżny odpowiednik `TreeMap` |

## Ile danych zniesie dana złożoność

Typowa ocena „na oko”: około 10⁸ prostych operacji na sekundę.

| n | Złożoność, która się zmieści | Przykład |
|---|---|---|
| ≤ 20 | O(2ⁿ), O(n · 2ⁿ) | wszystkie podzbiory |
| ≤ 500 | O(n³) | Floyd-Warshall |
| ≤ 5 000 | O(n²) | każda para |
| ≤ 10⁶ | O(n log n) | sortowanie, kopiec, `TreeMap` |
| ≤ 10⁸ | O(n) | jedno przejście |
| więcej | O(log n), O(1) na zapytanie | indeks przygotowany wcześniej |
