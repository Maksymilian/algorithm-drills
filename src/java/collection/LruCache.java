package collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Pamięć podręczna LRU (least recently used): ograniczona pojemność, a po jej przekroczeniu
/// wyrzucamy element najdawniej używany.
///
/// **Treść.** `get(key)` i `put(key, value)` w O(1). Odczyt też liczy się jako
/// użycie.
///
/// **Pomysł: dwie struktury naraz.**
///
/// - `HashMap<K, Node>` znajduje węzeł po kluczu w O(1);
///
/// - lista dwukierunkowa trzyma kolejność użycia: na początku najnowszy, na końcu kandydat do
///    wyrzucenia. Węzeł wyjmujemy i wstawiamy na początek w O(1), bo zna sąsiadów.
///
/// Dwa węzły-strażnicy (`head`, `tail`) usuwają przypadki brzegowe: lista nigdy nie jest
/// naprawdę pusta, więc nie ma sprawdzania `null`.
///
/// **Wersja w 5 linijkach.** [LinkedHashMap] z `accessOrder = true` robi dokładnie
/// to samo, a `removeEldestEntry` wyrzuca najstarszy element ([#withLinkedHashMap]).
/// Warto ją pokazać, ale rekruter zwykle poprosi o wersję ręczną.
///
/// **Dopytania.** Wątki: najprościej `synchronized` na obu metodach. Lepiej podzielić
/// cache na segmenty z osobnymi blokadami. W produkcji używa się biblioteki Caffeine. Wariant LFU
/// (najrzadziej używany) to mapa licznik → `LinkedHashSet`.
public final class LruCache<K, V> {

    private final class Node {
        final K key;
        V value;
        Node prev, next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node> nodes = new HashMap<>();
    private final Node head = new Node(null, null);   // head.next to najnowszy element
    private final Node tail = new Node(null, null);   // tail.prev to najstarszy element

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("pojemność musi być dodatnia");
        }
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        Node node = nodes.get(key);
        if (node == null) {
            return null;
        }
        moveToFront(node);
        return node.value;
    }

    public void put(K key, V value) {
        Node node = nodes.get(key);
        if (node != null) {
            node.value = value;
            moveToFront(node);
            return;
        }
        evictIfFull();
        addNew(key, value);
    }

    public int size() {
        return nodes.size();
    }

    public List<K> keys() {
        List<K> keys = new ArrayList<>();
        for (Node n = head.next; n != tail; n = n.next) {
            keys.add(n.key);
        }
        return keys;
    }

    private void moveToFront(Node node) {
        unlink(node);
        addFirst(node);
    }

    private void evictIfFull() {
        if (nodes.size() == capacity) {
            Node oldest = tail.prev;
            unlink(oldest);
            nodes.remove(oldest.key);
        }
    }

    private void addNew(K key, V value) {
        Node node = new Node(key, value);
        nodes.put(key, node);
        addFirst(node);
    }

    private void unlink(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void addFirst(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    public static <K, V> Map<K, V> withLinkedHashMap(int capacity) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    static void main() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");                      // "a" staje się najnowsze
        cache.put("c", 3);                   // wylatuje "b", najdawniej używane
        IO.println(cache.keys());    // [c, a]
    }
}
