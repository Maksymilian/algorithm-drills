package spatial;

import java.util.List;

/// Miliardy punktów o współrzędnych całkowitych na płaszczyźnie, zbudowane raz i odpytywane wiele
/// razy: **ile z nich leży w tym kole?**
///
/// Najważniejsze słowo w zadaniu to _miliardy_. Rozstrzyga reprezentację, zanim wybierzemy algorytm,
/// i decyduje, który algorytm w ogóle warto mieć.
///
/// **1. Nic tu nie może być obiektem.** W C++ `vector<pair<int,int>>` z treści to już osiem bajtów na
/// punkt w ciągłej pamięci, dokładnie tak, jak trzeba. Przeniesione dosłownie do Javy staje się
/// `List<int[]>`: dwa miliardy małych tablic, każda z własnym nagłówkiem, za dwoma miliardami
/// referencji, czyli 32 do 40 bajtów na punkt, 64 GB i więcej na 16 GB współrzędnych, a każdy z tych
/// obiektów garbage collector musi przejść. Te same punkty w dwóch tablicach `int[]` to dokładnie 8
/// bajtów na punkt, w ciągłej pamięci, i dwa obiekty w sumie. [#of(List)] przyjmuje sygnaturę z
/// treści w zapisie Javy; [#PointCloud(int\[\], int\[\])] to konstruktor do użycia w tej skali i
/// przejmuje tablice na własność zamiast je kopiować, bo kopia to kolejne 16 GB. Drugi konstruktor
/// przyjmuje docelową liczbę punktów na komórkę (domyślnie 64): małe komórki to mniej punktów
/// sprawdzanych na brzegu koła, ale więcej komórek do odwiedzenia.
///
/// **2. Przeglądanie wszystkiego odpada.** Przejście po dwóch miliardach punktów to kilka sekund
/// przepustowości pamięci na zapytanie: dobre raz, beznadziejne dla zapytań zadawanych często.
/// Zostaje jako [#countInCircleByScan(int, int, int)], bo to uczciwa odpowiedź na jedno zapytanie i
/// wzorzec, z którym porównujemy dwie pozostałe metody.
///
/// **3. Konstruktor buduje więc indeks**: jednorodną siatkę, w trzech tablicach i bez obiektów:
///
/// ```
///     xs, ys        punkty, przestawione w miejscu tak, że punkty jednej komórki leżą obok siebie
///     cellStart     gdzie w xs/ys zaczynają się punkty każdej komórki: cells + 1 liczb, wierszami
/// ```
///
/// Punkty trafiają do komórek w dwóch przejściach: najpierw zliczenie punktów w każdej komórce do
/// sum bieżących, potem permutacja obu tablic w miejscu (sortowanie przez zliczanie pisałoby do
/// drugiej pary tablic, czyli kolejnych 16 GB). Rozmiar komórki to potęga dwójki, więc indeks
/// komórki to przesunięcie bitowe, a nie dzielenie, a liczba komórek jest ograniczona do 2^26
/// (256 MB indeksu).
///
/// `cellStart` robi dwie rzeczy naraz i to jest cała sztuczka tej klasy. Czytana jako granice mówi,
/// gdzie są punkty komórki. Czytana jako to, czym jest, czyli suma bieżąca, odpowiada na pytanie _ile
/// punktów leży w komórkach i..k wiersza j_ jednym odejmowaniem, `cellStart[jk + 1] - cellStart[ji]`,
/// bo komórki wiersza leżą obok siebie w porządku wierszowym. Zliczenie całego wiersza wnętrza koła
/// kosztuje więc tyle, co jednej komórki, i _nic na punkt_.
///
/// **Zapytanie nigdy nie patrzy na punkty, które liczy**, tylko na te, co do których nie ma
/// pewności. Wiersz komórek po wierszu:
///
/// - komórki, których wszystkie narożniki są w kole, dodajemy jednym odejmowaniem za cały ich ciąg;
///
/// - otwieramy tylko komórki, przez które przechodzi brzeg koła, i sprawdzamy ich punkty po kolei.
///
/// Takich komórek brzegowych jest O(r/s) dla promienia `r` i rozmiaru komórki `s` (to obwód mierzony
/// w komórkach), więc praca jest proporcjonalna do **obwodu** koła i punktów przy nim, nigdy do pola
/// ani do punktów w środku. Zmierzone na miliardzie punktów: koło zawierające 785 milionów z nich
/// dostaje odpowiedź po przeczytaniu mniej więcej jednej tysięcznej tej liczby.
///
/// | Metoda | Sprawdzone punkty | Odwiedzone komórki |
/// |---|---|---|
/// | [#countInCircleByScan(int, int, int)] | n | - |
/// | [#countInCircleByCells(int, int, int)] | O(c r/s) | O((r/s)^2) |
/// | [#countInCircle(int, int, int)] | O(c r/s) | **O(r/s)** |
///
/// Dwie ostatnie różnią się tylko sposobem sumowania wnętrza: komórka po komórce albo cały wiersz
/// naraz przez sumy bieżące. Obie omijają _punkty_ wnętrza; tylko druga omija też _komórki_ wnętrza.
/// Punkt leżący dokładnie na okręgu liczy się jako należący do koła. Pomiary w
/// `docs/spatial/points-in-a-circle.md`.
///
/// **Dokładna arytmetyka wszędzie.** Koło to `dx*dx + dy*dy <= r*r` i nic więcej: żadnych
/// pierwiastków z odległości, żadnych `double` tam, gdzie zależy od nich porównanie. Współrzędne
/// obejmują cały zakres `int`, więc `dx` potrzebuje `long`, a `dx*dx` przepełniłoby nawet `long`;
/// każde sprawdzenie odrzuca więc najpierw `|dx| > r`, zanim cokolwiek podniesie do kwadratu, co
/// ogranicza iloczyn do `(2^31 - 1)^2`, a sumę do odrobinę poniżej `Long.MAX_VALUE`. Jedyny
/// pierwiastek, [#isqrt(long)], jest poprawiany do dokładnej podłogi całkowitej, zanim zostanie
/// użyty, bo `double` ma 53 bity mantysy, a `v` do 62.
///
/// **Ograniczenia.** Tablica w Javie mieści najwyżej `Integer.MAX_VALUE` elementów, więc jedna
/// instancja mieści najwyżej 2 147 483 647 punktów, czyli 17 GB współrzędnych. Powyżej tablice
/// trzeba by podzielić na bloki, co zmienia każde wyrażenie indeksowe w tej klasie i nic w
/// algorytmie. Siatka jest jednorodna, więc pasuje do punktów rozproszonych; dla danych mocno
/// skupionych drzewo k-d odpowiada na to samo zapytanie tym samym skrótem „całe poddrzewo jest w
/// środku” i bez tego założenia; zob. notatkę.
public class PointCloud {

