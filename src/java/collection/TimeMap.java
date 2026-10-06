package collection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/// Magazyn klucz → wartość z historią: jaka była wartość klucza w chwili t?
///
/// **Treść.** `set(key, value, t)` zapisuje wartość w chwili `t`.
/// `get(key, t)` zwraca wartość zapisaną w ostatniej chwili nie późniejszej niż `t`,
/// albo `null`, jeśli takiej nie ma.
///
/// **Pomysł: `HashMap` kluczy, a w niej [TreeMap] wersji.** Dla każdego klucza
/// drzewo czas → wartość. [TreeMap#floorEntry] zwraca największy czas ≤ t, czyli dokładnie
/// odpowiedź. Przy okazji [TreeMap#subMap] daje wszystkie zmiany w przedziale czasu.
///
/// **Złożoność.** `set` i `get` w O(log v) dla v wersji klucza. Pamięć O(liczba
/// zapisów).
///
/// **Dopytania.** Jeśli czasy przychodzą rosnąco, wystarczy `ArrayList` i wyszukiwanie
/// binarne: mniej pamięci niż drzewo. Dostęp z wielu wątków: `ConcurrentHashMap` i
/// `ConcurrentSkipListMap`, która ma te same `floorEntry` i `subMap`. Stare
/// wersje: `headMap(t).clear()`.
public final class TimeMap {

    private final Map<String, TreeMap<Integer, String>> history = new HashMap<>();

    public void set(String key, String value, int timestamp) {
        history.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
    }

    public String get(String key, int timestamp) {
        TreeMap<Integer, String> versions = history.get(key);
        if (versions == null) {
            return null;
        }
        Map.Entry<Integer, String> entry = versions.floorEntry(timestamp);
        return entry == null ? null : entry.getValue();
    }

    public NavigableMap<Integer, String> changes(String key, int from, int to) {
        TreeMap<Integer, String> versions = history.getOrDefault(key, new TreeMap<>());
        return Collections.unmodifiableNavigableMap(versions.subMap(from, true, to, true));
    }

    void main() {
        TimeMap prices = new TimeMap();
        prices.set("BTC", "60000", 10);
        prices.set("BTC", "62000", 20);
        prices.set("BTC", "58000", 30);
        IO.println(prices.get("BTC", 25));             // 62000
        IO.println(prices.get("BTC", 5));              // null, wtedy jeszcze nie było ceny
        IO.println(prices.changes("BTC", 15, 30));     // {20=62000, 30=58000}
    }
}
