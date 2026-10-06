package graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/// Najkrótsze ścieżki z jednego wierzchołka: algorytm Dijkstry.
///
/// **Treść.** Graf skierowany z nieujemnymi wagami krawędzi (np. miasta i czasy przejazdu).
/// Znajdź najkrótszą odległość z wierzchołka `source` do każdego innego i samą trasę.
///
/// **Pomysł.** Kolejka priorytetowa wierzchołków według dotychczas znalezionej odległości.
/// Zdejmujemy najbliższy. Jego odległość jest już ostateczna, bo wagi są nieujemne: żadna okrężna
/// droga nie będzie krótsza. Potem „relaksujemy” jego krawędzie: jeśli przez niego do sąsiada jest
/// bliżej, poprawiamy odległość i wkładamy sąsiada do kolejki.
///
/// **Leniwe usuwanie.** [PriorityQueue] nie umie zmniejszyć klucza, więc wkładamy nowy
/// wpis. Stary, nieaktualny wpis pomijamy przy zdjęciu (`distance > dist[vertex]`).
///
/// **Złożoność.** O((V + E) log V) czasu, O(V + E) pamięci. Graf trzymamy jako listy sąsiedztwa:
/// macierz kosztowałaby O(V²) nawet dla rzadkiego grafu.
///
/// **Dopytania.**
///
/// - Ujemne wagi: Bellman-Ford, O(V · E), który wykrywa też ujemne cykle.
///
/// - Wszystkie wagi równe 1: wystarczy BFS na `ArrayDeque`, O(V + E).
///
/// - Tylko jeden cel: przerywamy po zdjęciu celu z kolejki. Z heurystyką odległości to A\*.
///
/// - Wszystkie pary: Floyd-Warshall, O(V³).
public final class ShortestPath {

    public record Edge(int to, int weight) {}

    private record Visit(long distance, int vertex) {}

    public static final long UNREACHABLE = Long.MAX_VALUE;

    private final List<List<Edge>> adjacency = new ArrayList<>();

    public ShortestPath(int vertices) {
        for (int v = 0; v < vertices; v++) {
            adjacency.add(new ArrayList<>());
        }
    }

    public void addEdge(int from, int to, int weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("Dijkstra nie obsługuje ujemnych wag");
        }
        adjacency.get(from).add(new Edge(to, weight));
    }

    public static final class Paths {
        private final long[] distance;
        private final int[] previous;

        Paths(int vertices, int source) {
            distance = new long[vertices];
            previous = new int[vertices];
            Arrays.fill(distance, UNREACHABLE);
            Arrays.fill(previous, -1);
            distance[source] = 0;
        }

        public long distanceTo(int target) {
            return distance[target];
        }

        public List<Integer> pathTo(int target) {
            if (distance[target] == UNREACHABLE) {
                return List.of();
            }
            List<Integer> path = new ArrayList<>();
            for (int v = target; v != -1; v = previous[v]) {
                path.add(v);
            }
            return path.reversed();
        }

        boolean improve(int u, Edge edge) {
            long candidate = distance[u] + edge.weight();
            if (candidate >= distance[edge.to()]) {
                return false;
            }
            distance[edge.to()] = candidate;
            previous[edge.to()] = u;
            return true;
        }
    }

    public Paths from(int source) {
        Paths paths = new Paths(adjacency.size(), source);
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingLong(Visit::distance));
        queue.add(new Visit(0, source));
        while (!queue.isEmpty()) {
            relaxEdgesOf(queue.poll(), paths, queue);
        }
        return paths;
    }

    private void relaxEdgesOf(Visit visit, Paths paths, PriorityQueue<Visit> queue) {
        int u = visit.vertex();
        if (visit.distance() > paths.distanceTo(u)) {
            return;   // nieaktualny wpis, znamy już krótszą drogę
        }
        for (Edge edge : adjacency.get(u)) {
            if (paths.improve(u, edge)) {
                queue.add(new Visit(paths.distanceTo(edge.to()), edge.to()));
            }
        }
    }

    static void main() {
        // 0 = Kraków, 1 = Katowice, 2 = Kielce, 3 = Warszawa (czasy przejazdu w minutach)
        ShortestPath map = new ShortestPath(4);
        map.addEdge(0, 3, 300);   // bezpośrednio, ale wolno
        map.addEdge(0, 1, 60);
        map.addEdge(1, 3, 180);
        map.addEdge(0, 2, 90);
        map.addEdge(2, 3, 150);
        Paths paths = map.from(0);
        IO.println(paths.distanceTo(3) + " min przez " + paths.pathTo(3));   // 240 min przez [0, 1, 3]
    }
}
