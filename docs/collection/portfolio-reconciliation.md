# Uzgodnienie portfela: stan początkowy + transakcje = stan raportowany

Kod: [`PortfolioReconciliation.java`](../../src/java/collection/PortfolioReconciliation.java),
testy: [`PortfolioReconciliationTest.java`](../../test/java/collection/PortfolioReconciliationTest.java).

**Zadanie.** Mamy stan początkowy `p0`, listę transakcji (`BY` kupno, `SL` sprzedaż) i stan
raportowany `p1`. Zwróć pozycje, których saldo się nie zgadza, w formacie `"SYMBOL różnica"`
(różnica = raportowane − oczekiwane).

```
p0 = [AAB 10, GOOG 30, Cash 1000]
t  = [AAB BY 3 200, GOOG SL 10 20, AAB BY 20 210]
p1 = [AAB 20, GOOG 40, Cash 3000]

oczekiwane: AAB 10+3+20 = 33, GOOG 30−10 = 20, Cash 1000−600+200−4200 = −3600
wynik:      [AAB -13, GOOG 20, Cash 6600]
```

## Założenia

| | |
|---|---|
| Pozycja | `"SYMBOL ilość"`, ilość to dowolny `long` (także ujemna) |
| Transakcja | `"SYMBOL BY/SL ilość cena"`, ilość > 0, cena ≥ 0; inaczej `IllegalArgumentException` |
| Gotówka | symbol `Cash`; transakcja na `Cash` rzuca `IllegalArgumentException` |
| Brak symbolu po którejś stronie | liczy się jako 0 |
| Powtórzony symbol w `p0` albo `p1` | `IllegalArgumentException` |
| Puste listy | poprawne wejście, wynik pusty |
| `null` jako lista albo element | `NullPointerException` |
| Przepełnienie | `ArithmeticException` |

## Rozwiązanie

Każda transakcja to dwa księgowania w jednej mapie sald:

```java
balances.merge(symbol, ±quantity, Math::addExact);          // BY +, SL −
balances.merge("Cash", ∓quantity * price, Math::addExact);  // BY −, SL +
```

Na koniec przechodzimy po **sumie** symboli z mapy oczekiwanej i raportowanej
(`LinkedHashSet`, żeby wynik miał stabilną kolejność) i zgłaszamy niezerowe różnice.

**Złożoność:** O(n + t + m) czasu i pamięci. Test losowy porównuje wynik z niezależną symulacją.

## Pułapki

- Porównanie tylko po kluczach `p1` gubi pozycje, które zniknęły z raportu; tylko po kluczach `p0`
  gubi nowe. Stąd suma zbiorów.
- Pozycja wyzerowana sprzedażą i nieobecna w `p1` jest poprawna (0 = 0), nie zgłaszamy jej.
- `ilość·cena` łatwo przepełnia `int`; używamy `long` i `Math.multiplyExact`.
