# Trójki w ciągu geometrycznym: jedno przejście i dwie mapy

Kod: [`CountTriplets.java`](../../src/java/collection/CountTriplets.java),
testy: [`CountTripletsTest.java`](../../test/java/collection/CountTripletsTest.java).

**Zadanie (HackerRank „Count Triplets”).** Policz trójki indeksów `i < j < k`, dla których
`a[j] = a[i]·r` i `a[k] = a[j]·r`.

```
r = 2, [1, 2, 2, 4]            → 2   (indeksy 0,1,3 i 0,2,3)
r = 3, [1, 3, 9, 9, 27, 81]    → 6
r = 5, [1, 5, 5, 25, 125]      → 4
```

## Założenia

| | |
|---|---|
| `r` | co najmniej 1; mniejsze rzuca `IllegalArgumentException` |
| Elementy | dowolne `long`, także 0 i ujemne; `null` rzuca `NullPointerException` |
| Pusta lista, mniej niż 3 elementy | poprawne wejście, wynik 0 |
| Wynik | `long` (dla n = 10⁵ i `r = 1` wynik to ok. 1,7·10¹⁴, nie mieści się w `int`) |

## Rozwiązanie

Każdy element `x` traktujemy jako ostatni wyraz trójki i trzymamy dwie mapy:
`singles[v]` — ile razy widzieliśmy `v`, `pairs[v]` — ile par `(v/r, v)` kończy się na `v`.

```java
triplets += pairs[x / r];       // x domyka trójki      (tylko gdy x % r == 0)
pairs[x] += singles[x / r];     // x domyka nowe pary
singles[x]++;                   // x może zacząć trójkę
```

Kolejność kroków jest istotna: przy `r = 1` aktualizacja `singles` przed `pairs` połączyłaby
element sam ze sobą (test `ratioOneCountsCombinationsOfEqualValues`).

**Złożoność:** O(n) czasu, O(n) pamięci. Wzorzec O(n³) w teście porównuje wyniki na losowych danych.

## Pułapki

- Sprawdzamy `x % r == 0` zamiast liczyć `x / r` na ślepo: dla `x = 3, r = 2` dzielenie całkowite
  dałoby 1 i fałszywą parę `(1, 3)`. Dotyczy też liczb ujemnych (`-7 / 2 = -3`).
- Szukanie wstecz (`x / r`) zamiast w przód (`x · r`) omija przepełnienie mnożenia.
- Wynik jako `int` przepełnia się przy wielu równych wartościach i `r = 1`.
