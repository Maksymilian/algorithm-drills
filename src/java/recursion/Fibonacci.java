package recursion;

/// `F(0) = 0`, `F(1) = 1`, `F(n) = F(n-1) + F(n-2)` zapisane na trzy sposoby, które razem
/// obejmują całą drogę od obliczenia, na które nikogo nie stać, do takiego, które kończy się, zanim
/// się zacznie.
///
/// | Metoda | Dodawania | Wywołania | Sterta | Stos |
/// |---|---|---|---|---|
/// | [#fibonacci(int)] | **F(n+1) - 1** | **2F(n+1) - 1** | nic | n ramek |
/// | [#fibonacciMemoized(int)] | n - 1 | 2n - 1 | 8(n+1) bajtów | n ramek |
/// | [#fibonacciIterative(int)] | n | 1 | **nic** | **1 ramka** |
///
/// Dwie pierwsze kolumny to cała lekcja, a pogrubione wpisy to nie ten sam rodzaj liczby: `F(n+1)`
/// rośnie o czynnik `phi = 1.618...` na krok, więc naiwna rekurencja kosztuje `1.4 * 1.618^n` wywołań
/// tam, gdzie pozostałe kosztują `n`. Przy n = 40 to 300 milionów wywołań wobec 40 dodawań. Przy
/// n = 92 to 2.4·10^19 wywołań, czyli stulecia, wobec 92.
///
/// **Naiwna wersja jest wykładnicza** nie dlatego, że rekurencja jest wolna, tylko dlatego, że drzewo
/// wywołań liczy od nowa: `F(n-2)` jest liczone dwa razy, `F(n-3)` trzy razy, a ogólnie `F(k)` jest
/// liczone `F(n-k+1)` razy. Liczba wywołań to
///
/// ```
///     C(0) = C(1) = 1,   C(n) = C(n-1) + C(n-2) + 1
/// ```
///
/// czyli rekurencja Fibonacciego z dodatkową jedynką, a jej rozwiązanie to dokładnie
/// `C(n) = 2F(n+1) - 1`. Drzewo ma rozmiar samej odpowiedzi: żeby zsumować `F(n)` jedynek, potrzeba
/// `F(n)` dodawań jedynki, bo tym właśnie są liście drzewa.
///
/// **Zapamiętywanie usuwa każde powtórzenie** i nic więcej. Kształt rekurencji zostaje ten sam
/// ([#fibonacciMemoized(int)] to te same trzy linie z jednym odczytem z tabeli na początku), ale do
/// policzonego już poddrzewa nigdy nie wchodzimy ponownie, więc każde `F(k)` liczymy raz, a
/// wykładnicze drzewo zwija się do ścieżki. To różnica między `2F(n+1)` wywołaniami a `2n`, kupiona
/// za `8(n+1)` bajtów tabeli.
///
/// **Zapamiętywanie nie odzyskuje stosu.** Obie rekurencje schodzą na głębokość `n` ramek, zanim
/// nastąpi pierwsze dodawanie, i tę głębokość usuwa dopiero iteracja. Tutaj to nie ma znaczenia, bo
/// `n` kończy się na 92, ale ten sam kod na [java.math.BigInteger] to [StackOverflowError] przy
/// kilkudziesięciu tysiącach, a pętla w [#fibonacciIterative(int)] takiej granicy nie ma. Wszystko to
/// mierzy `docs/recursion/fibonacci.md`.
///
/// **Gdzie to się kończy: 92.** `F(92) = 7540113804746346429` to ostatnia liczba Fibonacciego, która
/// mieści się w `long`; `F(93) = 12200160415121876738` jest większa niż `Long.MAX_VALUE`. Każda metoda
/// odrzuca wszystko powyżej, zamiast zwracać po cichu zawiniętą odpowiedź. To ogranicza też pamięć,
/// jakiej to zadanie w ogóle może użyć: cała tabela od `F(0)` do `F(92)` to 93 liczby `long`, czyli
/// 744 bajty. Ciekawym kosztem Fibonacciego jest procesor, nie pamięć, dopóki liczby mieszczą się w
/// rejestrze.
public class Fibonacci {

    public static final int LARGEST_IN_A_LONG = 92;

    public static long fibonacci(int n) {
        requireInRange(n);
        return naive(n);
    }

    private static long naive(int n) {
        return n <= 1 ? n : naive(n - 1) + naive(n - 2);
    }

    public static long fibonacciMemoized(int n) {
        requireInRange(n);
        return memoized(n, new long[n + 1]);
    }

    private static long memoized(int n, long[] known) {
        if (n <= 1) return n;                        // F(0) i F(1) nie są zapisywane: 0 znaczy „nieznane”
        if (known[n] != 0) return known[n];
        return known[n] = memoized(n - 1, known) + memoized(n - 2, known);
    }

    public static long fibonacciIterative(int n) {
        requireInRange(n);

        long previous = 0;                           // F(0)
        long current = 1;                            // F(1)
        for (int i = 0; i < n; i++) {
            long next = previous + current;
            previous = current;
            current = next;
        }
        return previous;                             // po n krokach previous to F(n)
    }

    private static void requireInRange(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative, but was " + n);
        }
        if (n > LARGEST_IN_A_LONG) {
            throw new IllegalArgumentException(
                    "F(" + n + ") does not fit in a long: F(93) is 12200160415121876738, and Long.MAX_VALUE is "
                            + Long.MAX_VALUE);
        }
    }

    private Fibonacci() {
    }
}
