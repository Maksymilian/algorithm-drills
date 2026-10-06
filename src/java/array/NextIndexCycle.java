package array;

/// Każdy element tablicy zawiera indeks następnego elementu. Idziemy od indeksu startowego: gdzie
/// przejście zaczyna się powtarzać i jak długie jest to powtórzenie? Dla `tab = {3, 2, 1, 2, 4}` i
/// `start = 0` przejście wygląda tak:
///
/// ```
///     krok  0    1    2    3    4    5
///           0 -> 3 -> 2 -> 1 -> 2 -> 1 -> ...
///                     ^---------'
/// ```
///
/// więc wchodzi w cykl na indeksie 2, po 2 krokach, a cykl ma długość 2:
/// `Cycle[entry=2, stepsBefore=2, length=2]`.
///
/// **Każde takie przejście wpada w cykl, a kształt to zawsze rho.** Tablica to funkcja
/// `f(i) = tab[i]` z `0 .. n-1` w siebie, więc przejście `start, f(start), f(f(start)), ...` ma do
/// dyspozycji tylko n wartości i musi powtórzyć którąś w n+1 krokach. A ponieważ `f` jest _funkcją_
/// (jeden następnik na indeks), od pierwszego powtórzenia wszystko dalej powtarza się identycznie.
/// Przejście to więc ogon `mu` kroków, do którego już nie wraca, i cykl `lambda` indeksów, po którym
/// krąży bez końca: grecka litera rho narysowana przez samo przejście. Nie ma przypadku „brak cyklu”,
/// nie ma czego szukać, są tylko dwie liczby do znalezienia.
///
/// **Trzy sposoby ich znalezienia**, wymieniające czas na pamięć:
///
/// | Metoda | Odczyty `tab` | Dodatkowa pamięć |
/// |---|---|---|
/// | [#findCycle(int\[\], int)] | mu + lambda | 4n bajtów |
/// | [#findCycleInConstantSpace(int\[\], int)] (Floyd) | najwyżej 5mu + 4lambda | **O(1)** |
/// | [#findCycleByBrent(int\[\], int)] (Brent) | najwyżej 4(mu + lambda) + 2 | **O(1)** |
///
/// - [#findCycle(int\[\], int)]: oczywisty sposób. Każdy indeks dostaje znacznik z numerem kroku,
///   w którym go pierwszy raz widzieliśmy (plus jeden, żeby zero nowej tablicy znaczyło „nie
///   widziany”), a obie liczby wypadają z pierwszego indeksu widzianego dwa razy: znacznik to `mu`,
///   odległość do niego to `lambda`.
/// - [#findCycleInConstantSpace(int\[\], int)]: żółw i zając Floyda. Wskaźnik o kroku 1 i wskaźnik
///   o kroku 2 spotykają się na cyklu po `t` krokach, a `lambda` dzieli `t`. Potem jeden wraca na
///   `start` i oba idą po jednym kroku: spotykają się dokładnie na wejściu do cyklu, po `mu`
///   krokach. Na koniec jedno okrążenie mierzy `lambda`.
/// - [#findCycleByBrent(int\[\], int)]: Brent. Żółw stoi, zając biegnie z limitem kroków podwajanym
///   przy każdym przestawieniu żółwia; gdy zając wróci do żółwia, przebyta droga _jest_ `lambda`.
///   Potem wskaźnik z przewagą `lambda` i drugi idą razem do spotkania na wejściu. Na losowych
///   tablicach to około czterech piątych odczytów Floyda.
/// - [#walkToFirstRepeat(int\[\], int)]: samo przejście jako dane, do pierwszego powtórzonego
///   indeksu włącznie: `{3, 2, 1, 2, 4}` od 0 daje `[0, 3, 2, 1, 2]`.
///
/// Pierwszy sposób jako jedyny używa pamięci proporcjonalnej do _tablicy_, a nie do _przejścia_, i
/// w tym jest sens dwóch pozostałych: rho jest zwykle dużo krótsze niż tablica (dla losowego `f`
/// zarówno mu, jak i lambda wynoszą średnio około `sqrt(pi n / 8)`), więc zaalokowanie i wyzerowanie
/// n liczb kosztuje więcej niż samo przejście. Pomiary w `docs/array/next-index-cycle.md`.
///
/// **Wejście jest sprawdzane w trakcie przejścia**, nigdy z góry. Indeks spoza `0 .. n-1` to
/// [IllegalArgumentException], a nie [ArrayIndexOutOfBoundsException], ale dopiero gdy przejście do
/// niego dojdzie: pełne sprawdzenie kosztowałoby O(n) czasu i odebrałoby metodom o stałej pamięci
/// to, po co są. `{1, 0, 999}` od `start = 0` dostaje więc odpowiedź, a nie odmowę, bo przejście
/// nigdy nie czyta elementu 2.
///
/// **Gdzie to spotyka się z [MinimumSwaps].** Tamta klasa też chodzi po cyklach, ale po permutacji,
/// a permutacja to dokładnie przypadek, w którym ogona być nie może: każdy indeks ma jednego
/// poprzednika, więc do żadnego nie da się wejść z dwóch miejsc i `mu` wynosi 0, skądkolwiek
/// zaczniemy. Tutaj `f` jest dowolną funkcją, indeksy mogą mieć dowolnie wielu poprzedników i
/// właśnie ogon jest różnicą.
public class NextIndexCycle {

