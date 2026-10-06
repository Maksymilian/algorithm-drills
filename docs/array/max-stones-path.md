# Kamienie na mapie: najlepsza droga na północ i wschód

Kod: [`MaxStonesPath.java`](../../src/java/array/MaxStonesPath.java),
testy: [`MaxStonesPathTest.java`](../../test/java/array/MaxStonesPathTest.java).

## Treść

Siatka imituje mapę. Na każdym polu ukryta jest pewna liczba kamieni. Idziemy z punktu A do
punktu B (oba są stałe), tylko na północ albo na wschód. Ile najwięcej kamieni można zebrać?

## Założenia

| | |
|---|---|
| Mapa | `grid[wiersz][kolumna]`, wiersz 0 to północ, kolumna 0 to zachód |
| Ruch | o jedno pole: na północ (wiersz − 1) albo na wschód (kolumna + 1) |
| A i B | B leży na północny wschód od A; w przeciwnym razie drogi nie ma i rzucamy wyjątek |
| Kamienie | liczby nieujemne; zbieramy też z pól A i B |
| Wynik | największa suma oraz jedna z najlepszych dróg, np. `"NNEE"` |

## Rozwiązanie

Na pole `(r, c)` można wejść tylko z południa `(r + 1, c)` albo z zachodu `(r, c − 1)`. Najlepszy
wynik dla pola to więc jego kamienie plus lepszy z wyników tych dwóch pól:

```
best[r][c] = grid[r][c] + max(best[r + 1][c], best[r][c − 1])
```

Liczymy wiersze od A w stronę B (z południa na północ), a w wierszu kolumny z zachodu na wschód.
Obaj sąsiedzi są wtedy już policzeni. Wynik leży w polu B.

```
mapa                  best (A = lewy dół, B = prawy góra)
0 0 0 9               1  6  6 15   <- wynik
0 5 0 0               1  6  6  6
1 0 0 0               1  1  1  1
```

## Dlaczego programowanie dynamiczne

| Podejście | Czas | Dlaczego nie |
|---|---|---|
| Wszystkie drogi (rekurencja) | O(C(h + w, h)), wykładniczo | Dla mapy 20 × 20 to około 1,4 · 10¹¹ dróg |
| Zachłannie (zawsze bogatszy sąsiad) | O(h + w) | Daje zły wynik, bo nie wolno się cofać. Kontrprzykład: test `greedyChoiceIsWrong` |
| Rekurencja z zapamiętywaniem | O(h · w) | Poprawna, ale głębokość rekurencji to h + w, więc przy dużej mapie grozi `StackOverflowError` |
| **DP iteracyjne, jeden wiersz** | **O(h · w), pamięć O(w)** | **Wybrane** |

Dlaczego DP w ogóle działa: najlepsza droga do B przechodzi przez pole na południe albo na zachód
od B, a jej początek musi być najlepszą drogą do tego pola (optymalna podstruktura). Drogi do
różnych pól wielokrotnie dzielą początki (nakładające się podproblemy). Formalnie to najdłuższa
ścieżka w grafie acyklicznym (DAG), a kolejność wierszy i kolumn jest jego sortowaniem
topologicznym.

## Dlaczego takie struktury danych

- **`int[]` na jeden wiersz.** Do policzenia wiersza `r` potrzebny jest tylko wiersz `r + 1`, więc
  pamięć spada z O(h · w) do O(w). Przed nadpisaniem `best[i]` trzyma jeszcze wynik pola na
  południe, a `best[i − 1]` już wynik pola na zachód.
- **`int[][]` tylko w `bestPath`.** Żeby odtworzyć drogę, cofamy się od B do A i musimy znać
  wyniki wszystkich pól.
- **Tablice prymitywów, nie `List<List<Integer>>`.** Bez opakowywania liczb w obiekty (boxing),
  kilka razy mniej pamięci, a odczyt to zwykłe przesunięcie w pamięci.

## Dopytania

- **Bardzo duże sumy?** `long` zamiast `int`.
- **Ruch także na północny wschód?** Dochodzi trzeci składnik: `best[r + 1][c − 1]`.
- **Ruch w czterech kierunkach?** Graf przestaje być acykliczny. Najdłuższa droga prosta jest
  wtedy NP-trudna, a DP nie działa.
- **Najmniej kamieni (najtańsza droga)?** `min` zamiast `max`.
