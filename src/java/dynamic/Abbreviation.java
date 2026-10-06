package dynamic;

/// HackerRank „Abbreviation”: czy `a` da się zamienić w `b`, zamieniając niektóre małe litery na
/// wielkie i usuwając wszystkie pozostałe małe litery?
///
/// Cała trudność to asymetria między dwoma rodzajami liter. Mała litera ma wybór: zamień ją na
/// wielką i zużyj na `b` albo usuń. **Wielka litera wyboru nie ma**: żadna operacja jej nie usuwa,
/// więc każda wielka litera `a` musi zostać dopasowana, po kolei, albo odpowiedź brzmi „nie”.
///
/// Dlatego zawodzi też podejście zachłanne. Dopasowanie `a = "aA"` do `b = "A"` przez chwytanie
/// pierwszej pasującej litery zamienia `'a'` na wielką i zostawia `'A'` bez pary, a odpowiedź to
/// „tak”: usuń `'a'` i dopasuj `'A'`. Czy zużycie małej litery się opłaci, zależy od całej reszty
/// napisu, więc oba wybory muszą zostać przy życiu; to właśnie robi tabela.
///
/// **Stan** `reachable[i][j]`: pierwsze `i` liter `a` może dać pierwsze `j` liter `b`.
/// **Przejście** dla `c = a[i-1]`, `d = b[j-1]`:
///
/// ```
///     c mała:   reachable[i][j] = reachable[i-1][j]                      // usuń c
///                               | reachable[i-1][j-1] && upper(c) == d   // zamień c na wielką
///     c wielka: reachable[i][j] = reachable[i-1][j-1] && c == d          // c trzeba zużyć
/// ```
///
/// **Przypadek bazowy**: `reachable[i][0]` jest prawdą tylko wtedy, gdy `a[0..i-1]` to same małe
/// litery (gdy nie ma czego dopasować, wszystko musi dać się usunąć), a `reachable[0][j>0]` jest
/// fałszem. **Odpowiedź**: `reachable[n][m]`.
///
/// Czas O(n·m), pamięć O(m): przejście czyta tylko wiersz `i - 1`, więc wystarczy jeden wiersz.
/// [#abbreviation(String, String)] to sygnatura z HackerRank zwracająca `"YES"` albo `"NO"`, a
/// [#canAbbreviate(String, String)] zwraca `boolean`.
///
/// Zgodnie z ograniczeniami zadania `b` składa się z wielkich liter. Mała litera w `b` nie jest
/// odrzucana, po prostu nigdy nie pasuje: zamiana albo zostawienie litery `a` zawsze daje wielką,
/// więc takie zapytanie poprawnie dostaje odpowiedź „nie”.
public class Abbreviation {

    public static String abbreviation(String a, String b) {
        return canAbbreviate(a, b) ? "YES" : "NO";
    }

    public static boolean canAbbreviate(String a, String b) {
        if (a == null || b == null) throw new IllegalArgumentException("a and b must not be null");

        int n = a.length();
        int m = b.length();
        if (n < m) return false;   // każda litera b musi zostać zużyta z własnej litery a

        // Jeden wiersz tabeli: reachable[j] == „dotychczasowy prefiks a może dać b[0..j-1]”.
        boolean[] reachable = new boolean[m + 1];
        reachable[0] = true;       // pusty prefiks daje pusty prefiks

        for (int i = 1; i <= n; i++) {
            char c = a.charAt(i - 1);
            boolean droppable = Character.isLowerCase(c);
            char spent = Character.toUpperCase(c);

            // Malejąco, żeby reachable[j] i reachable[j - 1] przy odczycie były jeszcze wierszem i - 1.
            for (int j = m; j >= 1; j--) {
                boolean spendOnMatch = spent == b.charAt(j - 1) && reachable[j - 1];
                reachable[j] = spendOnMatch || (droppable && reachable[j]);
            }
            reachable[0] &= droppable;   // wielkiej litery nie można zostawić niezużytej
        }

        return reachable[m];
    }

    private Abbreviation() {
    }
}
