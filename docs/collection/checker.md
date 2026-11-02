# Komparator graczy: wynik malejąco, imię rosnąco, bez odejmowania

Kod: [`Checker.java`](../../src/java/collection/Checker.java),
testy: [`CheckerTest.java`](../../test/java/collection/CheckerTest.java).

**Zadanie (HackerRank „Java Comparator”).** Napisz klasę `Checker implements Comparator<Player>`,
która sortuje graczy malejąco po wyniku, a przy równym wyniku rosnąco po imieniu. Wczytanie danych,
sortowanie i wypisanie robi zablokowany kod zadania.

```
amy 100, david 100, heraldo 50, aakansha 75, aleksa 150
→ aleksa 150, amy 100, david 100, aakansha 75, heraldo 50
```

## Założenia

| | |
|---|---|
| Wynik | dowolny `int`, także ujemny i skrajny (`Integer.MIN_VALUE`, `MAX_VALUE`) |
| Imię | małe litery; może się powtarzać; `null` odrzuca konstruktor `Player` |
| Ten sam wynik i imię | `compare` zwraca 0; kolejność wejścia zostaje, bo sortowanie obiektów jest stabilne |

## Rozwiązanie

```java
int byScoreDescending = Integer.compare(b.score(), a.score());   // zamienione argumenty = malejąco
if (byScoreDescending != 0) {
    return byScoreDescending;
}
return a.name().compareTo(b.name());                              // rosnąco
```

To samo z gotowych klocków: `Comparator.comparingInt(Player::score).reversed().thenComparing(Player::name)`.
Test `matchesComparatorChainOnRandomPlayers` sprawdza, że obie wersje sortują identycznie.

## Dlaczego nie `b.score - a.score`

Odejmowanie przepełnia się: `Integer.MAX_VALUE - (-1)` to `Integer.MIN_VALUE`, czyli zły znak, i
gracz z najwyższym wynikiem ląduje na końcu. `Integer.compare` porównuje, a nie odejmuje (test
`extremeScoresDoNotOverflow`).

Uwaga też na `.reversed()` w łańcuchu: odwraca **cały** komparator zbudowany do tego miejsca.
`comparingInt(score).thenComparing(name).reversed()` dałby imiona malejąco.

## Przegląd pierwszej wersji

Pierwsze podejście czytało pary z `args` i sortowało listy napisów:

| Problem | Skutek | Poprawka |
|---|---|---|
| Dane z `args` zamiast `System.in` | na HackerRank `args` jest puste: nic się nie wypisuje | zadanie i tak każe napisać tylko `Checker`, wejście czyta zablokowany kod |
| Pierwszy token to liczba graczy `n` | pary są przesunięte: `5 amy`, `100 david`, … | pominąć `n` albo czytać `n` linii |
| Sortowanie rosnąco | najniższy wynik pierwszy | argumenty zamienione albo `.reversed()` |
| Brak drugiego klucza | `amy` i `david` w kolejności wejścia, nie alfabetycznej | `thenComparing(name)` |
| `Integer.valueOf` w komparatorze | parsowanie przy każdym porównaniu, O(n log n) razy | parsować raz, trzymać `int` w obiekcie |
| `List<String>` jako gracz | `get(0)`, `get(1)` nic nie mówią; nieparzysta liczba tokenów → `IndexOutOfBoundsException` | `record Player(String name, int score)` |
| `requireNonNull` na `args` | elementy `args` nigdy nie są `null` | usunąć |
| Brak klasy `Checker` | kod zadania się nie kompiluje | `class Checker implements Comparator<Player>` |

Dobra część: `Collectors.joining("\n")` i jedno `println` zamiast wypisywania w pętli.

## Dopytania

- **Kontrakt `compare`:** znak musi być antysymetryczny (`sgn(compare(a, b)) == -sgn(compare(b, a))`)
  i przechodni, inaczej `TimSort` może rzucić „Comparison method violates its general contract!”.
  Test `signIsAntisymmetric` sprawdza pierwszy warunek na losowych parach.
- **Zgodność z `equals`:** `compare == 0` dla dwóch różnych obiektów jest w porządku przy sortowaniu,
  ale w `TreeSet`/`TreeMap` drugi gracz o tym samym wyniku i imieniu zostałby uznany za duplikat.
- **Imiona z wielkimi literami albo polskimi znakami:** `String.CASE_INSENSITIVE_ORDER` albo
  `Collator.getInstance(Locale.of("pl"))` zamiast `compareTo`, które porównuje kody znaków.
