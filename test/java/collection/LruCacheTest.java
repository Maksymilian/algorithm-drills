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
        assertThrows(IllegalArgumentException.class, () -> new LruCache<String, String>(-1));
        assertThrows(IllegalArgumentException.class, () -> new LruCache<String, String>(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> LruCache.withLinkedHashMap(0));
    }

    @Test
    void rejectsNullKeyInPut() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        assertThrows(NullPointerException.class, () -> cache.put(null, 1));
        assertEquals(0, cache.size());
    }

    @Test
    void rejectsNullValueInPut() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        assertThrows(NullPointerException.class, () -> cache.put("a", null));
        assertEquals(0, cache.size());
    }

    @Test
    void rejectedNullDoesNotChangeExistingEntry() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        assertThrows(NullPointerException.class, () -> cache.put("a", null));
        assertEquals(List.of("b", "a"), cache.keys(), "odrzucony put nie odświeża klucza");
        assertEquals(1, cache.get("a"));
    }

    @Test
    void rejectsNullKeyInGet() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        assertThrows(NullPointerException.class, () -> cache.get(null));
    }

    @Test
    void missingKeyReturnsNullWithoutChangingOrder() {
        LruCache<String, Integer> cache = new LruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        assertNull(cache.get("x"));
        assertEquals(List.of("b", "a"), cache.keys());
    }

    @Test
    void capacityOneKeepsOnlyTheLastKey() {
        LruCache<String, Integer> cache = new LruCache<>(1);
        cache.put("a", 1);
        cache.put("b", 2);
        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(List.of("b"), cache.keys());
    }

    @Test
    void sizeNeverExceedsCapacity() {
        LruCache<Integer, Integer> cache = new LruCache<>(3);
        for (int i = 0; i < 100; i++) {
            cache.put(i, i);
            assertEquals(Math.min(i + 1, 3), cache.size());
        }
        assertEquals(List.of(99, 98, 97), cache.keys());
    }
}
