# Klepsydra w tablicy 2D: stały kształt, siedem odczytów i maksimum od `MIN_VALUE`

Notatka do [`src/java/array/HourglassSum.java`](../../src/java/array/HourglassSum.java),
testy: [`test/java/array/HourglassSumTest.java`](../../test/java/array/HourglassSumTest.java).

**Zadanie (HackerRank „2D Array - DS”).** W tablicy 6 × 6 klepsydra to 7 pól ułożonych tak:

```
a b c
  d
e f g
```

Tablica 6 × 6 ma 16 klepsydr. Policz sumę każdej i zwróć największą. Wartości: `−9 ≤ arr[i][j] ≤ 9`.

## Założenia

| Pytanie | Przyjęte |
|---|---|
| Rozmiar | dowolny prostokąt h × w, co najmniej 3 × 3 (zadanie: zawsze 6 × 6) |
| Wartości ujemne | tak, dlatego maksimum startuje od `Integer.MIN_VALUE` |
| Tablica za mała, wiersze różnej długości | `IllegalArgumentException` |
| `null` zamiast tablicy | `NullPointerException` |
| `null` jako wartość pola | liczy się jako 0 (`Objects.requireNonNullElse`); `Integer::intValue` rzuciłby `NullPointerException` |
| Przepełnienie | niemożliwe: 7 · 9 = 63 |

## Rozwiązanie

Lewy górny róg klepsydry `(r, c)` przebiega `0 ≤ r ≤ h − 3`, `0 ≤ c ≤ w − 3`, czyli
`(h − 2)(w − 2)` pozycji. Dla każdej suma to siedem stałych odczytów:

```
grid[r][c]     + grid[r][c+1]   + grid[r][c+2]
               + grid[r+1][c+1]
grid[r+2][c]   + grid[r+2][c+1] + grid[r+2][c+2]
```

Czas O(h · w), pamięć O(1) poza kopią `List<List<Integer>>` → `int[][]`.

## Dlaczego tak

- **Bez sum prefiksowych.** Kształt ma 7 pól, więc suma prefiksowa 2D (4 odczyty na prostokąt, a
  klepsydra to 2 prostokąty i pole środkowe) niczego nie przyspiesza. Opłaca się dopiero dla dużego,
  zmiennego kształtu.
- **Kopia do `int[][]`.** `arr.get(r).get(c)` rozpakowuje `Integer` przy każdym odczycie i jest
  mniej czytelne. Jedna kopia na wejściu daje `grid[r][c]` i oddziela format HackerRank od algorytmu.
- **Start od `Integer.MIN_VALUE`.** Przy samych `−9` najlepsza klepsydra ma `−63`. Start od 0
  zwróciłby 0, czyli sumę, której nie ma w tablicy (test `allNegativeGridReturnsNegativeMaximumNotZero`).
- **Wyjątek zamiast `return 0`.** 0 to poprawny wynik (np. tablica zer), więc nie może znaczyć „brak
  danych”. Wywołujący nie odróżniłby błędu od odpowiedzi.

## Dopytania

- **Klepsydra k × k** (górny i dolny wiersz pełne, środek tylko kolumna środkowa): sumy prefiksowe
  wierszy dają każdy wiersz w O(1), więc klepsydra kosztuje O(k) zamiast O(k²).
- **Wiele zapytań o różne fragmenty tablicy**: suma prefiksowa 2D liczona raz, potem O(1) na
  prostokąt.
