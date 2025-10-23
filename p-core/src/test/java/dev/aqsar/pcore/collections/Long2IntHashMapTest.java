package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Long2IntHashMap - long keys to int values
 * Tests sentinel value handling with different key/value types
 */
class Long2IntHashMapTest {

    private Long2IntHashMap map;

    @BeforeEach
    void setUp() {
        map = Long2IntHashMap.builder().build();
    }

    // ==================== Basic Operations ====================

    @Test
    void testPutAndGet() {
        map.putLongInt(1L, 100);
        map.putLongInt(2L, 200);
        map.putLongInt(3L, 300);

        assertEquals(100, map.getInt(1L));
        assertEquals(200, map.getInt(2L));
        assertEquals(300, map.getInt(3L));
    }

    @Test
    void testPutOverwrite() {
        map.putLongInt(1L, 100);
        map.putLongInt(1L, 999);

        assertEquals(999, map.getInt(1L));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(0, map.getInt(999L));
    }

    @Test
    void testRemove() {
        map.putLongInt(1L, 100);
        map.putLongInt(2L, 200);

        assertEquals(100, map.removeLong(1L));
        assertFalse(map.containsLong(1L));
        assertEquals(1, map.size());
    }

    // ==================== Large Key Values ====================

    @Test
    void testLargeKeyValues() {
        long key1 = Long.MAX_VALUE;
        long key2 = Long.MAX_VALUE - 1;
        long key3 = Long.MIN_VALUE + 1; // Avoid MIN_VALUE (tombstone)

        map.putLongInt(key1, 100);
        map.putLongInt(key2, 200);
        map.putLongInt(key3, 300);

        assertEquals(100, map.getInt(key1));
        assertEquals(200, map.getInt(key2));
        assertEquals(300, map.getInt(key3));
    }

    @Test
    void testZeroKey() {
        // 0L is EMPTY_KEY sentinel
        map.putLongInt(0L, 999);
        assertEquals(999, map.getInt(0L));
        assertTrue(map.containsLong(0L));

        map.removeLong(0L);
        assertFalse(map.containsLong(0L));
    }

    @Test
    void testMinValueKey() {
        // Long.MIN_VALUE is TOMBSTONE_KEY sentinel
        map.putLongInt(Long.MIN_VALUE, 999);
        assertEquals(999, map.getInt(Long.MIN_VALUE));
        assertTrue(map.containsLong(Long.MIN_VALUE));
    }

    @Test
    void testNegativeKeys() {
        map.putLongInt(-1L, 100);
        map.putLongInt(-1000L, 200);
        map.putLongInt(-999999L, 300);

        assertEquals(100, map.getInt(-1L));
        assertEquals(200, map.getInt(-1000L));
        assertEquals(300, map.getInt(-999999L));
    }

    // ==================== Hash Function Testing ====================

    @Test
    void testHashDistribution() {
        // Test that long hash function (XOR with high bits) works
        long[] keys = {
            0x0000000100000001L,
            0x0000000200000002L,
            0x0000000300000003L,
            0xFFFFFFFF00000000L,
            0x00000000FFFFFFFFL
        };

        for (int i = 0; i < keys.length; i++) {
            map.putLongInt(keys[i], i * 100);
        }

        assertEquals(keys.length, map.size());

        for (int i = 0; i < keys.length; i++) {
            assertEquals(i * 100, map.getInt(keys[i]));
        }
    }

    @Test
    void testSequentialKeys() {
        // Test with sequential long keys
        for (long i = 1000000L; i < 1001000L; i++) {
            map.putLongInt(i, (int)(i % 1000));
        }

        assertEquals(1000, map.size());

        for (long i = 1000000L; i < 1001000L; i++) {
            assertEquals((int)(i % 1000), map.getInt(i));
        }
    }

    // ==================== Integer Value Range ====================

    @Test
    void testFullIntRange() {
        map.putLongInt(1L, Integer.MAX_VALUE);
        map.putLongInt(2L, Integer.MIN_VALUE);
        map.putLongInt(3L, 0);
        map.putLongInt(4L, -1);

        assertEquals(Integer.MAX_VALUE, map.getInt(1L));
        assertEquals(Integer.MIN_VALUE, map.getInt(2L));
        assertEquals(0, map.getInt(3L));
        assertEquals(-1, map.getInt(4L));
    }

    @Test
    void testIntegerMinValueAsValue() {
        // Integer.MIN_VALUE might be used as nullValue - ensure it works as regular value
        map.putLongInt(1L, Integer.MIN_VALUE);
        map.putLongInt(2L, Integer.MIN_VALUE);

        assertEquals(Integer.MIN_VALUE, map.getInt(1L));
        assertEquals(Integer.MIN_VALUE, map.getInt(2L));
        assertTrue(map.containsInt(Integer.MIN_VALUE));
    }

    // ==================== Resize with Long Keys ====================

    @Test
    void testResizeWithLongKeys() {
        map = Long2IntHashMap.builder().initialCapacity(4).build();

        // Force multiple resizes
        for (long i = 0; i < 1000; i++) {
            map.putLongInt(i * 1000000L, (int)i);
        }

        assertEquals(1000, map.size());

        // Verify all entries after resize
        for (long i = 0; i < 1000; i++) {
            assertEquals((int)i, map.getInt(i * 1000000L));
        }
    }

