package array;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/// Maksimum w każdym oknie długości k przesuwanym po tablicy.
///
/// **Treść.** Dla `[1, 3, -1, -3, 5, 3, 6, 7]` i `k = 3` okna to
/// `[1 3 -1]`, `[3 -1 -3]`, ..., a ich maksima `[3, 3, 5, 5, 6, 7]`.
///
/// **Pomysł: kolejka monotoniczna ([ArrayDeque]).** Trzymamy indeksy elementów, które
/// _mogą jeszcze_ zostać maksimum. Ich wartości maleją od początku do końca kolejki.
///
/// - Z końca usuwamy elementy nie większe od nowego. Nowy jest od nich większy i zostanie w
///    oknie dłużej, więc one już nigdy nie będą maksimum.
///
/// - Z początku usuwamy indeks, który wypadł z okna.
///
/// - Maksimum okna to zawsze początek kolejki.
///
/// **Złożoność.** O(n) czasu: każdy indeks raz wchodzi do kolejki i najwyżej raz z niej wychodzi.
/// Pamięć O(k). Dla porównania: naiwnie O(n · k), a z `TreeMap` (wartość → licznik) O(n log k).
///
/// **Dopytania.** Minimum: to samo z odwróconym porównaniem. Strumień danych: ta sama kolejka
/// działa online, element po elemencie. Mediana w oknie: dwa kopce albo dwa `TreeSet`.
public class SlidingWindowMaximum {

    public static int[] maxInWindows(int[] a, int k) {
        requireWindowFits(a, k);
        int[] result = new int[a.length - k + 1];
        Deque<Integer> candidates = new ArrayDeque<>();   // indeksy, wartości malejąco
        for (int i = 0; i < a.length; i++) {
            slideTo(candidates, a, i, k);
            if (i >= k - 1) {
                result[i - k + 1] = a[candidates.peekFirst()];
            }
        }
        return result;
    }

    private static void slideTo(Deque<Integer> candidates, int[] a, int i, int k) {
        if (!candidates.isEmpty() && candidates.peekFirst() <= i - k) {
            candidates.pollFirst();
        }
        while (!candidates.isEmpty() && a[candidates.peekLast()] <= a[i]) {
            candidates.pollLast();
        }
        candidates.addLast(i);
    }

    private static void requireWindowFits(int[] a, int k) {
        if (k < 1 || k > a.length) {
            throw new IllegalArgumentException("k = " + k + ", n = " + a.length);
        }
    }

    void main() {
        int[] a = {1, 3, -1, -3, 5, 3, 6, 7};
        IO.println(Arrays.toString(maxInWindows(a, 3)));   // [3, 3, 5, 5, 6, 7]
    }
}
