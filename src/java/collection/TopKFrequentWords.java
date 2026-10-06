package collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/// k najczęstszych słów.
///
/// **Treść.** Z listy słów zwróć k najczęstszych, od najczęstszego. Przy równej liczbie
/// wystąpień wcześniej idzie słowo wcześniejsze alfabetycznie.
///
/// **Pomysł: `HashMap` + kopiec o rozmiarze k.** Najpierw liczymy wystąpienia w mapie.
/// Potem trzymamy kopiec minimalny ([PriorityQueue]) z k najlepszymi dotąd słowami. Na jego
/// szczycie jest _najsłabsze_ z nich. Każde nowe słowo wkładamy, a gdy kopiec ma k + 1
/// elementów, zdejmujemy najsłabsze. Na koniec zdejmujemy wszystko (od najsłabszego) i
/// odwracamy kolejność.
///
/// **Dlaczego kopiec minimalny, a nie maksymalny?** Maksymalny musiałby pomieścić wszystkie m
/// różnych słów. Minimalny trzyma tylko k.
///
/// **Złożoność.** O(n + m log k) czasu dla n słów i m różnych słów, O(m) pamięci na mapę.
/// Sortowanie całej mapy kosztuje O(m log m). Sortowanie kubełkowe po liczbie wystąpień daje O(n).
///
/// **Dopytania.** Nieskończony strumień i mało pamięci: algorytm Misra-Gries albo Count-Min
/// Sketch z kopcem, z wynikiem przybliżonym. Wiele maszyn: liczymy lokalnie, potem łączymy liczniki
/// (map-reduce).
public class TopKFrequentWords {

    private static final Comparator<Map.Entry<String, Integer>> WEAKEST_FIRST =
            Map.Entry.<String, Integer>comparingByValue()
                    .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder()));

    public static List<String> topK(Collection<String> words, int k) {
        requirePositive(k);
        PriorityQueue<Map.Entry<String, Integer>> heap = new PriorityQueue<>(WEAKEST_FIRST);
        for (Map.Entry<String, Integer> entry : countWords(words).entrySet()) {
            heap.add(entry);
            if (heap.size() > k) {
                heap.poll();   // wyrzucamy najsłabsze z k + 1
            }
        }
        return strongestFirst(heap);
    }

    private static Map<String, Integer> countWords(Collection<String> words) {
        Map<String, Integer> counts = new HashMap<>();
        for (String word : words) {
            counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }

    private static List<String> strongestFirst(PriorityQueue<Map.Entry<String, Integer>> heap) {
        List<String> result = new ArrayList<>();
        while (!heap.isEmpty()) {
            result.add(heap.poll().getKey());
        }
        return result.reversed();
    }

    private static void requirePositive(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k musi być dodatnie");
        }
    }

    void main() {
        List<String> words = List.of("kot", "pies", "kot", "mysz", "pies", "kot", "ryba", "mysz");
        IO.println(topK(words, 2));   // [kot, mysz] - mysz i pies po 2, mysz wcześniej w alfabecie
    }
}
