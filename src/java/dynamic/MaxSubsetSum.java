package dynamic;

import java.util.stream.IntStream;

/// HackerRank „Max Array Sum”: wybierz podzbiór elementów, z których żadne dwa nie sąsiadują, o
/// największej sumie.
///
/// Programowanie dynamiczne pod każdą metodą tutaj to jedna linia:
///
/// ```
///     best[i] = max(best[i - 1], best[i - 2] + arr[i])        best[-1] = best[-2] = 0
/// ```
///
/// **Stan** `best[i]` to odpowiedź dla prefiksu `arr[0..i]`. **Przejście**: element `i` jest albo
/// pominięty, więc przechodzi odpowiedź prefiksu bez niego, albo wzięty, co wyklucza element `i - 1`
/// i zostawia prefiks kończący się na `i - 2`. **Przypadek bazowy**: 0 dla obu pustych prefiksów, co
/// czyni pusty podzbiór kandydatem, więc odpowiedź nigdy nie jest ujemna, a wejście z samymi liczbami
/// ujemnymi zwraca 0 bez osobnego przypadku.
///
/// Dwie własności pozwalają skalować to na zbiory dużo większe niż 10<sup>5</sup> z treści zadania:
///
/// - **Pamięć O(1).** Przejście sięga wstecz tylko o dwa stany, więc tabela `best` zwija się do
///    dwóch zmiennych. Żadna tabela nie jest alokowana, niezależnie od rozmiaru wejścia, więc
///    [#maxSubsetSumAsLong(IntStream)] przetworzy zbiór, który nigdy nie zmieściłby się w pamięci.
///
/// - **Bez rekurencji.** Pętla od dołu nie ma głębokości stosu. Równoważna rekurencja z
///    zapamiętywaniem przepełnia stos JVM gdzieś przy n = 10<sup>4</sup>..10<sup>5</sup>.
///
/// Metody: [#maxSubsetSum(int\[\])] to sygnatura z HackerRank (`int`),
/// [#maxSubsetSumAsLong(int\[\])] to samo w `long`, bo suma przekracza [Integer#MAX_VALUE] już przy
/// około 2·10<sup>5</sup> elementach, a [#maxSubsetSumAsLong(IntStream)] czyta elementy ze strumienia.
/// Sumy bieżące są w `long`, więc jedynym prawdziwym limitem jest akumulator: przy danych typu `int`
/// przepełnienie wymaga rzędu 10<sup>15</sup> elementów.
public class MaxSubsetSum {

    public static int maxSubsetSum(int[] arr) {
        return Math.toIntExact(maxSubsetSumAsLong(arr));
    }

    public static long maxSubsetSumAsLong(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        Best best = new Best();
        for (int value : arr) {
            best.advance(value);
        }
        return best.forPrefix;
    }

    public static long maxSubsetSumAsLong(IntStream values) {
        if (values == null) throw new IllegalArgumentException("values must not be null");

        Best best = new Best();
        values.sequential().forEachOrdered(best::advance);
        return best.forPrefix;
    }

    private static final class Best {

        private long forPrefixBeforeLast;   // best[i - 2]
        private long forPrefix;             // best[i - 1], a po przesunięciu best[i]

        void advance(int value) {
            long withValue = forPrefixBeforeLast + value;
            forPrefixBeforeLast = forPrefix;
            forPrefix = Math.max(forPrefix, withValue);
        }
    }

    private MaxSubsetSum() {
    }
}
