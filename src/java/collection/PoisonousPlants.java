package collection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/// Po ilu dniach rośliny przestają umierać, jeśli każdego dnia ginie każda roślina z większą
/// ilością pestycydu niż jej lewy sąsiad (HackerRank „Poisonous Plants”).
///
/// **Pomysł: stos par (pestycyd, dzień śmierci).**
///
/// Zamiast symulować dni, liczymy dzień śmierci każdej rośliny. Roślina ginie dzień po tym, jak
/// znikną wszystkie rośliny między nią a najbliższą mniejszą z lewej; gdy mniejszej nie ma,
/// przeżywa. Przechodzimy od lewej do prawej i dla bieżącej rośliny:
/// 1. zdejmujemy ze stosu rośliny z pestycydem `≥` bieżącego, pamiętając największy ich dzień;
/// 2. pusty stos oznacza, że roślina przeżywa (dzień 0), inaczej ginie w dniu `max + 1`;
/// 3. wkładamy roślinę na stos.
///
/// Wynik to największy dzień śmierci. Rysunek i uzasadnienie: `docs/collection/poisonous-plants.md`.
///
/// **Założenia.**
///
/// | | |
/// |---|---|
/// | Lista lub element `null` | `NullPointerException` |
/// | Pusta lista | poprawne wejście, wynik 0 |
/// | Ilości pestycydu | dowolne `int`, także 0 i ujemne |
/// | Równe ilości | roślina nie ginie, warunek jest ostry |
///
/// **Złożoność.** O(n) czasu, bo każda roślina trafia na stos i schodzi z niego najwyżej raz;
/// O(n) pamięci.
public class PoisonousPlants {

    private record Plant(int pesticide, int deathDay) {
    }

    public static int poisonousPlants(List<Integer> pesticides) {
        requirePesticides(pesticides);
        Deque<Plant> plantsOnLeft = new ArrayDeque<>();
        int lastDeathDay = 0;
        for (int pesticide : pesticides) {
            int deathDay = popShadowedAndGetDeathDay(plantsOnLeft, pesticide);
            plantsOnLeft.push(new Plant(pesticide, deathDay));
            lastDeathDay = Math.max(lastDeathDay, deathDay);
        }
        return lastDeathDay;
    }

    private static int popShadowedAndGetDeathDay(Deque<Plant> plantsOnLeft, int pesticide) {
        int latestDeathBetween = 0;
        while (isTopAtLeast(plantsOnLeft, pesticide)) {
            latestDeathBetween = Math.max(latestDeathBetween, plantsOnLeft.pop().deathDay());
        }
        if (plantsOnLeft.isEmpty()) {
            return 0;
        }
        return latestDeathBetween + 1;
    }

    // roślina na szczycie nie jest mniejsza, więc nie zabije bieżącej ani żadnej dalej na prawo
    private static boolean isTopAtLeast(Deque<Plant> plantsOnLeft, int pesticide) {
        return !plantsOnLeft.isEmpty() && plantsOnLeft.peek().pesticide() >= pesticide;
    }

    private static void requirePesticides(List<Integer> pesticides) {
        Objects.requireNonNull(pesticides, "brak listy roślin");
        for (Integer pesticide : pesticides) {
            Objects.requireNonNull(pesticide, "brak ilości pestycydu w liście");
        }
    }

    void main() {
        IO.println(poisonousPlants(List.of(6, 5, 8, 4, 7, 10, 9)));
        IO.println(poisonousPlants(List.of(3, 6, 2, 7, 5)));
        IO.println(poisonousPlants(List.of(4, 3, 7, 5, 6, 4, 2)));
    }
}
