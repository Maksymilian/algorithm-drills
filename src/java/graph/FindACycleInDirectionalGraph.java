package graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Cykl w grafie skierowanym podanym jako listy sąsiedztwa.
///
/// - [#findCycle(List)]: przeszukiwanie w głąb z trzema kolorami (nieodwiedzony, na stosie,
///   zakończony). Krawędź do wierzchołka, który jest wciąż na stosie, zamyka cykl; odtwarzamy go po
///   wskaźnikach do rodzica. Zwraca cykl z wierzchołkiem wejściowym powtórzonym na końcu, np.
///   `[1, 2, 3, 1]`, albo pustą listę. Wersja iteracyjna z jawnym stosem, więc graf z setkami
///   tysięcy wierzchołków nie przepełni stosu JVM.
/// - [#hasCycle(List)]: samo wykrywanie algorytmem Kahna: usuwamy wierzchołki o stopniu
///   wejściowym 0, dopóki takie są. To, co zostanie, leży na cyklu albo za nim.
///
/// Oba w czasie O(V + E) i pamięci O(V). Graf może być niespójny, więc przeszukiwanie startuje z
/// każdego nieodwiedzonego wierzchołka. Klasyczna pułapka: wierzchołek osiągalny dwiema drogami
/// (romb) to jeszcze nie cykl, dlatego liczy się tylko krawędź do wierzchołka **na stosie**.
/// `main` uruchamia zestaw przypadków brzegowych.
public class FindACycleInDirectionalGraph {

    private static final int WHITE = 0;   // nieodwiedzony
    private static final int GRAY  = 1;   // odwiedzony, wciąż na stosie DFS
    private static final int BLACK = 2;   // odwiedzony i w pełni zbadany

    public static List<Integer> findCycle(List<List<Integer>> adj) {
        int n = adj.size();
        int[] color = new int[n];
        int[] parent = new int[n];
        int[] nextEdge = new int[n];   // jak daleko w adj.get(u) doszła każda ramka
        Arrays.fill(parent, -1);

        Deque<Integer> stack = new ArrayDeque<>();

        // każdy wierzchołek potrzebuje startu: graf może być niespójny
        for (int s = 0; s < n; s++) {
            if (color[s] != WHITE) continue;

            color[s] = GRAY;
            stack.push(s);

            while (!stack.isEmpty()) {
                int u = stack.peek();                 // peek, nie pop: ramka zostaje
                List<Integer> edges = adj.get(u);

                if (nextEdge[u] < edges.size()) {
                    int v = edges.get(nextEdge[u]++); // zużyj jedną krawędź i wróć do pętli
                    if (color[v] == GRAY) {           // krawędź wsteczna: v jest wciąż na stosie
                        return buildCycle(u, v, parent);
                    }
                    if (color[v] == WHITE) {
                        parent[v] = u;
                        color[v] = GRAY;
                        stack.push(v);                // „zejdź” do v
                    }
                    // color[v] == BLACK: już w pełni zbadany, to nie cykl
                } else {
                    color[u] = BLACK;                 // post-order: wszystkie krawędzie obsłużone
                    stack.pop();                      // „wróć” z u
                }
            }
        }
        return List.of();
    }

    private static List<Integer> buildCycle(int u, int start, int[] parent) {
        List<Integer> path = new ArrayList<>();
        for (int x = u; x != start; x = parent[x]) path.add(x);
        path.add(start);
        Collections.reverse(path);   // start -> ... -> u
        path.add(start);             // domknij
        return path;
    }

