package string;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/// Najdłuższe słowa ze słownika, które da się ułożyć z liter danego napisu.
///
/// **Treść.** Dla liter `"toestp"` i słów `{toes, toe, abc, stop, baseball}`
/// odpowiedź to `stop` i `toes`. Każdej litery można użyć tyle razy, ile razy występuje
/// w napisie.
///
/// **Pomysł.** Liczymy litery napisu w tablicy `int[26]`. Słowo da się ułożyć, jeśli
/// żadnej litery nie potrzebuje więcej razy, niż jest dostępna. Przechodzimy po słowach i
/// trzymamy listę najdłuższych dotąd pasujących. Bez sprawdzania liter pomijamy słowa krótsze od
/// obecnego rekordu i słowa dłuższe niż cały napis: każda litera pokrywa najwyżej jeden znak słowa.
///
/// **Złożoność.** O(|litery| + Σ|słowo|) czasu, O(1) dodatkowej pamięci (dwie tablice po 26
/// liczb). Sortowanie liter i porównywanie z posortowanym słowem też działa, ale kosztuje
/// O(k log k) na słowo i nie radzi sobie z „podzbiorem” liter.
///
/// **Dopytania.** Słownik jest ogromny, a zapytań dużo? Grupujemy słowa według długości i
/// sprawdzamy od najdłuższych: pierwsza długość z trafieniem kończy szukanie. Litery spoza a–z?
/// Zamiast `int[26]` używamy `Map<Character, Integer>`.
public class LongestWordsFromLetters {

    public static List<String> longestWords(String letters, Collection<String> words) {
        int[] available = countLetters(letters);
        List<String> best = new ArrayList<>();
        for (String word : words) {
            if (isWorthChecking(word, best, letters.length()) && canBuild(word, available)) {
                addCandidate(best, word);
            }
        }
        return best;
    }

    private static boolean isWorthChecking(String word, List<String> best, int letterCount) {
        int bestLength = best.isEmpty() ? 1 : best.get(0).length();   // puste słowo się nie liczy
        return word.length() >= bestLength && word.length() <= letterCount;
    }

    private static void addCandidate(List<String> best, String word) {
        if (!best.isEmpty() && word.length() > best.get(0).length()) {
            best.clear();
        }
        best.add(word);
    }

    private static int[] countLetters(String letters) {
        int[] available = new int[26];
        for (int i = 0; i < letters.length(); i++) {
            int index = indexOf(letters.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("tylko litery a-z: " + letters);
            }
            available[index]++;
        }
        return available;
    }

    static boolean canBuild(String word, int[] available) {
        int[] needed = new int[26];
        for (int i = 0; i < word.length(); i++) {
            int index = indexOf(word.charAt(i));
            if (index < 0 || ++needed[index] > available[index]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(char c) {
        char lower = Character.toLowerCase(c);
        return lower >= 'a' && lower <= 'z' ? lower - 'a' : -1;
    }

    void main() {
        List<String> words = List.of("toes", "toe", "abc", "stop", "baseball");
        IO.println(longestWords("toestp", words));   // [toes, stop]
        IO.println(longestWords("aabbcc", Set.of("abc", "aabb", "abcd")));   // [aabb]
    }
}
