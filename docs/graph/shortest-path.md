# Najkrótsza droga: algorytm Dijkstry

Kod: [`ShortestPath.java`](../../src/java/graph/ShortestPath.java),
testy: [`ShortestPathTest.java`](../../test/java/graph/ShortestPathTest.java).

## Treść

Mamy mapę miast i czasy przejazdu między nimi. Znajdź najkrótszy czas z jednego miasta do
każdego innego oraz samą trasę.

```
Kraków -> Warszawa bezpośrednio 300 min
Kraków -> Katowice 60, Katowice -> Warszawa 180      =>  240 min przez Katowice
```

## Założenia

| | |
|---|---|
| Graf | skierowany; wierzchołki 0..V−1; drogę dwukierunkową dodajemy jako dwie krawędzie |
| Wagi | całkowite i **nieujemne**; ujemna waga to wyjątek |
| Odległości | `long`, bo suma wielu wag `int` może się przepełnić |
| Nieosiągalne | `UNREACHABLE = Long.MAX_VALUE`, pusta trasa |
| Wynik | odległości ze źródła do wszystkich wierzchołków i trasa do dowolnego z nich |

## Rozwiązanie

Kolejka priorytetowa wierzchołków według dotychczasowej odległości. Zdejmujemy najbliższy: jego
odległość jest już ostateczna, bo przy nieujemnych wagach żadna okrężna droga nie będzie
krótsza. Potem relaksujemy jego krawędzie: jeśli przez niego do sąsiada jest bliżej, poprawiamy
`dist` i `previous` i wkładamy sąsiada do kolejki. Trasę odtwarzamy, idąc po `previous` od celu do
źródła.

## Dlaczego Dijkstra

| Algorytm | Czas | Kiedy |
|---|---|---|
| BFS | O(V + E) | Wszystkie wagi równe (np. liczba przesiadek) |
| **Dijkstra z kopcem binarnym** | **O((V + E) log V)** | **Nieujemne wagi, graf rzadki (mapy dróg). Wybrane** |
| Dijkstra na tablicy, bez kopca | O(V²) | Graf gęsty (E ≈ V²): wtedy szybszy niż z kopcem |
| Bellman-Ford | O(V · E) | Ujemne wagi; wykrywa ujemne cykle |
| Floyd-Warshall | O(V³) | Odległości między *wszystkimi* parami, mały graf |
| A* | zależy od heurystyki | Jeden cel i dobra heurystyka (np. odległość w linii prostej) |

## Dlaczego takie struktury danych

- **Listy sąsiedztwa `List<List<Edge>>`, nie macierz.** Pamięć O(V + E), a przejście po sąsiadach
  kosztuje tyle, ilu ich jest. Macierz to O(V²) pamięci nawet dla rzadkiej mapy dróg i O(V) na
  przejrzenie sąsiadów.
- **`PriorityQueue<Visit>` z leniwym usuwaniem.** `PriorityQueue` nie umie zmniejszyć klucza
  (decrease-key), więc przy poprawie odległości wkładamy nowy wpis. Stary pomijamy przy
  zdjęciu: `if (visit.distance() > dist[u]) continue`. Kopiec ma wtedy najwyżej E wpisów, a
  log E ≤ 2 log V, więc złożoność się nie zmienia.
  - Alternatywa: `TreeSet<Visit>` z prawdziwym decrease-key (`remove` + `add`, oba O(log V)).
    Komparator musi wtedy rozstrzygać remisy po numerze wierzchołka. Inaczej dwa wierzchołki z tą
    samą odległością są dla `TreeSet` „równe” i jeden znika. Łatwo o błąd, więc wybrałem kopiec.
- **`long[] dist` i `int[] previous`.** Wierzchołki to liczby 0..V−1, więc tablica zamiast
  `HashMap`: O(1) bez haszowania i bez opakowywania liczb w obiekty.
- **`record Edge` i `record Visit`.** Czytelniejsze niż `long[]{odległość, wierzchołek}`, a
  komparator to po prostu `comparingLong(Visit::distance)`.

## Dopytania

- **Tylko jeden cel?** Przerywamy po zdjęciu celu z kolejki.
- **Bardzo duży graf (mapa kraju)?** A*, Dijkstra dwukierunkowa, a w praktyce wstępne
  przetwarzanie grafu (contraction hierarchies).
- **Dlaczego Dijkstra psuje się przy ujemnych wagach?** Zdjęty wierzchołek uznajemy za ostateczny,
  a późniejsza ujemna krawędź mogłaby go jeszcze poprawić.
