package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Long2IntHashMapTest {

    private Long2IntHashMap map;

    @BeforeEach
    void setUp() {
        map = Long2IntHashMap.builder().build();
    }

    @Test
    void testPutAndGet() {
        map.putInt(1L, 100);
        map.putInt(2L, 200);
        map.putInt(3L, 300);

        assertEquals(100, map.getInt(1L));
        assertEquals(200, map.getInt(2L));
        assertEquals(300, map.getInt(3L));
    }

    @Test
    void testPutOverwrite() {
        map.putInt(1L, 100);
        map.putInt(1L, 999);

        assertEquals(999, map.getInt(1L));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(map.nullValue, map.getInt(999L));
    }

    @Test
    void testRemove() {
        map.putInt(1L, 100);
        map.putInt(2L, 200);

        assertEquals(100, map.removeInt(1L));
        assertFalse(map.containsLongKey(1L));
        assertEquals(1, map.size());
    }

    @Test
    void testLargeKeyValues() {
        long key1 = Long.MAX_VALUE;
        long key2 = Long.MAX_VALUE - 1;
        long key3 = Long.MIN_VALUE + 1; // Avoid MIN_VALUE (tombstone)

        map.putInt(key1, 100);
        map.putInt(key2, 200);
        map.putInt(key3, 300);

        assertEquals(100, map.getInt(key1));
        assertEquals(200, map.getInt(key2));
        assertEquals(300, map.getInt(key3));
    }

    @Test
    void testZeroKey() {
        // 0L is EMPTY_KEY sentinel
        map.putInt(0L, 999);
        assertEquals(999, map.getInt(0L));
        assertTrue(map.containsLongKey(0L));

        map.removeInt(0L);
        assertFalse(map.containsLongKey(0L));
    }

    @Test
    void testMinValueKey() {
        assertThrows(IllegalArgumentException.class, () -> map.putInt(Long.MIN_VALUE, 123));
        assertThrows(IllegalArgumentException.class, () -> map.containsLongKey(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> map.getInt(Long.MIN_VALUE));
    }

    @Test
    void testNegativeKeys() {
        map.putInt(-1L, 100);
        map.putInt(-1000L, 200);
        map.putInt(-999999L, 300);

        assertEquals(100, map.getInt(-1L));
        assertEquals(200, map.getInt(-1000L));
        assertEquals(300, map.getInt(-999999L));
    }

    @Test
    void testHashDistribution() {
        // Test that long hash function (XOR with high bits) works
        long[] keys = {0x0000000100000001L,
                       0x0000000200000002L,
                       0x0000000300000003L,
                       0xFFFFFFFF00000000L,
                       0x00000000FFFFFFFFL};

        for (int i = 0; i < keys.length; i++) {
            map.putInt(keys[i], i * 100);
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
            map.putInt(i, (int) (i % 1000));
        }

        assertEquals(1000, map.size());

        for (long i = 1000000L; i < 1001000L; i++) {
            assertEquals((int) (i % 1000), map.getInt(i));
        }
    }

    @Test
    void testFullIntRange() {
        map.putInt(1L, Integer.MAX_VALUE);
        map.putInt(2L, Integer.MIN_VALUE);
        map.putInt(3L, 0);
        map.putInt(4L, -1);

        assertEquals(Integer.MAX_VALUE, map.getInt(1L));
        assertEquals(Integer.MIN_VALUE, map.getInt(2L));
        assertEquals(0, map.getInt(3L));
        assertEquals(-1, map.getInt(4L));
    }

    @Test
    void testIntegerMinValueAsValue() {
        // Integer.MIN_VALUE might be used as nullValue - ensure it works as regular value
        map.putInt(1L, Integer.MIN_VALUE);
        map.putInt(2L, Integer.MIN_VALUE);

        assertEquals(Integer.MIN_VALUE, map.getInt(1L));
        assertEquals(Integer.MIN_VALUE, map.getInt(2L));
        assertTrue(map.containsIntValue(Integer.MIN_VALUE));
    }

    @Test
    void testResizeWithLongKeys() {
        map = Long2IntHashMap.builder().initialCapacity(4).build();

        // Force multiple resizes
        for (long i = 0; i < 1000; i++) {
            map.putInt(i * 1000000L, (int) i);
        }

        assertEquals(1000, map.size());

        // Verify all entries after resize
        for (long i = 0; i < 1000; i++) {
            assertEquals((int) i, map.getInt(i * 1000000L));
        }
    }

    @Test
    void testManyCollisions() {
        // Create keys that likely collide
        for (long i = 0; i < 1000; i++) {
            long key = i << 32 | i; // High and low 32 bits same
            map.putInt(key, (int) i);
        }

        assertEquals(1000, map.size());

        for (long i = 0; i < 1000; i++) {
            long key = i << 32 | i;
            assertEquals((int) i, map.getInt(key));
        }
    }

    @Test
    void testIteratorWithLongKeys() {
        map.putInt(100L, 1);
        map.putInt(200L, 2);
        map.putInt(300L, 3);

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
        map.putInt(100L, 1);
        map.putInt(200L, 2);
        map.putInt(300L, 3);

        Set<Long> keys = new HashSet<>();
        Set<Integer> values = new HashSet<>();

        map.forEachLongInt((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
    }

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

    @Test
    void testNullValueWithBoxedAPI() {
        map = Long2IntHashMap.builder().nullValue(-999).build();
        assertThrows(NullPointerException.class, () -> map.put(100L, null));
    }

    @Test
    void testLargeMap() {
        int size = 100_000;

        for (long i = 0; i < size; i++) {
            map.putInt(i * 1000L, (int) i);
        }

        assertEquals(size, map.size());

        // Random access test
        for (long i = 0; i < size; i += 1000) {
            assertEquals((int) i, map.getInt(i * 1000L));
        }
    }

    @Test
    void testManyRemovals() {
        for (long i = 0; i < 1000; i++) {
            map.putInt(i, (int) i);
        }

        // Remove every other entry
        for (long i = 0; i < 1000; i += 2) {
            map.removeInt(i);
        }

        assertEquals(500, map.size());

        // Verify remaining entries
        for (long i = 1; i < 1000; i += 2) {
            assertTrue(map.containsLongKey(i));
            assertEquals((int) i, map.getInt(i));
        }
    }

    @Test
    void testBuilderDefaults() {
        map = Long2IntHashMap.builder().build();

        assertNotNull(map);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuilderCustomValues() {
        map = Long2IntHashMap.builder().initialCapacity(64).loadFactor(0.6f).nullValue(-777).build();

        assertNull(map.get(1L));
        assertEquals(-777, map.getInt(1L));
    }
}