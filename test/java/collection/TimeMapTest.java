package collection;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeMapTest {

    private static TimeMap btcPrices() {
        TimeMap map = new TimeMap();
        map.set("BTC", "60000", 10);
        map.set("BTC", "62000", 20);
        map.set("BTC", "58000", 30);
        return map;
    }

    @Test
    void returnsTheValueAtOrBeforeTheGivenTime() {
        TimeMap map = btcPrices();
        assertNull(map.get("BTC", 9));
        assertEquals("60000", map.get("BTC", 10));
        assertEquals("62000", map.get("BTC", 29));
        assertEquals("58000", map.get("BTC", 1000));
        assertNull(map.get("ETH", 20));
    }

    @Test
    void listsChangesInATimeRange() {
        TimeMap map = btcPrices();
        assertEquals(Map.of(20, "62000", 30, "58000"), map.changes("BTC", 15, 30));
        assertEquals(Map.of(), map.changes("ETH", 0, 100));
    }

    @Test
    void laterWriteAtTheSameTimeReplacesTheValue() {
        TimeMap map = new TimeMap();
        map.set("k", "a", 5);
        map.set("k", "b", 5);
        assertEquals("b", map.get("k", 5));
    }
}
