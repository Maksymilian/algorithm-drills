package graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortestPathTest {

    private static ShortestPath polishRoads() {
        ShortestPath map = new ShortestPath(4);
        map.addEdge(0, 3, 300);
        map.addEdge(0, 1, 60);
        map.addEdge(1, 3, 180);
        map.addEdge(0, 2, 90);
        map.addEdge(2, 3, 150);
        return map;
    }

    @Test
    void detourIsShorterThanTheDirectRoad() {
        ShortestPath.Paths paths = polishRoads().from(0);
        assertEquals(240, paths.distanceTo(3));
        assertEquals(List.of(0, 1, 3), paths.pathTo(3));
        assertEquals(List.of(0), paths.pathTo(0));
    }

    @Test
    void unreachableVerticesAndOneWayEdges() {
        ShortestPath map = new ShortestPath(3);
        map.addEdge(0, 1, 5);
        ShortestPath.Paths fromOne = map.from(1);
        assertEquals(ShortestPath.UNREACHABLE, fromOne.distanceTo(0), "krawędź prowadzi tylko z 0 do 1");
        assertEquals(List.of(), fromOne.pathTo(2));
        assertThrows(IllegalArgumentException.class, () -> map.addEdge(1, 2, -1));
    }

    @Test
    void agreesWithBellmanFordOnRandomGraphs() {
        Random random = new Random(10);
        for (int round = 0; round < 200; round++) {
            int n = 1 + random.nextInt(8);
            List<int[]> edges = randomEdges(random, n);
            long[] expected = bellmanFord(n, edges);
            ShortestPath.Paths paths = graphOf(n, edges).from(0);
            for (int v = 0; v < n; v++) {
                assertEquals(expected[v], paths.distanceTo(v), "wierzchołek " + v);
            }
        }
    }

    private static List<int[]> randomEdges(Random random, int n) {
        List<int[]> edges = new ArrayList<>();
        int count = random.nextInt(20);
        for (int e = 0; e < count; e++) {
            edges.add(new int[]{random.nextInt(n), random.nextInt(n), random.nextInt(10)});
        }
        return edges;
    }

    private static ShortestPath graphOf(int n, List<int[]> edges) {
        ShortestPath graph = new ShortestPath(n);
        edges.forEach(e -> graph.addEdge(e[0], e[1], e[2]));
        return graph;
    }

    private static long[] bellmanFord(int n, List<int[]> edges) {
        long[] dist = new long[n];
        Arrays.fill(dist, ShortestPath.UNREACHABLE);
        dist[0] = 0;
        for (int pass = 0; pass < n; pass++) {
            edges.forEach(e -> relax(dist, e));
        }
        return dist;
    }

    private static void relax(long[] dist, int[] edge) {
        if (dist[edge[0]] != ShortestPath.UNREACHABLE) {
            dist[edge[1]] = Math.min(dist[edge[1]], dist[edge[0]] + edge[2]);
        }
    }
}
