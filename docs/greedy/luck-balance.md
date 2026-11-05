# Luck Balance: przegraj wszystko, wygraj najtańsze z wymuszonych

Notatka do [`src/java/greedy/LuckBalance.java`](../../src/java/greedy/LuckBalance.java),
testy: [`test/java/greedy/LuckBalanceTest.java`](../../test/java/greedy/LuckBalanceTest.java).

**Zadanie.** Każde zawody to para `[L, T]`. Przegrana dodaje `L` do szczęścia, wygrana odejmuje `L`.
`T = 1` oznacza zawody ważne, `T = 0` nieważne. Wolno przegrać najwyżej `k` ważnych. Szukamy
największego końcowego szczęścia (może być ujemne).

## Pomysł

Zaczynamy od planu „przegraj wszystko”: szczęście to suma wszystkich `L`. Zamiana przegranej na
wygraną kosztuje `2L` (znika `+L`, pojawia się `−L`). Wygrywamy więc tylko to, co musimy:

- nieważnych nic nie zmusza, więc przegrywamy wszystkie;
- ważnych jest `m`, a przegrać wolno `k`, więc musimy wygrać `max(0, m − k)`;
- najtańsze do wygrania są te o najmniejszym `L`.

```
wynik = suma wszystkich L  −  2 · (suma max(0, m − k) najmniejszych L wśród ważnych)
```

Na przykładzie z zadania, `k = 3`:

```
ważne posortowane   1   2   5   8      m = 4, wygrywamy 4 − 3 = 1
                   [1]                  koszt 2 · 1 = 2
suma wszystkich     5 + 2 + 1 + 8 + 10 + 5 = 31
wynik               31 − 2 = 29
```

## Dlaczego to jest zachłanne

Algorytm zachłanny podejmuje lokalnie najlepszą decyzję i nigdy się z niej nie wycofuje. Tu są dwie
takie decyzje: „przegraj każde zawody, których przegrać wolno” i „jeśli trzeba coś wygrać, wygraj
najtańsze”. Nie przeglądamy `2^n` planów ani nie budujemy tablicy jak w programowaniu dynamicznym.

Poprawność daje **argument wymiany**. Weź dowolny dopuszczalny plan:

1. Jeśli wygrywa jakieś nieważne zawody, zmień je na przegrane. Limit `k` dotyczy tylko ważnych,
   więc plan dalej jest dopuszczalny, a wynik rośnie o `2L ≥ 0`.
2. Jeśli wygrywa ważne o `L = a`, a przegrywa ważne o `L = b < a`, zamień je rolami. Liczba
   przegranych ważnych się nie zmienia, a wynik rośnie o `2(a − b) > 0`.
3. Jeśli przegrywa mniej niż `min(m, k)` ważnych, przegraj jeszcze jedne: wynik rośnie o `2L ≥ 0`.

Po tych krokach plan jest dokładnie planem zachłannym i nie jest gorszy od wyjściowego. Skoro każdy
plan da się tak przerobić bez straty, plan zachłanny jest optymalny.

**Kiedy to by przestało działać.** Cały argument stoi na `L ≥ 0`: przegrana nigdy nie szkodzi.
Przy ujemnym `L` wygrana byłaby zyskiem, krok 1 i 3 by się odwróciły, a reguła „przegraj
nieważne” byłaby błędna. Dlatego ujemne `L` odrzucamy wyjątkiem.

## Założenia

| | |
|---|---|
| `k` | `k ≥ 0`; `k` większe niż liczba ważnych jest poprawne (wtedy przegrywamy wszystko) |
| Zawody | dokładnie dwa pola `[L, T]`, inaczej `IllegalArgumentException` |
| `L` | `L ≥ 0`, inaczej `IllegalArgumentException` |
| `T` | `0` albo `1`, inaczej `IllegalArgumentException` |
| Brak zawodów | poprawne, wynik `0` |
| `null` | tablica albo zawody: `NullPointerException` |
| Wynik poza `int` | `ArithmeticException`; sumy pośrednie liczymy w `long` |

## Złożoność

Sortowanie ważnych: O(n log n) czasu, O(n) pamięci. Da się zejść do średnio O(n), wybierając
`m − k` najmniejszych przez _quickselect_ zamiast pełnego sortowania, ale przy `n ≤ 100` z
ograniczeń zadania nie ma to znaczenia.

## Testy

Poza przykładami z zadania testy sprawdzają `k = 0`, `k` większe od liczby ważnych, same nieważne,
wynik ujemny, powtórzone `L`, przepełnienie w obie strony, walidację, milion zawodów oraz zgodność
z przeglądem wszystkich `2^n` planów na losowych danych do `n = 10`.
