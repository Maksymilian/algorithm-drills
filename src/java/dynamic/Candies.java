package dynamic;

import java.util.List;
import java.util.stream.IntStream;

/// HackerRank „Candies”: rozdaj cukierki dzieciom stojącym w rzędzie. Każde dziecko dostaje co
/// najmniej jeden, a dziecko ocenione wyżej niż bezpośredni sąsiad dostaje ściśle więcej niż ten
/// sąsiad. Zminimalizuj sumę.
///
/// Ograniczenie dotyczy tylko dziecka ocenionego _wyżej_ i to czyni zadanie ciekawym: spadek albo
/// powtórzenie oceny niczego nie narzuca, więc ograniczenia tworzą łańcuchy biegnące w obie strony,
/// a zachłanne przejście od lewej zawodzi na `[3, 2, 1]`: rozdaje `[1, 1, 1]`, a odpowiedź to
/// `[3, 2, 1]`.
///
/// Każde ograniczenie to dolna granica, więc najtańsze poprawne rozdanie to najmniejsze przypisanie
/// spełniające wszystkie naraz, a ono rozkłada się czysto według kierunku:
///
/// ```
///     L[i] = ratings[i] > ratings[i-1] ? L[i-1] + 1 : 1        // łańcuchy od lewego sąsiada
///     R[i] = ratings[i] > ratings[i+1] ? R[i+1] + 1 : 1        // łańcuchy od prawego sąsiada
///     candy[i] = max(L[i], R[i])                               odpowiedź = suma candy
/// ```
///
/// **Stan** `L[i]` to odpowiedź dla prefiksu `ratings[0..i]` z pominięciem prawego sąsiada, a
/// `R[i]` lustrzanie dla sufiksu. **Przejście**: dziecko ocenione wyżej niż poprzednik musi go
/// przebić, czyli dostać jego odpowiedź plus jeden; inaczej łańcuch się urywa i dziecko może wziąć
/// minimum. **Przypadek bazowy**: 1, minimum należne każdemu dziecku. Oba kierunki są _potrzebne_:
/// samo `L` niedokarmia każdego spadku, samo `R` każdego wzrostu, a wzięcie większego z dwóch
/// spełnia oba łańcuchy naraz; dlaczego to maksimum jest nie tylko poprawne, ale i minimalne, wyjaśnia
/// `docs/dynamic/candies.md`.
///
/// Czytana jako tabela rekurencja chce O(n) pamięci, bo `R` wypełnia się od prawej i nie da się go
/// złożyć z `L` w jednym przejściu do przodu. Są tu dwie postacie:
///
/// - [#candiesWithTable(int\[\])] trzyma obie tabele i je sumuje: podręcznikowe dwa przejścia, czas
///    O(n) i pamięć O(n), zostawione do porównania i jako czytelny zapis rekurencji;
///
/// - [#candies(int\[\])] i jej przeciążenia robią to samo w jednym przejściu i O(1) pamięci, nigdy
///    nie budując `R`: `R[i]` przewyższa `L[i]` tylko w środku malejącego odcinka, a dodatkowe
///    cukierki, które winien jest spadek, można dodawać w trakcie przechodzenia odcinka, zamiast
///    po nim: po jednym dla każdego dziecka już w spadku i jeden dla szczytu, gdy spadek przerośnie
///    wzrost, który do niego prowadził. **Tej używaj.** [#candies(int, List)] to sygnatura z
///    HackerRank, a [#candies(IntStream)] czyta rząd ze strumienia, więc pamięta tylko pięć liczb.
///
/// Zgadzają się na każdym wejściu. Tabele czynią obie liniowymi (liczone od zera, każde `L[i]` i
/// `R[i]` przeglądałoby od nowa swój łańcuch, co na monotonicznym rzędzie daje O(n<sup>2</sup>)), ale
/// każdy wpis ma dokładnie jednego zależnego, więc łańcuchy można przechodzić przyrostowo i zwinięcie
/// tabel do liczników niczego nie traci. Tabele kosztują cały rząd w pamięci naraz, dlatego tylko
/// wersja zwinięta ma przeciążenia dla [java.util.stream.IntStream] i [List].
///
/// Czas O(n) w obu przypadkach. Suma potrzebuje `long`: przy ograniczeniu zadania, 10<sup>5</sup>
/// dzieci, ściśle rosnący rząd kosztuje 5 000 050 000 cukierków, wyraźnie ponad [Integer#MAX_VALUE].
public class Candies {

    public static long candies(int n, List<Integer> arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        Run run = new Run();
        for (Integer rating : arr) {
            run.advance(rating);
        }
        return run.total;
    }

    public static long candies(int[] ratings) {
        if (ratings == null) throw new IllegalArgumentException("ratings must not be null");

        Run run = new Run();
        for (int rating : ratings) {
            run.advance(rating);
        }
        return run.total;
    }

    public static long candies(IntStream ratings) {
        if (ratings == null) throw new IllegalArgumentException("ratings must not be null");

        Run run = new Run();
        ratings.sequential().forEachOrdered(run::advance);
        return run.total;
    }

    public static long candiesWithTable(int[] ratings) {
        if (ratings == null) throw new IllegalArgumentException("ratings must not be null");

        int n = ratings.length;
        int[] left = new int[n];    // L[i]: odpowiedź dla ratings[0..i], bez prawego sąsiada
        int[] right = new int[n];   // R[i]: lustrzanie, dla ratings[i..n-1]

        for (int i = 0; i < n; i++) {
            left[i] = i > 0 && ratings[i] > ratings[i - 1] ? left[i - 1] + 1 : 1;
        }
        for (int i = n - 1; i >= 0; i--) {
            right[i] = i < n - 1 && ratings[i] > ratings[i + 1] ? right[i + 1] + 1 : 1;
        }

        long total = 0;
        for (int i = 0; i < n; i++) {
            total += Math.max(left[i], right[i]);   // oba łańcuchy naraz i nic ponad to
        }
        return total;
    }

    private static final class Run {

        private boolean started;
        private int previousRating;
        private long ascent;       // kolejne wzrosty kończące się na poprzednim dziecku, więc L = ascent + 1
        private long descent;      // kolejne spadki od szczytu, z którego zszedł odcinek
        private long peakAscent;   // wzrost, którym doszliśmy do szczytu, więc jego L = peakAscent + 1
        private long total;

        void advance(int rating) {
            if (!started) {
                started = true;
                previousRating = rating;
                total = 1;             // pierwszemu dziecku należy się minimum i nic więcej
                return;
            }

            if (rating > previousRating) {
                descent = 0;
                peakAscent = ++ascent;
                total += 1 + ascent;   // przebij poprzednika: L = ascent + 1, a R jest tu 1
            } else if (rating == previousRating) {
                ascent = descent = peakAscent = 0;   // równe oceny nie ograniczają żadnego z dzieci
                total += 1;
            } else {
                ascent = 0;
                descent++;
                // Jeden cukierek więcej dla każdego dziecka już w spadku i własny nowego dziecka: razem `descent`,
                // różnica dwóch liczb trójkątnych. Szczyt potrzebuje jednego więcej dopiero wtedy, gdy spadek
                // przerośnie wzrost, z którego zszedł.
                total += descent + (peakAscent < descent ? 1 : 0);
            }

            previousRating = rating;
        }
    }

    private Candies() {
    }
}
