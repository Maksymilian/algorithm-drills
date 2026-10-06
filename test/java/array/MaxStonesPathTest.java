package array;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static array.MaxStonesPath.bestPath;
import static array.MaxStonesPath.maxStones;

class MaxStonesPathTest {

    private static final int[][] GRID = {
            {0, 0, 0, 9},
            {0, 5, 0, 0},
            {1, 0, 0, 0},
    };

    @Test
    void collectsTheMostStonesFromBottomLeftToTopRight() {
        assertEquals(15, maxStones(GRID, 2, 0, 0, 3));
        String path = bestPath(GRID, 2, 0, 0, 3);
        assertEquals(5, path.length(), "2 kroki na północ i 3 na wschód");
        assertEquals(15, collect(GRID, 2, 0, path));
    }

    @Test
    void greedyChoiceIsWrong() {
        int[][] grid = {
                {10, 0, 0},
                {0, 0, 0},
                {0, 1, 0},
        };
        assertEquals(10, maxStones(grid, 2, 0, 0, 2));
        assertEquals("NNEE", bestPath(grid, 2, 0, 0, 2));
    }

    @Test
    void worksOnAnInnerRectangleAndOnASingleField() {
        assertEquals(5, maxStones(GRID, 1, 1, 1, 1));
        assertEquals("", bestPath(GRID, 1, 1, 1, 1));
        assertEquals(5, maxStones(GRID, 2, 1, 1, 2));
    }

    @Test
    void matchesBruteForceOnRandomMaps() {
        Random random = new Random(7);
        for (int round = 0; round < 200; round++) {
            int[][] grid = randomGrid(random);
            int a = grid.length - 1, b = grid[0].length - 1;
            int expected = bruteForce(grid, a, 0, 0, b);
            assertEquals(expected, maxStones(grid, a, 0, 0, b));
            assertEquals(expected, collect(grid, a, 0, bestPath(grid, a, 0, 0, b)));
        }
    }

    @Test
    void rejectsBThatIsNotNorthEastOfA() {
        assertThrows(IllegalArgumentException.class, () -> maxStones(GRID, 0, 0, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> maxStones(GRID, 2, 3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> maxStones(GRID, 3, 0, 0, 3));
    }

    private static int collect(int[][] grid, int row, int col, String moves) {
        int total = grid[row][col];
        for (char move : moves.toCharArray()) {
            row -= move == 'N' ? 1 : 0;
            col += move == 'E' ? 1 : 0;
            total += grid[row][col];
        }
        return total;
    }

    private static int bruteForce(int[][] grid, int r, int c, int bRow, int bCol) {
        if (r == bRow && c == bCol) {
            return grid[r][c];
        }
        int north = r > bRow ? bruteForce(grid, r - 1, c, bRow, bCol) : Integer.MIN_VALUE;
        int east = c < bCol ? bruteForce(grid, r, c + 1, bRow, bCol) : Integer.MIN_VALUE;
        return grid[r][c] + Math.max(north, east);
    }

    private static int[][] randomGrid(Random random) {
        int[][] grid = new int[1 + random.nextInt(6)][1 + random.nextInt(6)];
        for (int[] row : grid) {
            Arrays.setAll(row, c -> random.nextInt(10));
        }
        return grid;
    }
}
