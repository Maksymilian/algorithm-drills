package collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoisonousPlantsTest {

    @Test
    void samplesFromTask() {
        assertEquals(2, PoisonousPlants.poisonousPlants(List.of(6, 5, 8, 4, 7, 10, 9)));
        assertEquals(2, PoisonousPlants.poisonousPlants(List.of(3, 6, 2, 7, 5)));
        assertEquals(3, PoisonousPlants.poisonousPlants(List.of(4, 3, 7, 5, 6, 4, 2)));
    }

    @Test
    void shortInputs() {
        assertEquals(0, PoisonousPlants.poisonousPlants(List.of()), "pusta lista");
        assertEquals(0, PoisonousPlants.poisonousPlants(List.of(5)), "jedna roślina");
        assertEquals(1, PoisonousPlants.poisonousPlants(List.of(1, 2)), "większa po prawej ginie");
        assertEquals(0, PoisonousPlants.poisonousPlants(List.of(2, 1)), "mniejsza po prawej przeżywa");
    }

    @Test
    void equalNeighboursSurvive() {
        assertEquals(0, PoisonousPlants.poisonousPlants(List.of(5, 5, 5, 5)), "równe nie giną");
        assertEquals(3, PoisonousPlants.poisonousPlants(List.of(1, 5, 5, 5)), "równe 5 giną po jednej dziennie");
    }

    @Test
    void monotonicSequences() {
        assertEquals(0, PoisonousPlants.poisonousPlants(List.of(9, 7, 4, 1)), "malejące");
        assertEquals(1, PoisonousPlants.poisonousPlants(List.of(1, 4, 7, 9)), "rosnące giną naraz");
    }

    @Test
    void extremeAndNegativeValues() {
        assertEquals(1, PoisonousPlants.poisonousPlants(List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)));
        assertEquals(0, PoisonousPlants.poisonousPlants(List.of(Integer.MAX_VALUE, Integer.MIN_VALUE)));
        assertEquals(2, PoisonousPlants.poisonousPlants(List.of(-5, -1, -2)), "ujemne");
    }

    @Test
    void oneDeathPerDayOnLargeInput() {
        int size = 100_000;
        List<Integer> plants = new ArrayList<>(List.of(0));
        IntStream.iterate(size - 1, value -> value > 0, value -> value - 1).forEach(plants::add);
        assertEquals(size - 1, PoisonousPlants.poisonousPlants(plants), "0, potem malejące giną po jednej");
    }

    @Test
    void largeIncreasingInputDiesInOneDay() {
        List<Integer> plants = IntStream.range(0, 100_000).boxed().toList();
        assertEquals(1, PoisonousPlants.poisonousPlants(plants));
        assertEquals(0, PoisonousPlants.poisonousPlants(Collections.nCopies(100_000, 7)));
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> PoisonousPlants.poisonousPlants(null));
        assertThrows(NullPointerException.class, () -> PoisonousPlants.poisonousPlants(Arrays.asList(1, null)));
    }

    @Test
    void agreesWithDayByDaySimulationOnRandomData() {
        Random random = new Random(17);
        for (int round = 0; round < 2_000; round++) {
            List<Integer> plants = randomPlants(random);
            assertEquals(simulate(plants), PoisonousPlants.poisonousPlants(plants), plants.toString());
        }
    }

    private static List<Integer> randomPlants(Random random) {
        return IntStream.range(0, random.nextInt(20))
                .mapToObj(index -> random.nextInt(-3, 8))
                .toList();
    }

    private static int simulate(List<Integer> plants) {
        List<Integer> alive = plants;
        int days = 0;
        while (true) {
            List<Integer> next = survivorsOfOneDay(alive);
            if (next.size() == alive.size()) {
                return days;
            }
            alive = next;
            days++;
        }
    }

    private static List<Integer> survivorsOfOneDay(List<Integer> alive) {
        List<Integer> survivors = new ArrayList<>();
        for (int i = 0; i < alive.size(); i++) {
            if (i == 0 || alive.get(i) <= alive.get(i - 1)) {
                survivors.add(alive.get(i));
            }
        }
        return survivors;
    }
}
