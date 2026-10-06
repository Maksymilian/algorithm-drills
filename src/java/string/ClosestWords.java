package string;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Najmniejsza odległość między dwoma słowami w długim tekście.
///
/// **Treść.** Odległość liczymy w słowach: sąsiednie słowa są w odległości 1. W tekście
/// „a b c a d b” słowa `a` i `b` są najbliżej na pozycjach 0 i 1, w odległości 1.
///
/// **Pomysł.** Jedno przejście. Pamiętamy ostatnią pozycję słowa A i ostatnią pozycję słowa B.
/// Przy każdym trafieniu porównujemy się z ostatnim wystąpieniem drugiego słowa. Dalszych wystąpień
/// nie trzeba sprawdzać, bo ostatnie jest zawsze najbliżej.
///
/// **Złożoność.** O(n) czasu i O(1) pamięci poza samymi słowami.
///
/// **Dopytania.**
///
/// - **Ten sam tekst, wiele zapytań:** raz budujemy [Index], czyli mapę ze słowa na
///    posortowaną listę pozycji. Zapytanie to scalanie dwóch posortowanych list dwoma wskaźnikami,
///    O(|A| + |B|). Wyniki dla par można trzymać w cache.
///
/// - **A == B:** najmniejsza odległość między dwoma kolejnymi wystąpieniami.
///
/// - **Słowa nie ma:** zwracamy −1.
public class ClosestWords {

    private static final int NONE = Integer.MAX_VALUE;

    public static int closest(String text, String a, String b) {
        return closest(words(text), normalize(a), normalize(b));
    }

    static int closest(String[] words, String a, String b) {
        int lastA = -1, lastB = -1, best = NONE;
        for (int i = 0; i < words.length; i++) {
            boolean isA = words[i].equals(a), isB = words[i].equals(b);
            best = Math.min(best, Math.min(gap(isA, i, lastB), gap(isB, i, lastA)));
            lastA = isA ? i : lastA;
            lastB = isB ? i : lastB;
        }
        return best == NONE ? -1 : best;
    }

    private static int gap(boolean hit, int i, int lastOther) {
        return hit && lastOther >= 0 ? i - lastOther : NONE;
    }

    public static final class Index {
        private final Map<String, List<Integer>> positions = new HashMap<>();

        public Index(String text) {
            String[] words = words(text);
            for (int i = 0; i < words.length; i++) {
                positions.computeIfAbsent(words[i], w -> new ArrayList<>()).add(i);   // rosnąco
            }
        }

        public int closest(String a, String b) {
            List<Integer> pa = positionsOf(a), pb = positionsOf(b);
            int best = pa == pb ? closestRepeat(pa) : closestMerge(pa, pb);   // to samo słowo?
            return best == NONE ? -1 : best;
        }

        private List<Integer> positionsOf(String word) {
            return positions.getOrDefault(normalize(word), List.of());
        }
    }

    private static int closestRepeat(List<Integer> p) {
        int best = NONE;
        for (int i = 1; i < p.size(); i++) {
            best = Math.min(best, p.get(i) - p.get(i - 1));
        }
        return best;
    }

    private static int closestMerge(List<Integer> pa, List<Integer> pb) {
        int best = NONE, i = 0, j = 0;
        while (i < pa.size() && j < pb.size()) {
            int x = pa.get(i), y = pb.get(j);
            best = Math.min(best, Math.abs(x - y));
            i += x < y ? 1 : 0;
            j += x < y ? 0 : 1;
        }
        return best;
    }

    static String[] words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isEmpty())
                .toArray(String[]::new);
    }

    private static String normalize(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    void main() {
        String text = "Ala ma kota, a kot ma Alę. Kot lubi mleko, a Ala lubi kota.";
        IO.println(closest(text, "ala", "kota"));            // 2
        IO.println(new Index(text).closest("kot", "lubi"));   // 1
    }
}