    // ==================== Collision Handling ====================

    @Test
    void testManyCollisions() {
        // Create keys that likely collide
        for (long i = 0; i < 1000; i++) {
            long key = i << 32 | i; // High and low 32 bits same
            map.putLongInt(key, (int)i);
        }

        assertEquals(1000, map.size());

        for (long i = 0; i < 1000; i++) {
            long key = i << 32 | i;
            assertEquals((int)i, map.getInt(key));
        }
    }

    // ==================== Iterator Testing ====================

    @Test
    void testIteratorWithLongKeys() {
        map.putLongInt(100L, 1);
        map.putLongInt(200L, 2);
        map.putLongInt(300L, 3);

        try (Long2IntHashMap.LongIntHashMapIterator iter = map.borrowIterator()) {
            Set<Long> keys = new HashSet<>();
            Set<Integer> values = new HashSet<>();

            iter.forEachRemaining((k, v) -> {
                keys.add(k);
                values.add(v);
            });

            assertEquals(3, keys.size());
            assertEquals(3, values.size());
            assertTrue(keys.contains(100L));
            assertTrue(values.contains(1));
        }
    }

    @Test
    void testForEachWithLongKeys() {
        map.putLongInt(100L, 1);
        map.putLongInt(200L, 2);
        map.putLongInt(300L, 3);

        Set<Long> keys = new HashSet<>();
        Set<Integer> values = new HashSet<>();

        map.forEachLongInt((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
    }

    // ==================== Boxed API Testing ====================

    @Test
    void testBoxedPutAndGet() {
        map.put(100L, 1);
        map.put(200L, 2);
        map.put(300L, 3);

        assertEquals(1, map.get(100L));
        assertEquals(2, map.get(200L));
        assertEquals(3, map.get(300L));
        assertNull(map.get(999L));
    }

    @Test
    void testBoxedContains() {
        map.put(100L, 1);

        assertTrue(map.containsKey(100L));
        assertTrue(map.containsValue(1));

        assertFalse(map.containsKey(200L));
        assertFalse(map.containsValue(2));

        // Test wrong types
        assertFalse(map.containsKey("not a long"));
        assertFalse(map.containsValue("not an int"));
    }

    @Test
    void testKeySetWithLongs() {
        map.put(100L, 1);
        map.put(200L, 2);
        map.put(300L, 3);

        Set<Long> keySet = map.keySet();
        assertEquals(3, keySet.size());

        Set<Long> collected = new HashSet<>();
        for (Long key : keySet) {
            collected.add(key);
        }

        assertEquals(3, collected.size());
        assertTrue(collected.contains(100L));
        assertTrue(collected.contains(200L));
        assertTrue(collected.contains(300L));
    }

    @Test
    void testValuesWithInts() {
        map.put(100L, 1);
        map.put(200L, 2);
        map.put(300L, 3);

        var values = map.values();
        assertEquals(3, values.size());

        Set<Integer> collected = new HashSet<>();
        for (Integer value : values) {
            collected.add(value);
        }

        assertEquals(3, collected.size());
        assertTrue(collected.contains(1));
        assertTrue(collected.contains(2));
        assertTrue(collected.contains(3));
    }

    @Test
    void testEntrySet() {
        map.put(100L, 1);
        map.put(200L, 2);
        map.put(300L, 3);

        var entrySet = map.entrySet();
        assertEquals(3, entrySet.size());

        Set<Long> keys = new HashSet<>();
        Set<Integer> values = new HashSet<>();

        for (var entry : entrySet) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
    }

    // ==================== Null Handling ====================

    @Test
    void testNullKeyWithBoxedAPI() {
        map = Long2IntHashMap.builder().nullKey(-999L).build();

        map.put(null, 100);
        assertEquals(100, map.get(null));
        assertTrue(map.containsKey(null));
    }

    @Test
    void testNullValueWithBoxedAPI() {
        map = Long2IntHashMap.builder().nullValue(-999).build();

        map.put(100L, null);
        assertNull(map.get(100L));
        assertTrue(map.containsValue(null));
    }

    // ==================== Performance Testing ====================

    @Test
    void testLargeMap() {
        int size = 100_000;

        for (long i = 0; i < size; i++) {
            map.putLongInt(i * 1000L, (int)i);
        }

        assertEquals(size, map.size());

        // Random access test
        for (long i = 0; i < size; i += 1000) {
            assertEquals((int)i, map.getInt(i * 1000L));
        }
    }

    @Test
    void testManyRemovals() {
        for (long i = 0; i < 1000; i++) {
            map.putLongInt(i, (int)i);
        }

        // Remove every other entry
        for (long i = 0; i < 1000; i += 2) {
            map.removeLong(i);
        }

        assertEquals(500, map.size());

        // Verify remaining entries
        for (long i = 1; i < 1000; i += 2) {
            assertTrue(map.containsLong(i));
            assertEquals((int)i, map.getInt(i));
        }
    }

    // ==================== Builder Testing ====================

    @Test
    void testBuilderDefaults() {
        map = Long2IntHashMap.builder().build();

        assertNotNull(map);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuilderCustomValues() {
        map = Long2IntHashMap.builder()
                .initialCapacity(64)
                .loadFactor(0.6f)
                .nullKey(-888L)
                .nullValue(-777)
                .build();

        map.put(null, null);
        assertNull(map.get(null));
    }
}