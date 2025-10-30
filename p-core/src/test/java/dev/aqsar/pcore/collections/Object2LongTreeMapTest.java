package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.ConcurrentModificationException;

import static org.junit.jupiter.api.Assertions.*;

class Object2LongTreeMapTest {

    private Object2LongTreeMap<String> map;
    private final long NULL_VALUE = Object2LongTreeMap.DEFAULT_NULL_VALUE; // Assuming 0L

    @BeforeEach
    void setUp() {
        // Build with default null value (0L)
        map = Object2LongTreeMap.<String>builder().build();
    }

    @Test
    void testPutAndGetPrimitive() {
        map.put("one", 1L);
        map.put("two", 2L);
        assertEquals(1L, map.getLong("one"));
        assertEquals(2L, map.getLong("two"));
        assertEquals(NULL_VALUE, map.getLong("three")); // missing key
    }

    @Test
    void testPutAndGetBoxed() {
        map.put("one", 1L);
        assertEquals(1L, map.get("one")); // Autoboxing
        assertNull(map.get("three")); // Boxed get returns null
    }

    @Test
    void testPutOverwrite() {
        map.put("alpha", 100L);
        map.put("alpha", 200L);
        assertEquals(200L, map.getLong("alpha"));
        assertEquals(1, map.size());
    }

    @Test
    void testGetOrDefaultPrimitive() {
        map.put("five", 5L);
        assertEquals(5L, map.getOrDefault("five", -1L));
        assertEquals(-1L, map.getOrDefault("ninety-nine", -1L));
    }

    @Test
    void testRemoveExistingAndMissingPrimitive() {
        map.put("a", 1L);
        map.put("b", 2L);
        assertEquals(1L, map.removeLong("a"));
        assertEquals(NULL_VALUE, map.removeLong("zzz")); // missing key
        assertEquals(1, map.size());
    }

    @Test
    void testSizeAndClear() {
        map.put("a", 1L);
        map.put("b", 2L);
        assertEquals(2, map.size());
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void testContainsKeyAndValue() {
        map.put("X", 100L);
        map.put("Y", 200L);
        assertTrue(map.containsKey("X"));
        assertTrue(map.containsValue(200L));
        assertFalse(map.containsKey("ZZZ"));
        assertFalse(map.containsValue(999L));
    }

    @Test
    void testDuplicateValuesDifferentKeys() {
        map.put("one", 123L);
        map.put("two", 123L);
        assertEquals(2, map.size());
        assertTrue(map.containsValue(123L));
    }

    @Test
    void testStringKeySorting() {
        map.put("c", 3L);
        map.put("a", 1L);
        map.put("b", 2L);
        map.put("aa", 0L);

        assertEquals("a", map.firstKey());
        assertEquals("c", map.lastKey());
        assertEquals("aa", map.lowerKey("b"));
        assertEquals("c", map.higherKey("b"));
    }

    @Test
    void testBuilderWithCustomNullValue() {
        map = Object2LongTreeMap.<String>builder().initialCapacity(32).nullValue(-999L).build();

        assertEquals(-999L, map.nullValue);
        assertEquals(-999L, map.getLong("missing"));
        assertNull(map.get("missing")); // Boxed get still returns null
    }

    @Test
    void testPutAllFromMap() {
        Map<String, Long> src = new HashMap<>();
        src.put("a", 1L);
        src.put("b", 2L);
        src.put("c", 3L);

        map.putAll(src);
        assertEquals(3, map.size());
        assertEquals(1L, map.getLong("a"));
        assertEquals(2L, map.getLong("b"));
        assertEquals(3L, map.getLong("c"));
    }

    @Test
    void testInOrderKeysAreSorted() {
        map.put("z", 26L);
        map.put("y", 25L);
        map.put("a", 1L);
        map.put("c", 3L);
        map.put("b", 2L);

        String prev = "";
        for (var e : map.entrySet()) {
            assertTrue(e.getKey().compareTo(prev) > 0, "Tree not sorted");
            prev = e.getKey();
        }
    }

    @Test
    void testRemoveMiddleNodesMaintainsOrder() {
        int added = 0;
        for (int i = 0; i < 26; i++) {
            String key = String.valueOf((char) ('a' + i));
            map.put(key, i);
            added++;
        }

        assertEquals(added, map.size());

        int removed = 0;
        // Remove every other key
        for (int i = 1; i < 25; i += 2) {
            String key = String.valueOf((char) ('a' + i));
            map.remove(key);
            removed++;
        }

        assertEquals(added - removed, map.size());

        String prev = "";
        for (var e : map.entrySet()) {
            assertTrue(e.getKey().compareTo(prev) > 0, "Tree not sorted after removes");
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
        var iters = new Object2LongTreeMap.ObjectLongTreeMapIterator[8];
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
        try (var ignored = map.borrowIterator()) {
            assertEquals(7, map.availableIteratorCount());
        }
        assertEquals(8, map.availableIteratorCount());
    }

    @Test
    void testIteratorTraversalOrder() {
        map.put("C", 3L);
        map.put("A", 1L);
        map.put("B", 2L);

        List<String> keys = new ArrayList<>();
        try (var it = map.borrowIterator()) {
            while (it.hasNext()) {
                keys.add(it.nextKey());
            }
        }
        assertEquals(List.of("A", "B", "C"), keys);
    }

    @Test
    void testIteratorConcurrentModificationThrows() {
        map.put("A", 1L);
        map.put("B", 2L);

        try (var it = map.borrowIterator()) {
            it.nextKey();
            map.put("C", 3L);
            assertThrows(ConcurrentModificationException.class, it::nextKey);
        }
    }

    @Test
    void testForEachIteratesAllEntries() {
        map.put("A", 1L);
        map.put("B", 2L);
        map.put("C", 3L);

        Set<String> keys = new HashSet<>();
        Set<Long> values = new HashSet<>();

        map.forEachObjectLong((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(Set.of("A", "B", "C"), keys);
        assertEquals(Set.of(1L, 2L, 3L), values);
    }

    @Test
    void testForEachEmptyMap() {
        map.forEachObjectLong((k, v) -> fail("Should not be called"));
    }

    @Test
    void testReinsertAfterRemove() {
        map.put("A", 1L);
        map.remove("A");
        map.put("A", 2L);
        assertEquals(2L, map.getLong("A"));
        assertEquals(1, map.size());
    }

    @Test
    void testLargeScaleInsertionsAndLookups() {
        int n = 5000;
        for (int i = 0; i < n; i++) {
            map.put("v" + i, i);
        }
        assertEquals(n, map.size());
        for (int i = 0; i < n; i += 100) {
            assertEquals(i, map.getLong("v" + i));
        }
    }
}
