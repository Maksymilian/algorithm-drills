package greedy;

import java.util.Arrays;
import java.util.List;

/// HackerRank „Angry Children” (`maxMin`): wybierz `k` spośród `n` elementów tak, żeby `max - min`
/// wybranych, czyli _niesprawiedliwość_, było jak najmniejsze.
///
/// Wyborów jest `C(n, k)`, co przy ograniczeniach zadania daje liczbę o dziesiątkach tysięcy cyfr.
/// Wszystkie poza `n - k + 1` odrzuca jeden argument wymiany:
///
/// **Optymalny wybór to k kolejnych elementów posortowanej tablicy.** Weź dowolny wybór, niech `m`
/// będzie jego najmniejszym elementem, `M` największym, a `i` pozycją `m` w posortowanej tablicy.
/// Każdy wybrany element jest nie mniejszy niż `m`, więc wszystkie `k` stoją na posortowanym indeksie
/// `i` lub dalej, a największy z nich na indeksie `i + k - 1` lub dalej. Stąd
///
/// ```
///     M - m >= sorted[i + k - 1] - sorted[i]
/// ```
///
/// a prawa strona sama jest poprawnym wyborem: oknem zaczynającym się w `i`. Żaden wybór nie bije
/// więc najlepszego okna, a każde okno da się wybrać:
///
/// ```
///     odpowiedź = min po i z [0, n - k] z  sorted[i + k - 1] - sorted[i]
/// ```
///
/// **Do czego odnosi się „greedy” w nazwie pakietu.** Nie do pętli „bierz najlepszy element” (takiej
/// tu nie ma, a wybór k najmniejszych wartości albo k najbliższych średniej jest po prostu błędny).
/// Krok zachłanny to wymiana powyżej: każdy wybór da się przesunąć do środka na kolejne elementy bez
/// pogorszenia. To zwija przestrzeń poszukiwań do jednego liniowego przejścia, a do zapłacenia
/// zostaje tylko sortowanie.
///
/// **Sortowania nie da się uniknąć**, nawet sprytniejszymi porównaniami. Przy `k = 2` odpowiedź to
/// najmniejsza różnica między dowolnymi dwoma elementami, a wynosi `0` dokładnie wtedy, gdy tablica
/// zawiera powtórzenie. Rozstrzygnięcie tego to problem różności elementów, który wymaga
/// `Omega(n log n)` porównań, więc żadna metoda oparta na porównaniach nie pobije tu sortowania.
/// [#maxMinByRadixSort(int, int\[\])] jest szybsza tylko dlatego, że wychodzi poza ten model: czyta
/// bity wartości zamiast je porównywać.
///
/// | Metoda | Czas | Dodatkowa pamięć | Argument po wywołaniu |
/// |---|---|---|---|
/// | [#maxMin(int, int\[\])] | O(n log n) | 4n bajtów | nietknięty |
/// | [#maxMinAsLong(int, int\[\])] | O(n log n) | 4n bajtów | nietknięty |
/// | [#maxMinInPlace(int, int\[\])] | O(n log n) | 0 - 4n bajtów | **posortowany** |
/// | [#maxMinByRadixSort(int, int\[\])] | **O(n)** | 8n bajtów | nietknięty |
/// | [#fairestSelection(int, int\[\])] | O(n log n) | 4n bajtów | nietknięty |
///
/// - [#maxMin(int, int\[\])]: sygnatura z HackerRank (jest też wersja dla `List<Integer>`); sortuje
///   kopię i sprawdza każde okno `k`.
/// - [#maxMinInPlace(int, int\[\])]: sortuje tablicę wywołującego zamiast kopii; opłaca się, gdy
///   tablica jest za duża na drugą kopię.
/// - [#maxMinByRadixSort(int, int\[\])]: sortowanie pozycyjne LSD po czterech bajtach `int`a, cztery
///   przejścia po 256 kubełków. Najstarszy bajt ma odwrócony bit znaku, żeby liczby ujemne trafiły
///   przed dodatnie.
/// - [#fairestSelection(int, int\[\])]: same wybrane elementy, rosnąco, zamiast ich rozpiętości.
///
/// **Poza ograniczeniami zadania.** Zadanie obiecuje `0 <= arr[i] <= 10`<sup>9</sup>, więc rozpiętość
/// zawsze mieści się w `int`. Nic tu na tym nie polega: każde odejmowanie jest w `long`, bo
/// `{Integer.MIN_VALUE, Integer.MAX_VALUE}` ma rozpiętość 2<sup>32</sup> - 1, a `int` zawinąłby ją do
/// `-1`, czyli niesprawiedliwości minus jeden, po cichu najlepszej możliwej odpowiedzi.
/// [#maxMin(int, int\[\])] zachowuje zwracany `int` z platformy i zawęża przez
/// [Math#toIntExact(long)], więc rzuca wyjątek zamiast kłamać; [#maxMinAsLong(int, int\[\])] to
/// metoda do wywoływania, gdy wartości są dowolne.
public class MaxMin {

