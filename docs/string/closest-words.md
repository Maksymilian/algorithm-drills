# Najmniejsza odległość między dwoma słowami w tekście

Kod: [`ClosestWords.java`](../../src/java/string/ClosestWords.java),
testy: [`ClosestWordsTest.java`](../../test/java/string/ClosestWordsTest.java).

## Treść

W długim tekście, dla dwóch słów, znajdź najmniejszą odległość między nimi.

```
"a b c a d b", a, b  ->  1    (pozycje 0 i 1)
```

## Założenia

| | |
|---|---|
| Odległość | w słowach: różnica indeksów, sąsiednie słowa to 1 |
| Słowa | wielkość liter i interpunkcja bez znaczenia (`"Ala,"` to `ala`) |
| a == b | odległość między dwoma kolejnymi wystąpieniami |
| Brak słowa | −1 |
| `null` jako tekst albo słowo | `NullPointerException` (`Objects.requireNonNull`) |
| Puste słowo, słowo ze znakiem spoza liter i cyfr (`"ala ma"`, `"kot!"`) | `IllegalArgumentException`: takie słowo nigdy nie trafi, bo podział tekstu je rozcina, a −1 ukryłoby błąd wywołującego |
| Zapytania | jedno |

## Rozwiązanie

Jedno przejście, pamiętamy tylko pozycję **poprzedniego trafienia**, czyli ostatniego wystąpienia
A albo B. Przy każdym trafieniu:

- poprzednie trafienie to **drugie** słowo → para, liczymy odległość;
- poprzednie trafienie to **to samo** słowo → pomijamy.

Dlaczego pominięcie jest bezpieczne: w „A … B₁ … B₂” najbliższe `A` dla `B₂` leży przed `B₁`, więc
para `A`–`B₁` jest krótsza i została już policzona. Wyjątek: `a == b`, wtedy każde dwa kolejne
trafienia są parą.

## Dlaczego taki algorytm

| Podejście | Czas | Dlaczego |
|---|---|---|
| Wszystkie pary pozycji A × B | O(\|A\| · \|B\|) | Dla częstych słów („i”, „w”) to miliony par |
| **Jedno przejście, ostatnie pozycje** | **O(n), pamięć O(1)** | **Tekst czytamy raz, nic nie zapisujemy** |

## Dlaczego takie struktury danych

- **`String[]` słów.** Tekst tokenizujemy raz. Porównanie `equals` jest szybkie, bo różne słowa
  zwykle różnią się już pierwszym znakiem albo długością.

## Dopytania

- **Wiele zapytań do tego samego tekstu?** Każde zapytanie to znowu O(n). Opłaca się raz zbudować
  mapę słowo → posortowana lista pozycji i scalać dwie listy, O(|A| + |B|) na zapytanie.
- **Tekst ciągle przyrasta (strumień)?** Jedno przejście działa online, nic nie trzeba budować.
- **Odległość w znakach, nie w słowach?** Zapisujemy pozycje znaków zamiast indeksów słów.
