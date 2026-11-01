package array;

import java.util.List;
import java.util.Objects;

/// HackerRank „2D Array - DS”: największa suma klepsydry w tablicy dwuwymiarowej. Notatka:
/// `docs/array/hourglass-sum.md`.
///
/// **Treść.** Klepsydra to 7 pól o stałym kształcie, zaczepionych w lewym górnym rogu `(r, c)`:
///
/// ```
/// a b c      (r, c)     (r, c + 1)     (r, c + 2)
///   d                   (r + 1, c + 1)
/// e f g      (r + 2, c) (r + 2, c + 1) (r + 2, c + 2)
/// ```
///
/// Policz sumę każdej klepsydry, która mieści się w tablicy, i zwróć największą.
///
/// **Pomysł.** Przejrzyj wszystkie rogi `(r, c)` z `0 ≤ r < h − 2` i `0 ≤ c < w − 2`. Kształt jest
/// stały, więc każda suma to 7 odczytów, a nie pętla.
///
/// **Pułapka.** Wartości mogą być ujemne (`−9 ≤ arr[i][j] ≤ 9`), więc maksimum zaczyna się od
/// `Integer.MIN_VALUE`, a nie od 0. Z tego samego powodu 0 nie nadaje się na wynik „brak klepsydry”:
/// to poprawna suma. Tablica mniejsza niż 3 × 3 albo z wierszami różnej długości to błąd wejścia.
/// Wartość `null` w wierszu listy liczy się jako 0 (bez tego rozpakowanie rzuca
/// `NullPointerException`).
///
/// **Złożoność.** O(h · w) czasu, O(1) dodatkowej pamięci poza kopią do `int[][]`.
public class HourglassSum {

    private static final int SIZE = 3;

    public static int hourglassSum(List<List<Integer>> arr) {
        return maxHourglassSum(toGrid(arr));
    }

    public static int maxHourglassSum(int[][] grid) {
        requireRectangleOfAtLeast3x3(grid);
        int best = Integer.MIN_VALUE;
        for (int r = 0; r + SIZE <= grid.length; r++) {
            for (int c = 0; c + SIZE <= grid[0].length; c++) {
                best = Math.max(best, sumAt(grid, r, c));
            }
        }
        return best;
    }

    private static int sumAt(int[][] grid, int r, int c) {
        int top = grid[r][c] + grid[r][c + 1] + grid[r][c + 2];
        int middle = grid[r + 1][c + 1];
        int bottom = grid[r + 2][c] + grid[r + 2][c + 1] + grid[r + 2][c + 2];
        return top + middle + bottom;
    }

    private static int[][] toGrid(List<List<Integer>> arr) {
        if (arr == null) {
            throw new IllegalArgumentException("brak tablicy");
        }
        return arr.stream()
                .map(row -> row.stream().mapToInt(HourglassSum::zeroIfNull).toArray())
                .toArray(int[][]::new);
    }

    // Integer::intValue rzuciłby NullPointerException przy rozpakowaniu null
    private static int zeroIfNull(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }

    private static void requireRectangleOfAtLeast3x3(int[][] grid) {
        if (grid.length < SIZE || grid[0].length < SIZE) {
            throw new IllegalArgumentException("tablica musi mieć co najmniej 3 × 3");
        }
        for (int[] row : grid) {
            if (row.length != grid[0].length) {
                throw new IllegalArgumentException("wiersze muszą mieć równą długość");
            }
        }
    }

    void main() {
        int[][] grid = {
                {1, 1, 1, 0, 0, 0},
                {0, 1, 0, 0, 0, 0},
                {1, 1, 1, 0, 0, 0},
                {0, 0, 2, 4, 4, 0},
                {0, 0, 0, 2, 0, 0},
                {0, 0, 1, 2, 4, 0},
        };
        IO.println("największa klepsydra: " + maxHourglassSum(grid));
    }
}
