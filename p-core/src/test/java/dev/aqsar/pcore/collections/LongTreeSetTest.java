package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LongTreeSetTest {

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        private LongTreeSet set;

        @BeforeEach
        void setUp() {
            set = LongTreeSet.builder().build();
        }

        @Test
        @DisplayName("should create empty set")
        void testEmptySet() {
            assertEquals(0, set.size());
            assertTrue(set.isEmpty());
            assertThrows(NoSuchElementException.class, () -> set.firstLong());
            assertThrows(NoSuchElementException.class, () -> set.lastLong());
        }

        @Test
        @DisplayName("should add elements and maintain order")
        void testAdd() {
            assertTrue(set.add(20L));
            assertTrue(set.add(10L));
            assertTrue(set.add(30L));

            assertEquals(3, set.size());
            assertEquals(10L, set.firstLong());
            assertEquals(30L, set.lastLong());

            assertTrue(set.contains(10L));
            assertTrue(set.contains(20L));
            assertTrue(set.contains(30L));
        }

        @Test
        @DisplayName("should not add duplicate elements")
        void testAddDuplicate() {
            assertTrue(set.add(1L));
            assertFalse(set.add(1L));
            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("should add boxed elements")
        void testAddBoxed() {
            assertTrue(set.add(Long.valueOf(10L)));
            assertTrue(set.add(Long.valueOf(20L)));

            assertEquals(2, set.size());
            assertTrue(set.contains(10L));
            assertTrue(set.contains(20L));
        }

        @Test
        @DisplayName("should throw on boxed null add")
        void testAddBoxedNull() {
            assertThrows(NullPointerException.class, () -> set.add(null));
        }

        @Test
        @DisplayName("should check containment")
        void testContains() {
            set.add(100L);
            set.add(200L);

            assertTrue(set.contains(100L));
            assertTrue(set.contains(200L));
            assertFalse(set.contains(300L));
            assertFalse(set.contains(999L));
        }

        @Test
        @DisplayName("should check boxed containment")
        void testContainsBoxed() {
            set.add(100L);

            assertTrue(set.contains(Long.valueOf(100L)));
            assertFalse(set.contains(Long.valueOf(200L)));
            assertFalse(set.contains("not a number"));
            assertFalse(set.contains(new Object()));
        }

        @Test
        @DisplayName("should remove elements")
        void testRemove() {
            set.add(10L);
            set.add(20L);
            set.add(30L);

            assertTrue(set.remove(20L));
            assertEquals(2, set.size());
            assertFalse(set.contains(20L));
            assertTrue(set.contains(10L));
            assertTrue(set.contains(30L));
            assertEquals(10L, set.firstLong());
            assertEquals(30L, set.lastLong());
        }

        @Test
        @DisplayName("should return false when removing non-existent element")
        void testRemoveNonExistent() {
            set.add(10L);
            assertFalse(set.remove(999L));
            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("should remove boxed elements")
        void testRemoveBoxed() {
            set.add(100L);
            assertTrue(set.remove(Long.valueOf(100L)));
            assertEquals(0, set.size());
            assertFalse(set.contains(100L));
        }

        @Test
        @DisplayName("should clear set")
        void testClear() {
            set.add(1L);
            set.add(2L);
            set.add(3L);
            set.clear();

            assertEquals(0, set.size());
            assertTrue(set.isEmpty());
            assertFalse(set.contains(1L));
        }
    }

    @Nested
    @DisplayName("Navigation Operations")
    class NavigationOperations {
        private LongTreeSet set;

        @BeforeEach
        void setUp() {
            set = LongTreeSet.builder().build();
            set.add(10L);
            set.add(20L);
            set.add(30L);
            set.add(40L);
            set.add(50L);
        }

        @Test
        @DisplayName("should find first and last elements")
        void testFirstLast() {
            assertEquals(10L, set.firstLong());
            assertEquals(50L, set.lastLong());
            assertEquals(10L, set.first());
            assertEquals(50L, set.last());
        }

        @Test
        @DisplayName("should poll first and last elements")
        void testPollFirstLast() {
            assertEquals(10L, set.pollFirstLong());
            assertEquals(4, set.size());
            assertEquals(50L, set.pollLastLong());
            assertEquals(3, set.size());
            assertEquals(20L, set.firstLong());
            assertEquals(40L, set.lastLong());

            assertEquals(20L, set.pollFirst());
            assertEquals(40L, set.pollLast());
            assertEquals(1, set.size());
            assertEquals(30L, set.firstLong());
        }

        @Test
        @DisplayName("should find floor elements")
        void testFloor() {
            assertEquals(30L, set.floorLong(30L));
            assertEquals(30L, set.floorLong(35L));
            assertEquals(50L, set.floorLong(100L));
            assertEquals(50L, set.floor(100L));
            assertThrows(NoSuchElementException.class, () -> set.floorLong(5L));
            assertNull(set.floor(5L));
        }

        @Test
        @DisplayName("should find ceiling elements")
        void testCeiling() {
            assertEquals(30L, set.ceilingLong(30L));
            assertEquals(40L, set.ceilingLong(35L));
            assertEquals(10L, set.ceilingLong(0L));
            assertEquals(10L, set.ceiling(0L));
            assertThrows(NoSuchElementException.class, () -> set.ceilingLong(100L));
            assertNull(set.ceiling(100L));
        }

        @Test
        @DisplayName("should find lower elements")
        void testLower() {
            assertEquals(20L, set.lowerLong(30L));
            assertEquals(30L, set.lowerLong(35L));
            assertEquals(50L, set.lowerLong(100L));
            assertEquals(50L, set.lower(100L));
            assertThrows(NoSuchElementException.class, () -> set.lowerLong(10L));
            assertNull(set.lower(10L));
        }

        @Test
        @DisplayName("should find higher elements")
        void testHigher() {
            assertEquals(40L, set.higherLong(30L));
            assertEquals(40L, set.higherLong(35L));
            assertEquals(10L, set.higherLong(0L));
            assertEquals(10L, set.higher(0L));
            assertThrows(NoSuchElementException.class, () -> set.higherLong(50L));
            assertNull(set.higher(50L));
        }

        @Test
        @DisplayName("should handle subSet")
        void testSubSet() {
            NavigableSet<Long> sub = set.subSet(20L, true, 40L, true);
            assertEquals(3, sub.size());
            assertEquals(20L, sub.first());
            assertEquals(40L, sub.last());
            assertTrue(sub.contains(30L));
            assertFalse(sub.contains(10L));

            sub.remove(30L);
            assertFalse(set.contains(30L));
            assertEquals(4, set.size());
        }

        @Test
        @DisplayName("should handle headSet")
        void testHeadSet() {
            NavigableSet<Long> head = set.headSet(30L, false);
            assertEquals(2, head.size());
            assertEquals(10L, head.first());
            assertEquals(20L, head.last());
            assertFalse(head.contains(30L));
        }

        @Test
        @DisplayName("should handle tailSet")
        void testTailSet() {
            NavigableSet<Long> tail = set.tailSet(40L, true);
            assertEquals(2, tail.size());
            assertEquals(40L, tail.first());
            assertEquals(50L, tail.last());
            assertTrue(tail.contains(40L));
        }

        @Test
        @DisplayName("should handle descendingSet")
        void testDescendingSet() {
            NavigableSet<Long> desc = set.descendingSet();
            assertEquals(50L, desc.first());
            assertEquals(10L, desc.last());
            assertEquals(40L, desc.higher(50L));
            assertEquals(20L, desc.lower(10L));

            Iterator<Long> it = desc.iterator();
            assertEquals(50L, it.next());
            assertEquals(40L, it.next());
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        private LongTreeSet set;

        @BeforeEach
        void setUp() {
            set = LongTreeSet.builder().build();
        }

        @Test
        @DisplayName("should convert to sorted array")
        void testToArray() {
            set.add(30L);
            set.add(10L);
            set.add(20L);

            long[] array = set.toLongArray();
            assertArrayEquals(new long[]{10L, 20L, 30L}, array);
        }

        @Test
        @DisplayName("should return empty array for empty set")
        void testToArrayEmpty() {
            long[] array = set.toLongArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should apply forEach consumer in sorted order")
        void testForEach() {
            set.add(30L);
            set.add(10L);
            set.add(20L);

            List<Long> visited = new ArrayList<>();
            set.forEachLong(visited::add);

            assertEquals(List.of(10L, 20L, 30L), visited);
        }

        @Test
        @DisplayName("should handle forEach on empty set")
        void testForEachEmpty() {
            set.forEachLong(value -> fail("Should not be called"));
        }
    }

    // The rest — capacity management, iterator pool, boxed iterator, and edge cases —
    // can be cloned the same way by replacing double → long and DoubleTreeSet → LongTreeSet.
}
