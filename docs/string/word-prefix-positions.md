# Pozycje słów zaczynających się od prefiksu

Kod: [`WordPrefixPositions.java`](../../src/java/string/WordPrefixPositions.java),
testy: [`WordPrefixPositionsTest.java`](../../test/java/string/WordPrefixPositionsTest.java).

## Treść

Zaimplementuj funkcję, która zwraca pozycje wszystkich słów zaczynających się od danego prefiksu,
bez rozróżniania wielkości liter i bez wyrażeń regularnych.

```
document = "aa aaa AaC a bb", prefix = "aa"  ->  [0, 3, 7]
```

## Założenia

| | |
|---|---|
| Słowo | ciąg znaków bez białych znaków; separatorem jest każdy znak, dla którego `Character.isWhitespace` (spacja, tabulator, nowa linia) |
| Pozycja | indeks pierwszego znaku słowa w dokumencie |
| Wielkość liter | bez znaczenia, porównujemy znak po znaku przez `Character.toLowerCase` |
| Ograniczenia | bez regex, więc bez `split`, `matches` i `Pattern` |
| Pusty prefiks | pasuje do każdego słowa |

## Rozwiązanie

Jedno przejście po dokumencie:

1. Pozycja `i` jest początkiem słowa, jeśli znak nie jest odstępem, a poprzedni znak jest
   odstępem albo go nie ma.
2. Na początku słowa porównujemy kolejne znaki z prefiksem. Przerywamy przy pierwszej różnicy albo
   gdy słowo się skończy.

## Dlaczego jedno przejście

| Podejście | Dlaczego nie |
|---|---|
| `document.split(" ")` | `split` używa wyrażeń regularnych (zakazane), gubi pozycje i alokuje tablicę napisów |
| `document.toLowerCase().indexOf(prefix)` w pętli | Kopiuje cały dokument i znajduje też prefiks w środku słowa (`"baa"`) |
| **Skan znak po znaku** | **O(n), bez kopiowania tekstu. Wybrane** |

Dlaczego O(n): porównanie nigdy nie wychodzi poza bieżące słowo, więc każdy znak oglądamy
najwyżej dwa razy (raz przy szukaniu początku, raz przy porównaniu).

## Dlaczego takie struktury danych

- **`ArrayList<Integer>` na wynik.** Pozycje dopisujemy rosnąco, więc wynik jest od razu
  posortowany.
- **`Index`: `TreeMap<String, List<Integer>>` na wiele zapytań.** Klucze (słowa małymi literami) są
  posortowane, więc wszystkie słowa z danym prefiksem leżą w drzewie obok siebie. `tailMap(prefix)`
  zaczyna od pierwszego z nich, a pętla kończy się na pierwszym słowie bez prefiksu. Zapytanie
  kosztuje O(log W + wynik), a nie O(n).
  - `HashMap` tu nie zadziała: nie ma kolejności, więc trzeba by przejrzeć wszystkie słowa.
  - Drzewo prefiksowe (trie) daje O(|prefiks| + wynik), ale to kilkadziesiąt linii własnego
    kodu. `TreeMap` daje prawie to samo w dziesięciu linijkach.

## Dopytania

- **Polskie znaki?** `Character.toLowerCase` działa dla Unicode. Dla tureckiego „İ” porównanie
  znak po znaku nie wystarcza, potrzebny jest `Collator`.
- **Dokument nie mieści się w pamięci?** Czytamy strumieniem. Stan to tylko „czy poprzedni znak był
  odstępem” i liczba dopasowanych znaków prefiksu.
