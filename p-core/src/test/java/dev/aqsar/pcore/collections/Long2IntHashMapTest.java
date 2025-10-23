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
        map.put(1L, 100);
        map.put(2L, 200);
        map.put(3L, 300);

        assertEquals(100, map.get(1L));
        assertEquals(200, map.get(2L));
        assertEquals(300, map.get(3L));
    }

    @Test
    void testPutOverwrite() {
        map.put(1L, 100);
        map.put(1L, 999);

        assertEquals(999, map.get(1L));
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(map.nullValue, map.get(999L));
    }

    @Test
    void testRemove() {
        map.put(1L, 100);
        map.put(2L, 200);

        assertEquals(100, map.remove(1L));
        assertFalse(map.containsKey(1L));
        assertEquals(1, map.size());
    }

    @Test
    void testLargeKeyValues() {
        long key1 = Long.MAX_VALUE;
        long key2 = Long.MAX_VALUE - 1;
        long key3 = Long.MIN_VALUE + 1; // Avoid MIN_VALUE (tombstone)

        map.put(key1, 100);
        map.put(key2, 200);
        map.put(key3, 300);

        assertEquals(100, map.get(key1));
        assertEquals(200, map.get(key2));
        assertEquals(300, map.get(key3));
    }

    @Test
    void testZeroKey() {
        // 0L is EMPTY_KEY sentinel
        map.put(0L, 999);
        assertEquals(999, map.get(0L));
        assertTrue(map.containsKey(0L));

        map.remove(0L);
        assertFalse(map.containsKey(0L));
    }

    @Test
    void testMinValueKey() {
        assertThrows(IllegalArgumentException.class, () -> map.put(Long.MIN_VALUE, 123));
        assertThrows(IllegalArgumentException.class, () -> map.containsKey(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> map.get(Long.MIN_VALUE));
    }

    @Test
    void testNegativeKeys() {
        map.put(-1L, 100);
        map.put(-1000L, 200);
        map.put(-999999L, 300);

        assertEquals(100, map.get(-1L));
        assertEquals(200, map.get(-1000L));
        assertEquals(300, map.get(-999999L));
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
            map.put(keys[i], i * 100);
        }

        assertEquals(keys.length, map.size());

        for (int i = 0; i < keys.length; i++) {
            assertEquals(i * 100, map.get(keys[i]));
        }
    }

    @Test
    void testSequentialKeys() {
        // Test with sequential long keys
        for (long i = 1000000L; i < 1001000L; i++) {
            map.put(i, (int) (i % 1000));
        }

        assertEquals(1000, map.size());

        for (long i = 1000000L; i < 1001000L; i++) {
            assertEquals((int) (i % 1000), map.get(i));
        }
    }

    @Test
    void testFullIntRange() {
        map.put(1L, Integer.MAX_VALUE);
        map.put(2L, Integer.MIN_VALUE);
        map.put(3L, 0);
        map.put(4L, -1);

        assertEquals(Integer.MAX_VALUE, map.get(1L));
        assertEquals(Integer.MIN_VALUE, map.get(2L));
        assertEquals(0, map.get(3L));
        assertEquals(-1, map.get(4L));
    }

    @Test
    void testIntegerMinValueAsValue() {
        // Integer.MIN_VALUE might be used as nullValue - ensure it works as regular value
        map.put(1L, Integer.MIN_VALUE);
        map.put(2L, Integer.MIN_VALUE);

        assertEquals(Integer.MIN_VALUE, map.get(1L));
        assertEquals(Integer.MIN_VALUE, map.get(2L));
        assertTrue(map.containsValue(Integer.MIN_VALUE));
    }

    @Test
    void testResizeWithLongKeys() {
        map = Long2IntHashMap.builder().initialCapacity(4).build();

        // Force multiple resizes
        for (long i = 0; i < 1000; i++) {
            map.put(i * 1000000L, (int) i);
        }

        assertEquals(1000, map.size());

        // Verify all entries after resize
        for (long i = 0; i < 1000; i++) {
            assertEquals((int) i, map.get(i * 1000000L));
        }
    }

    @Test
    void testManyCollisions() {
        // Create keys that likely collide
        for (long i = 0; i < 1000; i++) {
            long key = i << 32 | i; // High and low 32 bits same
            map.put(key, (int) i);
        }

        assertEquals(1000, map.size());

        for (long i = 0; i < 1000; i++) {
            long key = i << 32 | i;
            assertEquals((int) i, map.get(key));
        }
    }

    @Test
    void testIteratorWithLongKeys() {
        map.put(100L, 1);
        map.put(200L, 2);
        map.put(300L, 3);

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
        map.put(100L, 1);
        map.put(200L, 2);
        map.put(300L, 3);

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
        map.put(Long.valueOf(100L), Integer.valueOf(1));
        map.put(Long.valueOf(200L), Integer.valueOf(2));
        map.put(Long.valueOf(300L), Integer.valueOf(3));

        assertEquals(1, map.get(Long.valueOf(100L)));
        assertEquals(2, map.get(Long.valueOf(200L)));
        assertEquals(3, map.get(Long.valueOf(300L)));
        assertNull(map.get(Long.valueOf(999L)));
    }

    @Test
    void testBoxedContains() {
        map.put(100L, 1);

        assertTrue(map.containsKey(Long.valueOf(100L)));
        assertTrue(map.containsValue(Integer.valueOf(1)));

        assertFalse(map.containsKey(Long.valueOf(200L)));
        assertFalse(map.containsValue(Integer.valueOf(2)));

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
            map.put(i * 1000L, (int) i);
        }

        assertEquals(size, map.size());

        // Random access test
        for (long i = 0; i < size; i += 1000) {
            assertEquals((int) i, map.get(i * 1000L));
        }
    }

    @Test
    void testManyRemovals() {
        for (long i = 0; i < 1000; i++) {
            map.put(i, (int) i);
        }

        // Remove every other entry
        for (long i = 0; i < 1000; i += 2) {
            map.remove(i);
        }

        assertEquals(500, map.size());

        // Verify remaining entries
        for (long i = 1; i < 1000; i += 2) {
            assertTrue(map.containsKey(i));
            assertEquals((int) i, map.get(i));
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

        assertNull(map.get(Long.valueOf(1L)));
        assertEquals(-777, map.get(1L));
    }
}