package collection;

import java.util.Arrays;
import java.util.Comparator;

/// HackerRank „Java Comparator”: posortuj graczy malejąco po wyniku, a przy równym wyniku rosnąco po
/// imieniu. Nazwa `Checker` jest narzucona przez zadanie. Notatka: `docs/collection/checker.md`.
///
/// **Treść.** Dla `amy 100, david 100, heraldo 50, aakansha 75, aleksa 150` wynik to
/// `aleksa 150, amy 100, david 100, aakansha 75, heraldo 50`.
///
/// **Pomysł.** Porównujemy po kolei klucze, dopóki któryś nie rozstrzygnie:
///
/// 1. wynik **malejąco**: `Integer.compare(b.score(), a.score())`, czyli argumenty zamienione
///    miejscami;
/// 1. przy remisie imię **rosnąco**: `a.name().compareTo(b.name())`.
///
/// To samo w jednej linii: `Comparator.comparingInt(Player::score).reversed().thenComparing(Player::name)`.
///
/// **Pułapka.** `b.score() - a.score()` wygląda krócej, ale przepełnia się dla wyników o różnych
/// znakach i dużym module (`Integer.MAX_VALUE` kontra `-1`) i zwraca zły znak.
///
/// **Złożoność.** Porównanie O(długość imienia), sortowanie O(n log n) porównań. `Arrays.sort` na
/// obiektach jest stabilne, więc gracze o tym samym wyniku i imieniu zostają w kolejności wejścia.
public class Checker implements Comparator<Checker.Player> {

    record Player(String name, int score) {
        Player {
            if (name == null) {
                throw new IllegalArgumentException("gracz musi mieć imię");
            }
        }

        @Override
        public String toString() {
            return name + " " + score;
        }
    }

    @Override
    public int compare(Player a, Player b) {
        int byScoreDescending = Integer.compare(b.score(), a.score());
        if (byScoreDescending != 0) {
            return byScoreDescending;
        }
        return a.name().compareTo(b.name());
    }

    void main() {
        Player[] players = {
                new Player("amy", 100),
                new Player("david", 100),
                new Player("heraldo", 50),
                new Player("aakansha", 75),
                new Player("aleksa", 150),
        };
        Arrays.sort(players, this);
        Arrays.stream(players).forEach(IO::println);
    }
}
