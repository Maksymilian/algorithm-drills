# Największy zysk z akcji

Kod: [`StockMaxProfit.java`](../../src/java/array/StockMaxProfit.java),
testy: [`StockMaxProfitTest.java`](../../test/java/array/StockMaxProfitTest.java).

## Treść

Mamy tablicę notowań jednej spółki. Policz największy możliwy zysk: cena sprzedaży minus cena
kupna.

```
[7.20, 1.10, 5.35, 3.00, 6.15, 4.00]  ->  5.05   (kupno za 1.10 w dniu 1, sprzedaż za 6.15 w dniu 4)
```

## Założenia

| | |
|---|---|
| Transakcje | jedno kupno i jedna sprzedaż; sprzedaż później niż kupno |
| Ceny | `List<BigDecimal>` z dokładnymi kwotami dziesiętnymi, tworzone z napisów (`prices("7.20", "1.10")`) |
| Brak zysku | ceny tylko spadają, więc nie handlujemy i zwracamy 0 |
| Wynik | sam zysk albo `Trade(buyDay, sellDay, profit)` |

## Rozwiązanie

Sprzedając dnia `i`, najlepiej kupić po najniższej cenie *sprzed* dnia `i`. Idziemy więc raz od
lewej i pamiętamy najniższą cenę dotąd oraz najlepszą różnicę.

## Dlaczego jedno przejście

| Podejście | Czas | Dlaczego nie |
|---|---|---|
| Każda para dni | O(n²) | Za wolne dla notowań z wielu lat |
| `max − min` całej tablicy | O(n) | **Błędne**: maksimum może przypaść przed minimum (`[9, 8, 2, 4, 1]`) |
| Dziel i zwyciężaj | O(n log n) | Poprawne, ale niepotrzebnie złożone |
| **Jedno przejście z minimum** | **O(n), pamięć O(1)** | **Wybrane** |

To samo co algorytm Kadane'a (największa suma podtablicy) na dziennych zmianach cen: zysk z
kupna w dniu `b` i sprzedaży w dniu `s` to suma zmian od `b + 1` do `s`.

## Dlaczego `BigDecimal`

Ceny to pieniądze, a `double` nie zapisze dokładnie większości ułamków dziesiętnych:

```java
0.1 + 0.2                                              // 0.30000000000000004
new BigDecimal("0.1").add(new BigDecimal("0.2"))       // 0.3
```

| Typ ceny | Za | Przeciw |
|---|---|---|
| `double` | szybki, prosty | błędy zaokrągleń, które sumują się przy wielu transakcjach |
| `long` w groszach | szybki i dokładny | trzeba pamiętać o jednostce; nie każda giełda notuje w 2 miejscach po przecinku |
| **`BigDecimal`** | **dokładny, dowolna liczba miejsc po przecinku** | **wolniejszy, każda operacja tworzy nowy obiekt** |

Trzy pułapki, o które łatwo zahaczyć na rozmowie:

1. **Tworzenie z `double`.** `new BigDecimal(0.1)` daje
   `0.1000000000000000055511151231257827…`, bo przenosi błąd `double`. Tworzymy z napisu:
   `new BigDecimal("0.1")`, albo `BigDecimal.valueOf(0.1)`, które idzie przez `Double.toString`.
2. **`equals` uwzględnia skalę.** `new BigDecimal("5.0").equals(new BigDecimal("5.00"))` to
   `false`. Kwoty porównujemy przez `compareTo`, `min` i `max`. Dlatego w kodzie nie ma
   `Math.max`, tylko `best.max(...)`.
3. **Rekordy dziedziczą tę pułapkę.** `Trade` porównuje zysk przez `equals`, więc w teście
   oczekiwany zysk musi mieć tę samą skalę co ceny: `new Trade(1, 4, new BigDecimal("5.05"))`.
   Same kwoty test porównuje pomocniczą asercją przez `compareTo`.

## Dlaczego takie struktury danych

- **`List<BigDecimal>` na wejściu.** `BigDecimal` to obiekt, więc tablica typów prostych odpada.
  Kod czyta ceny przez `get(i)`, więc lista powinna mieć szybki dostęp po indeksie (`List.of`,
  `ArrayList`). Na `LinkedList` każde `get(i)` kosztowałoby O(n).
- **Dwie zmienne: najniższa cena i najlepszy zysk.** Innych kolekcji nie trzeba.
- **`record Trade`.** Niezmienny wynik z trzech pól. `equals`, `hashCode` i `toString` dostajemy
  za darmo, więc test porównuje cały wynik jedną asercją.

## Dopytania

| Wariant | Rozwiązanie |
|---|---|
| Dowolnie wiele transakcji | Suma dodatnich różnic między kolejnymi dniami, O(n) (`maxProfitManyTrades`) |
| Najwyżej k transakcji | DP: `zysk[t][dzień]`, O(n · k) |
| Opłata za transakcję albo dzień przerwy po sprzedaży | DP z dwoma stanami: „mam akcję” i „nie mam” |
| Transakcja obowiązkowa | Najlepszy zysk startuje od różnicy pierwszych dwóch dni, wynik może być ujemny |
