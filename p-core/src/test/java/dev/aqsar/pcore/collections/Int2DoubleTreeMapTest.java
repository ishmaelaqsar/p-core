package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Int2DoubleTreeMapTest {

    private Int2DoubleTreeMap map;

    @BeforeEach
    void setUp() {
        map = Int2DoubleTreeMap.builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1, 1.1);
        map.put(2, 2.2);
        map.put(3, 3.3);

        assertEquals(1.1, map.get(1), 0.000001);
        assertEquals(2.2, map.get(2), 0.000001);
        assertEquals(3.3, map.get(3), 0.000001);
    }

    @Test
    void testPutOverwrite() {
        map.put(1, 10.5);
        map.put(1, 99.9);

        assertEquals(99.9, map.get(1), 0.000001);
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(Int2DoubleTreeMap.DEFAULT_NULL_VALUE, map.get(999));
    }

    @Test
    void testGetOrDefault() {
        map.put(1, 123.456);

        assertEquals(123.456, map.getOrDefault(1, -1.0), 0.000001);
        assertEquals(-1.0, map.getOrDefault(2, -1.0), 0.000001);
    }

    @Test
    void testContains() {
        map.put(1, 1.0);
        map.put(2, 2.0);

        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertFalse(map.containsKey(3));

        assertTrue(map.containsValue(2.0));
        assertFalse(map.containsValue(999.0));
    }

    @Test
    void testRemove() {
        map.put(1, 10.0);
        map.put(2, 20.0);

        double removed = map.remove(1);
        assertEquals(10.0, removed, 0.000001);
        assertFalse(map.containsKey(1));
        assertEquals(1, map.size());

        // remove non-existent key returns nullValue sentinel
        assertEquals(Int2DoubleTreeMap.DEFAULT_NULL_VALUE, map.remove(999));
    }

    @Test
    void testRemoveExistingAndMissing() {
        map.put(1, 1.5);
        map.put(2, 2.5);
        assertEquals(1.5, map.remove(1), 0.000001);
        assertEquals(Int2DoubleTreeMap.DEFAULT_NULL_VALUE, map.remove(999)); // missing key
        assertEquals(1, map.size());
    }

    @Test
    void testSizeAndClear() {
        assertTrue(map.isEmpty());

        map.put(1, 1.0);
        map.put(2, 2.0);
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testContainsKeyAndValue() {
        map.put(1, 10.0);
        map.put(2, 20.0);
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue(20.0));
        assertFalse(map.containsKey(999));
        assertFalse(map.containsValue(999.0));
    }

    @Test
    void testDuplicateValuesDifferentKeys() {
        map.put(1, 5.5);
        map.put(2, 5.5);
        assertEquals(2, map.size());
        assertTrue(map.containsValue(5.5));
    }

    @Test
    void testBoundaryKeys() {
        map.put(Integer.MIN_VALUE, -999.999);
        map.put(Integer.MAX_VALUE, 999.999);
        map.put(0, 0.0);
        map.put(-1, -1.1);

        assertEquals(-999.999, map.get(Integer.MIN_VALUE), 0.000001);
        assertEquals(999.999, map.get(Integer.MAX_VALUE), 0.000001);
        assertEquals(0.0, map.get(0), 0.000001);
        assertEquals(-1.1, map.get(-1), 0.000001);
    }

    @Test
    void testBuilderInitialCapacityAndDisableIteratorPool() {
        var m = Int2DoubleTreeMap.builder().initialCapacity(16).disableIteratorPool().build();

        assertEquals(0, m.size());
        assertNull(m.borrowIterator()); // pool disabled
    }

    @Test
    void testPutAll() {
        var src = new HashMap<Integer, Double>();
        src.put(1, 1.1);
        src.put(2, 2.2);
        src.put(3, 3.3);

        map.putAll(src);

        assertEquals(3, map.size());
        assertEquals(1.1, map.get(1), 0.000001);
        assertEquals(2.2, map.get(2), 0.000001);
        assertEquals(3.3, map.get(3), 0.000001);
    }

    @Test
    void testPutAllFromMap() {
        Map<Integer, Double> src = new HashMap<>();
        src.put(1, 10.0);
        src.put(2, 20.0);
        src.put(3, 30.0);

        map.putAll(src);
        assertEquals(3, map.size());
        assertEquals(10.0, map.get(1), 0.000001);
        assertEquals(30.0, map.get(3), 0.000001);
    }

    @Test
    void testInOrderKeysAreSorted() {
        for (int i = 10; i >= 1; i--) {
            map.put(i, i * 1.0);
        }

        int prev = Integer.MIN_VALUE;
        for (var e : map.entrySet()) {
            assertTrue(e.getKey() > prev, "Tree not sorted");
            prev = e.getKey();
        }
    }

    @Test
    void testSortedOrder() {
        map.put(5, 5.0);
        map.put(1, 1.0);
        map.put(3, 3.0);

        var iter = map.entrySet().iterator();
        int prevKey = Integer.MIN_VALUE;
        while (iter.hasNext()) {
            var entry = iter.next();
            assertTrue(entry.getKey() > prevKey);
            prevKey = entry.getKey();
        }
    }

    @Test
    void testRemoveMiddleNodesMaintainsOrder() {
        for (int i = 1; i <= 20; i++) {
            map.put(i, i * 1.0);
        }
        for (int i = 2; i <= 18; i += 2) {
            map.remove(i);
        }

        int prev = Integer.MIN_VALUE;
        for (var e : map.entrySet()) {
            assertTrue(e.getKey() > prev);
            prev = e.getKey();
        }
    }

    @Test
    void testIteratorPoolingLifecycle() {
        assertEquals(8, map.availableIteratorCount());

        var it = map.borrowIterator();
        assertEquals(7, map.availableIteratorCount());
        map.returnIterator(it);
        assertEquals(8, map.availableIteratorCount());
    }

    @Test
    void testIteratorExhaustionAndReuse() {
        var iters = new Int2DoubleTreeMap.Int2DoubleTreeMapIterator[8];
        for (int i = 0; i < 8; i++) {
            iters[i] = map.borrowIterator();
        }
        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());

        map.returnIterator(iters[0]);
        assertNotNull(map.borrowIterator());
    }

    @Test
    void testIteratorAutoCloseRestoresPool() {
        assertEquals(8, map.availableIteratorCount());
        try (var it = map.borrowIterator()) {
            assertEquals(7, map.availableIteratorCount());
        }
        assertEquals(8, map.availableIteratorCount());
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
        map.put(1, 10.0);
        map.put(2, 20.0);
        map.put(3, 30.0);

        Set<Integer> keys = new HashSet<>();
        try (var iter = map.borrowIterator()) {
            while (iter.hasNext()) {
                keys.add(iter.nextKey());
            }
        }

        assertEquals(Set.of(1, 2, 3), keys);
    }

    @Test
    void testIteratorTraversalOrder() {
        map.put(3, 30.0);
        map.put(1, 10.0);
        map.put(2, 20.0);

        Set<Integer> keys = new HashSet<>();
        try (var it = map.borrowIterator()) {
            while (it.hasNext()) {
                keys.add(it.nextKey());
            }
        }
        assertEquals(Set.of(1, 2, 3), keys);
    }

    @Test
    void testIteratorConcurrentModification() {
        map.put(1, 10.0);
        map.put(2, 20.0);

        try (var iter = map.borrowIterator()) {
            iter.nextKey();
            map.put(3, 30.0);
            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    @Test
    void testIteratorConcurrentModificationThrows() {
        map.put(1, 10.0);
        map.put(2, 20.0);

        try (var it = map.borrowIterator()) {
            it.nextKey();
            map.put(3, 30.0);
            assertThrows(ConcurrentModificationException.class, it::nextKey);
        }
    }

    @Test
    void testForEachIteratesAllEntries() {
        map.put(1, 10.0);
        map.put(2, 20.0);
        map.put(3, 30.0);

        Set<Integer> keys = new HashSet<>();
        Set<Double> values = new HashSet<>();

        map.forEachIntDouble((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(Set.of(1, 2, 3), keys);
        assertEquals(Set.of(10.0, 20.0, 30.0), values);
    }

    @Test
    void testForEachEmptyMap() {
        map.forEachIntDouble((k, v) -> fail("Should not be called"));
    }

    @Test
    void testReinsertAfterRemove() {
        map.put(1, 10.0);
        map.remove(1);
        map.put(1, 20.0);
        assertEquals(20.0, map.get(1), 0.000001);
        assertEquals(1, map.size());
    }

    @Test
    void testLargeScaleInsertionsAndLookups() {
        int n = 5000;
        for (int i = 0; i < n; i++) {
            map.put(i, i * 1.0);
        }
        assertEquals(n, map.size());
        for (int i = 0; i < n; i += 100) {
            assertEquals(i * 1.0, map.get(i), 0.000001);
        }
    }

    @Test
    void testBuilderDefaults() {
        var built = Int2DoubleTreeMap.builder().build();
        assertNotNull(built);
        assertEquals(0, built.size());
        assertTrue(built.isEmpty());
    }

    @Test
    void testBuilderInitialCapacity() {
        var built = Int2DoubleTreeMap.builder().initialCapacity(64).build();
        for (int i = 0; i < 50; i++) {
            built.put(i, i * 1.0);
        }
        assertEquals(50, built.size());
    }

    @Test
    void testBuilderDisableIteratorPool() {
        var built = Int2DoubleTreeMap.builder().disableIteratorPool().build();
        assertNull(built.borrowIterator());
    }

    @Test
    void testNegativeKeysAndZero() {
        map.put(-1, -1.1);
        map.put(0, 0.0);
        map.put(10, 10.1);

        assertEquals(-1.1, map.get(-1), 0.000001);
        assertEquals(0.0, map.get(0), 0.000001);
        assertEquals(10.1, map.get(10), 0.000001);
    }
}
