package greedy;

import java.util.Arrays;
import java.util.Objects;

/// Lena startuje z zerowym szczęściem. Przegrana w
/// zawodach o szczęściu `L` dodaje `L`, wygrana odejmuje `L`. Może przegrać najwyżej `k` ważnych
/// zawodów (`T = 1`); nieważnych (`T = 0`) dowolnie wiele. Szukamy największego końcowego szczęścia.
///
/// ```
/// k = 3, zawody = [[5,1], [2,1], [1,1], [8,1], [10,0], [5,0]]
/// ważne posortowane: 1 2 5 8  →  wygrywamy 4 − 3 = 1 najmniejsze (1)
/// wynik = (5 + 2 + 1 + 8 + 10 + 5) − 2·1 = 29
/// ```
///
/// **Pomysł.** Zaczynamy od „przegraj wszystko”, czyli sumy wszystkich `L`. Każda wygrana kosztuje
/// `2L` (tracimy `+L`, dostajemy `−L`). Nieważnych nie musimy wygrywać, więc ich nie wygrywamy.
/// Z ważnych musimy wygrać `max(0, ważne − k)`, a najtaniej wygrać te o najmniejszym `L`.
///
/// **Dlaczego zachłannie.** Argument wymiany: jeśli optymalny plan wygrywa ważne zawody o `L = a`,
/// a przegrywa ważne o `L = b < a`, zamiana ról nie zmienia liczby przegranych ważnych, a wynik
/// rośnie o `2(a − b) ≥ 0`. Każdy plan da się więc przerobić na „wygraj najmniejsze” bez straty.
/// Wygrana dodatkowych zawodów (ponad wymuszone) tylko odejmuje `2L ≥ 0`, więc jej nie robimy.
///
/// **Założenia.**
///
/// | | |
/// |---|---|
/// | `k` | `k ≥ 0`, inaczej `IllegalArgumentException`; `k` większe niż liczba ważnych jest poprawne |
/// | Zawody | tablica `[L, T]`, inna długość: `IllegalArgumentException` |
/// | `L` | `L ≥ 0`, inaczej `IllegalArgumentException` (przy ujemnym `L` wygrana byłaby zyskiem i reguła „przegraj nieważne” przestaje działać) |
/// | `T` | `0` albo `1`, inaczej `IllegalArgumentException` |
/// | Brak zawodów | poprawne wejście, wynik `0` |
/// | `null` jako tablica albo zawody | `NullPointerException` |
/// | Wynik poza `int` | `ArithmeticException` (sumy liczymy w `long`) |
///
/// **Złożoność.** O(n log n) czasu przez sortowanie ważnych, O(n) pamięci.
public class LuckBalance {

    private static final int LUCK = 0;
    private static final int IMPORTANCE = 1;
    private static final int FIELDS = 2;
    private static final int UNIMPORTANT = 0;
    private static final int IMPORTANT = 1;
    private static final int WIN_COST_FACTOR = 2;

    public static int luckBalance(int k, int[][] contests) {
        requireValid(k, contests);
        long loseEverything = Arrays.stream(contests).mapToLong(contest -> contest[LUCK]).sum();
        int[] important = sortedImportantLuck(contests);
        int mustWin = Math.max(0, important.length - k);
        long wonLuck = Arrays.stream(important, 0, mustWin).asLongStream().sum();
        return Math.toIntExact(loseEverything - WIN_COST_FACTOR * wonLuck);
    }

    private static int[] sortedImportantLuck(int[][] contests) {
        return Arrays.stream(contests)
                .filter(contest -> contest[IMPORTANCE] == IMPORTANT)
                .mapToInt(contest -> contest[LUCK])
                .sorted()
                .toArray();
    }

    private static void requireValid(int k, int[][] contests) {
        Objects.requireNonNull(contests, "brak listy zawodów");
        if (k < 0) {
            throw new IllegalArgumentException("k nie może być ujemne: " + k);
        }
        for (int[] contest : contests) {
            requireContest(contest);
        }
    }

    private static void requireContest(int[] contest) {
        Objects.requireNonNull(contest, "brak zawodów w liście");
        if (contest.length != FIELDS) {
            throw new IllegalArgumentException("zawody muszą mieć %d pola: %s".formatted(FIELDS, Arrays.toString(contest)));
        }
        if (contest[LUCK] < 0) {
            throw new IllegalArgumentException("szczęście nie może być ujemne: " + Arrays.toString(contest));
        }
        if (contest[IMPORTANCE] != UNIMPORTANT && contest[IMPORTANCE] != IMPORTANT) {
            throw new IllegalArgumentException("ważność musi być 0 albo 1: " + Arrays.toString(contest));
        }
    }

    void main() {
        int[][] contests = {{5, 1}, {2, 1}, {1, 1}, {8, 1}, {10, 0}, {5, 0}};
        IO.println(luckBalance(3, contests));
    }
}
