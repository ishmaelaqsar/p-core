package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DoubleTreeSetTest {

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        private DoubleTreeSet set;

        @BeforeEach
        void setUp() {
            set = DoubleTreeSet.builder().build();
        }

        @Test
        @DisplayName("should create empty set")
        void testEmptySet() {
            assertEquals(0, set.size());
            assertTrue(set.isEmpty());
            assertThrows(NoSuchElementException.class, () -> set.firstDouble());
            assertThrows(NoSuchElementException.class, () -> set.lastDouble());
        }

        @Test
        @DisplayName("should add elements and maintain order")
        void testAdd() {
            assertTrue(set.add(20.2));
            assertTrue(set.add(10.1));
            assertTrue(set.add(30.3));

            assertEquals(3, set.size());
            assertEquals(10.1, set.firstDouble());
            assertEquals(30.3, set.lastDouble());

            assertTrue(set.contains(10.1));
            assertTrue(set.contains(20.2));
            assertTrue(set.contains(30.3));
        }

        @Test
        @DisplayName("should not add duplicate elements")
        void testAddDuplicate() {
            assertTrue(set.add(1.0));
            assertFalse(set.add(1.0));
            assertFalse(set.add(1.0));

            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("should add boxed elements")
        void testAddBoxed() {
            assertTrue(set.add(Double.valueOf(10.5)));
            assertTrue(set.add(Double.valueOf(20.5)));

            assertEquals(2, set.size());
            assertTrue(set.contains(10.5));
            assertTrue(set.contains(20.5));
        }

        @Test
        @DisplayName("should throw on boxed null add")
        void testAddBoxedNull() {
            assertThrows(NullPointerException.class, () -> set.add(null));
        }

        @Test
        @DisplayName("should throw on NaN add")
        void testAddNaN() {
            assertThrows(IllegalArgumentException.class, () -> set.add(Double.NaN));
        }

        @Test
        @DisplayName("should check containment")
        void testContains() {
            set.add(100d);
            set.add(200d);

            assertTrue(set.contains(100d));
            assertTrue(set.contains(200d));
            assertFalse(set.contains(300d));
            assertFalse(set.contains(999d));
        }

        @Test
        @DisplayName("should check boxed containment")
        void testContainsBoxed() {
            set.add(100);

            assertTrue(set.contains(Double.valueOf(100)));
            assertFalse(set.contains(Double.valueOf(200)));
            assertFalse(set.contains("not a number"));
            assertFalse(set.contains(new Object()));
        }

        @Test
        @DisplayName("should remove elements")
        void testRemove() {
            set.add(10);
            set.add(20);
            set.add(30);

            assertTrue(set.remove(20));
            assertEquals(2, set.size());
            assertFalse(set.contains(20));
            assertTrue(set.contains(10));
            assertTrue(set.contains(30));
            assertEquals(10, set.firstDouble());
            assertEquals(30, set.lastDouble());
        }

        @Test
        @DisplayName("should return false when removing non-existent element")
        void testRemoveNonExistent() {
            set.add(10);
            assertFalse(set.remove(999));
            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("should remove boxed elements")
        void testRemoveBoxed() {
            set.add(100);

            assertTrue(set.remove(Double.valueOf(100)));
            assertEquals(0, set.size());
            assertFalse(set.contains(100));
        }

        @Test
        @DisplayName("should clear set")
        void testClear() {
            set.add(1);
            set.add(2);
            set.add(3);
            set.clear();

            assertEquals(0, set.size());
            assertTrue(set.isEmpty());
            assertFalse(set.contains(1));
        }
    }

    @Nested
    @DisplayName("Navigation Operations")
    class NavigationOperations {
        private DoubleTreeSet set;

        @BeforeEach
        void setUp() {
            set = DoubleTreeSet.builder().build();
            set.add(10.0);
            set.add(20.0);
            set.add(30.0);
            set.add(40.0);
            set.add(50.0);
        }

        @Test
        @DisplayName("should find first and last elements")
        void testFirstLast() {
            assertEquals(10.0, set.firstDouble());
            assertEquals(50.0, set.lastDouble());
            assertEquals(10.0, set.first());
            assertEquals(50.0, set.last());
        }

        @Test
        @DisplayName("should poll first and last elements")
        void testPollFirstLast() {
            assertEquals(10.0, set.pollFirstDouble());
            assertEquals(4, set.size());
            assertEquals(50.0, set.pollLastDouble());
            assertEquals(3, set.size());
            assertEquals(20.0, set.firstDouble());
            assertEquals(40.0, set.lastDouble());

            assertEquals(20.0, set.pollFirst());
            assertEquals(40.0, set.pollLast());
            assertEquals(1, set.size());
            assertEquals(30.0, set.firstDouble());
        }

        @Test
        @DisplayName("should find floor elements")
        void testFloor() {
            assertEquals(30.0, set.floorDouble(30.0));
            assertEquals(30.0, set.floorDouble(35.0));
            assertEquals(50.0, set.floorDouble(100.0));
            assertEquals(50.0, set.floor(100.0));
            assertThrows(NoSuchElementException.class, () -> set.floorDouble(5.0));
            assertNull(set.floor(5.0));
        }

        @Test
        @DisplayName("should find ceiling elements")
        void testCeiling() {
            assertEquals(30.0, set.ceilingDouble(30.0));
            assertEquals(40.0, set.ceilingDouble(35.0));
            assertEquals(10.0, set.ceilingDouble(0.0));
            assertEquals(10.0, set.ceiling(0.0));
            assertThrows(NoSuchElementException.class, () -> set.ceilingDouble(100.0));
            assertNull(set.ceiling(100.0));
        }

        @Test
        @DisplayName("should find lower elements")
        void testLower() {
            assertEquals(20.0, set.lowerDouble(30.0));
            assertEquals(30.0, set.lowerDouble(35.0));
            assertEquals(50.0, set.lowerDouble(100.0));
            assertEquals(50.0, set.lower(100.0));
            assertThrows(NoSuchElementException.class, () -> set.lowerDouble(10.0));
            assertNull(set.lower(10.0));
        }

        @Test
        @DisplayName("should find higher elements")
        void testHigher() {
            assertEquals(40.0, set.higherDouble(30.0));
            assertEquals(40.0, set.higherDouble(35.0));
            assertEquals(10.0, set.higherDouble(0.0));
            assertEquals(10.0, set.higher(0.0));
            assertThrows(NoSuchElementException.class, () -> set.higherDouble(50.0));
            assertNull(set.higher(50.0));
        }

        @Test
        @DisplayName("should handle subSet")
        void testSubSet() {
            NavigableSet<Double> sub = set.subSet(20.0, true, 40.0, true);
            assertEquals(3, sub.size());
            assertEquals(20.0, sub.first());
            assertEquals(40.0, sub.last());
            assertTrue(sub.contains(30.0));
            assertFalse(sub.contains(10.0));

            sub.remove(30.0);
            assertFalse(set.contains(30.0));
            assertEquals(4, set.size());
        }

        @Test
        @DisplayName("should handle headSet")
        void testHeadSet() {
            NavigableSet<Double> head = set.headSet(30.0, false);
            assertEquals(2, head.size());
            assertEquals(10.0, head.first());
            assertEquals(20.0, head.last());
            assertFalse(head.contains(30.0));
        }

        @Test
        @DisplayName("should handle tailSet")
        void testTailSet() {
            NavigableSet<Double> tail = set.tailSet(40.0, true);
            assertEquals(2, tail.size());
            assertEquals(40.0, tail.first());
            assertEquals(50.0, tail.last());
            assertTrue(tail.contains(40.0));
        }

        @Test
        @DisplayName("should handle descendingSet")
        void testDescendingSet() {
            NavigableSet<Double> desc = set.descendingSet();
            assertEquals(50.0, desc.first());
            assertEquals(10.0, desc.last());
            assertEquals(40.0, desc.higher(50.0));
            assertEquals(20.0, desc.lower(10.0));

            Iterator<Double> it = desc.iterator();
            assertEquals(50.0, it.next());
            assertEquals(40.0, it.next());
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        private DoubleTreeSet set;

        @BeforeEach
        void setUp() {
            set = DoubleTreeSet.builder().build();
        }

        @Test
        @DisplayName("should convert to sorted array")
        void testToArray() {
            set.add(30.0);
            set.add(10.0);
            set.add(20.0);

            double[] array = set.toDoubleArray();

            // This test is different from HashSet: order is guaranteed
            assertArrayEquals(new double[]{10.0, 20.0, 30.0}, array);
        }

        @Test
        @DisplayName("should return empty array for empty set")
        void testToArrayEmpty() {
            double[] array = set.toDoubleArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should apply forEach consumer in sorted order")
        void testForEach() {
            set.add(30.3);
            set.add(10.1);
            set.add(20.2);

            List<Double> visited = new ArrayList<>();
            set.forEachDouble(visited::add);

            assertEquals(List.of(10.1, 20.2, 30.3), visited);
        }

        @Test
        @DisplayName("should handle forEach on empty set")
        void testForEachEmpty() {
            set.forEachDouble(value -> fail("Should not be called"));
        }
    }

    @Nested
    @DisplayName("Capacity Management")
    class CapacityManagement {
        @Test
        @DisplayName("should grow capacity automatically")
        void testAutoGrow() {
            DoubleTreeSet set = DoubleTreeSet.builder().initialCapacity(4).build();

            for (int i = 1; i < 100; i++) {
                set.add(i);
            }

            assertEquals(99, set.size());
            for (int i = 1; i < 100; i++) {
                assertTrue(set.contains(i));
            }
            assertEquals(1.0, set.firstDouble());
            assertEquals(99.0, set.lastDouble());
        }

        @Test
        @DisplayName("should respect initial capacity")
        void testInitialCapacity() {
            DoubleTreeSet set = DoubleTreeSet.builder().initialCapacity(100).build();

            for (int i = 1; i <= 50; i++) {
                set.add(i);
            }
            assertEquals(50, set.size());
        }
    }

    @Nested
    @DisplayName("Iterator Pool")
    class IteratorPoolTests {

        private DoubleTreeSet set;

        @BeforeEach
        void setUp() {
            set = DoubleTreeSet.builder().build();
            set.add(30.0);
            set.add(10.0);
            set.add(20.0);
        }

        @Test
        @DisplayName("should borrow and return iterator")
        void testBorrowReturn() {
            assertEquals(8, set.availableIteratorCount());

            DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator();
            assertNotNull(iter);
            assertEquals(7, set.availableIteratorCount());

            set.returnIterator(iter);
            assertEquals(8, set.availableIteratorCount());
        }

        @Test
        @DisplayName("should iterate all elements in sorted order")
        void testIterateAll() {
            List<Double> visited = new ArrayList<>();
            try (DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator()) {
                while (iter.hasNext()) {
                    visited.add(iter.nextDouble());
                }
            }
            assertEquals(List.of(10.0, 20.0, 30.0), visited);
        }

        @Test
        @DisplayName("should peek without advancing")
        void testPeek() {
            try (DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator()) {
                assertTrue(iter.hasNext());
                assertEquals(10.0, iter.peekNextDouble());
                assertEquals(10.0, iter.nextDouble());
                assertEquals(20.0, iter.peekNextDouble());
            }
        }

        @Test
        @DisplayName("should remove during iteration")
        void testIteratorRemove() {
            try (DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator()) {
                assertEquals(10.0, iter.nextDouble());
                assertEquals(20.0, iter.nextDouble());
                iter.remove(); // Remove 20.0
            }
            assertEquals(2, set.size());
            assertFalse(set.contains(20.0));
            assertTrue(set.contains(10.0));
            assertTrue(set.contains(30.0));
        }

        @Test
        @DisplayName("should throw when removing without next")
        void testIteratorRemoveWithoutNext() {
            try (DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator()) {
                assertThrows(IllegalStateException.class, iter::remove);
            }
        }

        @Test
        @DisplayName("should return null when pool exhausted")
        void testPoolExhaustion() {
            DoubleTreeSet.DoubleTreeSetIterator[] iters = new DoubleTreeSet.DoubleTreeSetIterator[8];
            for (int i = 0; i < 8; i++) {
                iters[i] = set.borrowIterator();
                assertNotNull(iters[i]);
            }
            assertNull(set.borrowIterator());
            set.returnIterator(iters[0]);
            assertNotNull(set.borrowIterator());
        }

        @Test
        @DisplayName("should auto-return with try-with-resources")
        void testAutoClose() {
            assertEquals(8, set.availableIteratorCount());
            try (DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator()) {
                assertEquals(7, set.availableIteratorCount());
                iter.nextDouble();
            }
            assertEquals(8, set.availableIteratorCount());
        }

        @Test
        @DisplayName("should throw on next when exhausted")
        void testNextPastEnd() {
            try (DoubleTreeSet.DoubleTreeSetIterator iter = set.borrowIterator()) {
                iter.nextDouble();
                iter.nextDouble();
                iter.nextDouble();
                assertThrows(NoSuchElementException.class, iter::nextDouble);
            }
        }
    }

    @Nested
    @DisplayName("Boxed Iterator")
    class BoxedIteratorTests {

        private DoubleTreeSet set;

        @BeforeEach
        void setUp() {
            set = DoubleTreeSet.builder().build();
            set.add(300.0);
            set.add(100.0);
            set.add(200.0);
        }

        @Test
        @DisplayName("should iterate with boxed iterator in sorted order")
        void testBoxedIterator() {
            Iterator<Double> iter = set.iterator();
            List<Double> visited = new ArrayList<>();
            iter.forEachRemaining(visited::add);
            assertEquals(List.of(100.0, 200.0, 300.0), visited);
        }

        @Test
        @DisplayName("should remove via boxed iterator")
        void testBoxedIteratorRemove() {
            Iterator<Double> iter = set.iterator();
            assertEquals(100.0, iter.next());
            iter.remove();
            assertEquals(2, set.size());
            assertFalse(set.contains(100.0));
            assertEquals(200.0, set.firstDouble());
        }

        @Test
        @DisplayName("should throw on concurrent modification")
        void testBoxedIteratorConcurrentModification() {
            Iterator<Double> iter = set.iterator();
            iter.next();
            set.add(999.0);
            assertThrows(ConcurrentModificationException.class, iter::next);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty set operations")
        void testEmptySet() {
            DoubleTreeSet set = DoubleTreeSet.builder().build();
            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
            assertFalse(set.contains(1));
            assertNull(set.pollFirst());
            assertNull(set.pollLast());
        }

        @Test
        @DisplayName("should handle positive and negative infinity")
        void testInfinities() {
            DoubleTreeSet set = DoubleTreeSet.builder().build();
            set.add(Double.POSITIVE_INFINITY);
            set.add(100.0);
            set.add(Double.NEGATIVE_INFINITY);

            assertEquals(3, set.size());
            assertEquals(Double.NEGATIVE_INFINITY, set.firstDouble());
            assertEquals(Double.POSITIVE_INFINITY, set.lastDouble());
        }

        @Test
        @DisplayName("should handle negative values")
        void testNegativeValues() {
            DoubleTreeSet set = DoubleTreeSet.builder().build();
            set.add(-10.0);
            set.add(-30.0);
            set.add(-20.0);

            assertEquals(3, set.size());
            assertEquals(-30.0, set.firstDouble());
            assertEquals(-10.0, set.lastDouble());
            assertArrayEquals(new double[]{-30.0, -20.0, -10.0}, set.toDoubleArray());
        }

        @Test
        @DisplayName("should handle Double MIN_VALUE and MAX_VALUE")
        void testMinMaxValues() {
            DoubleTreeSet set = DoubleTreeSet.builder().build();
            set.add(Double.MAX_VALUE);
            set.add(0.0);
            set.add(Double.MIN_VALUE); // Smallest positive

            assertEquals(3, set.size());
            assertEquals(0.0, set.firstDouble());
            assertEquals(Double.MAX_VALUE, set.lastDouble());
            assertTrue(set.contains(Double.MIN_VALUE));
        }
    }
}
