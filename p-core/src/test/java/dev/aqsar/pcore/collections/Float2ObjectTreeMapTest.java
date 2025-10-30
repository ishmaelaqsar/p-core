package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.ConcurrentModificationException;

import static org.junit.jupiter.api.Assertions.*;

class Float2ObjectTreeMapTest {

    private Float2ObjectTreeMap<String> map;

    @BeforeEach
    void setUp() {
        map = Float2ObjectTreeMap.<String>builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1.0f, "one");
        map.put(2.0f, "two");
        assertEquals("one", map.get(1.0f));
        assertEquals("two", map.get(2.0f));
        assertNull(map.get(3.0f)); // missing key
    }

    @Test
    void testPutOverwrite() {
        map.put(1.0f, "alpha");
        map.put(1.0f, "beta");
        assertEquals("beta", map.get(1.0f));
        assertEquals(1, map.size());
    }

    @Test
    void testGetOrDefault() {
        map.put(5.0f, "five");
        assertEquals("five", map.getOrDefault(5.0f, "x"));
        assertEquals("default", map.getOrDefault(99.0f, "default"));
    }

    @Test
    void testRemoveExistingAndMissing() {
        map.put(1.0f, "a");
        map.put(2.0f, "b");
        assertEquals("a", map.remove(1.0f));
        assertNull(map.remove(999.0f)); // missing key
        assertEquals(1, map.size());
    }

    @Test
    void testSizeAndClear() {
        map.put(1.0f, "a");
        map.put(2.0f, "b");
        assertEquals(2, map.size());
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testContainsKeyAndValue() {
        map.put(1.0f, "X");
        map.put(2.0f, "Y");
        assertTrue(map.containsKey(1.0f));
        assertTrue(map.containsValue("Y"));
        assertFalse(map.containsKey(999.0f));
        assertFalse(map.containsValue("Z"));
    }

    @Test
    void testDuplicateValuesDifferentKeys() {
        map.put(1.0f, "same");
        map.put(2.0f, "same");
        assertEquals(2, map.size());
        assertTrue(map.containsValue("same"));
    }

    @Test
    void testBoundaryKeys() {
        map.put(Float.MIN_VALUE, "min");
        map.put(Float.MAX_VALUE, "max");
        map.put(0.0f, "zero");
        map.put(-1.0f, "neg");

        assertEquals("min", map.get(Float.MIN_VALUE));
        assertEquals("max", map.get(Float.MAX_VALUE));
        assertEquals("zero", map.get(0.0f));
        assertEquals("neg", map.get(-1.0f));
    }

    @Test
    void testBuilderInitialCapacityAndDisableIteratorPool() {
        var m = Float2ObjectTreeMap.<String>builder().initialCapacity(16).disableIteratorPool().build();

        assertEquals(0, m.size());
        assertNull(m.borrowIterator()); // pool disabled
    }

    @Test
    void testPutAllFromMap() {
        Map<Float, String> src = new HashMap<>();
        src.put(1.0f, "a");
        src.put(2.0f, "b");
        src.put(3.0f, "c");

        map.putAll(src);
        assertEquals(3, map.size());
        assertEquals("a", map.get(1.0f));
        assertEquals("c", map.get(3.0f));
    }

    @Test
    void testInOrderKeysAreSorted() {
        for (int i = 10; i >= 1; i--) {
            map.put((float) i, "v" + i);
        }

        float prev = -Float.MAX_VALUE;
        for (var e : map.entrySet()) {
            assertTrue(e.getKey() > prev, "Tree not sorted");
            prev = e.getKey();
        }
    }

    @Test
    void testRemoveMiddleNodesMaintainsOrder() {
        for (int i = 1; i <= 20; i++) {
            map.put((float) i, "v" + i);
        }
        for (int i = 2; i <= 18; i += 2) {
            map.remove((float) i);
        }

        float prev = -Float.MAX_VALUE;
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
        var iters = new Float2ObjectTreeMap.FloatObjectTreeMapIterator[8];
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
        map.put(3.0f, "C");
        map.put(1.0f, "A");
        map.put(2.0f, "B");

        Set<Float> keys = new HashSet<>();
        try (var it = map.borrowIterator()) {
            while (it.hasNext()) {
                keys.add(it.nextKey());
            }
        }
        assertEquals(Set.of(1.0f, 2.0f, 3.0f), keys);
    }

    @Test
    void testIteratorConcurrentModificationThrows() {
        map.put(1.0f, "A");
        map.put(2.0f, "B");

        try (var it = map.borrowIterator()) {
            it.nextKey();
            map.put(3.0f, "C");
            assertThrows(ConcurrentModificationException.class, it::nextKey);
        }
    }

    @Test
    void testForEachIteratesAllEntries() {
        map.put(1.0f, "A");
        map.put(2.0f, "B");
        map.put(3.0f, "C");

        Set<Float> keys = new HashSet<>();
        Set<String> values = new HashSet<>();

        map.forEachFloatObject((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(Set.of(1.0f, 2.0f, 3.0f), keys);
        assertEquals(Set.of("A", "B", "C"), values);
    }

    @Test
    void testForEachEmptyMap() {
        map.forEachFloatObject((k, v) -> fail("Should not be called"));
    }

    @Test
    void testReinsertAfterRemove() {
        map.put(1.0f, "A");
        map.remove(1.0f);
        map.put(1.0f, "B");
        assertEquals("B", map.get(1.0f));
        assertEquals(1, map.size());
    }

    @Test
    void testLargeScaleInsertionsAndLookups() {
        int n = 5000;
        for (int i = 0; i < n; i++) {
            map.put((float) i, "v" + i);
        }
        assertEquals(n, map.size());
        for (int i = 0; i < n; i += 100) {
            assertEquals("v" + i, map.get((float) i));
        }
    }
}
