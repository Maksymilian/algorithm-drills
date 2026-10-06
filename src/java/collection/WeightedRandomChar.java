package collection;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/// Generator losowych znaków o zadanym rozkładzie prawdopodobieństwa.
///
/// **Treść.** Konstruktor dostaje mapę: znak → prawdopodobieństwo z przedziału (0, 1\].
/// Metoda [#next()] losuje znak zgodnie z tym rozkładem. Wolno używać tylko zwykłego
/// generatora liczb losowych (`rand()` w C++, [Random] w Javie).
///
/// **Pomysł: sumy prefiksowe i wyszukiwanie binarne.** Dla `{a: 0.5, b: 0.3, c: 0.2}`
/// układamy przedziały na odcinku \[0, 1): a = \[0, 0.5), b = \[0.5, 0.8), c = \[0.8, 1). Losujemy
/// jednostajnie liczbę `r` z \[0, 1) i szukamy przedziału, w który wpadła: pierwszej sumy
/// prefiksowej większej od `r`.
///
/// **Złożoność.** Konstruktor O(k log k) dla k znaków (sortowanie, by kolejność była
/// powtarzalna). `next()` O(log k). Pamięć O(k).
///
/// **Pułapki, o które pytają.**
///
/// - `rand() % 100` jest lekko nierówny, gdy `RAND_MAX + 1` nie dzieli się przez
///    100. Lepiej `rand() / (RAND_MAX + 1.0)`.
///
/// - Prawdopodobieństwa nie sumują się dokładnie do 1 (to `float`). Losujemy z
///    \[0, suma), a nie z \[0, 1), i nie wychodzimy poza ostatni przedział.
///
/// - Testowalność: generator przychodzi z zewnątrz, więc w teście ma stałe ziarno.
///
/// **Dopytania.** `next()` w O(1): metoda aliasów Walkera. Rozkład zmienia się w trakcie:
/// drzewo Fenwicka nad wagami.
public final class WeightedRandomChar {

    private final char[] symbols;
    private final double[] cumulative;   // cumulative[i] = p(symbols[0]) + ... + p(symbols[i])
    private final Random random;

    public WeightedRandomChar(Map<Character, ? extends Number> distribution, Random random) {
        if (distribution.isEmpty()) {
            throw new IllegalArgumentException("pusty alfabet");
        }
        TreeMap<Character, Number> sorted = new TreeMap<>(distribution);   // stała kolejność znaków
        symbols = new char[sorted.size()];
        cumulative = new double[sorted.size()];
        fillPrefixSums(sorted);
        this.random = random;
    }

    private void fillPrefixSums(TreeMap<Character, Number> sorted) {
        double sum = 0;
        int i = 0;
        for (Map.Entry<Character, Number> e : sorted.entrySet()) {
            sum += probability(e);
            symbols[i] = e.getKey();
            cumulative[i++] = sum;
        }
        requireSumOne(sum);
    }

    private static double probability(Map.Entry<Character, Number> e) {
        double p = e.getValue().doubleValue();
        if (!(p > 0 && p <= 1)) {
            throw new IllegalArgumentException("p(" + e.getKey() + ") = " + p);
        }
        return p;
    }

    private static void requireSumOne(double sum) {
        if (Math.abs(sum - 1) > 1e-5) {
            throw new IllegalArgumentException("suma = " + sum + ", nie 1");
        }
    }

    public char next() {
        double r = random.nextDouble() * cumulative[cumulative.length - 1];
        return symbols[firstGreaterThan(r)];
    }

    private int firstGreaterThan(double r) {
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > r) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    static void main() {
        WeightedRandomChar generator = new WeightedRandomChar(
                Map.of('a', 0.5f, 'b', 0.3f, 'c', 0.2f), new Random(42));
        Map<Character, Integer> counts = new TreeMap<>();
        for (int i = 0; i < 100_000; i++) {
            counts.merge(generator.next(), 1, Integer::sum);
        }
        IO.println(counts);   // około {a=50000, b=30000, c=20000}
    }
}
