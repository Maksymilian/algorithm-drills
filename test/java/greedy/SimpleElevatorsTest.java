package greedy;

import java.util.List;

import org.junit.jupiter.api.Test;

import greedy.SimpleElevators.Direction;
import greedy.SimpleElevators.Elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleElevatorsTest {

    @Test
    void stopsInTheOrderItPassesTheFloorsNotTheOrderTheyWerePressed() {
        SimpleElevators building = new SimpleElevators();
        Elevator a = building.elevators.get(0);
        List.of(12, 5, 9).forEach(a::press);
        building.run(30);
        assertEquals(List.of(5, 9, 12), a.visited);
        assertEquals(Direction.IDLE, a.direction, "nothing left: it rests where it is");
        assertEquals(12, a.floor);
    }

    @Test
    void finishesTheSweepBeforeTurning() {
        SimpleElevators building = new SimpleElevators();
        Elevator a = building.elevators.get(0);
        a.press(10);
        building.run(5);
        a.press(2);
        a.press(8);
        building.run(30);
        assertEquals(List.of(8, 10, 2), a.visited);
    }

    @Test
    void sendsTheElevatorThatArrivesFirstNotTheNearestOne() {
        SimpleElevators building = new SimpleElevators();
        Elevator a = building.elevators.get(0), b = building.elevators.get(1);
        a.press(19);
        building.run(3);
        assertEquals(3, a.floor);
        assertEquals((19 - 3) + (19 - 2), SimpleElevators.cost(a, 2), "up to 19 and back to 2");
        assertEquals(2, SimpleElevators.cost(b, 2));
        assertSame(b, building.call(2));
    }

    @Test
    void anElevatorAlreadyOnItsWayCostsOnlyTheDistance() {
        SimpleElevators building = new SimpleElevators();
        Elevator a = building.elevators.get(0), b = building.elevators.get(1);
        a.press(19);
        building.run(3);
        assertEquals(10 - 3, SimpleElevators.cost(a, 10));
        assertSame(a, building.call(10), "A gets there in 7 floors, B in 10");
        assertEquals(0, b.stops.size());
    }

    @Test
    void costGoingDownIsTheMirrorImage() {
        SimpleElevators building = new SimpleElevators();
        Elevator a = building.elevators.get(0);
        a.press(15);
        building.run(16);
        a.press(1);
        building.run(2);
        assertEquals(13, a.floor);
        assertEquals(Direction.DOWN, a.direction);
        assertEquals(13 - 6, SimpleElevators.cost(a, 6));
        assertEquals((13 - 1) + (17 - 1), SimpleElevators.cost(a, 17), "down to 1 and back up");
    }

    @Test
    void everyCallIsEventuallyServed() {
        SimpleElevators building = new SimpleElevators();
        int[] calls = {7, 3, 15, 0, 12, 19, 5};
        for (int floor : calls) {
            building.call(floor);
            building.run(2);
        }
        building.run(100);
        assertTrue(building.elevators.stream().allMatch(e -> e.stops.isEmpty()), "stops left");
        int stops = building.elevators.stream().mapToInt(e -> e.visited.size()).sum();
        assertEquals(calls.length, stops, "one stop per call, nothing missed, nothing twice");
    }

    @Test
    void rejectsFloorsThatDoNotExist() {
        SimpleElevators building = new SimpleElevators();
        assertThrows(IllegalArgumentException.class, () -> building.call(20));
        assertThrows(IllegalArgumentException.class, () -> building.call(-1));
    }
}
