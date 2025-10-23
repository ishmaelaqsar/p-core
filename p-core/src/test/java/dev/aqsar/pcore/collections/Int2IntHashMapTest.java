package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Int2IntHashMapTest {

    private Int2IntHashMap map;

    @BeforeEach
    void setUp() {
        map = Int2IntHashMap.builder().build();
    }

    // ==================== Basic Operations ====================

    @Test
    void testPutAndGet() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);
        map.putIntInt(3, 300);

        assertEquals(100, map.getInt(1));
        assertEquals(200, map.getInt(2));
        assertEquals(300, map.getInt(3));
    }

    @Test
    void testPutOverwrite() {
        map.putIntInt(1, 100);
        map.putIntInt(1, 999);

        assertEquals(999, map.getInt(1));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(0, map.getInt(999)); // Returns default value
    }

    @Test
    void testGetOrDefault() {
        map.putIntInt(1, 100);

        assertEquals(100, map.getOrDefaultInt(1, 999));
        assertEquals(999, map.getOrDefaultInt(2, 999));
    }

    @Test
    void testContains() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);

        assertTrue(map.containsInt(1));
        assertTrue(map.containsInt(2));
        assertFalse(map.containsInt(3));

        assertTrue(map.containsIntValue(100));
        assertFalse(map.containsIntValue(999));
    }

    @Test
    void testRemove() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);

        assertEquals(100, map.removeInt(1));
        assertFalse(map.containsInt(1));
        assertEquals(1, map.size());

        assertEquals(0, map.removeInt(999)); // Remove non-existent
    }

    @Test
    void testSize() {
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.putIntInt(1, 100);
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());

        map.putIntInt(2, 200);
        map.putIntInt(3, 300);
        assertEquals(3, map.size());

        map.removeInt(2);
        assertEquals(2, map.size());
    }

    @Test
    void testClear() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);
        map.putIntInt(3, 300);

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.containsInt(1));
    }

    @Test
    void testPutAll() {
        java.util.HashMap<Integer, Integer> source = new java.util.HashMap<>();
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
    void testPutAllEmpty() {
        map.putIntInt(1, 100);

        java.util.HashMap<Integer, Integer> empty = new java.util.HashMap<>();
        map.putAll(empty);

        assertEquals(1, map.size());
        assertEquals(100, map.getInt(1));
    }

    // ==================== Null Key/Value Handling ====================

    @Test
    void testNullKeyWithBoxedAPI() {
        map = Int2IntHashMap.builder().nullKey(-1).build();

        map.put(null, 100);
        assertEquals(100, map.get(null));
        assertTrue(map.containsKey(null));

        map.remove(null);
        assertFalse(map.containsKey(null));
    }

    @Test
    void testNullValueWithBoxedAPI() {
        map = Int2IntHashMap.builder().nullValue(-1).build();

        map.put(1, null);
        assertNull(map.get(1));
        assertTrue(map.containsValue(null));
    }

    @Test
    void testDisableNullKey() {
        map = Int2IntHashMap.builder().disableNullKey().build();

        // Should treat null as primitive 0
        map.put(null, 100);
        assertEquals(100, map.get(0));
    }

    // ==================== Collision Handling ====================

    @Test
    void testManyCollisions() {
        // Test linear probing with many entries
        for (int i = 0; i < 1000; i++) {
            map.putIntInt(i, i * 10);
        }

        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals(i * 10, map.getInt(i));
        }
    }

    @Test
    void testRemoveWithTombstones() {
        // Test that tombstones don't break lookups
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);
        map.putIntInt(3, 300);

        map.removeInt(2);

        assertTrue(map.containsInt(1));
        assertFalse(map.containsInt(2));
        assertTrue(map.containsInt(3));

        // Re-add the removed key
        map.putIntInt(2, 222);
        assertEquals(222, map.getInt(2));
    }

    // ==================== Resize Testing ====================

    @Test
    void testResizeGrowth() {
        // Start with small capacity and force resize
        map = Int2IntHashMap.builder().initialCapacity(4).build();

        for (int i = 0; i < 100; i++) {
            map.putIntInt(i, i * 10);
        }

        assertEquals(100, map.size());

        // Verify all entries survived resize
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 10, map.getInt(i), "Failed at index " + i);
        }
    }

    @Test
    void testResizePreservesEntries() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);

        // Force resize by adding many entries
        for (int i = 3; i < 50; i++) {
            map.putIntInt(i, i * 10);
        }

        // Original entries should still exist
        assertEquals(100, map.getInt(1));
        assertEquals(200, map.getInt(2));
    }

    // ==================== Iterator Testing ====================

    @Test
    void testIteratorPooling() {
        map = Int2IntHashMap.builder().build();
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);

        assertEquals(8, map.availableIteratorCount());

        Int2IntHashMap.IntIntHashMapIterator iter = map.borrowIterator();
        assertNotNull(iter);
        assertEquals(7, map.availableIteratorCount());

        map.returnIterator(iter);
        assertEquals(8, map.availableIteratorCount());
    }

    @Test
    void testIteratorExhaustion() {
        map = Int2IntHashMap.builder().build();

        // Borrow all 8 iterators
        Int2IntHashMap.IntIntHashMapIterator[] iters = new Int2IntHashMap.IntIntHashMapIterator[8];
        for (int i = 0; i < 8; i++) {
            iters[i] = map.borrowIterator();
            assertNotNull(iters[i]);
        }

        assertEquals(0, map.availableIteratorCount());

        // Try to borrow one more - should return null
        assertNull(map.borrowIterator());

        // Return one and try again
        map.returnIterator(iters[0]);
        assertNotNull(map.borrowIterator());
    }

    @Test
    void testIteratorTraversal() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);
        map.putIntInt(3, 300);

        try (Int2IntHashMap.IntIntHashMapIterator iter = map.borrowIterator()) {
            Set<Integer> keys = new HashSet<>();
            Set<Integer> values = new HashSet<>();

            while (iter.hasNext()) {
                keys.add(iter.nextKey());
                // Note: Need to call nextValue separately or track position
            }

            assertTrue(keys.contains(1) || keys.contains(2) || keys.contains(3));
        }
    }

    @Test
    void testIteratorForEachRemaining() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);
        map.putIntInt(3, 300);

        try (Int2IntHashMap.IntIntHashMapIterator iter = map.borrowIterator()) {
            Set<Integer> keys = new HashSet<>();
            Set<Integer> values = new HashSet<>();

            iter.forEachRemaining((k, v) -> {
                keys.add(k);
                values.add(v);
            });

            assertEquals(3, keys.size());
            assertTrue(keys.contains(1));
            assertTrue(keys.contains(2));
            assertTrue(keys.contains(3));

            assertEquals(3, values.size());
            assertTrue(values.contains(100));
            assertTrue(values.contains(200));
            assertTrue(values.contains(300));
        }
    }

    @Test
    void testIteratorAutoClose() {
        assertEquals(8, map.availableIteratorCount());

        try (Int2IntHashMap.IntIntHashMapIterator iter = map.borrowIterator()) {
            assertEquals(7, map.availableIteratorCount());
        }

        // Should be returned after close
        assertEquals(8, map.availableIteratorCount());
    }

    @Test
    void testIteratorConcurrentModification() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);

        try (Int2IntHashMap.IntIntHashMapIterator iter = map.borrowIterator()) {
            iter.nextKey();

            // Modify map during iteration
            map.putIntInt(3, 300);

            assertThrows(ConcurrentModificationException.class, iter::nextKey);
        }
    }

    // ==================== ForEach Testing ====================

    @Test
    void testForEach() {
        map.putIntInt(1, 100);
        map.putIntInt(2, 200);
        map.putIntInt(3, 300);

        Set<Integer> keys = new HashSet<>();
        Set<Integer> values = new HashSet<>();

        map.forEachIntInt((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.contains(1) && keys.contains(2) && keys.contains(3));
        assertTrue(values.contains(100) && values.contains(200) && values.contains(300));
    }

    @Test
    void testForEachEmpty() {
        map.forEachIntInt((k, v) -> fail("Should not be called on empty map"));
    }

    // ==================== Boxed API Testing ====================

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

        // Test wrong types
        assertFalse(map.containsKey("not an int"));
        assertFalse(map.containsValue("not an int"));
    }

    @Test
    void testBoxedRemove() {
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(100, map.remove(1));
        assertNull(map.remove(999));
        assertNull(map.remove("not an int"));
    }

    @Test
    void testKeySet() {
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        Set<Integer> keySet = map.keySet();
        assertEquals(3, keySet.size());
        assertTrue(keySet.contains(1));
        assertTrue(keySet.contains(2));
        assertTrue(keySet.contains(3));
        assertFalse(keySet.contains(4));

        // Test iteration
        Set<Integer> iterated = new HashSet<>();
        for (Integer key : keySet) {
            iterated.add(key);
        }
        assertEquals(3, iterated.size());
    }

    @Test
    void testValues() {
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        var values = map.values();
        assertEquals(3, values.size());
        assertTrue(values.contains(100));
        assertTrue(values.contains(200));
        assertTrue(values.contains(300));

        // Test iteration
        Set<Integer> iterated = new HashSet<>();
        for (Integer value : values) {
            iterated.add(value);
        }
        assertEquals(3, iterated.size());
    }

    @Test
    void testEntrySet() {
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        Set<java.util.Map.Entry<Integer, Integer>> entrySet = map.entrySet();
        assertEquals(3, entrySet.size());

        Set<Integer> keys = new HashSet<>();
        Set<Integer> values = new HashSet<>();

        for (var entry : entrySet) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.contains(1) && keys.contains(2) && keys.contains(3));
        assertTrue(values.contains(100) && values.contains(200) && values.contains(300));
    }

    @Test
    void testIteratorNoSuchElement() {
        map.put(1, 100);

        Iterator<Integer> iter = map.keySet().iterator();
        assertTrue(iter.hasNext());
        iter.next();
        assertFalse(iter.hasNext());

        assertThrows(NoSuchElementException.class, iter::next);
    }

    // ==================== Edge Cases ====================

    @Test
    void testZeroKey() {
        // 0 is the EMPTY_KEY sentinel, ensure it works correctly
        map.putIntInt(0, 999);
        assertEquals(999, map.getInt(0));
        assertTrue(map.containsInt(0));

        map.removeInt(0);
        assertFalse(map.containsInt(0));
    }

    @Test
    void testMinValueKey() {
        // Integer.MIN_VALUE is TOMBSTONE_KEY sentinel
        map.putIntInt(Integer.MIN_VALUE, 999);
        assertEquals(999, map.getInt(Integer.MIN_VALUE));
        assertTrue(map.containsInt(Integer.MIN_VALUE));
    }

    @Test
    void testNegativeKeys() {
        map.putIntInt(-1, 100);
        map.putIntInt(-2, 200);
        map.putIntInt(-999, 300);

        assertEquals(100, map.getInt(-1));
        assertEquals(200, map.getInt(-2));
        assertEquals(300, map.getInt(-999));
    }

    @Test
    void testSameKeyValuePairs() {
        map.putIntInt(1, 1);
        map.putIntInt(2, 2);
        map.putIntInt(3, 3);

        assertEquals(1, map.getInt(1));
        assertEquals(2, map.getInt(2));
        assertEquals(3, map.getInt(3));
    }

    @Test
    void testLargeMap() {
        int size = 10_000;

        for (int i = 0; i < size; i++) {
            map.putIntInt(i, i * 10);
        }

        assertEquals(size, map.size());

        // Random access test
        for (int i = 0; i < size; i += 100) {
            assertEquals(i * 10, map.getInt(i));
        }
    }

    // ==================== Builder Testing ====================

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

        // Should not need to resize for small number of entries
        for (int i = 0; i < 50; i++) {
            map.putIntInt(i, i);
        }

        assertEquals(50, map.size());
    }

    @Test
    void testBuilderLoadFactor() {
        map = Int2IntHashMap.builder().initialCapacity(16).loadFactor(0.5f) // Resize at 50% capacity
                            .build();

        assertNotNull(map);
    }

    @Test
    void testBuilderCustomNullKey() {
        map = Int2IntHashMap.builder().nullKey(-999).build();

        map.put(null, 100);
        assertEquals(100, map.getInt(-999));
    }

    @Test
    void testBuilderDisableIteratorPool() {
        map = Int2IntHashMap.builder().disableIteratorPool().build();

        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());
    }
}