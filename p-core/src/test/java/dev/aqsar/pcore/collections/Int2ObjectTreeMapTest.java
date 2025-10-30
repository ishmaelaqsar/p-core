package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.ConcurrentModificationException;

import static org.junit.jupiter.api.Assertions.*;

class Int2ObjectTreeMapTest {

    private Int2ObjectTreeMap<String> map;

    @BeforeEach
    void setUp() {
        map = Int2ObjectTreeMap.<String>builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1, "one");
        map.put(2, "two");
        assertEquals("one", map.get(1));
        assertEquals("two", map.get(2));
        assertNull(map.get(3)); // missing key
    }

    @Test
    void testPutOverwrite() {
        map.put(1, "alpha");
        map.put(1, "beta");
        assertEquals("beta", map.get(1));
        assertEquals(1, map.size());
    }

    @Test
    void testGetOrDefault() {
        map.put(5, "five");
        assertEquals("five", map.getOrDefault(5, "x"));
        assertEquals("default", map.getOrDefault(99, "default"));
    }

    @Test
    void testRemoveExistingAndMissing() {
        map.put(1, "a");
        map.put(2, "b");
        assertEquals("a", map.remove(1));
        assertNull(map.remove(999)); // missing key
        assertEquals(1, map.size());
    }

    @Test
    void testSizeAndClear() {
        map.put(1, "a");
        map.put(2, "b");
        assertEquals(2, map.size());
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testContainsKeyAndValue() {
        map.put(1, "X");
        map.put(2, "Y");
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue("Y"));
        assertFalse(map.containsKey(999));
        assertFalse(map.containsValue("Z"));
    }

    @Test
    void testDuplicateValuesDifferentKeys() {
        map.put(1, "same");
        map.put(2, "same");
        assertEquals(2, map.size());
        assertTrue(map.containsValue("same"));
    }

    @Test
    void testBoundaryKeys() {
        map.put(Integer.MIN_VALUE, "min");
        map.put(Integer.MAX_VALUE, "max");
        map.put(0, "zero");
        map.put(-1, "neg");

        assertEquals("min", map.get(Integer.MIN_VALUE));
        assertEquals("max", map.get(Integer.MAX_VALUE));
        assertEquals("zero", map.get(0));
        assertEquals("neg", map.get(-1));
    }

    @Test
    void testBuilderInitialCapacityAndDisableIteratorPool() {
        var m = Int2ObjectTreeMap.<String>builder().initialCapacity(16).disableIteratorPool().build();

        assertEquals(0, m.size());
        assertNull(m.borrowIterator()); // pool disabled
    }

    @Test
    void testPutAllFromMap() {
        Map<Integer, String> src = new HashMap<>();
        src.put(1, "a");
        src.put(2, "b");
        src.put(3, "c");

        map.putAll(src);
        assertEquals(3, map.size());
        assertEquals("a", map.get(1));
        assertEquals("c", map.get(3));
    }

    @Test
    void testInOrderKeysAreSorted() {
        for (int i = 10; i >= 1; i--) {
            map.put(i, "v" + i);
        }

        int prev = Integer.MIN_VALUE;
        for (var e : map.entrySet()) {
            assertTrue(e.getKey() > prev, "Tree not sorted");
            prev = e.getKey();
        }
    }

    @Test
    void testRemoveMiddleNodesMaintainsOrder() {
        for (int i = 1; i <= 20; i++) {
            map.put(i, "v" + i);
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
        var iters = new Int2ObjectTreeMap.IntObjectTreeMapIterator[8];
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
    void testIteratorTraversalOrder() {
        map.put(3, "C");
        map.put(1, "A");
        map.put(2, "B");

        Set<Integer> keys = new HashSet<>();
        try (var it = map.borrowIterator()) {
            while (it.hasNext()) {
                keys.add(it.nextKey());
            }
        }
        assertEquals(Set.of(1, 2, 3), keys);
    }

    @Test
    void testIteratorConcurrentModificationThrows() {
        map.put(1, "A");
        map.put(2, "B");

        try (var it = map.borrowIterator()) {
            it.nextKey();
            map.put(3, "C");
            assertThrows(ConcurrentModificationException.class, it::nextKey);
        }
    }

    @Test
    void testForEachIteratesAllEntries() {
        map.put(1, "A");
        map.put(2, "B");
        map.put(3, "C");

        Set<Integer> keys = new HashSet<>();
        Set<String> values = new HashSet<>();

        map.forEachIntObject((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(Set.of(1, 2, 3), keys);
        assertEquals(Set.of("A", "B", "C"), values);
    }

    @Test
    void testForEachEmptyMap() {
        map.forEachIntObject((k, v) -> fail("Should not be called"));
    }

    @Test
    void testReinsertAfterRemove() {
        map.put(1, "A");
        map.remove(1);
        map.put(1, "B");
        assertEquals("B", map.get(1));
        assertEquals(1, map.size());
    }

    @Test
    void testLargeScaleInsertionsAndLookups() {
        int n = 5000;
        for (int i = 0; i < n; i++) {
            map.put(i, "v" + i);
        }
        assertEquals(n, map.size());
        for (int i = 0; i < n; i += 100) {
            assertEquals("v" + i, map.get(i));
        }
    }
}