    public static int maxMin(int k, int[] arr) {
        return Math.toIntExact(maxMinAsLong(k, arr));
    }

    public static int maxMin(int k, List<Integer> arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int[] values = new int[arr.size()];
        int next = 0;
        for (Integer value : arr) {
            values[next++] = value;                    // rozpakowanie samo odrzuci element null
        }
        return maxMin(k, values);
    }

    public static long maxMinAsLong(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        int[] sorted = arr.clone();
        Arrays.sort(sorted);
        return smallestWindowSpread(k, sorted);
    }

    public static long maxMinInPlace(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        Arrays.sort(arr);
        return smallestWindowSpread(k, arr);
    }

    public static long maxMinByRadixSort(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        return smallestWindowSpread(k, radixSorted(arr));
    }

    public static int[] fairestSelection(int k, int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        requireSelectable(k, arr.length);

        int[] sorted = arr.clone();
        Arrays.sort(sorted);

        int bestStart = 0;
        long best = Long.MAX_VALUE;
        for (int start = 0, last = sorted.length - k; start <= last; start++) {
            long spread = (long) sorted[start + k - 1] - sorted[start];
            if (spread < best) {
                best = spread;
                bestStart = start;
            }
        }
        return Arrays.copyOfRange(sorted, bestStart, bestStart + k);
    }

    private static long smallestWindowSpread(int k, int[] sorted) {
        long best = Long.MAX_VALUE;
        for (int start = 0, last = sorted.length - k; start <= last; start++) {
            long spread = (long) sorted[start + k - 1] - sorted[start];
            if (spread < best) best = spread;
        }
        return best;
    }

    private static int[] radixSorted(int[] arr) {
        int n = arr.length;
        int[] from = arr.clone();
        int[] to = new int[n];
        int[] offsets = new int[257];

        for (int shift = 0; shift < 32; shift += 8) {
            boolean signByte = shift == 24;
            Arrays.fill(offsets, 0);
            for (int value : from) {
                offsets[digit(value, shift, signByte) + 1]++;
            }
            if (offsets[digit(from[0], shift, signByte) + 1] == n) continue;   // jeden kubełek: przejście nic nie zmienia

            for (int bucket = 1; bucket < offsets.length; bucket++) {
                offsets[bucket] += offsets[bucket - 1];                        // początki kubełków
            }
            for (int value : from) {
                to[offsets[digit(value, shift, signByte)]++] = value;
            }
            int[] swap = from;
            from = to;
            to = swap;
        }
        return from;
    }

    private static int digit(int value, int shift, boolean signByte) {
        int bucket = (value >>> shift) & 0xFF;
        return signByte ? bucket ^ 0x80 : bucket;
    }

    private static void requireSelectable(int k, int n) {
        if (k < 1) throw new IllegalArgumentException("k must be at least 1, but was " + k);
        if (k > n) throw new IllegalArgumentException("cannot select " + k + " of " + n + " elements");
    }

    private MaxMin() {
    }
}
