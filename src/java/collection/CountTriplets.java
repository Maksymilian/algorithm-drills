package collection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Policz trójki indeksów `i < j < k`, dla których `a[j] = a[i]·r` i `a[k] = a[j]·r`
/// (HackerRank „Count Triplets”).
///
/// **Pomysł: jedno przejście i dwie mapy.**
///
/// Każdy element `x` traktujemy jako **ostatni** wyraz trójki. Wtedy:
/// 1. trójek kończących się na `x` jest tyle, ile już widzianych par `(x/r², x/r)`;
/// 2. `x` domyka nowe pary `(x/r, x)` z każdym widzianym wcześniej `x/r`;
/// 3. `x` staje się kandydatem na pierwszy wyraz.
///
/// Kolejność kroków jest istotna: dzięki niej przy `r = 1` element nie łączy się sam ze sobą.
///
/// **Założenia.**
///
/// | | |
/// |---|---|
/// | `r` | co najmniej 1; mniejsze rzuca `IllegalArgumentException` |
/// | Elementy | dowolne `long`, także 0 i ujemne; `null` rzuca `NullPointerException` |
/// | Pusta lista, mniej niż 3 elementy | poprawne wejście, wynik 0 |
/// | Wynik | `long`; przepełnienie rzuca `ArithmeticException` |
///
/// **Złożoność.** O(n) czasu i O(n) pamięci.
public class CountTriplets {

    private static final int MINIMAL_RATIO = 1;

    public static long countTriplets(List<Long> values, long ratio) {
        requireValues(values);
        requireRatio(ratio);
        Map<Long, Long> singles = new HashMap<>();
        Map<Long, Long> pairs = new HashMap<>();
        long triplets = 0;
        for (long value : values) {
            triplets = Math.addExact(triplets, countEndingBefore(pairs, value, ratio));
            pairs.merge(value, countEndingBefore(singles, value, ratio), Math::addExact);
            singles.merge(value, 1L, Long::sum);
        }
        return triplets;
    }

    // ile wpisów kończy się na poprzedniku value, czyli na value / ratio
    private static long countEndingBefore(Map<Long, Long> counts, long value, long ratio) {
        if (value % ratio != 0) {
            return 0;
        }
        return counts.getOrDefault(value / ratio, 0L);
    }

    private static void requireValues(List<Long> values) {
        Objects.requireNonNull(values, "brak listy liczb");
        for (Long value : values) {
            Objects.requireNonNull(value, "brak liczby w liście");
        }
    }

    private static void requireRatio(long ratio) {
        if (ratio < MINIMAL_RATIO) {
            throw new IllegalArgumentException("iloraz musi być co najmniej 1, jest " + ratio);
        }
    }

    void main() {
        IO.println(countTriplets(List.of(1L, 2L, 2L, 4L), 2));
        IO.println(countTriplets(List.of(1L, 3L, 9L, 9L, 27L, 81L), 3));
        IO.println(countTriplets(List.of(1L, 5L, 5L, 25L, 125L), 5));
    }
}
