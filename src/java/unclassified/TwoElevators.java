package unclassified;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Two elevators serving twenty floors: how the requests are held, which car answers a call, and in
 * what order a car visits its floors. Notes in {@code docs/unclassified/two-elevators.md}.
 *
 * <p>Two kinds of request reach the system. A <b>hall call</b> is a button on a landing, and carries a
 * direction: someone on floor 7 wants to go down. A <b>car call</b> is a button inside a car, and
 * carries only a floor. Requests are kept as bitmasks, one bit per floor, and twenty floors fit in an
 * {@code int}. That makes "is anything above me?" a single AND. It also makes a request a fact rather
 * than an event: pressing a lit button again changes nothing. The only true queues are the people
 * waiting on a landing, one per floor and direction, who get in in the order they came.
 *
 * <p>A strategy is two independent choices, each with two resolutions here:
 * <ul>
 *   <li>{@link StopOrder}: the order one car visits its floors in. {@code FIFO} drives to the oldest
 *       request and stops nowhere on the way. {@code LOOK} keeps going while anything lies ahead,
 *       stopping wherever it can serve someone, and turns only when nothing is left in front of it.</li>
 *   <li>{@link Assignment}: which car gets a new hall call. {@code NEAREST} picks the car fewest floors
 *       away. {@code ETA} picks the car that would get there first, given the way it is going and the
 *       stops it has already promised.</li>
 * </ul>
 *
 * <p>Priority, under LOOK and ETA, comes down to four rules: people inside always get out when the
 * car passes their floor; a hall call is answered only by a car going its way; a car finishes its
 * sweep before it turns; a full car passes hall calls by. The simulation runs in one-second steps,
 * and {@link #main} compares all four combinations.
 */
public class TwoElevators {

    // ---------- the building ----------

    public static final int FLOORS = 20;                // 0 is the ground floor, 19 the top
    static final int ALL_FLOORS = (1 << FLOORS) - 1;
    public static final int CARS = 2;
    public static final int CAPACITY = 8;               // people per car
    public static final int SECONDS_PER_FLOOR = 2;      // about 1.5 m/s with 3 m storeys
    public static final int DOOR_SECONDS = 5;           // slow down, open, hold, close
    public static final int SECONDS_PER_PERSON = 1;     // each person getting in or out
    /** What the ETA assumes one stop costs: the doors, and a couple of people. */
    static final int STOP_ESTIMATE = DOOR_SECONDS + 2 * SECONDS_PER_PERSON;
    /** A full car cannot stop for anyone, so until somebody gets out it is as good as far away. */
    static final int FULL_CAR_PENALTY = 60;

    public enum Direction {
        UP(1), DOWN(-1), NONE(0);

        final int step;

        Direction(int step) {
            this.step = step;
        }

        static Direction of(int from, int to) {
            return to > from ? UP : to < from ? DOWN : NONE;
        }
    }

    /** The order one car visits the floors it has been asked for. */
    public enum StopOrder { FIFO, LOOK }

    /** Which car answers a hall call. */
    public enum Assignment { NEAREST, ETA }

    public record Strategy(StopOrder order, Assignment assignment) {
        public static final List<Strategy> ALL = List.of(
                new Strategy(StopOrder.FIFO, Assignment.NEAREST), new Strategy(StopOrder.FIFO, Assignment.ETA),
                new Strategy(StopOrder.LOOK, Assignment.NEAREST), new Strategy(StopOrder.LOOK, Assignment.ETA));

        @Override
        public String toString() {
            return order + " + " + assignment;
        }
    }

    /** Someone who arrives on floor {@code from} at second {@code arrival}, going to {@code to}. */
    public record Passenger(int id, int arrival, int from, int to) {
        public Direction direction() {
            return Direction.of(from, to);
        }
    }

    /** A car stopping: when, where, how many got out and in, and how long the doors stayed open. */
    public record Stop(int time, int floor, int out, int in, int seconds) {}

    public record Result(Strategy strategy, int passengers, int delivered, double meanWait, double p95Wait,
                         int maxWait, double meanJourney, int floorsTravelled, int stops) {}

    // ---------- the floors as bits ----------

    static int bit(int floor) {
        return 1 << floor;
    }

    /** Floors strictly above {@code floor}. */
    static int above(int floor) {
        return ALL_FLOORS & (-1 << (floor + 1));
    }

    /** Floors strictly below {@code floor}. */
    static int below(int floor) {
        return bit(floor) - 1;
    }

    // ---------- LOOK, as pure functions of the masks, so the ETA can replay them ----------

    /** Does a car at {@code floor}, heading {@code dir}, stop here? */
    static boolean lookStops(int floor, Direction dir, int carCalls, int upCalls, int downCalls, boolean full) {
        int b = bit(floor);
        if ((carCalls & b) != 0) return true;              // someone inside gets out: always
        if (full) return false;                            // nobody could get in
        int all = carCalls | upCalls | downCalls;
        return switch (dir) {
            // a call the other way is taken only where the car turns anyway
            case UP -> (upCalls & b) != 0 || (downCalls & b) != 0 && (all & above(floor)) == 0;
            case DOWN -> (downCalls & b) != 0 || (upCalls & b) != 0 && (all & below(floor)) == 0;
            case NONE -> ((upCalls | downCalls) & b) != 0;
        };
    }

    /** Which way to move from {@code floor}: on, while anything is ahead; back, if only behind. */
    static Direction lookNext(int floor, Direction dir, int requests) {
        int over = requests & above(floor), under = requests & below(floor);
        if (dir == Direction.UP) return over != 0 ? Direction.UP : under != 0 ? Direction.DOWN : Direction.NONE;
        if (dir == Direction.DOWN) return under != 0 ? Direction.DOWN : over != 0 ? Direction.UP : Direction.NONE;
        if (over == 0 && under == 0) return Direction.NONE;
        if (under == 0) return Direction.UP;
        if (over == 0) return Direction.DOWN;
        // idle, with work both ways: start towards the nearer, and ties go up
        int upDistance = Integer.numberOfTrailingZeros(over) - floor;
        int downDistance = floor - (31 - Integer.numberOfLeadingZeros(under));
        return upDistance <= downDistance ? Direction.UP : Direction.DOWN;
    }

    /**
     * Which way a car stopped at {@code floor} will leave, decided after the people inside got out,
     * so that only people going that way get in.
     */
    static Direction lookDeparture(int floor, Direction dir, int carCalls, int upCalls, int downCalls) {
        int b = bit(floor), requests = carCalls | upCalls | downCalls;
        boolean over = (requests & above(floor)) != 0, under = (requests & below(floor)) != 0;
        boolean upHere = (upCalls & b) != 0, downHere = (downCalls & b) != 0;
        return switch (dir) {
            case UP -> over || upHere ? Direction.UP : under || downHere ? Direction.DOWN : Direction.NONE;
            case DOWN -> under || downHere ? Direction.DOWN : over || upHere ? Direction.UP : Direction.NONE;
            case NONE -> upHere ? Direction.UP : downHere ? Direction.DOWN
                    : over ? Direction.UP : under ? Direction.DOWN : Direction.NONE;
        };
    }

    // ---------- the simulation ----------

    /** One building, one strategy, one list of passengers, run in one-second steps. */
    public static final class Simulation {

        private final Strategy strategy;
        private final List<Passenger> passengers;
        private final Car[] cars;
        private final List<Deque<Passenger>> upQueues = new ArrayList<>(), downQueues = new ArrayList<>();
        private int upButtons, downButtons;                       // the hall buttons that are lit
        private final int[] upOwner = new int[FLOORS], downOwner = new int[FLOORS];   // car index, or -1
        private final int[] boardedAt, deliveredAt, deliveredTo, servedBy;
        private final boolean trace;
        private int delivered;
        private int time;

        public Simulation(Strategy strategy, List<Passenger> passengers) {
            this(strategy, CARS, passengers, false);
        }

        public Simulation(Strategy strategy, int carCount, List<Passenger> passengers, boolean trace) {
            for (int i = 0; i < passengers.size(); i++) {
                Passenger p = passengers.get(i);
                if (p.id() != i) throw new IllegalArgumentException("ids must be 0, 1, 2... in order: " + p);
                if (i > 0 && p.arrival() < passengers.get(i - 1).arrival())
                    throw new IllegalArgumentException("passengers must be sorted by arrival: " + p);
                if (p.from() < 0 || p.from() >= FLOORS || p.to() < 0 || p.to() >= FLOORS || p.from() == p.to())
                    throw new IllegalArgumentException("not a trip between two of the floors: " + p);
            }
            this.strategy = strategy;
            this.passengers = List.copyOf(passengers);
            this.trace = trace;
            for (int f = 0; f < FLOORS; f++) {
                upQueues.add(new ArrayDeque<>());
                downQueues.add(new ArrayDeque<>());
            }
            Arrays.fill(upOwner, -1);
            Arrays.fill(downOwner, -1);
            cars = new Car[carCount];
            for (int i = 0; i < carCount; i++) {
                cars[i] = strategy.order() == StopOrder.FIFO ? new FifoCar(i) : new LookCar(i);
            }
            int n = passengers.size();
            boardedAt = filled(n);
            deliveredAt = filled(n);
            deliveredTo = filled(n);
            servedBy = filled(n);
        }

        private static int[] filled(int n) {
            int[] a = new int[n];
            Arrays.fill(a, -1);
            return a;
        }

        /** Runs until everyone has arrived where they were going, or until second {@code limit}. */
        public Simulation run(int limit) {
            int next = 0, n = passengers.size();
            for (time = 0; time <= limit && delivered < n; time++) {
                while (next < n && passengers.get(next).arrival() <= time) arrive(passengers.get(next++));
                dispatch();
                for (Car car : cars) {
                    car.step();
                    if (trace) car.positions.add(car.position());
                }
            }
            return this;
        }

        private void arrive(Passenger p) {
            queue(p.from(), p.direction()).add(p);
            if (p.direction() == Direction.UP) upButtons |= bit(p.from());
            else downButtons |= bit(p.from());
        }

        private Deque<Passenger> queue(int floor, Direction d) {
            return (d == Direction.UP ? upQueues : downQueues).get(floor);
        }

        private int[] owners(Direction d) {
            return d == Direction.UP ? upOwner : downOwner;
        }

        /** Every lit button without a car on its way gets one. */
        private void dispatch() {
            for (int f = 0; f < FLOORS; f++) {
                if ((upButtons & bit(f)) != 0 && upOwner[f] < 0) assign(f, Direction.UP);
                if ((downButtons & bit(f)) != 0 && downOwner[f] < 0) assign(f, Direction.DOWN);
            }
        }

        private void assign(int floor, Direction d) {
            Car best = null;
            long bestCost = Long.MAX_VALUE;
            for (Car car : cars) {
                long cost = strategy.assignment() == Assignment.NEAREST
                        ? Math.abs(car.floor - floor)
                        : car.eta(floor, d) + (car.isFull() ? FULL_CAR_PENALTY : 0);
                if (cost < bestCost) {          // ties go to the lower-numbered car
                    best = car;
                    bestCost = cost;
                }
            }
            owners(d)[floor] = best.id;
            best.assign(floor, d);
        }

        /**
         * Takes the call away from whichever car had it, so that the next dispatch hands it out again.
         * Not called unassign: inside a Car that name is the car's own, and would win.
         */
        private void release(int floor, Direction d) {
            int[] owner = owners(d);
            if (owner[floor] >= 0) {
                cars[owner[floor]].unassign(floor, d);
                owner[floor] = -1;
            }
        }

        // ---------- what a run produced ----------

        public Result result() {
            int n = passengers.size();
            int[] waits = new int[n];
            int boarded = 0, maxWait = 0, floorsTravelled = 0, stops = 0;
            long waitSum = 0, journeySum = 0;
            for (Passenger p : passengers) {
                if (boardedAt[p.id()] < 0) continue;
                int wait = boardedAt[p.id()] - p.arrival();
                waits[boarded++] = wait;
                waitSum += wait;
                maxWait = Math.max(maxWait, wait);
                if (deliveredAt[p.id()] >= 0) journeySum += deliveredAt[p.id()] - p.arrival();
            }
            for (Car car : cars) {
                floorsTravelled += car.floorsTravelled;
                stops += car.stops.size();
            }
            int[] sorted = Arrays.copyOf(waits, boarded);
            Arrays.sort(sorted);
            double p95 = boarded == 0 ? 0 : sorted[Math.min(boarded - 1, (int) Math.ceil(0.95 * boarded) - 1)];
            return new Result(strategy, n, delivered, boarded == 0 ? 0 : (double) waitSum / boarded, p95, maxWait,
                    delivered == 0 ? 0 : (double) journeySum / delivered, floorsTravelled, stops);
        }

        /** Seconds from pressing the hall button to getting in, or -1 if they never got in. */
        public int waitOf(int passenger) {
            int boarded = boardedAt[passenger];
            return boarded < 0 ? -1 : boarded - passengers.get(passenger).arrival();
        }

        /** The floor they got out at, or -1. */
        public int deliveredTo(int passenger) {
            return deliveredTo[passenger];
        }

        public int deliveredAt(int passenger) {
            return deliveredAt[passenger];
        }

        /** The car they rode in, or -1. */
        public int carOf(int passenger) {
            return servedBy[passenger];
        }

        public List<Stop> stops(int car) {
            return List.copyOf(cars[car].stops);
        }

        public List<Integer> stopFloors(int car) {
            return cars[car].stops.stream().map(Stop::floor).toList();
        }

        /** Where the car was at the end of each second, in half floors; recorded only with trace on. */
        public List<Integer> positions(int car) {
            return List.copyOf(cars[car].positions);
        }

        public int maxLoad(int car) {
            return cars[car].maxLoad;
        }

        public int seconds() {
            return time;
        }

        // ---------- a car: moving, stopping, and the people in it ----------

        private abstract class Car {
            final int id;
            int floor;              // the floor it is at, or the last one it passed
            int travel;             // seconds until it reaches the next floor; 0 when at a floor
            int doorTimer;          // seconds the doors stay open
            Direction dir = Direction.NONE;
            final List<Passenger> riders = new ArrayList<>();
            final List<Stop> stops = new ArrayList<>();
            final List<Integer> positions = new ArrayList<>();
            int floorsTravelled, maxLoad;

            Car(int id) {
                this.id = id;
            }

            boolean isFull() {
                return riders.size() >= CAPACITY;
            }

            int position() {
                return floor * SECONDS_PER_FLOOR + (travel > 0 ? dir.step * (SECONDS_PER_FLOOR - travel) : 0);
            }

            void step() {
                if (doorTimer > 0 && --doorTimer > 0) return;          // still loading
                if (travel > 0) {                                       // between two floors
                    if (--travel > 0) return;
                    floor += dir.step;
                    floorsTravelled++;
                }
                if (wantsStop()) {
                    stop();
                    return;
                }
                dir = nextDirection();
                if (dir != Direction.NONE) travel = SECONDS_PER_FLOOR;
            }

            private void stop() {
                int out = 0;
                for (Iterator<Passenger> it = riders.iterator(); it.hasNext(); ) {
                    Passenger p = it.next();
                    if (p.to() == floor) {
                        it.remove();
                        deliveredAt[p.id()] = time;
                        deliveredTo[p.id()] = floor;
                        delivered++;
                        out++;
                    }
                }
                clearCarCall(floor);

                Direction leave = departure();
                if (leave == Direction.NONE) {      // nothing to do: take whoever is here, up first
                    leave = !queue(floor, Direction.UP).isEmpty() ? Direction.UP
                            : !queue(floor, Direction.DOWN).isEmpty() ? Direction.DOWN : Direction.NONE;
                }
                int in = 0;
                if (leave != Direction.NONE) {
                    Deque<Passenger> waiting = queue(floor, leave);
                    while (!waiting.isEmpty() && !isFull()) {
                        Passenger p = waiting.poll();
                        riders.add(p);
                        boardedAt[p.id()] = time;
                        servedBy[p.id()] = id;
                        addCarCall(p.to());
                        in++;
                    }
                    if (waiting.isEmpty()) {
                        // the call is answered, whichever car it had been given to
                        if (leave == Direction.UP) upButtons &= ~bit(floor);
                        else downButtons &= ~bit(floor);
                        release(floor, leave);
                    } else if (owners(leave)[floor] == id) {
                        release(floor, leave);       // full: the people left behind need another car
                    }
                }
                dir = leave;
                maxLoad = Math.max(maxLoad, riders.size());
                int seconds = DOOR_SECONDS + SECONDS_PER_PERSON * (in + out);
                doorTimer = seconds;
                stops.add(new Stop(time, floor, out, in, seconds));
            }

            /** Where the car can next decide anything, and how long until it can. */
            int startFloor() {
                return travel > 0 ? floor + dir.step : floor;
            }

            int startDelay() {
                return doorTimer + travel;
            }

            abstract boolean wantsStop();

            abstract Direction nextDirection();

            abstract Direction departure();

            abstract void addCarCall(int floor);

            abstract void clearCarCall(int floor);

            abstract void assign(int floor, Direction d);

            abstract void unassign(int floor, Direction d);

            /** Seconds until this car would stop at {@code floor} ready to take people going {@code d}. */
            abstract int eta(int floor, Direction d);
        }

        /** Sweeps: on while anything lies ahead, stopping wherever it can serve someone, then back. */
        private final class LookCar extends Car {
            int carCalls, upCalls, downCalls;

            LookCar(int id) {
                super(id);
            }

            @Override
            boolean wantsStop() {
                return lookStops(floor, dir, carCalls, upCalls, downCalls, isFull());
            }

            @Override
            Direction nextDirection() {
                return lookNext(floor, dir, carCalls | upCalls | downCalls);
            }

            @Override
            Direction departure() {
                return lookDeparture(floor, dir, carCalls, upCalls, downCalls);
            }

            @Override
            void addCarCall(int f) {
                carCalls |= bit(f);
            }

            @Override
            void clearCarCall(int f) {
                carCalls &= ~bit(f);
            }

            @Override
            void assign(int f, Direction d) {
                if (d == Direction.UP) upCalls |= bit(f);
                else downCalls |= bit(f);
            }

            @Override
            void unassign(int f, Direction d) {
                if (d == Direction.UP) upCalls &= ~bit(f);
                else downCalls &= ~bit(f);
            }

            /** Replays LOOK on copies of the masks, with the new call added, until it is served. */
            @Override
            int eta(int target, Direction want) {
                int car = carCalls, up = upCalls, down = downCalls;
                if (want == Direction.UP) up |= bit(target);
                else down |= bit(target);
                int at = startFloor(), time = startDelay();
                Direction heading = dir;
                for (int moves = 0; moves <= 3 * FLOORS; moves++) {
                    if (lookStops(at, heading, car, up, down, false)) {
                        Direction leave = lookDeparture(at, heading, car & ~bit(at), up, down);
                        if (at == target && leave == want) return time;
                        time += STOP_ESTIMATE;
                        car &= ~bit(at);
                        if (leave == Direction.UP) up &= ~bit(at);
                        if (leave == Direction.DOWN) down &= ~bit(at);
                        heading = leave;
                    }
                    heading = lookNext(at, heading, car | up | down);
                    at += heading.step;
                    time += SECONDS_PER_FLOOR;
                }
                throw new IllegalStateException("LOOK never reaches floor " + target + " going " + want);
            }
        }

        /** A queue of requests, served strictly in the order they were made: no stops on the way. */
        private final class FifoCar extends Car {
            /** {@code hall} is the direction of a hall call, or NONE for a car call. */
            record Request(int floor, Direction hall) {}

            final Deque<Request> queue = new ArrayDeque<>();

            FifoCar(int id) {
                super(id);
            }

            /** A full car cannot take anyone in, so hall calls at the front wait at the back. */
            private void skipWhatAFullCarCannotServe() {
                for (int i = queue.size(); i > 0 && isFull() && queue.peek().hall() != Direction.NONE; i--) {
                    queue.add(queue.poll());
                }
            }

            @Override
            boolean wantsStop() {
                skipWhatAFullCarCannotServe();
                return !queue.isEmpty() && queue.peek().floor() == floor;
            }

            @Override
            Direction nextDirection() {
                skipWhatAFullCarCannotServe();
                return queue.isEmpty() ? Direction.NONE : Direction.of(floor, queue.peek().floor());
            }

            /** The way to the next request; or, for a hall call on this floor, the way it asked for. */
            @Override
            Direction departure() {
                for (Request r : queue) {
                    if (r.floor() != floor) return Direction.of(floor, r.floor());
                    if (r.hall() != Direction.NONE) return r.hall();
                }
                return Direction.NONE;
            }

            @Override
            void addCarCall(int f) {
                Request r = new Request(f, Direction.NONE);
                if (!queue.contains(r)) queue.add(r);
            }

            @Override
            void clearCarCall(int f) {
                queue.remove(new Request(f, Direction.NONE));
            }

            @Override
            void assign(int f, Direction d) {
                queue.add(new Request(f, d));
            }

            @Override
            void unassign(int f, Direction d) {
                queue.remove(new Request(f, d));
            }

            /** The whole queue first, then the new call: that is what joining the back of a queue means. */
            @Override
            int eta(int target, Direction want) {
                int at = startFloor(), time = startDelay(), lastStop = -1;
                for (Request r : queue) {
                    if (r.floor() == lastStop) continue;            // served in the same stop
                    time += Math.abs(r.floor() - at) * SECONDS_PER_FLOOR + STOP_ESTIMATE;
                    at = lastStop = r.floor();
                }
                return time + Math.abs(target - at) * SECONDS_PER_FLOOR;
            }
        }
    }

    // ---------- traffic ----------

    /** Who travels where. Each pattern mixes in some trips between two upper floors. */
    public enum Traffic {
        /** Morning: nine in ten trips start on the ground floor. */
        UP_PEAK,
        /** Evening: nine in ten trips end on the ground floor. */
        DOWN_PEAK,
        /** Any floor to any other, uniformly. */
        INTER_FLOOR,
        /** Lunch: two in five to the ground floor, two in five from it, the rest between floors. */
        LUNCH;

        int[] trip(Random random) {
            double r = random.nextDouble();
            return switch (this) {
                case UP_PEAK -> r < 0.9 ? new int[]{0, 1 + random.nextInt(FLOORS - 1)} : anyTwo(random);
                case DOWN_PEAK -> r < 0.9 ? new int[]{1 + random.nextInt(FLOORS - 1), 0} : anyTwo(random);
                case INTER_FLOOR -> anyTwo(random);
                case LUNCH -> r < 0.4 ? new int[]{1 + random.nextInt(FLOORS - 1), 0}
                        : r < 0.8 ? new int[]{0, 1 + random.nextInt(FLOORS - 1)} : anyTwo(random);
            };
        }

        private static int[] anyTwo(Random random) {
            int from = random.nextInt(FLOORS), to = random.nextInt(FLOORS - 1);
            return new int[]{from, to >= from ? to + 1 : to};
        }
    }

    /** Arrivals as a Poisson process, {@code perMinute} on average, over the first {@code seconds}. */
    public static List<Passenger> traffic(Traffic pattern, double perMinute, int seconds, long seed) {
        Random random = new Random(seed);
        List<Passenger> passengers = new ArrayList<>();
        double t = 0;
        while (true) {
            t += -Math.log(1 - random.nextDouble()) * 60 / perMinute;   // exponential gaps
            if (t >= seconds) return passengers;
            int[] trip = pattern.trip(random);
            passengers.add(new Passenger(passengers.size(), (int) t, trip[0], trip[1]));
        }
    }

    // ---------- the comparison ----------

    static final int HOUR = 3_600;
    /** An hour of arrivals, then up to four more hours to deliver everyone. */
    static final int LIMIT = 5 * HOUR;
    static final int SEEDS = 20;

    /** The four strategies' results, each averaged over {@link #SEEDS} independent hours. */
    static double[][] compare(Traffic pattern, double perMinute) {
        double[][] rows = new double[Strategy.ALL.size()][];
        for (int s = 0; s < rows.length; s++) {
            double[] sum = new double[6];
            for (long seed = 1; seed <= SEEDS; seed++) {
                Result r = new Simulation(Strategy.ALL.get(s), traffic(pattern, perMinute, HOUR, seed)).run(LIMIT).result();
                double[] row = {r.meanWait(), r.p95Wait(), r.maxWait(), r.meanJourney(), r.floorsTravelled(),
                        r.passengers() - r.delivered()};
                for (int i = 0; i < row.length; i++) sum[i] += row[i] / SEEDS;
            }
            rows[s] = sum;
        }
        return rows;
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Two elevators, %d floors (0-%d), %d people per car, %d s per floor,"
                        + " %d s per stop + %d s per person; an hour of arrivals, averaged over %d hours%n",
                FLOORS, FLOORS - 1, CAPACITY, SECONDS_PER_FLOOR, DOOR_SECONDS, SECONDS_PER_PERSON, SEEDS);

        for (Traffic pattern : Traffic.values()) {
            for (double perMinute : new double[]{2, 4}) {
                System.out.printf("%n%s, %.0f people a minute%n", pattern, perMinute);
                System.out.printf("  %-16s %10s %10s %10s %13s %12s %10s%n",
                        "strategy", "mean wait", "p95 wait", "max wait", "mean journey", "floors/hour", "unserved");
                double[][] rows = compare(pattern, perMinute);
                for (int s = 0; s < rows.length; s++) {
                    double[] r = rows[s];
                    System.out.printf("  %-16s %9.1fs %9.1fs %9.0fs %12.1fs %12.0f %10.1f%n",
                            Strategy.ALL.get(s), r[0], r[1], r[2], r[3], r[4], r[5]);
                }
            }
        }

        System.out.printf("%nMean wait in seconds as the load grows, LUNCH traffic"
                + " (a strategy that cannot keep up leaves people unserved)%n");
        System.out.printf("  %-10s", "per minute");
        for (Strategy s : Strategy.ALL) System.out.printf(" %16s", s);
        System.out.println();
        for (int perMinute = 2; perMinute <= 20; perMinute += 2) {
            System.out.printf("  %-10d", perMinute);
            for (double[] r : compare(Traffic.LUNCH, perMinute)) {
                System.out.printf(" %15.1f%s", r[0], r[5] > 0 ? "*" : " ");
            }
            System.out.println();
        }
        System.out.println("  * not everyone delivered within four hours of the last arrival");
    }
}
