package string;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/// Najmniejsza odległość między dwoma słowami w długim tekście.
///
/// **Treść.** Odległość liczymy w słowach: sąsiednie słowa są w odległości 1. W tekście
/// „a b c a d b” słowa `a` i `b` są najbliżej na pozycjach 0 i 1, w odległości 1.
///
/// **Pomysł.** Jedno przejście, pamiętamy tylko pozycję **poprzedniego trafienia** (słowa A albo B).
/// Jeśli poprzednie trafienie to drugie słowo, mamy parę i liczymy odległość. Jeśli to samo słowo,
/// pomijamy: np. w „A … B … B” drugie `B` jest dalej od `A` niż pierwsze, a parę `A`–pierwsze `B`
/// już policzyliśmy. Wcześniejszych wystąpień nie trzeba więc pamiętać.
///
/// **Złożoność.** O(n) czasu i O(1) pamięci poza samymi słowami.
///
/// - **Słowa nie ma:** zwracamy −1.
/// - **Błędne parametry:** `null` zamiast tekstu albo słowa, puste słowo i słowo ze znakiem spoza
///   liter i cyfr (`"ala ma"`, `"kot!"`) to `IllegalArgumentException`. Takie słowo nigdy by nie
///   trafiło, bo podział tekstu rozcina je na części, więc cichy wynik −1 ukrywałby błąd.
public class ClosestWords {

    private static final int NONE = Integer.MAX_VALUE;
    private static final int NOT_SEEN = -1;

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    public static int closest(String text, String a, String b) {
        Objects.requireNonNull(text, "brak tekstu");
        return closest(words(text), requireWord(a), requireWord(b));
    }

    // słowo z separatorem („ala ma”, „kot!”) nigdy nie trafi, bo podział tekstu je rozcina
    private static String requireWord(String word) {
        Objects.requireNonNull(word, "brak szukanego słowa");
        if (word.isEmpty() || SEPARATORS.matcher(word).find()) {
            throw new IllegalArgumentException("szukane słowo musi być niepuste, z samych liter i cyfr: " + word);
        }
        return normalize(word);
    }

    static int closest(String[] words, String a, String b) {
        int best = NONE;
        int previousHit = NOT_SEEN;
        for (int i = 0; i < words.length; i++) {
            if (isSearched(words[i], a, b)) {
                best = Math.min(best, distance(words, previousHit, i, a.equals(b)));
                previousHit = i;
            }
        }
        return best == NONE ? -1 : best;
    }

    private static boolean isSearched(String word, String a, String b) {
        return word.equals(a) || word.equals(b);
    }

    // dwa trafienia tym samym słowem nie tworzą pary, chyba że szukamy a == b
    private static int distance(String[] words, int previousHit, int i, boolean sameWord) {
        if (previousHit == NOT_SEEN) {
            return NONE;
        }
        boolean pair = sameWord || !words[previousHit].equals(words[i]);
        return pair ? i - previousHit : NONE;
    }

    static String[] words(String text) {
        return Arrays.stream(SEPARATORS.split(text.toLowerCase(Locale.ROOT)))
                .filter(w -> !w.isEmpty())
                .toArray(String[]::new);
    }

    private static String normalize(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    void main() {
        String text = "Ala ma kota, a kot ma Alę. Kot lubi mleko, a Ala lubi kota.";
        IO.println(closest(text, "ala", "kota"));            // 2
    }
}
