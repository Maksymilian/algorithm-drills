package unclassified;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import unclassified.TwoElevators.Assignment;
import unclassified.TwoElevators.Passenger;
import unclassified.TwoElevators.Result;
import unclassified.TwoElevators.Simulation;
import unclassified.TwoElevators.Stop;
import unclassified.TwoElevators.StopOrder;
import unclassified.TwoElevators.Strategy;
import unclassified.TwoElevators.Traffic;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unclassified.TwoElevators.DOOR_SECONDS;
import static unclassified.TwoElevators.SECONDS_PER_FLOOR;
import static unclassified.TwoElevators.SECONDS_PER_PERSON;

class TwoElevatorsTest {

    private static final Strategy FIFO_NEAREST = new Strategy(StopOrder.FIFO, Assignment.NEAREST);
    private static final Strategy LOOK_NEAREST = new Strategy(StopOrder.LOOK, Assignment.NEAREST);
    private static final Strategy LOOK_ETA = new Strategy(StopOrder.LOOK, Assignment.ETA);

    private static Simulation oneCar(Strategy strategy, Passenger... passengers) {
        return new Simulation(strategy, 1, List.of(passengers), true).run(10_000);
    }

    private static Simulation twoCars(Strategy strategy, Passenger... passengers) {
        return new Simulation(strategy, 2, List.of(passengers), true).run(10_000);
    }

    // ---------- the floors as bits ----------

    @Test
    void aboveAndBelowAreTheFloorsStrictlyOnEitherSide() {
        assertEquals(0b1111_1111_1111_1111_1110, TwoElevators.above(0));
        assertEquals(0, TwoElevators.above(19), "nothing above the top floor");
        assertEquals(0, TwoElevators.below(0), "nothing below the ground floor");
        assertEquals(0b0111_1111_1111_1111_1111, TwoElevators.below(19));
        assertEquals(0b0000_0000_0000_0001_1111, TwoElevators.below(5));
        assertEquals(TwoElevators.ALL_FLOORS & ~TwoElevators.bit(7),
                TwoElevators.above(7) | TwoElevators.below(7), "together, every floor but the one you are on");
    }

    // ---------- time ----------

    @Test
    void aTripCostsTheDoorsPlusTwoSecondsAFloor() {
        Simulation sim = oneCar(LOOK_ETA, new Passenger(0, 0, 0, 10));
        int boarding = DOOR_SECONDS + SECONDS_PER_PERSON;            // one person gets in
        assertEquals(0, sim.waitOf(0), "the car is already there");
        assertEquals(boarding + 10 * SECONDS_PER_FLOOR, sim.deliveredAt(0));
        assertEquals(List.of(0, 0, 0, 0, 0, 0, 0, 1, 2, 3), sim.positions(0).subList(0, 10),
                "half floors: six seconds of doors, then half a floor a second");
    }

    // ---------- the order one car visits its floors in ----------

    /** Three people get in on the ground floor, pressing 12, 5 and 9 in that order. */
    private static final Passenger[] THREE_FROM_THE_LOBBY = {
            new Passenger(0, 0, 0, 12), new Passenger(1, 0, 0, 5), new Passenger(2, 0, 0, 9)};

    @Test
    void lookVisitsTheFloorsInTheOrderItPassesThem() {
        Simulation sim = oneCar(LOOK_ETA, THREE_FROM_THE_LOBBY);
        assertEquals(List.of(0, 5, 9, 12), sim.stopFloors(0));
    }

    @Test
    void fifoVisitsThemInTheOrderTheyWerePressedAndCarriesPeoplePastTheirFloor() {
        Simulation sim = oneCar(FIFO_NEAREST, THREE_FROM_THE_LOBBY);
        assertEquals(List.of(0, 12, 5, 9), sim.stopFloors(0), "up to 12 past 5 and 9, then back down");

        Simulation look = oneCar(LOOK_ETA, THREE_FROM_THE_LOBBY);
        assertTrue(sim.deliveredAt(1) > 2 * look.deliveredAt(1),
                "the person for 5 rides to 12 and back: " + sim.deliveredAt(1) + " s against " + look.deliveredAt(1));
    }

    @Test
    void lookTakesAHallCallOnTheWayOnlyIfItIsGoingTheSameWay() {
        // the car leaves the ground floor for 15; at second 8 it is at floor 1, below 8
        Simulation goingUp = oneCar(LOOK_ETA, new Passenger(0, 0, 0, 15), new Passenger(1, 8, 8, 12));
        assertEquals(List.of(0, 8, 12, 15), goingUp.stopFloors(0), "someone going up at 8 is picked up on the way");

        Simulation goingDown = oneCar(LOOK_ETA, new Passenger(0, 0, 0, 15), new Passenger(1, 8, 8, 2));
        assertEquals(List.of(0, 15, 8, 2), goingDown.stopFloors(0),
                "someone going down at 8 is passed by, and picked up on the way back");
    }

    @Test
    void lookGoesOnToACallTheOtherWayAndTurnsThere() {
        // nothing is above 15 but a call to go down from it: the car goes up to it and turns
        Simulation sim = oneCar(LOOK_ETA, new Passenger(0, 0, 0, 10), new Passenger(1, 1, 15, 3));
        assertEquals(List.of(0, 10, 15, 3), sim.stopFloors(0));
    }

    @Test
    void anIdleCarStartsTowardsTheNearerCall() {
        assertEquals(TwoElevators.Direction.DOWN,
                TwoElevators.lookNext(10, TwoElevators.Direction.NONE, TwoElevators.bit(8) | TwoElevators.bit(15)));
        assertEquals(TwoElevators.Direction.UP,
                TwoElevators.lookNext(10, TwoElevators.Direction.NONE, TwoElevators.bit(7) | TwoElevators.bit(13)),
                "a tie goes up");
        assertEquals(TwoElevators.Direction.UP,
                TwoElevators.lookNext(10, TwoElevators.Direction.UP, TwoElevators.bit(9) | TwoElevators.bit(19)),
                "a moving car does not turn for something nearer behind it");
    }

