package greedy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/// Dwie windy, dwadzieścia pięter: wersja do wyjaśnienia przy tablicy. Pełna symulacja, z czasami,
/// pojemnością i pomiarami, to [TwoElevators]; tu zostały tylko dwa pomysły.
///
/// 1. **Każda winda zamiata (algorytm LOOK).** Trzyma dwa posortowane zbiory pięter: przystanki do
///    zrobienia w drodze w górę i w drodze w dół. Jedzie dalej, dopóki cokolwiek jest przed nią, i
///    zawraca dopiero, gdy nic nie ma. `TreeSet.higher/lower` odpowiada na pytanie „czy coś jest
///    przede mną?” w O(log n).
///
/// 1. **Dyspozytor wysyła windę, która dojedzie pierwsza, a nie najbliższą.** Winda piętro dalej,
///    która właśnie minęła wzywającego i jedzie na 19, jest najdalej ze wszystkich.
///
/// Jeden [#step()] przesuwa każdą windę o jedno piętro. Nie ma drzwi ani zegara w sekundach.
public class ElevatorSystem {

    public static final int FLOORS = 20;   // 0 (parter) .. 19

    public enum Direction { UP, DOWN, IDLE }

    public static final class Elevator {
        final String name;
        int floor;
        Direction direction = Direction.IDLE;
        final TreeSet<Integer> upStops = new TreeSet<>();     // obsługiwane w drodze w górę
        final TreeSet<Integer> downStops = new TreeSet<>();   // obsługiwane w drodze w dół
        final List<Integer> visited = new ArrayList<>();      // piętra, na których się zatrzymała, po kolei

        Elevator(String name, int floor) {
            this.name = name;
            this.floor = floor;
        }

        void pressButtonInside(int target) {
            if (target > floor) {
                upStops.add(target);
            } else if (target < floor) {
                downStops.add(target);
            }
        }

        void assignHallCall(int callFloor, Direction wanted) {
            (wanted == Direction.UP ? upStops : downStops).add(callFloor);
        }

        boolean anythingAbove() {
            return upStops.higher(floor) != null || downStops.higher(floor) != null;
        }

        boolean anythingBelow() {
            return upStops.lower(floor) != null || downStops.lower(floor) != null;
        }

        boolean shouldStopHere() {
            return switch (direction) {
                case UP -> upStops.contains(floor) || downStops.contains(floor) && !anythingAbove();
                case DOWN -> downStops.contains(floor) || upStops.contains(floor) && !anythingBelow();
                case IDLE -> upStops.contains(floor) || downStops.contains(floor);
            };
        }

        void step() {
            if (shouldStopHere()) {
                visited.add(floor);
                serveThisFloor();
            }
            direction = nextDirection();
            move();
        }

        private void serveThisFloor() {
            boolean servesUp = direction != Direction.DOWN || !anythingBelow();
            boolean servesDown = direction != Direction.UP || !anythingAbove();
            if (servesUp) {
                upStops.remove(floor);
            }
            if (servesDown) {
                downStops.remove(floor);
            }
        }

        private Direction nextDirection() {
            return switch (direction) {
                case UP -> anythingAbove() ? Direction.UP : anythingBelow() ? Direction.DOWN : Direction.IDLE;
                case DOWN -> anythingBelow() ? Direction.DOWN : anythingAbove() ? Direction.UP : Direction.IDLE;
                case IDLE -> anythingAbove() ? Direction.UP : anythingBelow() ? Direction.DOWN : Direction.IDLE;
            };
        }

        private void move() {
            if (direction == Direction.UP) {
                floor++;
            } else if (direction == Direction.DOWN) {
                floor--;
            }
        }

        int endOfSweep() {
            TreeSet<Integer> all = new TreeSet<>(upStops);
            all.addAll(downStops);
            if (all.isEmpty()) {
                return floor;
            }
            return direction == Direction.UP ? Math.max(floor, all.last()) : Math.min(floor, all.first());
        }
    }

    final List<Elevator> elevators = List.of(new Elevator("A", 0), new Elevator("B", 0));

    static int cost(Elevator e, int callFloor, Direction wanted) {
        if (e.direction == Direction.IDLE || isOnTheWay(e, callFloor, wanted)) {
            return Math.abs(e.floor - callFloor);
        }
        int turn = e.direction == Direction.UP ? Math.max(e.endOfSweep(), callFloor)
                                               : Math.min(e.endOfSweep(), callFloor);
        return Math.abs(turn - e.floor) + Math.abs(turn - callFloor);
    }

    private static boolean isOnTheWay(Elevator e, int callFloor, Direction wanted) {
        return e.direction == Direction.UP
                ? wanted == Direction.UP && callFloor >= e.floor
                : wanted == Direction.DOWN && callFloor <= e.floor;
    }

    public Elevator call(int callFloor, Direction wanted) {
        if (callFloor < 0 || callFloor >= FLOORS || wanted == Direction.IDLE) {
            throw new IllegalArgumentException("no such call: floor " + callFloor + " " + wanted);
        }
        Elevator best = elevators.stream()
                .min(Comparator.comparingInt(e -> cost(e, callFloor, wanted)))   // przy remisie pierwsza z listy
                .orElseThrow();
        best.assignHallCall(callFloor, wanted);
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
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0), b = building.elevators.get(1);
        a.pressButtonInside(19);
        building.run(3);
        IO.println("A is at %d going %s, B is idle at %d".formatted(a.floor, a.direction, b.floor));
        IO.println("call UP at 2: cost for A %d floors, for B %d floors"
                .formatted(cost(a, 2, Direction.UP), cost(b, 2, Direction.UP)));
        IO.println("sent: " + building.call(2, Direction.UP).name
                + "  (A is nearer, but it has gone past and must reach 19 first)");
    }

    private static void showSweepOrder() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator c = building.elevators.get(0);
        List.of(12, 5, 9).forEach(c::pressButtonInside);
        building.run(30);
        IO.println("pressed 12, 5, 9; stopped at " + c.visited);
    }
}
