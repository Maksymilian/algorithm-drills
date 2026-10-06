package greedy;

import java.util.Arrays;
import java.util.List;

/// HackerRank „Greedy Florist”: `k` przyjaciół kupuje razem `n` kwiatów. Cena kwiatu to jego cena
/// katalogowa razy jeden więcej niż liczba kwiatów, które _ten sam kupujący_ już kupił: pierwszy
/// kwiat kosztuje `c`, drugi `2c`, trzeci `3c`. Kup wszystkie jak najtaniej.
///
/// O całym zadaniu decydują dwa niezależne fakty.
///
/// **1. Mnożniki są wymuszone.** Każdy przyjaciel ma dokładnie jeden pierwszy zakup, więc w całej
/// grupie najwyżej `k` kwiatów może mieć mnożnik 1, najwyżej `k` mnożnik 2 i tak dalej: najwyżej
/// `m * k` kwiatów może mieć mnożnik `m` lub mniejszy. Te ograniczenia obejmują każdy sposób zakupu,
/// a jeden spełnia je wszystkie naraz: rozdaj `k` kwiatów po `x1`, potem `k` po `x2` i tak dalej.
/// Zbiór mnożników nie jest więc wyborem. To
///
/// ```
///     1, 1, ... 1,  2, 2, ... 2,  3, ...      (po k każdego, aż skończą się kwiaty)
/// ```
///
/// **2. Przy danych mnożnikach paruj je według nierówności o przestawieniu.** Gdyby droższy kwiat
/// miał większy mnożnik niż tańszy, zamiana zmieniłaby koszt o `(m_b - m_a) * (c_a - c_b) < 0`,
/// czyli ściśle obniżyła. W optymalnym zakupie najdroższy kwiat ma więc najmniejszy mnożnik, a
/// odpowiedź to
///
/// ```
///     koszt = suma po i z  descending[i] * (i / k + 1)
/// ```
///
/// **To zadanie jest zachłanne w ścisłym sensie**, co warto powiedzieć, bo jego sąsiad [MaxMin] nie
/// jest. Jest tu prawdziwa reguła przyrostowa (_daj najdroższemu pozostałemu kwiatowi najtańszy
/// pozostały mnożnik_), stosowana nieodwołalnie, kwiat po kwiecie, a fakt 2 to argument wymiany
/// dowodzący, że ta decyzja nigdy nie jest błędna. To podręcznikowy kształt: własność wyboru
/// zachłannego plus dowód, że lokalna optymalność przetrwa.
///
/// | Metoda | Czas | Dodatkowa pamięć | Argument po wywołaniu |
/// |---|---|---|---|
/// | [#getMinimumCost(int, int\[\])] | O(n log n) | 4n bajtów | nietknięty |
/// | [#getMinimumCostAsLong(int, int\[\])] | O(n log n) | 4n bajtów | nietknięty |
/// | [#getMinimumCostInPlace(int, int\[\])] | O(n log n) | 0 - 4n bajtów | **posortowany** |
/// | [#getMinimumCostByCounting(int, int\[\])] | **O(n + maxPrice)** | 4·maxPrice bajtów | nietknięty |
/// | [#purchasePlan(int, int\[\])] | O(n log n) | 4n bajtów | nietknięty |
///
/// - [#getMinimumCost(int, int\[\])]: sygnatura z platformy (jest też wersja dla `List<Integer>`).
/// - [#getMinimumCostByCounting(int, int\[\])]: bez sortowania; algorytm nie potrzebuje cen
///   uporządkowanych, tylko przejścia od najdroższej, a histogram cen daje je wprost. Powyżej
///   10 milionów histogram kosztuje więcej niż sortowanie, które ma zastąpić.
/// - [#purchasePlan(int, int\[\])]: kto co kupuje: `plan[f]` to kwiaty przyjaciela `f` w kolejności
///   zakupu, więc `plan[f][j]` kosztuje `j + 1` razy swoją cenę. Dowód liczby, którą zwracają
///   pozostałe metody.
///
/// **Zwracany `int` z platformy jest za wąski dla ograniczeń samego zadania.** Przy zwykłych
/// ograniczeniach (`n, k <= 100` i `c[i] <= 10`<sup>6</sup>) najgorszy przypadek to jeden przyjaciel
/// kupujący sto kwiatów po milionie:
///
/// ```
///     10^6 * (1 + 2 + ... + 100) = 5 050 000 000        Integer.MAX_VALUE = 2 147 483 647
/// ```
///
/// Ponad dwa razy za dużo. [#getMinimumCost(int, int\[\])] zachowuje sygnaturę platformy i zawęża
/// przez [Math#toIntExact(long)], więc rzuca wyjątek zamiast zawinąć sumę do ujemnej;
/// [#getMinimumCostAsLong(int, int\[\])] to metoda, którą naprawdę warto wołać.
///
/// **Ujemne ceny psują zadanie, nie tylko dowód.** Fakt 1 zakłada, że małe mnożniki są pożądane, a
/// to przestaje być prawdą przy pierwszej cenie poniżej zera: dla `c = {-10, 1}` i `k = 2` odpowiedź
/// z wymuszonymi mnożnikami to `1*1 + (-10)*1 = -9`, a jeden przyjaciel kupujący oba daje
/// `1*1 + (-10)*2 = -19`. Każda metoda tutaj odrzuca ujemną cenę zamiast zwracać złą odpowiedź. Zero
/// jest w porządku.
public class GreedyFlorist {

