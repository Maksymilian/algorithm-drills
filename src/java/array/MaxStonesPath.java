package array;

/// Programowanie dynamiczne: najwięcej kamieni na drodze z A do B, idąc tylko na północ albo na
/// wschód. Notatka: `docs/array/max-stones-path.md`.
///
/// **Treść.** Mapa to siatka `grid[wiersz][kolumna]`: wiersz 0 to północ, kolumna 0 to
/// zachód. Na każdym polu ukryta jest pewna liczba kamieni. Idziemy z pola A do pola B, w każdym kroku
/// o jedno pole na północ (wiersz − 1) albo na wschód (kolumna + 1), i zbieramy kamienie z każdego
/// odwiedzonego pola, także z A i z B. Ile najwięcej można zebrać?
///
/// **Pomysł.** Na pole (r, c) wchodzimy tylko z południa (r + 1, c) albo z zachodu (r, c − 1).
/// Najlepszy wynik dla (r, c) to więc jego kamienie plus lepszy z wyników tych dwóch pól:
///
/// ```
/// best[r][c] = grid[r][c] + max(best[r + 1][c], best[r][c − 1])
/// ```
///
/// Liczymy wiersze od A w stronę B (z południa na północ), a w wierszu kolumny z zachodu na wschód.
/// Obaj sąsiedzi są wtedy już policzeni.
///
/// **Dlaczego nie zachłannie?** Wybór w każdym kroku bogatszego sąsiada może odciąć nas od
/// bogatego pola dalej, bo nie wolno się cofać (test `greedyChoiceIsWrong`).
///
/// **Złożoność.** O(h · w) czasu dla prostokąta h × w między A i B. Pamięć O(w): wiersz
/// `best[r + 1]` jest potrzebny tylko do policzenia wiersza `best[r]`, więc wystarczy
/// jedna tablica. Odtworzenie drogi ([#bestPath]) potrzebuje całej tabeli, O(h · w).
public class MaxStonesPath {

    public static int maxStones(int[][] grid, int aRow, int aCol, int bRow, int bCol) {
        requireNorthEast(grid, aRow, aCol, bRow, bCol);
        int width = bCol - aCol + 1;
        int[] best = new int[width];
        for (int r = aRow; r >= bRow; r--) {
            for (int i = 0; i < width; i++) {
                best[i] = grid[r][aCol + i] + bestBefore(best, r == aRow, i);
            }
        }
        return best[width - 1];
    }

    private static int bestBefore(int[] best, boolean firstRow, int i) {
        int fromSouth = firstRow ? Integer.MIN_VALUE : best[i];
        int fromWest = i > 0 ? best[i - 1] : Integer.MIN_VALUE;
        return firstRow && i == 0 ? 0 : Math.max(fromSouth, fromWest);
    }

    public static String bestPath(int[][] grid, int aRow, int aCol, int bRow, int bCol) {
        requireNorthEast(grid, aRow, aCol, bRow, bCol);
        int[][] best = bestTable(grid, aRow, aCol, bRow, bCol);
        return backtrack(best, aRow, aCol, bRow, bCol);
    }

    private static int[][] bestTable(int[][] grid, int aRow, int aCol, int bRow, int bCol) {
        int[][] best = new int[grid.length][grid[0].length];
        for (int r = aRow; r >= bRow; r--) {
            for (int c = aCol; c <= bCol; c++) {
                int fromSouth = r < aRow ? best[r + 1][c] : Integer.MIN_VALUE;
                int fromWest = c > aCol ? best[r][c - 1] : Integer.MIN_VALUE;
                int before = r == aRow && c == aCol ? 0 : Math.max(fromSouth, fromWest);
                best[r][c] = grid[r][c] + before;
            }
        }
        return best;
    }

    private static String backtrack(int[][] best, int aRow, int aCol, int bRow, int bCol) {
        char[] moves = new char[(aRow - bRow) + (bCol - aCol)];
        int r = bRow, c = bCol;
        for (int k = moves.length - 1; k >= 0; k--) {
            boolean fromSouth = c == aCol || r < aRow && best[r + 1][c] >= best[r][c - 1];
            moves[k] = fromSouth ? 'N' : 'E';
            r += fromSouth ? 1 : 0;
            c -= fromSouth ? 0 : 1;
        }
        return new String(moves);
    }

    private static void requireNorthEast(int[][] grid, int aRow, int aCol, int bRow, int bCol) {
        if (grid.length == 0 || grid[0].length == 0) {
            throw new IllegalArgumentException("pusta mapa");
        }
        boolean inside = aRow >= 0 && aRow < grid.length && bRow >= 0 && aCol >= 0
                && bCol < grid[0].length;
        if (!inside || bRow > aRow || bCol < aCol) {
            throw new IllegalArgumentException("B musi leżeć na północny wschód od A, na mapie");
        }
    }

    void main() {
        int[][] grid = {
                {0, 0, 0, 9},   // północ
                {0, 5, 0, 0},
                {1, 0, 0, 0},   // południe; A = lewy dolny róg, B = prawy górny róg
        };
        IO.println("najwięcej kamieni: " + maxStones(grid, 2, 0, 0, 3));
        IO.println("droga: " + bestPath(grid, 2, 0, 0, 3));
    }
}
