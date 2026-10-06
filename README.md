# algorithm-drills

Ćwiczenia z algorytmów w Javie 25: każde ma kod, testy i notatkę wyjaśniającą wybory. Notatki do
zadań rekrutacyjnych mają tę samą budowę: treść, tabelę założeń, rozwiązanie, dlaczego ten algorytm,
dlaczego te kolekcje i dopytania.

**Zacznij od [ściągi](docs/cheatsheet.md)**: jak przedstawić rozwiązanie w 30 minut, jak rozpoznać
wzorzec po treści, wnioski ze wszystkich ćwiczeń, pułapki Javy i którą kolekcję wybrać.

| Co | Gdzie |
|---|---|
| Kod | `src/java/<pakiet>/` |
| Testy | `test/java/<pakiet>/`, jeden test na klasę, w tym samym pakiecie |
| Notatki | `docs/<pakiet>/`, ten sam układ co kod |
| Zasady (klamry po `if`, metody do 10 linii, `void main()`, `IO.println`, dokumentacja po polsku, Javadoc w Markdown tylko nad `public class`, testy bez komentarzy) | [`CLAUDE.md`](CLAUDE.md) |

```sh
export JAVA_HOME=~/.sdkman/candidates/java/25.0.1-tem
mvn test                                    # wszystkie testy
mvn test -Dtest='array.*Test'               # jeden pakiet
mvn -q compile && java -cp target/classes graph.ShortestPath   # demo: większość klas ma main
```

## array

- [Left Rotation: jedna mapa indeksów i dlaczego najmniej zapisów przegrywa](docs/array/left-rotation.md)
- [Minimum Swaps: odpowiedź to `n − cykle`, a żadne sortowanie jej nie policzy](docs/array/minimum-swaps.md)
- [Cykl w tablicy „indeks następnego”: rho i dlaczego stała pamięć jest też szybsza](docs/array/next-index-cycle.md)
- [Kamienie na mapie: DP, droga tylko na północ i wschód](docs/array/max-stones-path.md)
- [Śnieg między wzgórzami / woda po deszczu: tablice, dwa wskaźniki i stos na `ArrayDeque`](docs/array/trapping-water.md)
- [Największy zysk z akcji: minimum dotąd, ceny jako `BigDecimal`](docs/array/stock-max-profit.md)
- [Maksimum w przesuwanym oknie: kolejka monotoniczna na `ArrayDeque`](docs/array/sliding-window-maximum.md)
- Bez notatek: [`SecondLowestInArrayMain`](src/java/array/SecondLowestInArrayMain.java),
  [`MedianWithoutMerging`](src/java/array/MedianWithoutMerging.java) (mediana dwóch posortowanych tablic)

## string

- [Najdłuższe słowa ułożone z liter: licznik `int[26]`](docs/string/longest-words-from-letters.md)
- [Pozycje słów z prefiksem, bez regex: jeden skan i indeks na `TreeMap`](docs/string/word-prefix-positions.md)
- [Najbliższe dwa słowa w tekście: jedno przejście i scalanie list pozycji](docs/string/closest-words.md)
- [Pierwsza unikalna litera bez alokacji: dwie maski bitowe](docs/string/first-unique-letter.md)

## collection

- [Pamięć podręczna LRU: `HashMap` i własna lista dwukierunkowa](docs/collection/lru-cache.md)
- [k najczęstszych słów: `HashMap` i kopiec o rozmiarze k](docs/collection/top-k-frequent-words.md)
- [Ile sal na spotkania: `PriorityQueue` i zamiatanie na `TreeMap`](docs/collection/meeting-rooms.md)
- [Klucz → wartość z historią: `TreeMap.floorEntry`](docs/collection/time-map.md)
- [Losowy znak o zadanym rozkładzie: sumy prefiksowe i wyszukiwanie binarne](docs/collection/weighted-random-char.md)

## graph

- [Najkrótsza droga: Dijkstra na `PriorityQueue`](docs/graph/shortest-path.md)
- Bez notatek: [`FindACycleInDirectionalGraph`](src/java/graph/FindACycleInDirectionalGraph.java)

## greedy

- [Greedy Florist: mnożniki są wymuszone, więc wybieramy tylko parowanie](docs/greedy/greedy-florist.md)
- [Najmniejsza niesprawiedliwość: posortuj, potem przesuwaj okno `k`](docs/greedy/max-min.md)
- Windy, od najmniejszej wersji do pełnej symulacji. Dyspozytor i zamiatanie LOOK to wybory
  zachłanne, podejmowane krok po kroku:
  - [Dwie windy: wersja najmniejsza, jeden posortowany zbiór i „kto dojedzie pierwszy”](docs/greedy/simple-elevators.md)
  - [Dwie windy: wersja na rozmowę, dwa posortowane zbiory i koszt zależny od kierunku](docs/greedy/elevator-interview.md)
  - [Dwie windy, dwadzieścia pięter: przechowywanie żądań, wybór windy, kolejność przystanków, pomiary](docs/greedy/two-elevators.md)

## dynamic

- [Max Subset Sum: dlaczego to programowanie dynamiczne](docs/dynamic/max-subset-sum.md)
- [Abbreviation: DP i na co idzie jego czas](docs/dynamic/abbreviation.md)
- [Candies: dwa kierunki złożone w jedno przejście](docs/dynamic/candies.md)

## recursion

- [Fibonacci: rekurencja i cena zapisania jej dosłownie](docs/recursion/fibonacci.md)

## spatial

- [Punkty w kole: liczenie miliarda punktów bez czytania ich](docs/spatial/points-in-a-circle.md)

## jdk

- [`Gatherers.mapConcurrent` i semafor, który dzieli po równo](docs/jdk/map-concurrent-and-equal-share-semaphore.md)
- [Trzy blokady: `ReentrantLock`, `StampedLock` i jedna zbudowana z `VarHandle`](docs/jdk/locks-reentrant-stamped-and-varhandle.md)
- [Listy typu prostego `int`: fastutil, Eclipse Collections i zwykłe `int[]`, zmierzone](docs/jdk/primitive-int-lists.md)
- Bez notatek: [`TeeingCollectorExample`](src/java/jdk/TeeingCollectorExample.java) (`Collectors.teeing`)

## Narzędzia

- [Obserwowanie testów i benchmarków przez `taskset`, `perf`, `jstack` i `jcmd`](scripts/perf/README.md)
