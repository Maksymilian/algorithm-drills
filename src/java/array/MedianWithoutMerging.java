package array;

/// Mediana dwóch posortowanych tablic bez ich scalania.
///
/// Scalenie kosztuje O(m + n). Zamiast tego szukamy binarnie, ile elementów lewej połowy wziąć z
/// krótszej tablicy `a` (`i`), a resztę bierzemy z `b` (`j = half - i`). Podział jest
/// dobry, gdy `a[i - 1] <= b[j]` i `b[j - 1] <= a[i]`: wtedy mediana to największy element lewej
/// połowy (nieparzysta suma długości) albo średnia z niego i najmniejszego elementu prawej połowy
/// (parzysta). Krawędzie tablic zastępują strażnicy `Integer.MIN_VALUE` i `Integer.MAX_VALUE`.
///
/// Czas O(log min(m, n)), pamięć O(1). Dwie puste tablice i nieposortowane dane to
/// [IllegalArgumentException]. `main` uruchamia zestaw przypadków brzegowych.
public class MedianWithoutMerging {

    public static double findMedian(int[] a, int[] b) {
        // zawsze szukamy binarnie w krótszej tablicy
        if (a.length > b.length) return findMedian(b, a);

        int m = a.length, n = b.length;
        if (m + n == 0) throw new IllegalArgumentException("both sets are empty");

        int half = (m + n + 1) / 2;
        int lo = 0, hi = m;

        while (lo <= hi) {
            int i = (lo + hi) / 2;   // ile elementów bierzemy z a
            int j = half - i;        // ile elementów bierzemy z b

            int aLeft  = (i > 0) ? a[i - 1] : Integer.MIN_VALUE;
            int aRight = (i < m) ? a[i]     : Integer.MAX_VALUE;
            int bLeft  = (j > 0) ? b[j - 1] : Integer.MIN_VALUE;
            int bRight = (j < n) ? b[j]     : Integer.MAX_VALUE;

            if (aLeft <= bRight && bLeft <= aRight) {
                int maxLeft = Math.max(aLeft, bLeft);
                if (((m + n) % 2) == 1) return maxLeft;
                int minRight = Math.min(aRight, bRight);
                return (maxLeft + (double) minRight) / 2.0;
            } else if (aLeft > bRight) {
                hi = i - 1;          // za dużo z a
            } else {
                lo = i + 1;          // za mało z a
            }
        }
        throw new IllegalArgumentException("input arrays are not sorted");
    }

    // ---------- testy ----------

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, double expected, int[] a, int[] b) {
        try {
            double actual = findMedian(a, b);
            if (Math.abs(actual - expected) < 1e-9) {
                passed++;
                IO.println("PASS  %-28s -> %s".formatted(name, actual));
            } else {
                failed++;
                IO.println("FAIL  %-28s -> %s (expected %s)".formatted(name, actual, expected));
            }
        } catch (RuntimeException e) {
            failed++;
            IO.println("FAIL  %-28s -> threw %s: %s"
                    .formatted(name, e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    private static void checkThrows(String name, int[] a, int[] b) {
        try {
            double actual = findMedian(a, b);
            failed++;
            IO.println("FAIL  %-28s -> %s (expected an exception)".formatted(name, actual));
        } catch (IllegalArgumentException e) {
            passed++;
            IO.println("PASS  %-28s -> threw: %s".formatted(name, e.getMessage()));
        }
    }

    void main() {
        // nieparzysta i parzysta liczba elementów
        check("odd total",            2.0,  new int[]{1, 3},          new int[]{2});
        check("even total",           2.5,  new int[]{1, 2},          new int[]{3, 4});

        // jedna strona pusta
        check("empty + single",       1.0,  new int[]{},              new int[]{1});
        check("empty + even",         1.5,  new int[]{},              new int[]{1, 2});
        check("empty + odd",          2.0,  new int[]{},              new int[]{1, 2, 3});

        // cięcie na granicy tablicy (przypadki ze strażnikami)
        check("disjoint, a below b",  3.0,  new int[]{1, 2},          new int[]{3, 4, 5});
        check("disjoint, a above b", 10.0,  new int[]{10, 20, 30},    new int[]{1, 2});

        // bardzo różne rozmiary
        check("lopsided sizes",       5.5,  new int[]{1,2,3,4,5,6,7,8,9}, new int[]{10});
        check("single vs single",     1.5,  new int[]{1},             new int[]{2});

        // powtórzenia
        check("all identical",        1.0,  new int[]{1, 1, 1},       new int[]{1, 1, 1});
        check("duplicates spanning",  2.0,  new int[]{1, 2, 2, 3},    new int[]{2, 2});

        // liczby ujemne i skrajne
        check("negatives",           -2.0,  new int[]{-5, -3, -1},    new int[]{-2, 0});
        check("mixed signs",          0.0,  new int[]{-2, -1},        new int[]{1, 2});
        check("int extremes",  Integer.MAX_VALUE,
                new int[]{Integer.MAX_VALUE}, new int[]{Integer.MAX_VALUE});
        check("overflow-prone even",
                ((double) Integer.MIN_VALUE + Integer.MAX_VALUE) / 2.0,
                new int[]{Integer.MIN_VALUE}, new int[]{Integer.MAX_VALUE});

        // kolejność argumentów nie ma znaczenia
        check("swapped args",         3.0,  new int[]{3, 4, 5},       new int[]{1, 2});

        // błędne dane
        checkThrows("both empty",            new int[]{},             new int[]{});

        IO.println("%n%d passed, %d failed".formatted(passed, failed));
    }

}