    // ---------- which car answers ----------

    /**
     * Car 0 takes someone from the ground floor to 19. Twelve seconds later, when it is at floor 3,
     * someone presses up on floor 2. Car 0 is the nearer, one floor away, but it has already passed
     * the floor going up; car 1 is idle on the ground floor, two floors away.
     */
    private static final Passenger[] PASSED_BY = {new Passenger(0, 0, 0, 19), new Passenger(1, 12, 2, 10)};

    @Test
    void nearestSendsTheCarThatHasAlreadyGonePast() {
        Simulation sim = twoCars(LOOK_NEAREST, PASSED_BY);
        assertEquals(6, sim.positions(0).get(12), "car 0 is at floor 3 at second 12, in half floors");
        assertEquals(0, sim.carOf(1));
        assertEquals(72, sim.waitOf(1), "up to 19, out, and all the way back down to 2");
    }

    @Test
    void etaSendsTheCarThatWillArriveFirst() {
        Simulation sim = twoCars(LOOK_ETA, PASSED_BY);
        assertEquals(1, sim.carOf(1));
        assertEquals(2 * SECONDS_PER_FLOOR, sim.waitOf(1), "two floors up from the ground floor");
    }

    // ---------- what must hold whatever the strategy ----------

    static Stream<Arguments> strategiesAndTraffic() {
        return Strategy.ALL.stream().flatMap(s -> Arrays.stream(Traffic.values()).map(t -> Arguments.of(s, t)));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("strategiesAndTraffic")
    void everyoneGetsInAfterTheyArriveAndOutWhereTheyWereGoing(Strategy strategy, Traffic traffic) {
        List<Passenger> passengers = TwoElevators.traffic(traffic, 4, TwoElevators.HOUR, 7);
        Simulation sim = new Simulation(strategy, passengers).run(TwoElevators.LIMIT);

        assertEquals(passengers.size(), sim.result().delivered(), "everyone delivered");
        for (Passenger p : passengers) {
            assertEquals(p.to(), sim.deliveredTo(p.id()), "got out at their own floor: " + p);
            assertTrue(sim.waitOf(p.id()) >= 0, "got in no earlier than they arrived: " + p);
            assertTrue(sim.deliveredAt(p.id()) > p.arrival() + sim.waitOf(p.id()), "got out after getting in: " + p);
        }
        for (int car = 0; car < TwoElevators.CARS; car++) {
            assertTrue(sim.maxLoad(car) <= TwoElevators.CAPACITY, "car " + car + " never over capacity");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Traffic.class)
    void aCarNeverStopsWithoutLettingSomeoneOutOrIn(Traffic traffic) {
        // LOOK stops only for a car call, or a hall call it can serve; FIFO may arrive to find its
        // call answered by the other car, so the claim is LOOK's alone
        List<Passenger> passengers = TwoElevators.traffic(traffic, 4, TwoElevators.HOUR, 11);
        Simulation sim = new Simulation(LOOK_ETA, passengers).run(TwoElevators.LIMIT);
        for (int car = 0; car < TwoElevators.CARS; car++) {
            for (Stop stop : sim.stops(car)) {
                assertTrue(stop.in() + stop.out() > 0, "car " + car + " stopped for nobody: " + stop);
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameHour() {
        List<Passenger> a = TwoElevators.traffic(Traffic.LUNCH, 4, TwoElevators.HOUR, 3);
        List<Passenger> b = TwoElevators.traffic(Traffic.LUNCH, 4, TwoElevators.HOUR, 3);
        assertEquals(a, b);
        assertEquals(new Simulation(LOOK_ETA, a).run(TwoElevators.LIMIT).result(),
                new Simulation(LOOK_ETA, b).run(TwoElevators.LIMIT).result());
    }

    // ---------- the comparison ----------

    /** The claim the notes make, checked on hours the notes did not average over. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(Traffic.class)
    void lookWithEtaWaitsLeastOnAverage(Traffic traffic) {
        double[] meanWait = new double[Strategy.ALL.size()];
        for (long seed = 101; seed <= 110; seed++) {
            List<Passenger> passengers = TwoElevators.traffic(traffic, 4, TwoElevators.HOUR, seed);
            for (int s = 0; s < meanWait.length; s++) {
                Result r = new Simulation(Strategy.ALL.get(s), passengers).run(TwoElevators.LIMIT).result();
                meanWait[s] += r.meanWait();
            }
        }
        int best = Strategy.ALL.indexOf(LOOK_ETA);
        for (int s = 0; s < meanWait.length; s++) {
            if (s != best) {
                assertTrue(meanWait[best] < meanWait[s],
                        Strategy.ALL.get(s) + " waited " + meanWait[s] / 10 + " s, LOOK + ETA " + meanWait[best] / 10);
            }
        }
    }

    // ---------- input ----------

    @Test
    void rejectsTripsThatAreNotTrips() {
        assertThrows(IllegalArgumentException.class, () -> oneCar(LOOK_ETA, new Passenger(0, 0, 4, 4)));
        assertThrows(IllegalArgumentException.class, () -> oneCar(LOOK_ETA, new Passenger(0, 0, 0, 20)));
        assertThrows(IllegalArgumentException.class,
                () -> oneCar(LOOK_ETA, new Passenger(0, 5, 0, 3), new Passenger(1, 4, 0, 3)), "out of order");
    }
}
