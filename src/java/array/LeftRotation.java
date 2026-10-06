package array;

/// HackerRank „Arrays: Left Rotation”: przesuń każdy element `a` o `d` pozycji w lewo, a pierwsze
/// `d` elementów przenieś na koniec.
///
/// Zadanie opisuje powtarzaną operację („wykonaj `d` obrotów w lewo”), a dosłowne potraktowanie tego
/// to pułapka: obracanie o jeden krok naraz kosztuje O(n·d), co przy ograniczeniach zadania
/// (`n = d = 10`<sup>5</sup>) daje 10<sup>10</sup> przesunięć elementów. Złożenie `d` pojedynczych
/// obrotów to jednak nie pętla, tylko jedno przenumerowanie indeksów:
///
/// ```
///     rotated[i] = a[(i + d) mod n]
/// ```
///
/// Każda metoda tutaj to ta sama tożsamość, liczona w innej kolejności; żadna nie obraca dwa razy.
/// Ten wzór wyjaśnia też, dlaczego obrót nigdy nie musi kosztować więcej niż O(n): każdy element ma
/// dokładnie jedno miejsce docelowe, znane w postaci zamkniętej, więc żadnego nie trzeba ruszać dwa razy.
///
/// **Trzy kolejności liczenia**, wszystkie w czasie O(n):
///
/// | Metoda | Dodatkowa pamięć | Zapisy elementów | Dostęp do pamięci |
/// |---|---|---|---|
/// | [#rotLeft(int\[\], int)] | O(n) | n | dwa ciągłe bloki |
/// | [#rotateLeftInPlace(int\[\], int)] | O(1) | ~2n | sekwencyjny |
/// | [#rotateLeftInPlaceByCycles(int\[\], int)] | O(1) | n | skoki co d |
///
/// Dwie ostatnie płacą tę samą walutą w przeciwne strony: chodzenie po cyklach zapisuje każdy element
/// dokładnie raz, ale skacze o `d` pól, a trzy odwrócenia zapisują wszystko dwa razy i nigdy nie
/// skaczą. Która wygrywa, to pytanie o pamięć, nie o arytmetykę; zob. `docs/array/left-rotation.md`.
///
/// **Poza ograniczeniami zadania.** Zadanie gwarantuje `1 <= d <= n`. Każda metoda normalizuje `d`
/// przez [Math#floorMod(int, int)], więc `d = 0` i `d = n` niczego nie zmieniają, `d > n` zawija się,
/// a **ujemne `d` obraca w prawo**: obrót w prawo o `k` to obrót w lewo o `n - k`. Szczególna jest
/// tylko pusta tablica, bo nie ma `n`, modulo którego można by liczyć.
///
/// **Metody:**
///
/// - [#rotLeft(int\[\], int)]: sygnatura z HackerRank; zwraca nową tablicę złożoną z dwóch wywołań
///   [System#arraycopy] (`a[d..n)`, potem `a[0..d)`), wejścia nie zmienia.
/// - [#rotateLeftInPlace(int\[\], int)]: trzy odwrócenia (głowa, ogon, całość); około 2n zapisów,
///   ale zawsze po kolei w pamięci, więc to bezpieczny wybór.
/// - [#rotateLeftInPlaceByCycles(int\[\], int)]: przejście po `gcd(n, d)` cyklach permutacji;
///   dokładnie n zapisów, ale skoki co `d`, więc na dużej tablicy bywa do 56 razy wolniejsze.
public class LeftRotation {

    public static int[] rotLeft(int[] a, int d) {
        if (a == null) throw new IllegalArgumentException("a must not be null");

        int n = a.length;
        if (n == 0) return new int[0];
        int shift = Math.floorMod(d, n);

        int[] rotated = new int[n];
        System.arraycopy(a, shift, rotated, 0, n - shift);   // ogon przechodzi na początek
        System.arraycopy(a, 0, rotated, n - shift, shift);   // głowa zawija się za nim
        return rotated;
    }

    public static void rotateLeftInPlace(int[] a, int d) {
        if (a == null) throw new IllegalArgumentException("a must not be null");

        int n = a.length;
        if (n == 0) return;
        int shift = Math.floorMod(d, n);
        if (shift == 0) return;

        reverse(a, 0, shift);
        reverse(a, shift, n);
        reverse(a, 0, n);
    }

    public static void rotateLeftInPlaceByCycles(int[] a, int d) {
        if (a == null) throw new IllegalArgumentException("a must not be null");

        int n = a.length;
        if (n == 0) return;
        int shift = Math.floorMod(d, n);
        if (shift == 0) return;

        for (int start = 0, cycles = gcd(n, shift); start < cycles; start++) {
            int held = a[start];
            int hole = start;

            while (true) {
                int source = hole + shift;
                if (source >= n) source -= n;          // wystarczy jedno odejmowanie: shift < n
                if (source == start) break;            // cykl się domknął
                a[hole] = a[source];
                hole = source;
            }
            a[hole] = held;
        }
    }

    private static void reverse(int[] a, int from, int toExclusive) {
        for (int lo = from, hi = toExclusive - 1; lo < hi; lo++, hi--) {
            int swap = a[lo];
            a[lo] = a[hi];
            a[hi] = swap;
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    private LeftRotation() {
    }
}
