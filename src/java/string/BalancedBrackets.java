package string;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

/// HackerRank „Balanced Brackets”: czy każdy nawias w napisie ma parę tego samego typu, a nawiasy
/// wewnątrz każdej pary też są zrównoważone. Notatka: `docs/string/balanced-brackets.md`.
///
/// **Pomysł.** Nawias zamykający musi pasować do **ostatniego jeszcze niezamkniętego** otwierającego,
/// a nie do znaku w lustrzanym miejscu napisu. „Ostatni otwarty” to stos (LIFO):
///
/// 1. otwierający: połóż na stos,
/// 1. zamykający: zdejmij ze stosu; pusty stos albo inny typ oznacza `NO`,
/// 1. na końcu stos musi być pusty, inaczej coś zostało otwarte.
///
/// Porównanie pierwszego znaku z ostatnim działa tylko dla par zagnieżdżonych jedna w drugiej
/// (`{[()]}`) i myli się na parach obok siebie: `()[]` paruje `(` z `]`.
///
/// **Złożoność.** O(n) czasu, O(n) pamięci na stos w najgorszym przypadku (same otwierające). Stos to
/// [ArrayDeque] na stercie, a nie rekurencja, więc głębokość zagnieżdżenia nie zależy od rozmiaru
/// stosu wątku (`-Xss`).
///
/// **Poza zadaniem.** Pusty napis jest zrównoważony. Znaki spoza `(){}[]` są pomijane, więc metoda
/// sprawdza też nawiasy w zwykłym tekście albo kodzie (`f(a[0]) { }` → `YES`). `null` to
/// `IllegalArgumentException`.
public class BalancedBrackets {

    private static final String YES = "YES";
    private static final String NO = "NO";
    private static final Map<Character, Character> OPENING_FOR = Map.of(')', '(', ']', '[', '}', '{');

    public static String isBalanced(String s) {
        return balanced(s) ? YES : NO;
    }

    public static boolean balanced(String s) {
        Objects.requireNonNull(s, "brak napisu");
        Deque<Character> open = new ArrayDeque<>();
        for (int i = 0; i < s.length(); i++) {
            if (!accept(open, s.charAt(i))) {
                return false;
            }
        }
        return open.isEmpty();
    }

    // poll() na pustym stosie zwraca null, więc zamykający bez pary daje false bez osobnego warunku
    private static boolean accept(Deque<Character> open, char c) {
        if (OPENING_FOR.containsValue(c)) {
            open.push(c);
            return true;
        }
        Character expected = OPENING_FOR.get(c);
        if (expected == null) {
            return true;
        }
        return expected.equals(open.poll());
    }

    void main() {
        for (String s : new String[]{"{[()]}", "{[(])}", "{{[[(())]]}}", "{(([])[])[]}[]"}) {
            IO.println("%s -> %s".formatted(s, isBalanced(s)));
        }
    }
}
