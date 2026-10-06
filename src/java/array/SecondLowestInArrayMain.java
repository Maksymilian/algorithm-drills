package array;

import java.util.Arrays;

/// Druga najmniejsza wartość w tablicy, w jednym przejściu.
///
/// Zamiast sortować (O(n log n)), trzymamy dwie zmienne: najmniejszą wartość dotąd i drugą
/// najmniejszą. Każdy element albo spycha obecne minimum na drugie miejsce, albo zajmuje drugie
/// miejsce, albo niczego nie zmienia. Czas O(n), pamięć O(1).
///
/// **Metody:**
///
/// - [#secondLowest(int\[\])]: druga najmniejsza **różna** wartość; dla `[1, 1, 2]` to 2. Zmienne
///   są typu `long`, żeby `Integer.MAX_VALUE` w danych dało się odróżnić od „jeszcze nic”.
/// - [#mySecondLowest(int\[\])]: pierwsza, samodzielna wersja na indeksach zamiast wartości. Ma
///   błąd: oba indeksy startują od 0, więc gdy minimum stoi na początku, zwraca minimum
///   (`[1, 2]` daje 1).
/// - [#secondLowestWithDuplicates(int\[\])]: powtórzenia liczą się osobno, czyli drugi element
///   posortowanej tablicy; dla `[1, 1, 2]` to 1.
///
/// Mniej niż dwa elementy albo brak drugiej różnej wartości to [IllegalArgumentException].
public class SecondLowestInArrayMain {

    public static int secondLowest(int[] nums) {
        if (nums == null || nums.length < 2) {
            throw new IllegalArgumentException("Need at least two elements");
        }

        long lowest = Long.MAX_VALUE;
        long second = Long.MAX_VALUE;

        for (int n : nums) {
            if (n < lowest) {
                second = lowest;
                lowest = n;
            } else if (n > lowest && n < second) {
                second = n;
            }
        }

        if (second == Long.MAX_VALUE) {
            throw new IllegalArgumentException("All elements are equal");
        }
        return (int) second;
    }

    public static int mySecondLowest(int[] nums) {
        if (nums == null || nums.length < 2) {
            throw new IllegalArgumentException("Need at least two elements");
        }
        int lowestIndex = 0;
        int secondLowestIndex = 0;

        for (int i = 1; i < nums.length; i++) {
            if (nums[i] < nums[lowestIndex]) {
                secondLowestIndex = lowestIndex;
                lowestIndex = i;
            } else if(nums[i] > nums[lowestIndex] && nums[i] < nums[secondLowestIndex]) {
                secondLowestIndex = i;
            }
        }
        return nums[secondLowestIndex];
    }

    public static int secondLowestWithDuplicates(int[] nums) {
        if (nums == null || nums.length < 2) {
            throw new IllegalArgumentException("Need at least two elements");
        }

        long lowest = Long.MAX_VALUE;
        long second = Long.MAX_VALUE;

        for (int n : nums) {
            if (n < lowest) {
                second = lowest;
                lowest = n;
            } else if (n < second) {
                second = n;
            }
        }
        return (int) second;
    }

    void main() {
        int[][] samples = {
                {5, 3, 9, 1, 7},
                {4, 4, 2, 2, 8},
                {-3, -1, -7, -7},
                {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE},
                {2, 1},
        };

        for (int[] sample : samples) {
            IO.println("%-42s -> distinct: %-12d with duplicates: %d".formatted(Arrays.toString(sample),
                    secondLowest(sample),
                    secondLowestWithDuplicates(sample)));
        }
    }
}
