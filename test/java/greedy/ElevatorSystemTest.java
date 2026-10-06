package greedy;

import java.util.List;

import org.junit.jupiter.api.Test;

import greedy.ElevatorSystem.Direction;
import greedy.ElevatorSystem.Elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevatorSystemTest {

    @Test
    void stopsInTheOrderItPassesTheFloorsNotTheOrderTheyWerePressed() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0);
        List.of(12, 5, 9).forEach(a::pressButtonInside);
        building.run(30);
        assertEquals(List.of(5, 9, 12), a.visited);
        assertEquals(Direction.IDLE, a.direction, "nothing left: it rests where it is");
    }

    @Test
    void passesSomeoneGoingTheOtherWayAndCollectsThemOnTheWayBack() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0);
        a.pressButtonInside(15);
        building.run(2);
        a.assignHallCall(8, Direction.DOWN);
        building.run(40);
        assertEquals(List.of(15, 8), a.visited, "floor 8 is passed on the way up");
    }

    @Test
    void picksUpSomeoneGoingTheSameWayOnTheWay() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0);
        a.pressButtonInside(15);
        building.run(2);
        a.assignHallCall(8, Direction.UP);
        building.run(40);
        assertEquals(List.of(8, 15), a.visited);
    }

    @Test
    void goesOnToACallTheOtherWayAndTurnsThere() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0);
        a.pressButtonInside(10);
        a.assignHallCall(15, Direction.DOWN);
        building.run(20);
        assertEquals(List.of(10, 15), a.visited);
        assertEquals(Direction.IDLE, a.direction);
    }

    @Test
    void sendsTheElevatorThatArrivesFirstNotTheNearestOne() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0), b = building.elevators.get(1);
        a.pressButtonInside(19);
        building.run(3);
        assertEquals(3, a.floor);
        assertEquals((19 - 3) + (19 - 2), ElevatorSystem.cost(a, 2, Direction.UP), "up to 19 and back to 2");
        assertEquals(2, ElevatorSystem.cost(b, 2, Direction.UP));
        assertSame(b, building.call(2, Direction.UP));
    }

    @Test
    void anElevatorAlreadyOnItsWayCostsOnlyTheDistance() {
        ElevatorSystem building = new ElevatorSystem();
        Elevator a = building.elevators.get(0);
        a.pressButtonInside(19);
        building.run(3);
        assertEquals(10 - 3, ElevatorSystem.cost(a, 10, Direction.UP));
        assertEquals((19 - 3) + (19 - 10), ElevatorSystem.cost(a, 10, Direction.DOWN), "wrong way: after the turn");
    }

    @Test
    void everyCallIsEventuallyServed() {
        ElevatorSystem building = new ElevatorSystem();
        int[][] calls = {{7, 1}, {3, 0}, {15, 1}, {0, 1}, {12, 0}, {19, 0}, {5, 1}};
        for (int[] call : calls) {
            building.call(call[0], call[1] == 1 ? Direction.UP : Direction.DOWN);
            building.run(2);
        }
        building.run(100);
        assertTrue(building.elevators.stream().allMatch(e -> e.upStops.isEmpty() && e.downStops.isEmpty()));
        long stops = building.elevators.stream().mapToLong(e -> e.visited.size()).sum();
        assertEquals(calls.length, stops, "one stop per call, nothing missed, nothing twice");
    }

    @Test
    void rejectsCallsThatCannotBeMade() {
        ElevatorSystem building = new ElevatorSystem();
        assertThrows(IllegalArgumentException.class, () -> building.call(20, Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> building.call(5, Direction.IDLE));
    }
}
