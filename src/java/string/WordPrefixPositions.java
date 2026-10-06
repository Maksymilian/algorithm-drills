package string;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/// Pozycje wszystkich słów zaczynających się od danego prefiksu, bez wyrażeń regularnych i bez
/// rozróżniania wielkości liter.
///
/// **Treść.** `positions("aa aaa AaC a bb", "aa")` zwraca `[0, 3, 7]`: tam
/// zaczynają się słowa `aa`, `aaa` i `AaC`.
///
/// **Pomysł.** Jedno przejście po tekście. Pozycja `i` jest początkiem słowa, jeśli znak
/// nie jest odstępem, a poprzedni znak jest odstępem albo go nie ma. Na początku każdego słowa
/// porównujemy kolejne znaki z prefiksem przez `Character.toLowerCase`. Przerywamy przy
/// pierwszej różnicy albo gdy słowo się skończy.
///
/// **Złożoność.** O(n) czasu: porównanie nigdy nie wychodzi poza bieżące słowo, więc każdy znak
/// tekstu oglądamy najwyżej dwa razy. Pamięć O(wynik). Bez dodatkowych napisów:
/// `toLowerCase()` na całym tekście zaalokowałby jego kopię.
///
/// **Dopytania.** Ten sam tekst i wiele zapytań? Budujemy raz indeks [Index]:
/// `TreeMap` ze słowa (małymi literami) na listę pozycji. Słowa z prefiksem leżą w drzewie
/// obok siebie, od `ceilingKey(prefix)` dalej, więc zapytanie kosztuje O(log W + wynik).
/// Drzewo prefiksowe (trie) daje to samo w O(|prefiks| + wynik).
public class WordPrefixPositions {

    public static List<Integer> positions(String document, String prefix) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < document.length(); i++) {
            if (isWordStart(document, i) && startsWithIgnoreCase(document, i, prefix)) {
                result.add(i);
            }
        }
        return result;
    }

    static boolean isWordStart(String s, int i) {
        return !isSeparator(s.charAt(i)) && (i == 0 || isSeparator(s.charAt(i - 1)));
    }

    static boolean startsWithIgnoreCase(String s, int from, String prefix) {
        if (from + prefix.length() > s.length()) {
            return false;
        }
        for (int k = 0; k < prefix.length(); k++) {
            char c = s.charAt(from + k);
            if (isSeparator(c) || Character.toLowerCase(c) != Character.toLowerCase(prefix.charAt(k))) {
                return false;
            }
        }
        return true;
    }

    private static int wordEnd(String s, int start) {
        int end = start;
        while (end < s.length() && !isSeparator(s.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c);
    }

    public static final class Index {
        private final TreeMap<String, List<Integer>> positionsByWord = new TreeMap<>();

        public Index(String document) {
            for (int i = 0; i < document.length(); i++) {
                if (isWordStart(document, i)) {
                    String word = document.substring(i, wordEnd(document, i)).toLowerCase(Locale.ROOT);
                    positionsByWord.computeIfAbsent(word, w -> new ArrayList<>()).add(i);
                }
            }
        }

        public List<Integer> positions(String prefix) {
            String lower = prefix.toLowerCase(Locale.ROOT);
            return positionsByWord.tailMap(lower, true).entrySet().stream()
                    .takeWhile(e -> e.getKey().startsWith(lower))
                    .flatMap(e -> e.getValue().stream())
                    .sorted()   // pozycje różnych słów przychodzą w kolejności alfabetycznej słów
                    .toList();
        }
    }

    void main() {
        String document = "aa aaa AaC a bb";
        IO.println(positions(document, "aa"));             // [0, 3, 7]
        IO.println(new Index(document).positions("AA"));   // [0, 3, 7]
    }
}
