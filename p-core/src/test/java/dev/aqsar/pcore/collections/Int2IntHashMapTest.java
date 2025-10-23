package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Int2IntHashMapTest {

    private Int2IntHashMap map;

    @BeforeEach
    void setUp() {
        map = Int2IntHashMap.builder().build();
    }

    @Test
    void testPutAndGet() {
        map.putInt(1, 100);
        map.putInt(2, 200);
        map.putInt(3, 300);

        assertEquals(100, map.getInt(1));
        assertEquals(200, map.getInt(2));
        assertEquals(300, map.getInt(3));
    }

    @Test
    void testPutOverwrite() {
        map.putInt(1, 100);
        map.putInt(1, 999);

        assertEquals(999, map.getInt(1));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        // Missing key returns nullValue sentinel (Integer.MIN_VALUE)
        assertEquals(Integer.MIN_VALUE, map.getInt(999));
    }

    @Test
    void testGetOrDefault() {
        map.putInt(1, 100);

        assertEquals(100, map.getOrDefaultInt(1, 999));
        assertEquals(999, map.getOrDefaultInt(2, 999));
    }

    @Test
    void testContains() {
        map.putInt(1, 100);
        map.putInt(2, 200);

        assertTrue(map.containsIntKey(1));
        assertTrue(map.containsIntKey(2));
        assertFalse(map.containsIntKey(3));

        assertTrue(map.containsIntValue(100));
        assertFalse(map.containsIntValue(999));
    }

    @Test
    void testRemove() {
        map.putInt(1, 100);
        map.putInt(2, 200);

        assertEquals(100, map.removeInt(1));
        assertFalse(map.containsIntKey(1));
        assertEquals(1, map.size());

        assertEquals(Integer.MIN_VALUE, map.removeInt(999)); // remove non-existent returns nullValue
    }

    @Test
    void testSizeAndClear() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.putInt(1, 100);
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());

        map.putInt(2, 200);
        map.putInt(3, 300);
        assertEquals(3, map.size());

        map.removeInt(2);
        assertEquals(2, map.size());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.containsIntKey(1));
    }

    @Test
    void testPutAll() {
        var source = new java.util.HashMap<Integer, Integer>();
        source.put(1, 100);
        source.put(2, 200);
        source.put(3, 300);

        map.putAll(source);

        assertEquals(3, map.size());
        assertEquals(100, map.getInt(1));
        assertEquals(200, map.getInt(2));
        assertEquals(300, map.getInt(3));
    }

    @Test
    void testMinValueKey() {
        assertThrows(IllegalArgumentException.class, () -> map.putInt(Integer.MIN_VALUE, 123));
        assertThrows(IllegalArgumentException.class, () -> map.containsIntKey(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> map.getInt(Integer.MIN_VALUE));
    }

    @Test
    void testManyCollisions() {
        for (int i = 0; i < 1000; i++) {
            map.putInt(i, i * 10);
        }

        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals(i * 10, map.getInt(i));
        }
    }

    @Test
    void testRemoveAndReinsert() {
        map.putInt(1, 100);
        map.putInt(2, 200);
        map.putInt(3, 300);

        map.removeInt(2);

        assertTrue(map.containsIntKey(1));
        assertFalse(map.containsIntKey(2));
        assertTrue(map.containsIntKey(3));

        map.putInt(2, 222);
        assertEquals(222, map.getInt(2));
    }

    @Test
    void testResizeGrowth() {
        map = Int2IntHashMap.builder().initialCapacity(4).build();

        for (int i = 0; i < 100; i++) {
            map.putInt(i, i * 10);
        }

        assertEquals(100, map.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 10, map.getInt(i));
        }
    }

    @Test
    void testResizePreservesEntries() {
        map.putInt(1, 100);
        map.putInt(2, 200);

        for (int i = 3; i < 50; i++) {
            map.putInt(i, i * 10);
        }

        assertEquals(100, map.getInt(1));
        assertEquals(200, map.getInt(2));
    }

    @Test
    void testIteratorPooling() {
        assertEquals(8, map.availableIteratorCount());

        var iter = map.borrowIterator();
        assertNotNull(iter);
        assertEquals(7, map.availableIteratorCount());

        map.returnIterator(iter);
        assertEquals(8, map.availableIteratorCount());
    }

    @Test
    void testIteratorExhaustion() {
        var iters = new Int2IntHashMap.IntIntHashMapIterator[8];
        for (int i = 0; i < 8; i++) {
            iters[i] = map.borrowIterator();
            assertNotNull(iters[i]);
        }

        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());

        map.returnIterator(iters[0]);
        assertNotNull(map.borrowIterator());
    }

    @Test
    void testIteratorTraversal() {
        map.putInt(1, 100);
        map.putInt(2, 200);
        map.putInt(3, 300);

        try (var iter = map.borrowIterator()) {
            Set<Integer> keys = new HashSet<>();
            while (iter.hasNext()) {
                keys.add(iter.nextKey());
            }
            assertEquals(3, keys.size());
            assertTrue(keys.containsAll(Set.of(1, 2, 3)));
        }
    }

    @Test
    void testIteratorForEachRemaining() {
        map.putInt(1, 100);
        map.putInt(2, 200);
        map.putInt(3, 300);

        try (var iter = map.borrowIterator()) {
            Set<Integer> keys = new HashSet<>();
            Set<Integer> values = new HashSet<>();

            iter.forEachRemaining((k, v) -> {
                keys.add(k);
                values.add(v);
            });

            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.containsAll(Set.of(1, 2, 3)));
            assertTrue(values.containsAll(Set.of(100, 200, 300)));
        }
    }

    @Test
    void testIteratorAutoClose() {
        assertEquals(8, map.availableIteratorCount());
        try (var iter = map.borrowIterator()) {
            assertEquals(7, map.availableIteratorCount());
        }
        assertEquals(8, map.availableIteratorCount());
    }

    @Test
    void testIteratorConcurrentModification() {
        map.putInt(1, 100);
        map.putInt(2, 200);

        try (var iter = map.borrowIterator()) {
            iter.nextKey();
            map.putInt(3, 300);
            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    @Test
    void testForEach() {
        map.putInt(1, 100);
        map.putInt(2, 200);
        map.putInt(3, 300);

        Set<Integer> keys = new HashSet<>();
        Set<Integer> values = new HashSet<>();

        map.forEachIntInt((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.containsAll(Set.of(1, 2, 3)));
        assertTrue(values.containsAll(Set.of(100, 200, 300)));
    }

    @Test
    void testForEachEmpty() {
        map.forEachIntInt((k, v) -> fail("Should not be called on empty map"));
    }

    @Test
    void testBoxedPutAndGet() {
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(100, map.get(1));
        assertEquals(200, map.get(2));
        assertNull(map.get(999));
    }

    @Test
    void testBoxedContains() {
        map.put(1, 100);

        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue(100));
        assertFalse(map.containsKey(2));
        assertFalse(map.containsValue(200));
    }

    @Test
    void testBoxedRemove() {
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(100, map.remove(1));
        assertNull(map.remove(999));
    }

    @Test
    void testZeroKey() {
        map.putInt(0, 999);
        assertEquals(999, map.getInt(0));
        assertTrue(map.containsIntKey(0));

        map.removeInt(0);
        assertFalse(map.containsIntKey(0));
    }

    @Test
    void testNegativeKeys() {
        map.putInt(-1, 100);
        map.putInt(-2, 200);
        map.putInt(-999, 300);

        assertEquals(100, map.getInt(-1));
        assertEquals(200, map.getInt(-2));
        assertEquals(300, map.getInt(-999));
    }

    @Test
    void testSameKeyValuePairs() {
        map.putInt(1, 1);
        map.putInt(2, 2);
        map.putInt(3, 3);

        assertEquals(1, map.getInt(1));
        assertEquals(2, map.getInt(2));
        assertEquals(3, map.getInt(3));
    }

    @Test
    void testLargeMap() {
        int size = 10_000;
        for (int i = 0; i < size; i++) {
            map.putInt(i, i * 10);
        }
        assertEquals(size, map.size());

        for (int i = 0; i < size; i += 100) {
            assertEquals(i * 10, map.getInt(i));
        }
    }

    @Test
    void testBuilderDefaults() {
        map = Int2IntHashMap.builder().build();
        assertNotNull(map);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuilderInitialCapacity() {
        map = Int2IntHashMap.builder().initialCapacity(128).build();
        for (int i = 0; i < 50; i++) {
            map.putInt(i, i);
        }
        assertEquals(50, map.size());
    }

    @Test
    void testBuilderLoadFactor() {
        map = Int2IntHashMap.builder().initialCapacity(16).loadFactor(0.5f).build();
        assertNotNull(map);
    }

    @Test
    void testBuilderDisableIteratorPool() {
        map = Int2IntHashMap.builder().disableIteratorPool().build();
        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());
    }
}