    private static final int DEFAULT_POINTS_PER_CELL = 64;

    private static final int MAX_CELLS = 1 << 26;

    private final int[] xs;
    private final int[] ys;
    private final int[] cellStart;               // cells + 1 sum bieżących, wierszami: przesunięcia i sumy prefiksowe naraz
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int shift;                     // rozmiar komórki to 1 << shift, więc indeks komórki to przesunięcie, nie dzielenie
    private final int cols;
    private final int rows;

    public PointCloud(int[] xs, int[] ys) {
        this(xs, ys, DEFAULT_POINTS_PER_CELL);
    }

    public PointCloud(int[] xs, int[] ys, int targetPointsPerCell) {
        if (xs == null || ys == null) throw new IllegalArgumentException("xs and ys must not be null");
        if (xs.length != ys.length) {
            throw new IllegalArgumentException(
                    "xs and ys must be the same length, but were " + xs.length + " and " + ys.length);
        }
        if (targetPointsPerCell < 1) {
            throw new IllegalArgumentException("targetPointsPerCell must be at least 1, but was " + targetPointsPerCell);
        }

        this.xs = xs;
        this.ys = ys;
        int n = xs.length;

        int loX = Integer.MAX_VALUE, hiX = Integer.MIN_VALUE;
        int loY = Integer.MAX_VALUE, hiY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (xs[i] < loX) loX = xs[i];
            if (xs[i] > hiX) hiX = xs[i];
            if (ys[i] < loY) loY = ys[i];
            if (ys[i] > hiY) hiY = ys[i];
        }
        if (n == 0) {                            // pusta chmura też musi odpowiadać na pytania
            loX = loY = 0;
            hiX = hiY = 0;
        }
        this.minX = loX;
        this.minY = loY;
        this.maxX = hiX;
        this.maxY = hiY;

