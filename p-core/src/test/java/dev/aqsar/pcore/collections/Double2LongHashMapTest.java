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

        assertEquals(100L, map.getLong(1.5));
        assertEquals(200L, map.getLong(2.5));
    }

    @Test
    void testGetMissing() {
        assertEquals(Long.MIN_VALUE, map.getLong(999.999));
    }

    @Test
    void testContainsKey() {
        map.put(10.0, 123L);
        assertTrue(map.containsDoubleKey(10.0));
        assertFalse(map.containsDoubleKey(99.9));
    }

    @Test
    void testContainsValue() {
        map.put(1.0, 100L);
        map.put(2.0, 200L);
        assertTrue(map.containsLongValue(100L));
        assertFalse(map.containsLongValue(999L));
    }

    @Test
    void testRemove() {
        map.put(3.3, 300L);
        assertEquals(300L, map.removeLong(3.3));
        assertEquals(Long.MIN_VALUE, map.getLong(3.3));
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
        assertEquals(Long.MIN_VALUE, map.getLong(1.1));
    }

    @Test
    void testBoxedPutAndGet() {
        map.put(1.5, 100L);
        map.put(2.5, 200L);

        assertEquals(100L, map.get(1.5));
        assertEquals(200L, map.get(2.5));
        assertNull(map.get(999.0)); // missing boxed key still returns null per Map API
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
        assertEquals(10L, map.replaceLong(1.0, 20L));
        assertEquals(20L, map.getLong(1.0));

        assertEquals(Long.MIN_VALUE, map.replaceLong(2.0, 50L)); // key doesn't exist
    }

    @Test
    void testComputeIfAbsent() {
        long result = map.computeIfAbsentLong(3.14, k -> 42L);
        assertEquals(42L, result);
        assertEquals(42L, map.getLong(3.14));

        // Existing key — lambda should not run
        long again = map.computeIfAbsentLong(3.14, k -> 99L);
        assertEquals(42L, again);
    }

    @Test
    void testComputeIfPresent() {
        map.put(1.0, 10L);
        map.computeIfPresentLong(1.0, (k, v) -> v + 5);
        assertEquals(15L, map.getLong(1.0));

        // Non-existent key should be ignored
        map.computeIfPresentLong(2.0, (k, v) -> 100L);
        assertEquals(Long.MIN_VALUE, map.getLong(2.0));
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
        map.computeLong(1.0, (k, v) -> v == map.nullValue ? 10L : v + 1);
        assertEquals(10L, map.getLong(1.0));

        map.computeLong(1.0, (k, v) -> v + 5);
        assertEquals(15L, map.getLong(1.0));
    }

    @Test
    void testMerge() {
        map.putLong(1.0, 10L);
        map.mergeLong(1.0, 5L, Long::sum);
        assertEquals(15L, map.getLong(1.0));

        map.mergeLong(2.0, 7L, Long::sum);
        assertEquals(7L, map.getLong(2.0));
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
