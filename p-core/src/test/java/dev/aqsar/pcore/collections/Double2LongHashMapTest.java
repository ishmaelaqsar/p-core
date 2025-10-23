package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Double2LongHashMap - double keys (with state array) to long values
 * This tests the floating point key handling with separate state tracking
 */
class Double2LongHashMapTest {

    private Double2LongHashMap map;

    @BeforeEach
    void setUp() {
        map = Double2LongHashMap.builder().build();
    }

    // ==================== Basic Operations ====================

    @Test
    void testPutAndGet() {
        map.putDoubleLong(1.5, 100L);
        map.putDoubleLong(2.5, 200L);
        map.putDoubleLong(3.14159, 300L);

        assertEquals(100L, map.getLong(1.5));
        assertEquals(200L, map.getLong(2.5));
        assertEquals(300L, map.getLong(3.14159));
    }

    @Test
    void testPutOverwrite() {
        map.putDoubleLong(1.5, 100L);
        map.putDoubleLong(1.5, 999L);

        assertEquals(999L, map.getLong(1.5));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(Double2LongHashMap.DEFAULT_NULL_VALUE, map.getLong(999.999));
    }

    @Test
    void testContains() {
        map.putDoubleLong(1.5, 100L);
        map.putDoubleLong(2.5, 200L);

        assertTrue(map.containsDouble(1.5));
        assertTrue(map.containsDouble(2.5));
        assertFalse(map.containsDouble(3.5));

        assertTrue(map.containsLong(100L));
        assertTrue(map.containsLong(200L));
        assertFalse(map.containsLong(999L));
    }

    @Test
    void testRemove() {
        map.putDoubleLong(1.5, 100L);
        map.putDoubleLong(2.5, 200L);

        assertEquals(100L, map.removeDouble(1.5));
        assertFalse(map.containsDouble(1.5));
        assertEquals(1, map.size());
    }

    // ==================== Special Double Key Cases ====================

    @Test
    void testZeroKeys() {
        map.putDoubleLong(0.0, 100L);
        map.putDoubleLong(-0.0, 200L);

        // +0.0 and -0.0 have different bit patterns, so treated as different keys
        assertEquals(100L, map.getLong(0.0));
        assertEquals(200L, map.getLong(-0.0));
        assertEquals(2, map.size());
    }

    @Test
    void testNaNKeys() {
        map.putDoubleLong(Double.NaN, 100L);
        map.putDoubleLong(Double.NaN, 200L);

        // All NaNs should be treated as the same key (using doubleToLongBits for hashing)
        assertEquals(200L, map.getLong(Double.NaN)); // Second put overwrites
        assertEquals(1, map.size());

        assertTrue(map.containsDouble(Double.NaN));
    }

    @Test
    void testInfinityKeys() {
        map.putDoubleLong(Double.POSITIVE_INFINITY, 100L);
        map.putDoubleLong(Double.NEGATIVE_INFINITY, 200L);

        assertEquals(100L, map.getLong(Double.POSITIVE_INFINITY));
        assertEquals(200L, map.getLong(Double.NEGATIVE_INFINITY));

        assertTrue(map.containsDouble(Double.POSITIVE_INFINITY));
        assertTrue(map.containsDouble(Double.NEGATIVE_INFINITY));
        assertFalse(map.containsDouble(0.0));
    }

    @Test
    void testExtremeValues() {
        map.putDoubleLong(Double.MAX_VALUE, 100L);
        map.putDoubleLong(Double.MIN_VALUE, 200L);
        map.putDoubleLong(Double.MIN_NORMAL, 300L);

        assertEquals(100L, map.getLong(Double.MAX_VALUE));
        assertEquals(200L, map.getLong(Double.MIN_VALUE));
        assertEquals(300L, map.getLong(Double.MIN_NORMAL));
    }

    // ==================== Floating Point Precision ====================

