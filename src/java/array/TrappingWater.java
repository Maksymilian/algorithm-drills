package array;

import java.util.ArrayDeque;
import java.util.Deque;

/// Ile śniegu (albo wody po deszczu) zmieści się między wzgórzami. To jedno zadanie w dwóch wersjach
/// z listy: „snow between hills” i „trapping rain water”.
///
/// **Treść.** Wysokości wzgórz `[0, 1, 2, 1, 0, 3, 1, 2]`, każde szerokości 1. Między 2 i 3
/// mieszczą się 3 jednostki, między 3 i 2 jedna, razem **4**.
///
/// **Kluczowa obserwacja.** Nad słupkiem `i` zmieści się
///
/// ```
/// min(najwyższy po lewej, najwyższy po prawej) − h[i]
/// ```
///
/// bo śnieg zsunie się przez niższą z dwóch ścian. Wliczamy sam słupek, więc wynik nie jest ujemny.
///
/// **Trzy wersje, jak na rozmowie:**
///
/// 1. [#trapWithArrays]: najpierw tablica maksimów od lewej, potem od prawej, potem suma.
///    O(n) czasu i O(n) pamięci. Najłatwiej ją wyjaśnić.
///
/// 1. [#trap]: dwa wskaźniki, O(n) czasu i O(1) pamięci. Przesuwamy wskaźnik stojący przy
///    niższym słupku. Jeśli `h[left] < h[right]`, to po prawej stoi ściana co najmniej
///    `h[right]`, więc wynik dla `left` zależy już tylko od maksimum po lewej, które
///    znamy.
///
/// 1. [#trapWithDeque]: stos na [ArrayDeque], O(n) czasu i O(n) pamięci. Liczy
///    śnieg poziomymi warstwami, w chwili, gdy po prawej pojawi się wyższa ściana.
///
/// **Dopytania.** Wersja 2D (mapa wysokości): kolejka priorytetowa od brzegu do środka,
/// O(n·m·log(n·m)).
public class TrappingWater {

    public static long trapWithArrays(int[] h) {
        int[] maxLeft = maxFromLeft(h), maxRight = maxFromRight(h);
        long total = 0;
        for (int i = 0; i < h.length; i++) {
            total += Math.min(maxLeft[i], maxRight[i]) - h[i];
        }
        return total;
    }

    private static int[] maxFromLeft(int[] h) {
        int[] max = new int[h.length];
        for (int i = 0; i < h.length; i++) {
            max[i] = Math.max(i > 0 ? max[i - 1] : 0, h[i]);
        }
        return max;
    }

    private static int[] maxFromRight(int[] h) {
        int[] max = new int[h.length];
        for (int i = h.length - 1; i >= 0; i--) {
            max[i] = Math.max(i < h.length - 1 ? max[i + 1] : 0, h[i]);
        }
        return max;
    }

    public static long trap(int[] h) {
        int left = 0, right = h.length - 1, level = 0;
        long total = 0;
        while (left < right) {
            int lower = h[left] < h[right] ? h[left++] : h[right--];
            level = Math.max(level, lower);
            total += level - lower;
        }
        return total;
    }

    public static long trapWithDeque(int[] h) {
        Deque<Integer> walls = new ArrayDeque<>();   // indeksy, wysokości nierosnąco od dna stosu
        long total = 0;
        for (int i = 0; i < h.length; i++) {
            while (!walls.isEmpty() && h[walls.peek()] < h[i]) {
                total += layerAbove(h, walls, i);
            }
            walls.push(i);
        }
        return total;
    }

    private static long layerAbove(int[] h, Deque<Integer> walls, int i) {
        int bottom = walls.pop();
        if (walls.isEmpty()) {
            return 0;   // brak lewej ściany: śnieg zsuwa się poza mapę
        }
        int left = walls.peek();
        return (long) (i - left - 1) * (Math.min(h[left], h[i]) - h[bottom]);
    }

    void main() {
        IO.println(trap(new int[]{0, 1, 2, 1, 0, 3, 1, 2}));                // 4
        IO.println(trap(new int[]{0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1}));    // 6
        IO.println(trapWithDeque(new int[]{0, 1, 2, 1, 0, 3, 1, 2}));       // 4
    }
}
