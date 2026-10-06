package array;

import java.util.Arrays;

/// HackerRank „Minimum Swaps 2”: tablica zawiera `1, 2, ... n` w jakiejś kolejności; policz
/// najmniejszą liczbę zamian dowolnych par, która ją sortuje.
///
/// Nic tu nie sortuje po to, żeby liczyć. Tablica _jest_ zapisaną permutacją, a odpowiedź to
/// własność tej permutacji. Czytaj pozycję `i` jako wskaźnik na pozycję, na której jej wartość
/// powinna stać:
///
/// ```
///     sigma(i) = arr[i] - 1
/// ```
///
/// Ta mapa jest permutacją `0 .. n-1`, więc rozpada się na rozłączne cykle, i
///
/// ```
///     najmniej zamian = n - (liczba cykli)
/// ```
///
/// przy czym punkt stały liczy się jako cykl długości jeden. Obie strony tej równości to dwie linijki:
///
/// - **Nie mniej.** Jedna zamiana zmienia liczbę cykli dokładnie o jeden: zamiana dwóch elementów
///    tego samego cyklu dzieli go na dwa, a zamiana elementów różnych cykli je łączy. Posortowana
///    tablica to permutacja z `n` cyklami, więc z `c` cykli nie da się do niej dojść w mniej niż
///    `n - c` zamianach.
///
/// - **Nie więcej.** Cykl długości `k` sortuje się w `k - 1` zamianach: wyślij dowolny element na
///    jego miejsce, a reszta to cykl długości `k - 1`. Suma po wszystkich cyklach to
///    `sum(k_i - 1) = n - c`.
///
/// Ograniczenie jest więc dokładne, a zadanie to liczenie cykli: O(n), bez porównań.
///
/// **To nie jest liczba, którą poda sortowanie.** Liczba zamian w sortowaniu bąbelkowym odpowiada na
/// inne pytanie, bo bąbelkowe zamienia tylko _sąsiednie_ elementy; ta liczba to liczba inwersji. Na
/// odwróconej tablicy obie rozjeżdżają się najbardziej: `n(n-1)/2` inwersji wobec `floor(n/2)` zamian
/// dowolnych par, czyli kwadratowo wobec liniowo. Sortowanie przez wybieranie jest tu za to
/// optymalne i właśnie tym jest [#minimumSwapsInPlace(int\[\])].
///
/// **Pięć sposobów liczenia cykli**, wymieniających czas procesora na pamięć:
///
/// | Metoda | Czas | Dodatkowa pamięć | Argument po wywołaniu |
/// |---|---|---|---|
/// | [#minimumSwaps(int\[\])] | O(n) | n bajtów | nietknięty |
/// | [#minimumSwapsInPlace(int\[\])] | O(n) | **O(1)** | **posortowany** |
/// | [#minimumSwapsMarkingSigns(int\[\])] | O(n) | **O(1)** | nietknięty |
/// | [#minimumSwapsByUnionFind(int\[\])] | O(n a(n)) | 9n bajtów | nietknięty |
/// | [#minimumSwapsOfAnyDistinctValues(int\[\])] | O(n log n) | 9n bajtów | nietknięty |
///
/// Ciekawe są pierwsze trzy: to samo liniowe przejście, a za bit „czy już tu byłem?” płacą tablicą
/// pomocniczą, bitem znaku w samej tablicy albo wcale, zużywając wejście. Union-find przegrywa na obu
/// osiach i jest tu dla tego, co uogólnia, a nie dla kosztu; zob. `docs/array/minimum-swaps.md`.
///
/// **Poza ograniczeniami zadania.** Zadanie gwarantuje permutację `1 .. n`, a pierwsze cztery metody
/// tego pilnują: wartość spoza zakresu albo powtórzenie to [IllegalArgumentException], a nie zła
/// odpowiedź ani, w przejściu w miejscu, które na powtórzonej wartości kręciłoby się w
/// nieskończoność, zawieszenie. [#minimumSwapsOfAnyDistinctValues(int\[\])] porzuca to założenie i
/// przyjmuje dowolne różne `int`y, za cenę sortowania. Poza zakresem zostają tylko powtórzone
/// _wartości_, i to z powodu: równe elementy sprawiają, że przypisanie wartości do miejsc nie jest
/// jednoznaczne, więc odpowiedź przestaje być liczbą cykli i staje się maksimum po możliwych
/// przypisaniach.
///
/// **Metody:**
///
/// - [#minimumSwaps(int\[\])]: sygnatura z HackerRank; przejście po cyklach z tablicą odwiedzin.
///   Przy okazji sprawdza, czy to permutacja: przejście, które nie wraca do swojego początku,
///   znalazło powtórzenie.
/// - [#minimumSwapsInPlace(int\[\])]: sortowanie przez wybieranie bez szukania, bo `arr[i]` mówi,
///   gdzie należy. Wykonane zamiany to najkrótszy ciąg; wejście zostaje posortowane.
/// - [#minimumSwapsMarkingSigns(int\[\])]: bit „odwiedzony” to znak liczby w samej tablicy;
///   `finally` przywraca znaki także przy wyjątku. Na losowej permutacji przy n = 10^7 jest szybsza
///   od wersji z tablicą odwiedzin.
/// - [#minimumSwapsByUnionFind(int\[\])]: najdroższa, ale odpowiada na pytanie o zamiany
///   **ograniczone** do danych par: składowe mówią, co da się posortować. Samej liczby zamian już
///   nie (to problem NP-trudny).
/// - [#minimumSwapsOfAnyDistinctValues(int\[\])]: dowolne różne liczby; rangi z sortowania kopii,
///   O(n log n).
public class MinimumSwaps {

