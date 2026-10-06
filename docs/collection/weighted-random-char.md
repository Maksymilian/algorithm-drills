# Generator losowych znaków o zadanym rozkładzie

Kod: [`WeightedRandomChar.java`](../../src/java/collection/WeightedRandomChar.java),
testy: [`WeightedRandomCharTest.java`](../../test/java/collection/WeightedRandomCharTest.java).

## Treść

Zaimplementuj generator losowych znaków z zadanego alfabetu. Konstruktor dostaje rozkład
prawdopodobieństwa: mapę znak → liczba zmiennoprzecinkowa z przedziału (0, 1). Metoda losująca
ma ten rozkład respektować. Wolno używać tylko zwykłego `rand()`.

## Założenia

| | |
|---|---|
| Rozkład | `Map<Character, ? extends Number>`: działa i z `Float`, i z `Double` |
| Prawdopodobieństwa | każde w (0, 1]; suma 1 z tolerancją 10⁻⁵, bo `float` nie sumuje się dokładnie |
| Źródło losowości | `Random` przekazany w konstruktorze; w testach ze stałym ziarnem |
| Użycie | konstruktor raz, `next()` wiele razy, więc pracę opłaca się zrobić w konstruktorze |

## Rozwiązanie

Sumy prefiksowe i wyszukiwanie binarne. Dla `{a: 0.5, b: 0.3, c: 0.2}`:

```
0         0.5      0.8    1.0
|----a----|---b----|--c---|
cumulative = [0.5, 0.8, 1.0]
```

Losujemy jednostajnie `r` z [0, suma) i szukamy pierwszego `cumulative[i] > r`.

## Dlaczego taki algorytm

| Podejście | Konstruktor | `next()` | Komentarz |
|---|---|---|---|
| Liniowe przejście po sumach | O(k) | O(k) | W porządku dla kilku znaków |
| **Sumy prefiksowe + wyszukiwanie binarne** | **O(k log k)** | **O(log k)** | **Wybrane: proste i szybkie** |
| Tablica 100 (albo 1000) pól wypełniona znakami | O(precyzja) | O(1) | Traci dokładność (0.333 → 33 pola), pamięć rośnie z precyzją |
| Metoda aliasów Walkera | O(k) | O(1) | Najszybsze, ale trudniejsze do napisania na tablicy. Warto wspomnieć |

## Dlaczego takie struktury danych

- **Kopia do `TreeMap` w konstruktorze.** Kolejność iteracji `HashMap` i `Map.of` nie jest
  określona i bywa różna między uruchomieniami JVM. Ten sam seed dawałby wtedy różne znaki.
  `TreeMap` porządkuje znaki, więc wynik jest powtarzalny.
- **Potem `char[]` i `double[]`, a nie `List<Double>`.** Wyszukiwanie binarne po tablicy typów
  prostych to sąsiednie komórki pamięci, bez rozpakowywania obiektów.
- **Własna pętla zamiast `Arrays.binarySearch`.** `Arrays.binarySearch` szuka dokładnej wartości,
  a my potrzebujemy „pierwszy większy niż r”.
- **`Random`, nie `SecureRandom`.** Losowość nie służy bezpieczeństwu. Przy wielu wątkach:
  `ThreadLocalRandom`.

## Pułapki, o które pytają

- **`rand() % 100` w C++ jest nierówny,** gdy `RAND_MAX + 1` nie dzieli się przez 100: małe
  reszty wypadają częściej. Lepiej `rand() / (RAND_MAX + 1.0)`.
- **Zaokrąglenia.** `r` losujemy z [0, ostatnia suma), a nie z [0, 1), a wyszukiwanie nigdy nie
  wychodzi poza ostatni indeks.
- **Jak to przetestować?** Stałe ziarno i 200 000 losowań: częstość każdego znaku w granicach
  ±0,01 od zadanej. Dokładniej: test chi-kwadrat.
