package greedy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/// Dwie windy, dwadzieścia pięter, w najmniejszej postaci, którą warto napisać na rozmowie. Notatka:
/// `docs/greedy/simple-elevators.md`.
///
/// 1. **Jedna winda = piętro, kierunek i posortowany zbiór przystanków.** W każdym kroku otwiera
///    drzwi, jeśli to piętro jest przystankiem, jedzie dalej, dopóki przystanek jest przed nią,
///    zawraca, gdy żadnego nie ma, i staje, gdy nie ma przystanków (algorytm LOOK).
///
/// 1. **Wezwanie z piętra dostaje winda, która dojedzie pierwsza.** Wzywający przed windą: sama
///    odległość. Za windą: do jej ostatniego przystanku i z powrotem.
///
/// Celowo pominięte: w którą stronę chce jechać osoba na piętrze, drzwi, pojemność, czas. Każde z
/// nich to dopytanie: [ElevatorSystem] dokłada kierunek, a [TwoElevators] resztę.
public class SimpleElevators {

    public static final int FLOORS = 20;   // 0 .. 19

    public enum Direction { UP, DOWN, IDLE }

    public static final class Elevator {
        final String name;
        int floor = 0;
        Direction direction = Direction.IDLE;
        final TreeSet<Integer> stops = new TreeSet<>();
        final List<Integer> visited = new ArrayList<>();   // piętra, na których się zatrzymała, po kolei

        Elevator(String name) {
            this.name = name;
        }

        public void press(int target) {
            stops.add(target);
        }

        void step() {
            openDoorsIfStop();
            if (stops.isEmpty()) {
                direction = Direction.IDLE;
                return;
            }
            direction = nextDirection();
            floor += direction == Direction.UP ? 1 : -1;
        }

        private void openDoorsIfStop() {
            if (stops.remove(floor)) {
                visited.add(floor);
            }
        }

        private Direction nextDirection() {
            boolean above = stops.higher(floor) != null;
            boolean below = stops.lower(floor) != null;
            return switch (direction) {
                case UP -> above ? Direction.UP : Direction.DOWN;
                case DOWN -> below ? Direction.DOWN : Direction.UP;
                case IDLE -> above ? Direction.UP : Direction.DOWN;
            };
        }
    }

    final List<Elevator> elevators = List.of(new Elevator("A"), new Elevator("B"));

    static int cost(Elevator e, int target) {
        return switch (e.direction) {
            case IDLE -> Math.abs(e.floor - target);
            case UP -> target >= e.floor ? target - e.floor : costViaTop(e, target);
            case DOWN -> target <= e.floor ? e.floor - target : costViaBottom(e, target);
        };
    }

    private static int costViaTop(Elevator e, int target) {
        int top = Math.max(e.floor, e.stops.last());
        return (top - e.floor) + (top - target);
    }

    private static int costViaBottom(Elevator e, int target) {
        int bottom = Math.min(e.floor, e.stops.first());
        return (e.floor - bottom) + (target - bottom);
    }

    public Elevator call(int floor) {
        if (floor < 0 || floor >= FLOORS) {
            throw new IllegalArgumentException("no floor " + floor);
        }
        Elevator best = elevators.stream()
                .min(Comparator.comparingInt(e -> cost(e, floor)))   // przy remisie pierwsza z listy
                .orElseThrow();
        best.press(floor);
        return best;
    }

    public void step() {
        for (Elevator e : elevators) {
            e.step();
        }
    }

    void run(int steps) {
        for (int i = 0; i < steps; i++) {
            step();
        }
    }

    void main() {
        showArrivesFirstNotNearest();
        showSweepOrder();
    }

    private static void showArrivesFirstNotNearest() {
        SimpleElevators building = new SimpleElevators();
        Elevator a = building.elevators.get(0), b = building.elevators.get(1);
        a.press(19);
        building.run(3);
        IO.println("A is at %d going %s, B is idle at %d".formatted(a.floor, a.direction, b.floor));
        IO.println("call at 2: cost for A %d floors, for B %d floors".formatted(cost(a, 2), cost(b, 2)));
        IO.println("sent: " + building.call(2).name + "  (A is nearer, but it must reach 19 first)");
    }

    private static void showSweepOrder() {
        SimpleElevators building = new SimpleElevators();
        Elevator c = building.elevators.get(0);
        List.of(12, 5, 9).forEach(c::press);
        building.run(30);
        IO.println("pressed 12, 5, 9; stopped at " + c.visited);
    }
}
