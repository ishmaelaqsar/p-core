package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class Double2LongHashMapTest {

    private Double2LongHashMap map;

    @BeforeEach
    void setup() {
        map = Double2LongHashMap.builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1.5, 100L);
        map.put(2.5, 200L);

        assertEquals(100L, map.get(1.5));
        assertEquals(200L, map.get(2.5));
    }

    @Test
    void testGetMissing() {
        assertEquals(Long.MIN_VALUE, map.get(999.999));
    }

    @Test
    void testContainsKey() {
        map.put(10.0, 123L);
        assertTrue(map.containsKey(10.0));
        assertFalse(map.containsKey(99.9));
    }

    @Test
    void testContainsValue() {
        map.put(1.0, 100L);
        map.put(2.0, 200L);
        assertTrue(map.containsValue(100L));
        assertFalse(map.containsValue(999L));
    }

    @Test
    void testRemove() {
        map.put(3.3, 300L);
        assertEquals(300L, map.remove(3.3));
        assertEquals(Long.MIN_VALUE, map.get(3.3));
        assertFalse(map.containsKey(3.3));
    }

    @Test
    void testSizeAndIsEmpty() {
        assertTrue(map.isEmpty());
        map.put(1.0, 111L);
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    void testClear() {
        map.put(1.1, 10L);
        map.put(2.2, 20L);
        map.clear();
        assertEquals(0, map.size());
        assertEquals(Long.MIN_VALUE, map.get(1.1));
    }

    @Test
    void testBoxedPutAndGet() {
        map.put(Double.valueOf(1.5), Long.valueOf(100L));
        map.put(Double.valueOf(2.5), Long.valueOf(200L));

        assertEquals(100L, map.get(Double.valueOf(1.5)));
        assertEquals(200L, map.get(Double.valueOf(2.5)));
        assertNull(map.get(Double.valueOf(999.0))); // missing boxed key still returns null per Map API
    }

    @Test
    void testBoxedContainsKeyValue() {
        map.put(1.1, 10L);
        map.put(2.2, 20L);

        assertTrue(map.containsKey(Double.valueOf(1.1)));
        assertTrue(map.containsKey(Double.valueOf(2.2)));
        assertFalse(map.containsKey(Double.valueOf(3.3)));
    }

    @Test
    void testNullKeyOrValueNotAllowed() {
        assertThrows(NullPointerException.class, () -> map.put(null, 100L));
        assertThrows(NullPointerException.class, () -> map.put(1.5, null));
    }

    @Test
    void testBuilderDefaultsWithNullSentinels() {
        map = Double2LongHashMap.builder().build();
        assertEquals(Long.MIN_VALUE, map.nullValue);
        assertEquals(0, map.size());
    }

    @Test
    void testReplace() {
        map.put(1.0, 10L);
        assertEquals(10L, map.replace(1.0, 20L));
        assertEquals(20L, map.get(1.0));

        assertEquals(Long.MIN_VALUE, map.replace(2.0, 50L)); // key doesn't exist
    }

    @Test
    void testComputeIfAbsent() {
        long result = map.computeIfAbsent(3.14, k -> 42L);
        assertEquals(42L, result);
        assertEquals(42L, map.get(3.14));

        // Existing key — lambda should not run
        long again = map.computeIfAbsent(3.14, k -> 99L);
        assertEquals(42L, again);
    }

    @Test
    void testComputeIfPresent() {
        map.put(1.0, 10L);
        map.computeIfPresent(1.0, (k, v) -> v + 5);
        assertEquals(15L, map.get(1.0));

        // Non-existent key should be ignored
        map.computeIfPresent(2.0, (k, v) -> 100L);
        assertEquals(Long.MIN_VALUE, map.get(2.0));
    }

    @Test
    void testForEach() {
        map.put(1.0, 100L);
        map.put(2.0, 200L);

        long[] sum = {0};
        map.forEachDoubleLong((k, v) -> sum[0] += v);

        assertEquals(300L, sum[0]);
    }

    @Test
    void testEntrySetView() {
        map.put(1.0, 100L);
        map.put(2.0, 200L);

        assertEquals(2, map.entrySet().size());
        for (Map.Entry<Double, Long> e : map.entrySet()) {
            assertNotNull(e.getKey());
            assertNotNull(e.getValue());
        }
    }

    @Test
    void testIteratorRemoval() {
        map.put(1.0, 100L);
        map.put(2.0, 200L);

        try (var it = map.borrowIterator()) {
            assertTrue(it.hasNext());
            it.nextValue();
            it.remove();
        }

        assertEquals(1, map.size());
    }

    @Test
    void testGetOrDefault() {
        map.put(1.0, 10L);
        assertEquals(10L, map.getOrDefault(1.0, 99L));
        assertEquals(99L, map.getOrDefault(2.0, 99L));
    }

    @Test
    void testCompute() {
        map.compute(1.0, (k, v) -> v == map.nullValue ? 10L : v + 1);
        assertEquals(10L, map.get(1.0));

        map.compute(1.0, (k, v) -> v + 5);
        assertEquals(15L, map.get(1.0));
    }

    @Test
    void testMerge() {
        map.put(1.0, 10L);
        map.merge(1.0, 5L, Long::sum);
        assertEquals(15L, map.get(1.0));

        map.merge(2.0, 7L, Long::sum);
        assertEquals(7L, map.get(2.0));
    }

    @Test
    void testKeySetAndValuesView() {
        map.put(1.0, 100L);
        map.put(2.0, 200L);

        assertTrue(map.keySet().contains(1.0));
        assertTrue(map.values().contains(200L));
    }

    @Test
    void testNoSuchElementOnIterator() {
        var it = map.entrySet().iterator();
        assertThrows(NoSuchElementException.class, it::next);
    }
}