    public static boolean hasCycle(List<List<Integer>> adj) {
        int n = adj.size();
        int[] inDegree = new int[n];
        for (List<Integer> edges : adj)
            for (int v : edges) inDegree[v]++;

        Deque<Integer> queue = new ArrayDeque<>();
        for (int u = 0; u < n; u++)
            if (inDegree[u] == 0) queue.add(u);

        int removed = 0;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            removed++;
            for (int v : adj.get(u))
                if (--inDegree[v] == 0) queue.add(v);
        }
        return removed < n;
    }

    // ---------- testy ----------

    private static int passed = 0;
    private static int failed = 0;

    private static List<List<Integer>> graph(int n, int[][] edges) {
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int[] e : edges) adj.get(e[0]).add(e[1]);
        return adj;
    }

    private static List<List<Integer>> chain(int n, boolean closeLoop) {
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>(1));
        for (int i = 0; i + 1 < n; i++) adj.get(i).add(i + 1);
        if (closeLoop) adj.get(n - 1).add(0);
        return adj;
    }

    private static boolean isRealCycle(List<Integer> cycle, List<List<Integer>> adj) {
        if (cycle.size() < 2) return false;
        if (!cycle.get(0).equals(cycle.get(cycle.size() - 1))) return false;
        for (int i = 0; i + 1 < cycle.size(); i++)
            if (!adj.get(cycle.get(i)).contains(cycle.get(i + 1))) return false;
        Set<Integer> interior = new HashSet<>(cycle.subList(0, cycle.size() - 1));
        return interior.size() == cycle.size() - 1;   // żaden wierzchołek nie powtarza się w środku cyklu
    }

    private static void check(String name, boolean cyclic, int n, int[][] edges) {
        check(name, cyclic, graph(n, edges));
    }

    private static void check(String name, boolean cyclic, List<List<Integer>> adj) {
        List<Integer> cycle = findCycle(adj);
        boolean kahn = hasCycle(adj);

        String problem = null;
        if (cyclic) {
            if (cycle.isEmpty()) problem = "found no cycle, expected one";
            else if (!isRealCycle(cycle, adj)) problem = "returned " + describe(cycle) + ", which is not a cycle";
        } else if (!cycle.isEmpty()) {
            problem = "reported cycle " + describe(cycle) + " in an acyclic graph";
        }
        if (problem == null && kahn != cyclic)
            problem = "hasCycle returned " + kahn + ", expected " + cyclic;

        if (problem == null) {
            passed++;
            IO.println("PASS  %-28s -> %s".formatted(name, cycle.isEmpty() ? "acyclic" : describe(cycle)));
        } else {
            failed++;
            IO.println("FAIL  %-28s -> %s".formatted(name, problem));
        }
    }

    private static String describe(List<Integer> cycle) {
        if (cycle.size() <= 12) return cycle.toString();
        return "cycle of length " + (cycle.size() - 1) + " starting " + cycle.subList(0, 4) + "...";
    }

    void main() {
        // podstawy
        check("triangle",         true,  3, new int[][]{{0,1},{1,2},{2,0}});
        check("two-node cycle",   true,  2, new int[][]{{0,1},{1,0}});
        check("self loop",        true,  1, new int[][]{{0,0}});
        check("simple chain",     false, 3, new int[][]{{0,1},{1,2}});

        // klasyczny fałszywy alarm: do C dochodzimy dwa razy, ale cyklu nie ma
        check("cross edge",       false, 3, new int[][]{{0,1},{0,2},{1,2}});
        check("diamond DAG",      false, 4, new int[][]{{0,1},{0,2},{1,3},{2,3}});

        // cykl schowany za ogonem, więc wierzchołek wejściowy do niego nie należy
        check("tail into cycle",  true,  5, new int[][]{{0,1},{1,2},{2,3},{3,4},{4,1}});

        // niespójne składowe
        check("cycle in 2nd comp", true, 5, new int[][]{{0,1},{2,3},{3,4},{4,2}});
        check("both comps acyclic", false, 4, new int[][]{{0,1},{2,3}});

        // kształty zdegenerowane
        check("empty graph",      false, 0, new int[][]{});
        check("isolated nodes",   false, 3, new int[][]{});

        // krawędzie równoległe i wierzchołek zasilający cykl, do którego nie należy
        check("parallel edges",   false, 2, new int[][]{{0,1},{0,1}});
        check("source into loop", true,  4, new int[][]{{0,1},{1,2},{2,3},{3,2}});

        // głębokość, której wersja rekurencyjna by nie przeżyła
        check("deep chain 200k",  false, chain(200_000, false));
        check("deep cycle 200k",  true,  chain(200_000, true));

        IO.println("%n%d passed, %d failed".formatted(passed, failed));
    }

}
