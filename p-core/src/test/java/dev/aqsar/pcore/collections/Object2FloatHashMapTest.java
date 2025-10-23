package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Object2FloatHashMapTest {

    private Object2FloatHashMap<String> map;

    @BeforeEach
    void setUp() {
        map = Object2FloatHashMap.<String>builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
        assertEquals(3, map.get("three"));
    }

    @Test
    void testPutOverwrite() {
        map.put("one", 1);
        map.put("one", 1.5f);

        assertEquals(1.5f, map.getFloat("one"));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(map.nullValue, map.getFloat(""));
    }

    @Test
    void testGetOrDefault() {
        map.put("one", 1);

        assertEquals(1, map.getOrDefault("one", 2));
        assertEquals(2, map.getOrDefault("two", 2));
    }

    @Test
    void testContains() {
        map.put("one", 1);
        map.put("two", 2);

        assertTrue(map.containsKey("one"));
        assertTrue(map.containsKey("two"));
        assertFalse(map.containsKey("three"));

        assertTrue(map.containsValue(1));
        assertTrue(map.containsValue(2));
        assertFalse(map.containsValue(3));
    }

    @Test
    void testRemove() {
        map.put("one", 1);
        map.put("two", 2);

        assertEquals(1, map.remove("one"));
        assertFalse(map.containsKey("one"));
        assertEquals(1, map.size());

        assertNull(map.remove(""));
    }

    @Test
    void testSizeAndClear() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.put("one", 1);
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());

        map.put("two", 2);
        map.put("three", 3);
        assertEquals(3, map.size());

        map.remove("two");
        assertEquals(2, map.size());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.containsKey("one"));
    }

    @Test
    void testPutAll() {
        var source = new java.util.HashMap<String, Float>();
        source.put("one", 1f);
        source.put("two", 2f);
        source.put("three", 3f);

        map.putAll(source);

        assertEquals(3, map.size());
        assertEquals(1, map.getFloat("one"));
        assertEquals(2, map.getFloat("two"));
        assertEquals(3, map.getFloat("three"));
    }

    @Test
    void testNullKeyRejected() {
        assertThrows(IllegalArgumentException.class, () -> map.put(null, 1));
        assertThrows(IllegalArgumentException.class, () -> map.containsKey(null));
        assertThrows(IllegalArgumentException.class, () -> map.getFloat(null));
    }

    @Test
    void testManyCollisions() {
        for (int i = 0; i < 1000; i++) {
            map.put("value-" + i, i);
        }

        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals(i, map.getFloat("value-" + i));
        }
    }

    @Test
    void testRemoveAndReinsert() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        map.remove("two");

        assertTrue(map.containsKey("one"));
        assertFalse(map.containsKey("two"));
        assertTrue(map.containsKey("three"));

        map.put("TWO", 2);
        assertEquals(2, map.getFloat("TWO"));
    }

    @Test
    void testResizeGrowth() {
        map = Object2FloatHashMap.<String>builder().initialCapacity(4).build();

        for (int i = 0; i < 100; i++) {
            map.put("value-" + i, i);
        }

        assertEquals(100, map.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i, map.getFloat("value-" + i));
        }
    }

    @Test
    void testResizePreservesEntries() {
        map.put("one", 1);
        map.put("two", 2);

        for (int i = 3; i < 50; i++) {
            map.put("value-" + i, i);
        }

        assertEquals(1, map.getFloat("one"));
        assertEquals(2, map.getFloat("two"));
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
        var iters = new Object2FloatHashMap.ObjectFloatHashMapIterator[8];
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
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        try (var iter = map.borrowIterator()) {
            Set<String> keys = new HashSet<>();
            Set<Float> values = new HashSet<>();
            while (iter.hasNext()) {
                keys.add(iter.peekNextKey());
                values.add(iter.nextValue());
            }
            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.containsAll(Set.of("one", "two", "three")));
            assertTrue(values.containsAll(Set.of(1f, 2f, 3f)));
        }
    }

    @Test
    void testIteratorPeekAndNextKey() {
        map.put("one", 1);
        map.put("two", 2);

        try (var iter = map.borrowIterator()) {
            assertTrue(iter.hasNext());
            String firstKey = iter.peekNextKey();
            float firstValue = iter.peekNextValue();

            // Peek doesn't advance
            assertEquals(firstKey, iter.peekNextKey());
            assertEquals(firstValue, iter.peekNextValue());

            // Now advance using nextKey()
            assertEquals(firstKey, iter.nextKey());

            // Now the iterator should be at the second element
            assertTrue(iter.hasNext());
            assertEquals("two", iter.peekNextKey());
            assertEquals(2, iter.peekNextValue());
        }
    }

    @Test
    void testIteratorPeekAndNextValue() {
        map.put("one", 1);
        map.put("two", 2);

        try (var iter = map.borrowIterator()) {
            assertTrue(iter.hasNext());
            String firstKey = iter.peekNextKey();
            float firstValue = iter.peekNextValue();

            // Peek doesn't advance
            assertEquals(firstKey, iter.peekNextKey());
            assertEquals(firstValue, iter.peekNextValue());

            // Now advance using nextValue()
            assertEquals(firstValue, iter.nextValue());

            // Now the iterator should be at the second element
            assertTrue(iter.hasNext());
            assertEquals("two", iter.peekNextKey());
            assertEquals(2, iter.peekNextValue());
        }
    }

    @Test
    void testIteratorRemove() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        try (var iter = map.borrowIterator()) {
            while (iter.hasNext()) {
                String key = iter.nextKey();
                if (key.equals("two")) {
                    iter.remove();
                }
            }
        }

        assertEquals(2, map.size());
        assertFalse(map.containsKey("two"));
        assertTrue(map.containsKey("one"));
        assertTrue(map.containsKey("three"));
    }

    @Test
    void testIteratorForEachRemaining() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        try (var iter = map.borrowIterator()) {
            Set<String> keys = new HashSet<>();
            Set<Float> values = new HashSet<>();

            iter.forEachRemaining((k, v) -> {
                keys.add(k);
                values.add(v);
            });

            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.containsAll(Set.of("one", "two", "three")));
            assertTrue(values.containsAll(Set.of(1f, 2f, 3f)));
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
        map.put("one", 1);
        map.put("two", 2);

        try (var iter = map.borrowIterator()) {
            iter.nextKey();
            map.put("three", 3);
            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    @Test
    void testForEach() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        Set<String> keys = new HashSet<>();
        Set<Float> values = new HashSet<>();

        map.forEachObjectFloat((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.containsAll(Set.of("one", "two", "three")));
        assertTrue(values.containsAll(Set.of(1f, 2f, 3f)));
    }

    @Test
    void testForEachEmpty() {
        map.forEachObjectFloat((k, v) -> fail("Should not be called on empty map"));
    }

    @Test
    void testBoxedPutAndGet() {
        map.put("one", Float.valueOf(1.1f));
        map.put("two", Float.valueOf(2.2f));

        assertEquals(Float.valueOf(1.1f), map.get("one"));
        assertEquals(Float.valueOf(2.2f), map.get("two"));
        assertNull(map.get(""));
    }

    @Test
    void testBoxedContains() {
        map.put("one", Float.valueOf(1.1f));

        assertTrue(map.containsKey("one"));
        assertTrue(map.containsValue(Float.valueOf(1.1f)));
        assertFalse(map.containsKey("two"));
        assertFalse(map.containsValue(Float.valueOf(2.2f)));
    }

    @Test
    void testBoxedRemove() {
        map.put("one", Float.valueOf(1.1f));
        map.put("two", Float.valueOf(2.2f));

        assertEquals(2, map.size());
        assertEquals(Float.valueOf(1.1f), map.remove("one"));
        assertNull(map.remove(""));
        assertEquals(1, map.size());
    }

    @Test
    void testLargeMap() {
        int size = 10_000;
        for (int i = 0; i < size; i++) {
            map.put("value-" + i, i);
        }
        assertEquals(size, map.size());

        for (int i = 0; i < size; i += 100) {
            assertEquals(i, map.getFloat("value-" + i));
        }
    }

    @Test
    void testBuilderDefaults() {
        map = Object2FloatHashMap.<String>builder().build();
        assertNotNull(map);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuilderInitialCapacity() {
        map = Object2FloatHashMap.<String>builder().initialCapacity(128).build();
        for (int i = 0; i < 50; i++) {
            map.put("value-" + i, i);
        }
        assertEquals(50, map.size());
    }

    @Test
    void testBuilderLoadFactor() {
        map = Object2FloatHashMap.<String>builder().initialCapacity(16).loadFactor(0.5f).build();
        assertNotNull(map);
    }

    @Test
    void testBuilderDisableIteratorPool() {
        map = Object2FloatHashMap.<String>builder().disableIteratorPool().build();
        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());
    }

    @Test
    void testKeySetView() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        var keySet = map.keySet();
        assertEquals(3, keySet.size());
        assertTrue(keySet.contains("one"));
        assertTrue(keySet.contains("two"));
        assertTrue(keySet.contains("three"));
        assertFalse(keySet.contains("four"));
    }

    @Test
    void testValuesView() {
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        var values = map.values();
        assertEquals(3, values.size());
        assertTrue(values.contains(1f));
        assertTrue(values.contains(2f));
        assertTrue(values.contains(3f));
        assertFalse(values.contains(4f));
    }

    @Test
    void testEntrySetView() {
        map.put("one", 1);
        map.put("two", 2);

        var entrySet = map.entrySet();
        assertEquals(2, entrySet.size());

        boolean foundOne = false;
        boolean foundTwo = false;
        for (var entry : entrySet) {
            if (entry.getKey().equals("one") && entry.getValue() == 1) {
                foundOne = true;
            }
            if (entry.getKey().equals("two") && entry.getValue() == 2) {
                foundTwo = true;
            }
        }
        assertTrue(foundOne);
        assertTrue(foundTwo);
    }
}