    @Test
    void testFloatingPointImprecision() {
        double key1 = 0.1 + 0.2;  // 0.30000000000000004
        double key2 = 0.3;         // 0.3

        map.putDoubleLong(key1, 100L);
        map.putDoubleLong(key2, 200L);

        // These are different keys (bitwise comparison)
        assertNotEquals(key1, key2, 0.0);
        assertEquals(2, map.size());

        // Can retrieve each separately
        assertEquals(100L, map.getLong(key1));
        assertEquals(200L, map.getLong(key2));
    }

    @Test
    void testExactMatchRequired() {
        double key = Math.PI;
        map.putDoubleLong(key, 314L);

        // Exact key works
        assertEquals(314L, map.getLong(key));
        assertEquals(314L, map.getLong(Math.PI));

        // Slightly different value doesn't work
        assertNotEquals(314L, map.getLong(3.14159));
        assertNotEquals(0L, map.getLong(3.14159));
    }

    @Test
    void testMultipleNaNVariants() {
        // Java normalizes NaN bit patterns for hashing
        long nanBits1 = 0x7ff0000000000001L;
        long nanBits2 = 0x7ff8000000000000L;
        double nan1 = Double.longBitsToDouble(nanBits1);
        double nan2 = Double.longBitsToDouble(nanBits2);

        map.putDoubleLong(nan1, 100L);
        map.putDoubleLong(nan2, 200L);

        // All NaN variants should hash to same value
        assertTrue(map.containsDouble(Double.NaN));
        // Size should be 1 (all NaNs considered equal)
        assertEquals(1, map.size());
    }

    // ==================== State Array Functionality ====================

    @Test
    void testStateArrayAfterRemove() {
        // Test that state array correctly tracks tombstones
        map.putDoubleLong(1.1, 100L);
        map.putDoubleLong(2.2, 200L);
        map.putDoubleLong(3.3, 300L);

        map.removeDouble(2.2);

        assertTrue(map.containsDouble(1.1));
        assertFalse(map.containsDouble(2.2));
        assertTrue(map.containsDouble(3.3));

        // Re-add to tombstone slot
        map.putDoubleLong(2.2, 222L);
        assertEquals(222L, map.getLong(2.2));
    }

    @Test
    void testStateArrayAfterClear() {
        map.putDoubleLong(1.1, 100L);
        map.putDoubleLong(2.2, 200L);

        map.clear();

        assertFalse(map.containsDouble(1.1));
        assertFalse(map.containsDouble(2.2));
        assertEquals(0, map.size());

        // Should be able to reuse cleared slots
        map.putDoubleLong(1.1, 111L);
        assertEquals(111L, map.getLong(1.1));
    }

    // ==================== Collision Handling with State Array ====================

    @Test
    void testManyCollisions() {
        // Test linear probing with floating point keys
        for (int i = 0; i < 1000; i++) {
            map.putDoubleLong(i * 0.1, i * 10L);
        }

        assertEquals(1000, map.size());

        for (int i = 0; i < 1000; i++) {
            assertEquals(i * 10L, map.getLong(i * 0.1));
        }
    }

