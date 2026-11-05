package dynamic;

import java.util.Arrays;

/// Liczba decybinarna stojąca na pozycji `x` posortowanej listy (HackerRank „Decibinary Numbers”).
/// Cyfry są dziesiętne (0–9), ale cyfra na pozycji `i` waży `2^i`; lista jest posortowana według
/// wartości, a przy równej wartości według zapisu jako zwykłej liczby.
///
/// **Pomysł: zliczanie zapisów zamiast generowania listy.** Tablice liczymy raz, przy pierwszym
/// zapytaniu:
///
/// 1. `representations[v]`: ile zapisów ma wartość `v`. Najmłodsza cyfra `d` ma parzystość `v`,
///    a reszta to zapis wartości `(v − d) / 2`, więc
///    `representations[v] = suma po d ≡ v (mod 2), d ≤ 9 z representations[(v − d) / 2]`.
///    Liczymy kolejne wartości, aż suma rozmiarów grup sięgnie [#MAX_POSITION].
/// 2. `positionsBefore[v]`: sumy prefiksowe, czyli ile pozycji listy zajmują wartości mniejsze
///    od `v`. Wartość dla `x` daje wyszukiwanie binarne.
/// 3. `ways[p][v]`: ile zapisów na `p` najmłodszych pozycjach ma wartość `v`. Z niej wybieramy
///    cyfry od najstarszej: cyfra `d` na pozycji `p` obejmuje `ways[p][v − d·2^p]` kolejnych
///    zapisów, więc pomijamy całe takie bloki, dopóki numer w grupie się w nich nie mieści.
///
/// Rysunek i uzasadnienie: `docs/dynamic/decibinary-numbers.md`.
///
/// **Założenia.**
///
/// | | |
/// |---|---|
/// | `x` | od 1 do [#MAX_POSITION] = 10¹⁶; poza zakresem `IllegalArgumentException` |
/// | Wynik | `long` bez zer wiodących; najstarsza możliwa cyfra stoi na pozycji 18 i jest ≤ 1, więc wynik < 2·10¹⁸ |
///
/// **Złożoność.** Tablice: dla 10¹⁶ wartości sięgają `V = 285 112` przy 19 pozycjach, czyli
/// O(P · V · 10) czasu raz i ok. 45 MB pamięci. Zapytanie: O(log V + 10 · P).
public class DecibinaryNumbers {

    public static final long MAX_POSITION = 10_000_000_000_000_000L;

    private static final int DIGITS = 10;
    private static final int DECIMAL_BASE = 10;
    private static final int INITIAL_CAPACITY = 1 << 10;

    // klasa-schowek: tablice powstają przy pierwszym użyciu, a nie przy ładowaniu klasy
    private static final class Tables {
        static final long[] REPRESENTATIONS = countRepresentationsUpTo(MAX_POSITION);
        static final long[] POSITIONS_BEFORE = prefixSums(REPRESENTATIONS);
        static final long[][] WAYS = waysByLowestPositions(REPRESENTATIONS.length - 1);
    }

    private static final class DigitSearch {
        private int rest;
        private long index;

        DigitSearch(int value, long indexInGroup) {
            rest = value;
            index = indexInGroup;
        }

        int nextDigit(int position) {
            int weight = 1 << position;
            int digit = 0;
            while (index >= Tables.WAYS[position][rest - digit * weight]) {
                index -= Tables.WAYS[position][rest - digit * weight];
                digit++;
            }
            rest -= digit * weight;
            return digit;
        }
    }

    public static long decibinaryNumbers(long x) {
        requirePosition(x);
        int value = valueAt(x);
        long indexInGroup = x - 1 - Tables.POSITIONS_BEFORE[value];
        return representationAt(value, indexInGroup);
    }

    // ostatnia wartość v, przed którą stoi najwyżej x − 1 pozycji
    private static int valueAt(long x) {
        int found = Arrays.binarySearch(Tables.POSITIONS_BEFORE, x - 1);
        if (found >= 0) {
            return found;
        }
        int insertionPoint = -found - 1;
        return insertionPoint - 1;
    }

    private static long representationAt(int value, long indexInGroup) {
        DigitSearch search = new DigitSearch(value, indexInGroup);
        long result = 0;
        for (int position = Tables.WAYS.length - 2; position >= 0; position--) {
            result = result * DECIMAL_BASE + search.nextDigit(position);
        }
        return result;
    }

    private static long[] countRepresentationsUpTo(long positions) {
        long[] counts = new long[INITIAL_CAPACITY];
        counts[0] = 1;
        long total = counts[0];
        int value = 0;
        while (total < positions) {
            value++;
            counts = withRoomFor(counts, value);
            counts[value] = representations(counts, value);
            total += counts[value];
        }
        return Arrays.copyOf(counts, value + 1);
    }

    private static long[] withRoomFor(long[] counts, int index) {
        if (index < counts.length) {
            return counts;
        }
        return Arrays.copyOf(counts, counts.length * 2);
    }

    // najmłodsza cyfra ma parzystość value, reszta to zapis (value − cyfra) / 2
    private static long representations(long[] counts, int value) {
        long sum = 0;
        for (int digit = value % 2; digit < DIGITS && digit <= value; digit += 2) {
            sum += counts[(value - digit) / 2];
        }
        return sum;
    }

    private static long[] prefixSums(long[] counts) {
        long[] sums = new long[counts.length + 1];
        for (int value = 0; value < counts.length; value++) {
            sums[value + 1] = sums[value] + counts[value];
        }
        return sums;
    }

    private static long[][] waysByLowestPositions(int maxValue) {
        int positions = Integer.SIZE - Integer.numberOfLeadingZeros(maxValue);
        long[][] ways = new long[positions + 1][];
        ways[0] = new long[maxValue + 1];
        ways[0][0] = 1;
        for (int position = 1; position <= positions; position++) {
            ways[position] = nextRow(ways[position - 1], 1 << (position - 1));
        }
        return ways;
    }

    private static long[] nextRow(long[] previous, int weight) {
        long[] row = new long[previous.length];
        for (int value = 0; value < row.length; value++) {
            row[value] = sumOverDigits(previous, value, weight);
        }
        return row;
    }

    private static long sumOverDigits(long[] previous, int value, int weight) {
        long sum = 0;
        for (int digit = 0; digit < DIGITS && digit * weight <= value; digit++) {
            sum += previous[value - digit * weight];
        }
        return sum;
    }

    private static void requirePosition(long x) {
        if (x < 1 || x > MAX_POSITION) {
            throw new IllegalArgumentException(
                    "pozycja musi być od 1 do %d, jest %d".formatted(MAX_POSITION, x));
        }
    }

    void main() {
        for (long x : new long[] {1, 2, 3, 4, 9, 10, MAX_POSITION}) {
            IO.println("%d → %d".formatted(x, decibinaryNumbers(x)));
        }
    }
}
