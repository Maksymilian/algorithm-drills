# k najczęstszych słów

Kod: [`TopKFrequentWords.java`](../../src/java/collection/TopKFrequentWords.java),
testy: [`TopKFrequentWordsTest.java`](../../test/java/collection/TopKFrequentWordsTest.java).

## Treść

Z listy słów zwróć k najczęstszych, od najczęstszego. Przy równej liczbie wystąpień wcześniej
idzie słowo wcześniejsze alfabetycznie.

```
[kot, pies, kot, mysz, pies, kot, ryba, mysz], k = 2  ->  [kot, mysz]
```

## Założenia

| | |
|---|---|
| Słowa | porównywane dokładnie (`Kot` ≠ `kot`); normalizacja to osobny krok |
| Remis | alfabetycznie |
| k | ≥ 1; gdy k > liczby różnych słów, zwracamy wszystkie |
| Rozmiar | n słów, m różnych; zwykle m ≪ n i k ≪ m |

## Rozwiązanie

1. Policz wystąpienia: `counts.merge(word, 1, Integer::sum)`.
2. Przejdź po mapie z **kopcem minimalnym o rozmiarze k**. Na szczycie jest *najsłabsze* z k
   najlepszych słów. Każde słowo wkładamy, a gdy kopiec ma k + 1 elementów, zdejmujemy szczyt.
3. Zdejmij wszystko (od najsłabszego) i odwróć kolejność.

## Dlaczego taki algorytm

| Podejście | Czas | Pamięć poza mapą | Komentarz |
|---|---|---|---|
| Posortuj całą mapę | O(m log m) | O(m) | Najprostsze. Dobre, gdy k ≈ m |
| Kopiec maksymalny ze wszystkimi słowami | O(m + k log m) | O(m) | Trzyma wszystko |
| **Kopiec minimalny o rozmiarze k** | **O(m log k)** | **O(k)** | **Wybrane: najlepsze dla k ≪ m** |
| Sortowanie kubełkowe po liczbie wystąpień | O(n) | O(n) | Liczności są ≤ n. Remisy w kubełku i tak trzeba sortować |
| Quickselect | O(m) średnio | O(m) | Najszybsze asymptotycznie, ale wynik trzeba potem posortować, a najgorszy przypadek to O(m²) |

Łącznie z liczeniem: O(n + m log k).

## Dlaczego takie struktury danych

- **`HashMap<String, Integer>` do liczenia.** O(1) na słowo. Kolejność kluczy niepotrzebna, więc
  nie `TreeMap` (O(log m) na słowo). `merge` robi „wstaw 1 albo dodaj 1” w jednym wywołaniu.
- **`PriorityQueue` z komparatorem „najsłabszy pierwszy”.** To kopiec binarny w tablicy:
  `add` i `poll` w O(log k), `peek` w O(1). Komparator: mniej wystąpień jest słabsze, a przy
  remisie słabsze jest słowo dalsze w alfabecie (`reverseOrder` na kluczu).
- **Dlaczego nie `TreeMap<liczba, słowo>`?** Różne słowa mają te same liczby, więc klucze by się
  nadpisywały. Potrzebny byłby `TreeMap<Integer, List<String>>`, czyli więcej kodu za ten sam efekt.

## Dopytania

- **Nieskończony strumień, mało pamięci?** Algorytm Misra-Gries albo Count-Min Sketch z kopcem.
  Wynik jest przybliżony, a pamięć O(k).
- **Dane na wielu maszynach?** Każda liczy lokalnie, potem sumujemy liczniki (map-reduce). Nie
  wystarczy połączyć lokalnych top-k: słowo może być w każdym miejscu jedenaste, a w sumie
  pierwsze.