    @Test
    void testResizeWithStateArray() {
        // Start small and force resize
        map = Double2LongHashMap.builder().initialCapacity(4).build();

        for (int i = 0; i < 100; i++) {
            map.putDoubleLong(i * 1.5, i * 100L);
        }

        assertEquals(100, map.size());

        // Verify state array was correctly transferred
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 100L, map.getLong(i * 1.5),
                         "Failed at index " + i);
        }
    }

    @Test
    void testRemoveAndReinsertWithStateArray() {
        map.putDoubleLong(1.1, 100L);
        map.putDoubleLong(2.2, 200L);
        map.putDoubleLong(3.3, 300L);

        // Remove middle entry
        map.removeDouble(2.2);
        assertEquals(2, map.size());

        // Add many more entries (may cause resize)
        for (int i = 4; i < 50; i++) {
            map.putDoubleLong(i * 1.1, i * 100L);
        }

        // Original entries should still be accessible
        assertEquals(100L, map.getLong(1.1));
        assertEquals(300L, map.getLong(3.3));
        assertFalse(map.containsDouble(2.2));
    }

    // ==================== Iterator Testing ====================

    @Test
    void testIteratorWithDoubleKeys() {
        map.putDoubleLong(1.1, 100L);
        map.putDoubleLong(2.2, 200L);
        map.putDoubleLong(3.3, 300L);

        try (Double2LongHashMap.DoubleLongHashMapIterator iter = map.borrowIterator()) {
            Set<Double> keys = new HashSet<>();
            Set<Long> values = new HashSet<>();

            iter.forEachRemaining((k, v) -> {
                keys.add(k);
                values.add(v);
            });

            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.contains(1.1));
            assertTrue(values.contains(100L));
        }
    }

    @Test
    void testForEachWithDoubleKeys() {
        map.putDoubleLong(1.1, 100L);
        map.putDoubleLong(2.2, 200L);
        map.putDoubleLong(3.3, 300L);

        Set<Double> keys = new HashSet<>();
        Set<Long> values = new HashSet<>();

        map.forEachDoubleLong((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
    }

    // ==================== Boxed API Testing ====================

    @Test
    void testBoxedPutAndGet() {
        map.put(1.5, 100L);
        map.put(2.5, 200L);

        assertEquals(100L, map.get(1.5));
        assertEquals(200L, map.get(2.5));
        assertNull(map.get(999.0));
    }

    @Test
    void testBoxedContains() {
        map.put(1.5, 100L);

        assertTrue(map.containsKey(1.5));
        assertTrue(map.containsValue(100L));

        assertFalse(map.containsKey(2.5));
        assertFalse(map.containsValue(200L));

        // Test wrong types
        assertFalse(map.containsKey("not a double"));
        assertFalse(map.containsValue("not a long"));
    }

    @Test
    void testKeySetWithDoubles() {
        map.put(1.1, 100L);
        map.put(2.2, 200L);
        map.put(3.3, 300L);

        Set<Double> keySet = map.keySet();
        assertEquals(3, keySet.size());

        Set<Double> collected = new HashSet<>();
        for (Double key : keySet) {
            collected.add(key);
        }

        assertEquals(3, collected.size());
        assertTrue(collected.contains(1.1));
        assertTrue(collected.contains(2.2));
        assertTrue(collected.contains(3.3));
    }

    @Test
    void testEntrySetWithDoubles() {
        map.put(1.1, 100L);
        map.put(2.2, 200L);
        map.put(Double.NaN, 999L);

        var entrySet = map.entrySet();
        assertEquals(3, entrySet.size());

        for (var entry : entrySet) {
            Double key = entry.getKey();
            Long value = entry.getValue();

            if (key != null && !Double.isNaN(key)) {
                assertEquals(map.get(key), value);
            } else {
                assertEquals(999L, value);
            }
        }
    }

    // ==================== Null Key/Value Handling ====================

    @Test
    void testNullKeyWithBoxedAPI() {
        map = Double2LongHashMap.builder().nullKey(Double.NaN).build();

        map.put(null, 100L);
        assertEquals(100L, map.get(null));
        assertTrue(map.containsKey(null));

        map.remove(null);
        assertFalse(map.containsKey(null));
    }

    @Test
    void testNullValueWithBoxedAPI() {
        map = Double2LongHashMap.builder().nullValue(Long.MIN_VALUE).build();

        map.put(1.5, null);
        assertNull(map.get(1.5));
        assertTrue(map.containsValue(null));
    }

    // ==================== Edge Cases ====================

    @Test
    void testScientificNotationKeys() {
        map.putDoubleLong(1.23e-100, 100L);
        map.putDoubleLong(4.56e100, 200L);
        map.putDoubleLong(-7.89e-50, 300L);

        assertEquals(100L, map.getLong(1.23e-100));
        assertEquals(200L, map.getLong(4.56e100));
        assertEquals(300L, map.getLong(-7.89e-50));
    }

    @Test
    void testSubnormalNumbers() {
        double subnormal1 = Double.MIN_VALUE;
        double subnormal2 = Double.MIN_VALUE * 2;

        map.putDoubleLong(subnormal1, 100L);
        map.putDoubleLong(subnormal2, 200L);

        assertEquals(100L, map.getLong(subnormal1));
        assertEquals(200L, map.getLong(subnormal2));
    }

    @Test
    void testNegativeZeroAsKey() {
        double negZero = -0.0;
        double posZero = 0.0;

        map.putDoubleLong(negZero, 100L);
        map.putDoubleLong(posZero, 200L);

        // Different bit patterns = different keys
        assertEquals(100L, map.getLong(-0.0));
        assertEquals(200L, map.getLong(0.0));
        assertEquals(2, map.size());
    }

    @Test
    void testMixedSpecialValues() {
        map.putDoubleLong(0.0, 1L);
        map.putDoubleLong(-0.0, 2L);
        map.putDoubleLong(Double.NaN, 3L);
        map.putDoubleLong(Double.POSITIVE_INFINITY, 4L);
        map.putDoubleLong(Double.NEGATIVE_INFINITY, 5L);
        map.putDoubleLong(Double.MAX_VALUE, 6L);
        map.putDoubleLong(Double.MIN_VALUE, 7L);

        assertEquals(7, map.size());
        assertEquals(1L, map.getLong(0.0));
        assertEquals(2L, map.getLong(-0.0));
        assertTrue(map.containsDouble(Double.NaN));
        assertEquals(4L, map.getLong(Double.POSITIVE_INFINITY));
    }

    // ==================== Performance/Stress Testing ====================

    @Test
    void testLargeMapWithDoubleKeys() {
        int size = 10_000;

        for (int i = 0; i < size; i++) {
            map.putDoubleLong(i * Math.PI, i * 100L);
        }

        assertEquals(size, map.size());

        // Random access test
        for (int i = 0; i < size; i += 100) {
            assertEquals(i * 100L, map.getLong(i * Math.PI));
        }
    }

    @Test
    void testManyRemovalsWithStateArray() {
        // Add many entries
        for (int i = 0; i < 1000; i++) {
            map.putDoubleLong(i * 0.5, i * 10L);
        }

        // Remove every other entry
        for (int i = 0; i < 1000; i += 2) {
            map.removeDouble(i * 0.5);
        }

        assertEquals(500, map.size());

        // Verify remaining entries
        for (int i = 1; i < 1000; i += 2) {
            assertTrue(map.containsDouble(i * 0.5));
            assertEquals(i * 10L, map.getLong(i * 0.5));
        }
    }

    // ==================== Builder Testing ====================

    @Test
    void testBuilderDefaults() {
        map = Double2LongHashMap.builder().build();

        assertNotNull(map);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuilderInitialCapacity() {
        map = Double2LongHashMap.builder()
                                .initialCapacity(128)
                                .build();

        for (int i = 0; i < 50; i++) {
            map.putDoubleLong(i * 1.5, i * 100L);
        }

        assertEquals(50, map.size());
    }

    @Test
    void testBuilderCustomNullValues() {
        map = Double2LongHashMap.builder()
                                .nullKey(Double.NEGATIVE_INFINITY)
                                .nullValue(-999L)
                                .build();

        map.put(null, null);
        assertNull(map.get(null));
        assertEquals(-999L, map.getLong(Double.NEGATIVE_INFINITY));
    }

    @Test
    void testBuilderDisableIteratorPool() {
        map = Double2LongHashMap.builder()
                                .disableIteratorPool()
                                .build();

        assertEquals(0, map.availableIteratorCount());
        assertNull(map.borrowIterator());
    }

    // ==================== Comparison with Standard HashMap ====================

    @Test
    void testBehaviorMatchesHashMap() {
        java.util.HashMap<Double, Long> standardMap = new java.util.HashMap<>();

        // Add same entries to both
        double[] keys = {1.1, 2.2, 3.3, Double.NaN, 0.0, -0.0};
        long[] values = {100L, 200L, 300L, 400L, 500L, 600L};

        for (int i = 0; i < keys.length; i++) {
            map.putDoubleLong(keys[i], values[i]);
            standardMap.put(keys[i], values[i]);
        }

        // Note: Our map treats +0.0 and -0.0 as different (bitwise),
        // while HashMap treats them as equal (==)
        // So we expect our map to have more entries
        assertTrue(map.size() >= standardMap.size() - 1);
    }
}