    public static int minimumSwaps(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int n = arr.length;
        boolean[] visited = new boolean[n];
        int swaps = 0;

        for (int start = 0; start < n; start++) {
            if (visited[start]) continue;

            int position = start;
            int length = 0;
            while (!visited[position]) {
                visited[position] = true;
                position = destinationOf(arr[position], n);
                length++;
            }
            if (position != start) throw duplicateAt(position, arr);

            swaps += length - 1;               // cykl długości k kosztuje k - 1
        }
        return swaps;
    }

    public static int minimumSwapsInPlace(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int n = arr.length;
        int swaps = 0;

        for (int i = 0; i < n; i++) {
            while (arr[i] != i + 1) {
                int home = destinationOf(arr[i], n);
                if (arr[home] == arr[i]) throw duplicateAt(home, arr);   // inaczej to by się nie skończyło

                int displaced = arr[home];
                arr[home] = arr[i];
                arr[i] = displaced;
                swaps++;
            }
        }
        return swaps;
    }

    public static int minimumSwapsMarkingSigns(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");

        int n = arr.length;
        for (int value : arr) {
            destinationOf(value, n);           // sprawdzenie zakresu, zanim jakikolwiek znak stanie się niejednoznaczny
        }

        int swaps = 0;
        try {
            for (int start = 0; start < n; start++) {
                if (arr[start] < 0) continue;

                int position = start;
                int length = 0;
                while (arr[position] > 0) {
                    int value = arr[position];
                    arr[position] = -value;    // oznacz, nie gubiąc wartości
                    position = value - 1;
                    length++;
                }
                if (position != start) throw duplicateAt(position, arr);

                swaps += length - 1;
            }
        } finally {
            for (int i = 0; i < n; i++) {
                if (arr[i] < 0) arr[i] = -arr[i];
            }
        }
        return swaps;
    }

    public static int minimumSwapsByUnionFind(int[] arr) {
        if (arr == null) throw new IllegalArgumentException("arr must not be null");
        int n = arr.length;

        // Union-find łączy po cichu: powtórzona wartość połączyłaby krawędź dwa razy i po prostu by
        // przepadła, więc w odróżnieniu od przejść po cyklach ta metoda nie sprawdza danych po drodze.
        requirePermutation(arr);

        int[] parent = new int[n];
        int[] size = new int[n];
        Arrays.setAll(parent, i -> i);
        Arrays.fill(size, 1);

        int components = n;
        for (int i = 0; i < n; i++) {
            int a = find(parent, i);
            int b = find(parent, destinationOf(arr[i], n));
            if (a == b) continue;

            if (size[a] < size[b]) {           // łączenie według rozmiaru, żeby drzewa były płytkie
                int swap = a;
                a = b;
                b = swap;
            }
            parent[b] = a;
            size[a] += size[b];
            components--;
        }
        return n - components;
    }

    public static int minimumSwapsOfAnyDistinctValues(int[] a) {
        if (a == null) throw new IllegalArgumentException("a must not be null");
        int n = a.length;

        int[] sorted = a.clone();
        Arrays.sort(sorted);
        for (int i = 1; i < n; i++) {
            if (sorted[i] == sorted[i - 1]) {
                throw new IllegalArgumentException("values must be distinct, but " + sorted[i] + " repeats");
            }
        }

        int[] destination = new int[n];
        for (int i = 0; i < n; i++) {
            destination[i] = Arrays.binarySearch(sorted, a[i]);   // ranga a[i], czyli jego miejsce
        }

        boolean[] visited = new boolean[n];
        int swaps = 0;
        for (int start = 0; start < n; start++) {
            if (visited[start]) continue;

            int length = 0;
            for (int position = start; !visited[position]; position = destination[position]) {
                visited[position] = true;
                length++;
            }
            swaps += length - 1;
        }
        return swaps;
    }

    private static int destinationOf(int value, int n) {
        if (value < 1 || value > n) {
            throw new IllegalArgumentException(
                    "arr must hold 1.." + n + " but found " + value);
        }
        return value - 1;
    }

    private static IllegalArgumentException duplicateAt(int position, int[] arr) {
        return new IllegalArgumentException(
                "arr must hold 1.." + arr.length + " without duplicates, but " + (position + 1) + " repeats");
    }

    private static void requirePermutation(int[] arr) {
        int n = arr.length;
        boolean[] seen = new boolean[n];
        for (int value : arr) {
            int home = destinationOf(value, n);
            if (seen[home]) throw duplicateAt(home, arr);
            seen[home] = true;
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private MinimumSwaps() {
    }
}
