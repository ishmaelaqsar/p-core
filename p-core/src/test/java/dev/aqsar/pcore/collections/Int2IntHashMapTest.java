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
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        assertEquals(100, map.get(1));
        assertEquals(200, map.get(2));
        assertEquals(300, map.get(3));
    }

    @Test
    void testPutOverwrite() {
        map.put(1, 100);
        map.put(1, 999);

        assertEquals(999, map.get(1));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        // Missing key returns nullValue sentinel (Integer.MIN_VALUE)
        assertEquals(Integer.MIN_VALUE, map.get(999));
    }

    @Test
    void testGetOrDefault() {
        map.put(1, 100);

        assertEquals(100, map.getOrDefault(1, 999));
        assertEquals(999, map.getOrDefault(2, 999));
    }

    @Test
    void testContains() {
        map.put(1, 100);
        map.put(2, 200);

        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertFalse(map.containsKey(3));

        assertTrue(map.containsValue(100));
        assertFalse(map.containsValue(999));
    }

    @Test
    void testRemove() {
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(100, map.remove(1));
        assertFalse(map.containsKey(1));
        assertEquals(1, map.size());

        assertEquals(Integer.MIN_VALUE, map.remove(999)); // remove non-existent returns nullValue
    }

    @Test
    void testSizeAndClear() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.put(1, 100);
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());

        map.put(2, 200);
        map.put(3, 300);
        assertEquals(3, map.size());

        map.remove(2);
        assertEquals(2, map.size());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.containsKey(1));
    }

    @Test
    void testPutAll() {
        var source = new java.util.HashMap<Integer, Integer>();
        source.put(1, 100);
        source.put(2, 200);
        source.put(3, 300);

        map.putAll(source);

        assertEquals(3, map.size());
        assertEquals(100, map.get(1));
        assertEquals(200, map.get(2));
        assertEquals(300, map.get(3));
    }

    @Test
    void testMinValueKey() {
        assertThrows(IllegalArgumentException.class, () -> map.put(Integer.MIN_VALUE, 123));
        assertThrows(IllegalArgumentException.class, () -> map.containsKey(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> map.get(Integer.MIN_VALUE));
    }

    @Test
    void testManyCollisions() {
        for (int i = 0; i < 1000; i++) {
            map.put(i, i * 10);
        }

        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals(i * 10, map.get(i));
        }
    }

    @Test
    void testRemoveAndReinsert() {
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        map.remove(2);

        assertTrue(map.containsKey(1));
        assertFalse(map.containsKey(2));
        assertTrue(map.containsKey(3));

        map.put(2, 222);
        assertEquals(222, map.get(2));
    }

    @Test
    void testResizeGrowth() {
        map = Int2IntHashMap.builder().initialCapacity(4).build();

        for (int i = 0; i < 100; i++) {
            map.put(i, i * 10);
        }

        assertEquals(100, map.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 10, map.get(i));
        }
    }

    @Test
    void testResizePreservesEntries() {
        map.put(1, 100);
        map.put(2, 200);

        for (int i = 3; i < 50; i++) {
            map.put(i, i * 10);
        }

        assertEquals(100, map.get(1));
        assertEquals(200, map.get(2));
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
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

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
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

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
        map.put(1, 100);
        map.put(2, 200);

        try (var iter = map.borrowIterator()) {
            iter.nextKey();
            map.put(3, 300);
            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    @Test
    void testForEach() {
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

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
        map.put(Integer.valueOf(1), Integer.valueOf(100));
        map.put(Integer.valueOf(2), Integer.valueOf(200));

        assertEquals(100, map.get(Integer.valueOf(1)));
        assertEquals(200, map.get(Integer.valueOf(2)));
        assertNull(map.get(Integer.valueOf(999)));
    }

    @Test
    void testBoxedContains() {
        map.put(1, 100);

        assertTrue(map.containsKey(Integer.valueOf(1)));
        assertTrue(map.containsValue(Integer.valueOf(100)));
        assertFalse(map.containsKey(Integer.valueOf(2)));
        assertFalse(map.containsValue(Integer.valueOf(200)));
    }

    @Test
    void testBoxedRemove() {
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(2, map.size());
        assertEquals(100, map.remove(Integer.valueOf(1)));
        assertNull(map.remove(Integer.valueOf(999)));
        assertEquals(1, map.size());
    }

    @Test
    void testZeroKey() {
        map.put(0, 999);
        assertEquals(999, map.get(0));
        assertTrue(map.containsKey(0));

        map.remove(0);
        assertFalse(map.containsKey(0));
    }

    @Test
    void testNegativeKeys() {
        map.put(-1, 100);
        map.put(-2, 200);
        map.put(-999, 300);

        assertEquals(100, map.get(-1));
        assertEquals(200, map.get(-2));
        assertEquals(300, map.get(-999));
    }

    @Test
    void testSameKeyValuePairs() {
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);

        assertEquals(1, map.get(1));
        assertEquals(2, map.get(2));
        assertEquals(3, map.get(3));
    }

    @Test
    void testLargeMap() {
        int size = 10_000;
        for (int i = 0; i < size; i++) {
            map.put(i, i * 10);
        }
        assertEquals(size, map.size());

        for (int i = 0; i < size; i += 100) {
            assertEquals(i * 10, map.get(i));
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
            map.put(i, i);
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
