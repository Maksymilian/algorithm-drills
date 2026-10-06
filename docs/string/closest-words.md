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
| Zapytania | jedno albo wiele do tego samego tekstu (dwie wersje) |

## Rozwiązanie

**Jedno zapytanie:** jedno przejście, pamiętamy ostatnią pozycję słowa A i ostatnią pozycję B.
Przy każdym trafieniu porównujemy się z ostatnim wystąpieniem drugiego słowa. Wcześniejszych nie
trzeba sprawdzać, bo ostatnie jest najbliżej.

**Wiele zapytań:** raz budujemy `Index`, czyli mapę słowo → posortowana lista pozycji. Zapytanie
scala dwie listy dwoma wskaźnikami i zawsze przesuwa mniejszą pozycję.

## Dlaczego taki algorytm

| Podejście | Czas | Dlaczego |
|---|---|---|
| Wszystkie pary pozycji A × B | O(\|A\| · \|B\|) | Dla częstych słów („i”, „w”) to miliony par |
| **Jedno przejście, ostatnie pozycje** | **O(n), pamięć O(1)** | **Najlepsze dla jednego zapytania** |
| **Indeks + scalanie dwóch list** | **O(\|A\| + \|B\|) na zapytanie** | **Najlepsze dla wielu zapytań: tekst czytamy raz** |
| Indeks + wyszukiwanie binarne | O(\|A\| · log \|B\|) | Lepsze od scalania, gdy jedno słowo jest rzadkie, a drugie bardzo częste |

## Dlaczego takie struktury danych

- **`HashMap<String, List<Integer>>`.** Lista pozycji słowa w O(1). Kolejność kluczy jest tu
  niepotrzebna, więc `TreeMap` byłby tylko wolniejszy (O(log W)).
- **`ArrayList<Integer>` na pozycje.** Pozycje dopisujemy w kolejności tekstu, więc lista jest
  posortowana bez sortowania. `get(i)` w O(1), co jest potrzebne przy scalaniu i wyszukiwaniu
  binarnym. `LinkedList` miałby `get(i)` w O(n) i osobny obiekt na każdy element.
- **`String[]` słów.** Tekst tokenizujemy raz. Porównanie `equals` jest szybkie, bo różne słowa
  zwykle różnią się już pierwszym znakiem albo długością.

## Dopytania

- **Te same pary pytane wielokrotnie?** Cache `HashMap<para, wynik>`.
- **Tekst ciągle przyrasta (strumień)?** Wersja z ostatnimi pozycjami działa online, bez indeksu.
- **Odległość w znakach, nie w słowach?** Zapisujemy pozycje znaków zamiast indeksów słów.