    private static final int MAX_PRICE_FOR_HISTOGRAM = 10_000_000;

    public static int getMinimumCost(int k, int[] c) {
        return Math.toIntExact(getMinimumCostAsLong(k, c));
    }

    public static int getMinimumCost(int k, List<Integer> c) {
        if (c == null) throw new IllegalArgumentException("c must not be null");

        int[] prices = new int[c.size()];
        int next = 0;
        for (Integer price : c) {
            prices[next++] = price;                    // rozpakowanie samo odrzuci element null
        }
        return getMinimumCost(k, prices);
    }

    public static long getMinimumCostAsLong(int k, int[] c) {
        requireBuyable(k, c);

        int[] ascending = c.clone();
        Arrays.sort(ascending);
        return costReadingDownwards(k, ascending);
    }

    public static long getMinimumCostInPlace(int k, int[] c) {
        requireBuyable(k, c);

        Arrays.sort(c);
        return costReadingDownwards(k, c);
    }

    public static long getMinimumCostByCounting(int k, int[] c) {
        requireBuyable(k, c);
        if (c.length == 0) return 0L;

        int dearest = 0;
        for (int price : c) {
            if (price > MAX_PRICE_FOR_HISTOGRAM) {
                throw new IllegalArgumentException("price " + price + " is above the "
                        + MAX_PRICE_FOR_HISTOGRAM + " this method will build a histogram for; "
                        + "use getMinimumCostAsLong instead");
            }
            dearest = Math.max(dearest, price);
        }

        int[] flowersAtPrice = new int[dearest + 1];
        for (int price : c) {
            flowersAtPrice[price]++;
        }

        long total = 0;
        int bought = 0;
        for (int price = dearest; price >= 0; price--) {
            for (int copies = flowersAtPrice[price]; copies > 0; copies--) {
                total += (long) price * (bought / k + 1);
                bought++;
            }
        }
        return total;
    }

    public static int[][] purchasePlan(int k, int[] c) {
        requireBuyable(k, c);

        int[] ascending = c.clone();
        Arrays.sort(ascending);
        int n = ascending.length;

        int[][] plan = new int[k][];
        for (int friend = 0; friend < k; friend++) {
            plan[friend] = new int[(n - friend + k - 1) / k];      // rangi przyjaciela, +k, +2k, ...
        }
        for (int rank = 0; rank < n; rank++) {
            plan[rank % k][rank / k] = ascending[n - 1 - rank];    // ranga 0 to najdroższy kwiat
        }
        return plan;
    }

    private static long costReadingDownwards(int k, int[] ascending) {
        long total = 0;
        for (int bought = 0, i = ascending.length - 1; i >= 0; i--, bought++) {
            total += (long) ascending[i] * (bought / k + 1);
        }
        return total;
    }

    private static void requireBuyable(int k, int[] c) {
        if (c == null) throw new IllegalArgumentException("c must not be null");
        if (k < 1) throw new IllegalArgumentException("k must be at least 1, but was " + k);

        for (int price : c) {
            if (price < 0) {
                throw new IllegalArgumentException("prices must not be negative, but found " + price
                        + "; a negative flower gets cheaper the later it is bought, which inverts "
                        + "the problem - see the class note");
            }
        }
    }

    private GreedyFlorist() {
    }
}
