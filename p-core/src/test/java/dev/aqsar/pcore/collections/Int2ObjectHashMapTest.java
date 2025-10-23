package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Int2ObjectHashMapTest {

    private Int2ObjectHashMap<String> map;

    @BeforeEach
    void setUp() {
        map = Int2ObjectHashMap.<String>builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        assertEquals("one", map.get(1));
        assertEquals("two", map.get(2));
        assertEquals("three", map.get(3));
    }

    @Test
    void testPutOverwrite() {
        map.put(1, "one");
        map.put(1, "ONE");

        assertEquals("ONE", map.get(1));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertNull(map.get(999));
    }

    @Test
    void testGetOrDefault() {
        map.put(1, "one");

        assertEquals("one", map.getOrDefault(1, "default"));
        assertEquals("default", map.getOrDefault(2, "default"));
    }

    @Test
    void testContains() {
        map.put(1, "one");
        map.put(2, "two");

        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertFalse(map.containsKey(3));

        assertTrue(map.containsValue("one"));
        assertFalse(map.containsValue("three"));
    }

    @Test
    void testRemove() {
        map.put(1, "one");
        map.put(2, "two");

        assertEquals("one", map.remove(1));
        assertFalse(map.containsKey(1));
        assertEquals(1, map.size());

        assertNull(map.remove(999));
    }

    @Test
    void testSizeAndClear() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.put(1, "one");
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());

        map.put(2, "two");
        map.put(3, "three");
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
        var source = new java.util.HashMap<Integer, String>();
        source.put(1, "one");
        source.put(2, "two");
        source.put(3, "three");

        map.putAll(source);

        assertEquals(3, map.size());
        assertEquals("one", map.get(1));
        assertEquals("two", map.get(2));
        assertEquals("three", map.get(3));
    }

    @Test
    void testNullKeyRejected() {
        assertThrows(NullPointerException.class, () -> map.put(null, "value"));
        assertFalse(map.containsKey(null));
        assertNull(map.get(null));
    }

    @Test
    void testNullValueRejected() {
        assertThrows(IllegalArgumentException.class, () -> map.put(1, null));
    }

    @Test
    void testMinValueKey() {
        assertThrows(IllegalArgumentException.class, () -> map.put(Integer.MIN_VALUE, "value"));
        assertThrows(IllegalArgumentException.class, () -> map.containsKey(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> map.get(Integer.MIN_VALUE));
    }

    @Test
    void testManyCollisions() {
        for (int i = 0; i < 1000; i++) {
            map.put(i, "value-" + i);
        }

        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals("value-" + i, map.get(i));
        }
    }

    @Test
    void testRemoveAndReinsert() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        map.remove(2);

        assertTrue(map.containsKey(1));
        assertFalse(map.containsKey(2));
        assertTrue(map.containsKey(3));

        map.put(2, "TWO");
        assertEquals("TWO", map.get(2));
    }

    @Test
    void testResizeGrowth() {
        map = Int2ObjectHashMap.<String>builder().initialCapacity(4).build();

        for (int i = 0; i < 100; i++) {
            map.put(i, "value-" + i);
        }

        assertEquals(100, map.size());
        for (int i = 0; i < 100; i++) {
            assertEquals("value-" + i, map.get(i));
        }
    }

    @Test
    void testResizePreservesEntries() {
        map.put(1, "one");
        map.put(2, "two");

        for (int i = 3; i < 50; i++) {
            map.put(i, "value-" + i);
        }

        assertEquals("one", map.get(1));
        assertEquals("two", map.get(2));
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
        var iters = new Int2ObjectHashMap.IntObjectHashMapIterator[8];
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
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        try (var iter = map.borrowIterator()) {
            Set<Integer> keys = new HashSet<>();
            Set<String> values = new HashSet<>();
            while (iter.hasNext()) {
                keys.add(iter.peekNextKey());
                values.add(iter.nextValue());
            }
            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.containsAll(Set.of(1, 2, 3)));
            assertTrue(values.containsAll(Set.of("one", "two", "three")));
        }
    }

    @Test
    void testIteratorPeekAndNextKey() {
        map.put(1, "one");
        map.put(2, "two");

        try (var iter = map.borrowIterator()) {
            assertTrue(iter.hasNext());
            int firstKey = iter.peekNextKey();
            String firstValue = iter.peekNextValue();

            // Peek doesn't advance
            assertEquals(firstKey, iter.peekNextKey());
            assertEquals(firstValue, iter.peekNextValue());

            // Now advance using nextKey()
            assertEquals(firstKey, iter.nextKey());

            // Now the iterator should be at the second element
            assertTrue(iter.hasNext());
            assertEquals(2, iter.peekNextKey());
            assertEquals("two", iter.peekNextValue());
        }
    }

    @Test
    void testIteratorPeekAndNextValue() {
        map.put(1, "one");
        map.put(2, "two");

        try (var iter = map.borrowIterator()) {
            assertTrue(iter.hasNext());
            int firstKey = iter.peekNextKey();
            String firstValue = iter.peekNextValue();

            // Peek doesn't advance
            assertEquals(firstKey, iter.peekNextKey());
            assertEquals(firstValue, iter.peekNextValue());

            // Now advance using nextValue()
            assertEquals(firstValue, iter.nextValue());

            // Now the iterator should be at the second element
            assertTrue(iter.hasNext());
            assertEquals(2, iter.peekNextKey());
            assertEquals("two", iter.peekNextValue());
        }
    }

    @Test
    void testIteratorRemove() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        try (var iter = map.borrowIterator()) {
            while (iter.hasNext()) {
                int key = iter.nextKey();
                if (key == 2) {
                    iter.remove();
                }
            }
        }

        assertEquals(2, map.size());
        assertFalse(map.containsKey(2));
        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(3));
    }

    @Test
    void testIteratorForEachRemaining() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        try (var iter = map.borrowIterator()) {
            Set<Integer> keys = new HashSet<>();
            Set<String> values = new HashSet<>();

            iter.forEachRemaining((k, v) -> {
                keys.add(k);
                values.add(v);
            });

            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.containsAll(Set.of(1, 2, 3)));
            assertTrue(values.containsAll(Set.of("one", "two", "three")));
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
        map.put(1, "one");
        map.put(2, "two");

        try (var iter = map.borrowIterator()) {
            iter.nextKey();
            map.put(3, "three");
            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    @Test
    void testForEach() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        Set<Integer> keys = new HashSet<>();
        Set<String> values = new HashSet<>();

        map.forEachIntObject((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.containsAll(Set.of(1, 2, 3)));
        assertTrue(values.containsAll(Set.of("one", "two", "three")));
    }

    @Test
    void testForEachEmpty() {
        map.forEachIntObject((k, v) -> fail("Should not be called on empty map"));
    }

    @Test
    void testBoxedPutAndGet() {
        map.put(Integer.valueOf(1), "one");
        map.put(Integer.valueOf(2), "two");

        assertEquals("one", map.get(Integer.valueOf(1)));
        assertEquals("two", map.get(Integer.valueOf(2)));
        assertNull(map.get(Integer.valueOf(999)));
    }

    @Test
    void testBoxedContains() {
        map.put(1, "one");

        assertTrue(map.containsKey(Integer.valueOf(1)));
        assertTrue(map.containsValue("one"));
        assertFalse(map.containsKey(Integer.valueOf(2)));
        assertFalse(map.containsValue("two"));
    }

    @Test
    void testBoxedRemove() {
        map.put(1, "one");
        map.put(2, "two");

        assertEquals(2, map.size());
        assertEquals("one", map.remove(Integer.valueOf(1)));
        assertNull(map.remove(Integer.valueOf(999)));
        assertEquals(1, map.size());
    }

    @Test
    void testZeroKey() {
        map.put(0, "zero");
        assertEquals("zero", map.get(0));
        assertTrue(map.containsKey(0));

        map.remove(0);
        assertFalse(map.containsKey(0));
    }

    @Test
    void testNegativeKeys() {
        map.put(-1, "minus-one");
        map.put(-2, "minus-two");
        map.put(-999, "minus-999");

        assertEquals("minus-one", map.get(-1));
        assertEquals("minus-two", map.get(-2));
        assertEquals("minus-999", map.get(-999));
    }

    @Test
    void testLargeMap() {
        int size = 10_000;
        for (int i = 0; i < size; i++) {
            map.put(i, "value-" + i);
        }
        assertEquals(size, map.size());

        for (int i = 0; i < size; i += 100) {
            assertEquals("value-" + i, map.get(i));
        }
    }

    @Test
    void testBuilderDefaults() {
        map = Int2ObjectHashMap.<String>builder().build();
        assertNotNull(map);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuilderInitialCapacity() {
        map = Int2ObjectHashMap.<String>builder().initialCapacity(128).build();
        for (int i = 0; i < 50; i++) {
            map.put(i, "value-" + i);
        }
        assertEquals(50, map.size());
    }

    @Test
    void testBuilderLoadFactor() {
        map = Int2ObjectHashMap.<String>builder().initialCapacity(16).loadFactor(0.5f).build();
        assertNotNull(map);
    }

    @Test
    void testBuilderDisableIteratorPool() {
        map = Int2ObjectHashMap.<String>builder().disableIteratorPool().build();
        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());
    }

    @Test
    void testKeySetView() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        var keySet = map.keySet();
        assertEquals(3, keySet.size());
        assertTrue(keySet.contains(1));
        assertTrue(keySet.contains(2));
        assertTrue(keySet.contains(3));
        assertFalse(keySet.contains(4));
    }

    @Test
    void testValuesView() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        var values = map.values();
        assertEquals(3, values.size());
        assertTrue(values.contains("one"));
        assertTrue(values.contains("two"));
        assertTrue(values.contains("three"));
        assertFalse(values.contains("four"));
    }

    @Test
    void testEntrySetView() {
        map.put(1, "one");
        map.put(2, "two");

        var entrySet = map.entrySet();
        assertEquals(2, entrySet.size());

        boolean foundOne = false;
        boolean foundTwo = false;
        for (var entry : entrySet) {
            if (entry.getKey() == 1 && "one".equals(entry.getValue())) {
                foundOne = true;
            }
            if (entry.getKey() == 2 && "two".equals(entry.getValue())) {
                foundTwo = true;
            }
        }
        assertTrue(foundOne);
        assertTrue(foundTwo);
    }
}