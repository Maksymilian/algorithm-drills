package collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LruCacheTest {

    @Test
    void evictsTheLeastRecentlyUsed() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(1, cache.get("a"));
        cache.put("c", 3);
        assertNull(cache.get("b"), "b było najdawniej używane");
        assertEquals(List.of("c", "a"), cache.keys());
    }

    @Test
    void overwritingAlsoCountsAsUse() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("c", 3);
        cache.put("a", 10);
        cache.put("d", 4);
        assertEquals(List.of("d", "a"), cache.keys(), "wylatuje c, nie nadpisane a");
        assertEquals(10, cache.get("a"));
        assertEquals(2, cache.size());
    }

    @Test
    void handBuiltListAgreesWithLinkedHashMap() {
        Random random = new Random(9);
        LruCache<Integer, Integer> cache = new LruCache<>(3);
        Map<Integer, Integer> reference = LruCache.withLinkedHashMap(3);
        for (int i = 0; i < 2000; i++) {
            putOrGet(random, i, cache, reference);
            assertEquals(new ArrayList<>(reference.keySet()), cache.keys().reversed(), "od najstarszego");
        }
    }

    private static void putOrGet(Random random, int value, LruCache<Integer, Integer> cache,
                                 Map<Integer, Integer> reference) {
        int key = random.nextInt(6);
        if (random.nextBoolean()) {
            cache.put(key, value);
            reference.put(key, value);
        } else {
            assertEquals(reference.get(key), cache.get(key));
        }
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<String, String>(0));
    }
}