    public record Cycle(int entry, int stepsBefore, int length) {

        public Cycle {
            if (entry < 0) throw new IllegalArgumentException("entry must be an index, but was " + entry);
            if (stepsBefore < 0) throw new IllegalArgumentException("stepsBefore must not be negative, but was " + stepsBefore);
            if (length < 1) throw new IllegalArgumentException("length must be at least 1, but was " + length);
        }

        public int firstRepeatStep() {
            return stepsBefore + length;
        }
    }

    public static Cycle findCycle(int[] tab, int start) {
        requireWalkable(tab, start);

        int[] firstSeen = new int[tab.length];       // 0 znaczy nieodwiedzony; inaczej numer kroku plus jeden
        int index = start;
        int step = 0;

        while (firstSeen[index] == 0) {
            firstSeen[index] = ++step;               // = (krok na tym indeksie) + 1
            index = nextOf(tab, index);
        }

        int stepsBefore = firstSeen[index] - 1;      // ten indeks już tu był i jest tu znowu
        return new Cycle(index, stepsBefore, step - stepsBefore);
    }

    public static Cycle findCycleInConstantSpace(int[] tab, int start) {
        requireWalkable(tab, start);

        int tortoise = nextOf(tab, start);           // jeden krok dalej
        int hare = nextOf(tab, tortoise);            // dwa kroki dalej
        while (tortoise != hare) {
            tortoise = nextOf(tab, tortoise);
            hare = nextOf(tab, nextOf(tab, hare));
        }

        int stepsBefore = 0;
        tortoise = start;                            // z powrotem na początek; teraz oba robią po jednym kroku
        while (tortoise != hare) {
            tortoise = nextOf(tab, tortoise);
            hare = nextOf(tab, hare);
            stepsBefore++;
        }
        int entry = tortoise;

        int length = 1;
        for (int index = nextOf(tab, entry); index != entry; index = nextOf(tab, index)) {
            length++;
        }
        return new Cycle(entry, stepsBefore, length);
    }

    public static Cycle findCycleByBrent(int[] tab, int start) {
        requireWalkable(tab, start);

        int power = 1;
        int length = 1;
        int tortoise = start;
        int hare = nextOf(tab, start);

        while (tortoise != hare) {
            if (power == length) {                   // limit wyczerpany: przestaw żółwia i podwój limit
                tortoise = hare;
                power = power > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : power * 2;
                length = 0;
            }
            hare = nextOf(tab, hare);
            length++;
        }

        hare = start;                                // lambda znana: daj jednemu wskaźnikowi taką przewagę
        for (int lap = 0; lap < length; lap++) {
            hare = nextOf(tab, hare);
        }

        int stepsBefore = 0;
        tortoise = start;
        while (tortoise != hare) {
            tortoise = nextOf(tab, tortoise);
            hare = nextOf(tab, hare);
            stepsBefore++;
        }
        return new Cycle(tortoise, stepsBefore, length);
    }

    public static int[] walkToFirstRepeat(int[] tab, int start) {
        Cycle cycle = findCycle(tab, start);

        int[] walk = new int[cycle.firstRepeatStep() + 1];
        int index = start;
        for (int step = 0; step < walk.length; step++) {
            walk[step] = index;
            index = tab[index];                      // już sprawdzone przez przejście powyżej
        }
        return walk;
    }

    private static void requireWalkable(int[] tab, int start) {
        if (tab == null) throw new IllegalArgumentException("tab must not be null");
        if (tab.length == 0) throw new IllegalArgumentException("tab must not be empty: there is nowhere to walk");
        if (start < 0 || start >= tab.length) {
            throw new IllegalArgumentException(
                    "start must be an index in 0.." + (tab.length - 1) + " but was " + start);
        }
    }

    private static int nextOf(int[] tab, int index) {
        int next = tab[index];
        if (next < 0 || next >= tab.length) {
            throw new IllegalArgumentException(
                    "tab[" + index + "] must be an index in 0.." + (tab.length - 1) + " but was " + next);
        }
        return next;
    }

    private NextIndexCycle() {
    }
}
