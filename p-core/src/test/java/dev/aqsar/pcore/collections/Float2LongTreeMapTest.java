package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Float2LongTreeMapTest {

    private Float2LongTreeMap map;

    @BeforeEach
    void setUp() {
        map = Float2LongTreeMap.builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1.0f, 100L);
        map.put(2.0f, 200L);
        map.put(3.0f, 300L);

        assertEquals(100L, map.get(1.0f));
        assertEquals(200L, map.get(2.0f));
        assertEquals(300L, map.get(3.0f));
    }

    @Test
    void testPutOverwrite() {
        map.put(1.0f, 100L);
        map.put(1.0f, 999L);

        assertEquals(999L, map.get(1.0f));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(Float2LongTreeMap.DEFAULT_NULL_VALUE, map.get(123.456f));
    }

    @Test
    void testGetOrDefault() {
        map.put(1.0f, 100L);

        assertEquals(100L, map.getOrDefault(1.0f, -1L));
        assertEquals(-1L, map.getOrDefault(2.0f, -1L));
    }

    @Test
    void testContains() {
        map.put(1.0f, 10L);
        map.put(2.0f, 20L);

        assertTrue(map.containsKey(1.0f));
        assertTrue(map.containsKey(2.0f));
        assertFalse(map.containsKey(3.0f));

        assertTrue(map.containsValue(20L));
        assertFalse(map.containsValue(999L));
    }

    @Test
    void testRemove() {
        map.put(1.0f, 10L);
        map.put(2.0f, 20L);

        assertEquals(10L, map.remove(1.0f));
        assertFalse(map.containsKey(1.0f));
        assertEquals(1, map.size());

        // removing a missing key returns null sentinel
        assertEquals(Float2LongTreeMap.DEFAULT_NULL_VALUE, map.remove(999.0f));
    }

    @Test
    void testSizeAndClear() {
        assertTrue(map.isEmpty());
        map.put(1.0f, 1L);
        map.put(2.0f, 2L);
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testPutAll() {
        var src = new java.util.HashMap<Float, Long>();
        src.put(1.0f, 100L);
        src.put(2.0f, 200L);
        src.put(3.0f, 300L);

        map.putAll(src);

        assertEquals(3, map.size());
        assertEquals(100L, map.get(1.0f));
        assertEquals(200L, map.get(2.0f));
        assertEquals(300L, map.get(3.0f));
    }

    @Test
    void testSortedOrder() {
        map.put(5.0f, 5L);
        map.put(1.0f, 1L);
        map.put(3.0f, 3L);

        var iter = map.entrySet().iterator();
        float prev = Float.NEGATIVE_INFINITY;
        while (iter.hasNext()) {
            var entry = iter.next();
            assertTrue(entry.getKey() >= prev);
            prev = entry.getKey();
        }
    }

    @Test
    void testIteratorPool() {
        assertDoesNotThrow(() -> {
            try (var iter = map.borrowIterator()) {
                assertNotNull(iter);
            }
        });
    }

    @Test
    void testIteratorTraversal() {
        map.put(1.0f, 10L);
        map.put(2.0f, 20L);
        map.put(3.0f, 30L);

        Set<Float> keys = new HashSet<>();
        try (var iter = map.borrowIterator()) {
            while (iter.hasNext()) {
                keys.add(iter.nextKey());
            }
        }

        assertEquals(Set.of(1.0f, 2.0f, 3.0f), keys);
    }

    @Test
    void testIteratorConcurrentModification() {
        map.put(1.0f, 10L);
        map.put(2.0f, 20L);

        try (var iter = map.borrowIterator()) {
            iter.nextKey();
            map.put(3.0f, 30L);
            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    @Test
    void testBuilderDefaults() {
        var built = Float2LongTreeMap.builder().build();
        assertNotNull(built);
        assertTrue(built.isEmpty());
        assertEquals(0, built.size());
    }

    @Test
    void testBuilderInitialCapacity() {
        var built = Float2LongTreeMap.builder().initialCapacity(32).build();
        for (int i = 0; i < 20; i++) {
            built.put((float) i, i * 10L);
        }
        assertEquals(20, built.size());
    }

    @Test
    void testBuilderDisableIteratorPool() {
        var built = Float2LongTreeMap.builder().disableIteratorPool().build();
        assertNull(built.borrowIterator());
    }

    @Test
    void testSpecialFloatKeys() {
        map.put(Float.NaN, 999L);
        map.put(+0.0f, 111L);
        map.put(-0.0f, 222L); // should overwrite +0.0f if treated equal

        assertEquals(222L, map.get(+0.0f));
        assertEquals(999L, map.get(Float.NaN));
    }
}
