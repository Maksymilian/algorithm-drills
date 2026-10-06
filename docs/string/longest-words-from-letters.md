# Najdłuższe słowa ułożone z liter

Kod: [`LongestWordsFromLetters.java`](../../src/java/string/LongestWordsFromLetters.java),
testy: [`LongestWordsFromLettersTest.java`](../../test/java/string/LongestWordsFromLettersTest.java).

## Treść

Dany jest napis, np. `"toestp"`, i zbiór słów `{toes, toe, abc, stop, baseball}`. Znajdź
najdłuższe słowa, które da się ułożyć z liter napisu. Odpowiedź: `stop` i `toes`.

## Założenia

| | |
|---|---|
| Alfabet | litery a–z; wielkość liter nie ma znaczenia |
| Litery | każdej można użyć tyle razy, ile razy występuje w napisie (`"abc"` nie da `"aab"`) |
| Wynik | wszystkie słowa o największej długości, w kolejności ze słownika |
| Brzegi | puste słowa pomijamy; nic nie pasuje → pusta lista |

## Rozwiązanie

1. Liczymy litery napisu w tablicy `available[26]`.
2. Dla każdego słowa liczymy jego litery w `needed[26]`. Przerywamy, gdy któraś przekroczy
   `available`.
3. Trzymamy listę najdłuższych dotąd pasujących słów. Słowo dłuższe od rekordu czyści listę.
   Słowo krótsze pomijamy od razu, bez sprawdzania liter. Tak samo słowo dłuższe niż cały napis:
   każda litera pokrywa najwyżej jeden znak, więc `"baseball"` (8) z `"toestp"` (6) odpada bez
   liczenia.

## Dlaczego zliczanie liter

| Podejście | Czas | Dlaczego nie |
|---|---|---|
| Wszystkie permutacje podzbiorów liter, sprawdzane w `HashSet` słów | O(n! · …) | Dla 10 liter to miliony napisów |
| Posortować litery napisu i słowa, porównać | O(k log k) na słowo | Działa tylko dla słów z *wszystkich* liter; „podzbiór” i tak wymaga scalania |
| **Licznik liter `int[26]`** | **O(\|napis\| + Σ\|słowo\|)** | **Wybrane: każdą literę oglądamy raz** |
| Trie słownika + DFS po licznikach | O(rozmiar trie) | Lepsze, gdy słownik jest ogromny, a zapytań dużo; więcej kodu |

## Dlaczego takie struktury danych

- **`int[26]` zamiast `HashMap<Character, Integer>`.** Litera to od razu indeks (`c − 'a'`), bez
  liczenia hasha i bez opakowywania liczb w obiekty. 26 liczb mieści się w jednej-dwóch liniach
  pamięci podręcznej procesora. `HashMap` wybieramy dopiero wtedy, gdy alfabet jest nieznany
  (np. pełny Unicode).
- **`ArrayList` na wynik.** Dopisywanie w O(1). `clear()` przy nowym rekordzie nie zwalnia tablicy,
  tylko zeruje rozmiar.
- **Wejście jako `Collection<String>`.** Działa i z `List`, i z `Set`, więc kolejność wyniku
  zależy od kolekcji, którą przekaże wywołujący.

## Dopytania

- **Wiele zapytań do tego samego słownika?** Grupujemy słowa według długości
  (`TreeMap<Integer, List<String>>` malejąco) i sprawdzamy od najdłuższych. Pierwsza długość z
  trafieniem kończy szukanie.
- **Ułożyć jak najwięcej słów naraz?** To już problem plecakowy / przeszukiwanie z nawrotami.