        this.shift = chooseShift((long) hiX - loX, (long) hiY - loY, cellBudget(n, targetPointsPerCell));
        this.cols = columnsAt((long) hiX - loX, shift);
        this.rows = columnsAt((long) hiY - loY, shift);

        this.cellStart = new int[cols * rows + 1];
        countIntoCellStart();
        movePointsIntoCellOrder();
    }

    public static PointCloud of(List<int[]> points) {
        if (points == null) throw new IllegalArgumentException("points must not be null");

        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            int[] point = points.get(i);
            if (point == null || point.length != 2) {
                throw new IllegalArgumentException("points[" + i + "] must be an {x, y} pair");
            }
            xs[i] = point[0];
            ys[i] = point[1];
        }
        return new PointCloud(xs, ys);
    }

    public long countInCircle(int centreX, int centreY, int radius) {
        requireRadius(radius);
        if (xs.length == 0) return 0;

        long cx = centreX, cy = centreY, r = radius;
        long size = 1L << shift;
        long total = 0;

        int lastRow = lastRow(cy + r);
        for (int row = firstRow(cy - r); row <= lastRow; row++) {
            long y0 = minY + (long) row * size;             // pas y tego wiersza, włącznie
            long y1 = y0 + size - 1;
            long dyNear = nearDistance(y0, y1, cy);
            if (dyNear > r) continue;                       // pas w ogóle nie trafia w koło
            long dyFar = farDistance(y0, y1, cy);

            // Dokąd koło sięga w x: przy najbliższej krawędzi pasa i przy najdalszej.
            long reach = isqrt(r * r - dyNear * dyNear);
            int from = firstColumn(cx - reach);
            int to = lastColumn(cx + reach);
            if (from > to) continue;

            int base = row * cols;
            int insideFrom = to + 1, insideTo = to;         // pusty ciąg, chyba że następne linie go poszerzą
            if (dyFar <= r) {
                long safe = isqrt(r * r - dyFar * dyFar);   // każdy punkt komórki w tym zakresie x jest w kole
                long first = Math.max(from, Math.ceilDiv(cx - safe - minX, size));
                long last = Math.min(to, Math.floorDiv(cx + safe + 1 - minX, size) - 1);
                if (first <= last) {                        // teraz oba są w [from, to], więc oba są intami:
                    insideFrom = (int) first;               // same ilorazy nie musiały być
                    insideTo = (int) last;
                }
            }

            if (insideFrom <= insideTo) {                   // cały ciąg, policzony bez czytania punktów
                total += cellStart[base + insideTo + 1] - cellStart[base + insideFrom];
                total += countPointsOf(base + from, base + insideFrom - 1, cx, cy, r);
                total += countPointsOf(base + insideTo + 1, base + to, cx, cy, r);
            } else {
                total += countPointsOf(base + from, base + to, cx, cy, r);
            }
        }
        return total;
    }

    public long countInCircleByCells(int centreX, int centreY, int radius) {
        requireRadius(radius);
        if (xs.length == 0) return 0;

        long cx = centreX, cy = centreY, r = radius;
        long size = 1L << shift;
        long total = 0;

        int fromColumn = firstColumn(cx - r);
        int toColumn = lastColumn(cx + r);
        int lastRow = lastRow(cy + r);
        for (int row = firstRow(cy - r); row <= lastRow; row++) {
            long y0 = minY + (long) row * size;
            long y1 = y0 + size - 1;
            int base = row * cols;

            for (int column = fromColumn; column <= toColumn; column++) {
                int cell = base + column;
                int begin = cellStart[cell], end = cellStart[cell + 1];
                if (begin == end) continue;                 // pusta komórka: w żadnym przypadku nie ma czego liczyć

                long x0 = minX + (long) column * size;
                long x1 = x0 + size - 1;
                if (!inDisk(nearDistance(x0, x1, cx), nearDistance(y0, y1, cy), r)) continue;

                if (inDisk(farDistance(x0, x1, cx), farDistance(y0, y1, cy), r)) {
                    total += end - begin;                   // cała w środku, więc jej punktów nie czytamy
                } else {
                    total += countPoints(begin, end, cx, cy, r);
                }
            }
        }
        return total;
    }

    public long countInCircleByScan(int centreX, int centreY, int radius) {
        requireRadius(radius);
        return countPoints(0, xs.length, centreX, centreY, radius);
    }

    public int size() {
        return xs.length;
    }

    public long cellSize() {
        return 1L << shift;
    }

    public int cells() {
        return cols * rows;
    }

    // --- indeks ----------------------------------------------------------------------------------------

    private static int cellBudget(int n, int targetPointsPerCell) {
        if (n == 0) return 1;
        return (int) Math.min(MAX_CELLS, Math.max(1, n / (long) targetPointsPerCell));
    }

    private static int chooseShift(long extentX, long extentY, int budget) {
        for (int shift = 0; shift < 32; shift++) {
            long columns = (extentX >>> shift) + 1;
            long rows = (extentY >>> shift) + 1;
            if (columns <= budget / rows) return shift;      // czyli columns * rows <= budget, bez przepełnienia
        }
        return 32;                                          // jedna komórka: zakresu nie da się już podzielić
    }

    private static int columnsAt(long extent, int shift) {
        return shift == 32 ? 1 : (int) ((extent >>> shift) + 1);
    }

    private void countIntoCellStart() {
        for (int i = 0; i < xs.length; i++) {
            cellStart[cellOf(xs[i], ys[i]) + 1]++;
        }
        for (int cell = 0; cell < cellStart.length - 1; cell++) {
            cellStart[cell + 1] += cellStart[cell];
        }
    }

    private void movePointsIntoCellOrder() {
        int[] next = cellStart.clone();                     // gdzie trafi następny punkt każdej komórki
        for (int cell = 0; cell < cells(); cell++) {
            while (next[cell] < cellStart[cell + 1]) {
                int here = next[cell];
                int home = cellOf(xs[here], ys[here]);
                if (home == cell) {
                    next[cell]++;                           // już jest na swoim miejscu
                    continue;
                }
                int there = next[home]++;
                int x = xs[here];
                xs[here] = xs[there];
                xs[there] = x;
                int y = ys[here];
                ys[here] = ys[there];
                ys[there] = y;
            }
        }
    }

    private int cellOf(int x, int y) {
        int column = (int) ((x - (long) minX) >>> shift);
        int row = (int) ((y - (long) minY) >>> shift);
        return row * cols + column;
    }

    // --- geometria, w liczbach całkowitych -----------------------------------------------------------

    private long countPointsOf(int fromCell, int toCell, long cx, long cy, long r) {
        if (fromCell > toCell) return 0;
        return countPoints(cellStart[fromCell], cellStart[toCell + 1], cx, cy, r);
    }

    private long countPoints(int from, int to, long cx, long cy, long r) {
        long found = 0;
        for (int i = from; i < to; i++) {
            long dx = xs[i] - cx;
            if (dx > r || dx < -r) continue;                // a co równie ważne, dx*dx nie może już przepełnić
            long dy = ys[i] - cy;
            if (dy > r || dy < -r) continue;
            if (dx * dx + dy * dy <= r * r) found++;
        }
        return found;
    }

    private static boolean inDisk(long dx, long dy, long r) {
        return dx <= r && dy <= r && dx * dx + dy * dy <= r * r;
    }

    private static long nearDistance(long lo, long hi, long c) {
        if (c < lo) return lo - c;
        if (c > hi) return c - hi;
        return 0;
    }

    private static long farDistance(long lo, long hi, long c) {
        return Math.max(Math.abs(lo - c), Math.abs(hi - c));
    }

    private static long isqrt(long v) {
        long root = (long) Math.sqrt((double) v);
        while (root > 0 && root * root > v) root--;
        while ((root + 1) * (root + 1) <= v) root++;
        return root;
    }

    // --- krawędzie siatki ----------------------------------------------------------------------------

    private int firstRow(long y) {
        return y <= minY ? 0 : (int) ((Math.min(y, maxY) - (long) minY) >>> shift);
    }

    private int lastRow(long y) {
        return y >= maxY ? rows - 1 : (y < minY ? -1 : (int) ((y - (long) minY) >>> shift));
    }

    private int firstColumn(long x) {
        return x <= minX ? 0 : (int) ((Math.min(x, maxX) - (long) minX) >>> shift);
    }

    private int lastColumn(long x) {
        return x >= maxX ? cols - 1 : (x < minX ? -1 : (int) ((x - (long) minX) >>> shift));
    }

    private static void requireRadius(int radius) {
        if (radius < 0) throw new IllegalArgumentException("radius must not be negative, but was " + radius);
    }
}
